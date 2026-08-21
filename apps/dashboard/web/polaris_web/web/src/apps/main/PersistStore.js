import { create } from "zustand";
import { devtools, persist } from "zustand/middleware";

import pako from "pako"; // Gzip Compression

import { getInitialDashboardCategory } from "./labelHelper";
import { devtoolsOptions } from "./devtoolsConfig";

// Base64 <-> bytes without materializing two N-element boxed-value arrays
// (Array.from(bytes).map(String.fromCharCode).join("")). Chunking keeps each
// String.fromCharCode.apply() call under the engine's argument-count limit while still avoiding
// per-byte allocation.
const B64_CHUNK = 0x8000; // 32k

const bytesToBase64 = (bytes) => {
    let binary = "";
    for (let i = 0; i < bytes.length; i += B64_CHUNK) {
        binary += String.fromCharCode.apply(null, bytes.subarray(i, i + B64_CHUNK));
    }
    return btoa(binary);
};

export const base64ToBytes = (base64) => {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
};

// Cheap 32-bit FNV-1a hash used only to skip re-compressing an unchanged payload — not a security
// or correctness hash, just a fast "did this change" check keyed per storage name.
const fnv1aHash = (str) => {
    let hash = 0x811c9dc5;
    for (let i = 0; i < str.length; i++) {
        hash ^= str.charCodeAt(i);
        hash = Math.imul(hash, 0x01000193);
    }
    return hash >>> 0;
};

const lastWriteHashByName = new Map();

// A write beyond this size will not fit sessionStorage's ~5-10MB quota once base64-encoded
// (base64 adds ~33%), so pre-flight rather than discover the limit by throwing and re-running the
// whole stringify+deflate pass a second time.
const QUOTA_PREFLIGHT_BYTES = 3 * 1024 * 1024;

// Factory function to create Custom Storage with Gzip Compression
// (base64ToBytes is also exported so other ad-hoc decoders — e.g. LocalStorageStore's
// cross-tab storage-event listener — don't hand-roll a second copy of the same per-byte loop.)
export const createGzipStorage = (storage) => ({
    getItem: (name) => {
        const compressedData = storage.getItem(name);
        if (!compressedData) return null;

        try {
            // Try to decode base64 & Gunzip (decompress)
            const decompressed = pako.inflate(base64ToBytes(compressedData), { to: "string" });
            return JSON.parse(decompressed);
        } catch (error) {
            // Fallback: Try to parse as plain JSON (for backward compatibility with old uncompressed data)
            try {
                const parsed = JSON.parse(compressedData);
                // If successful, re-save it in compressed format
                storage.setItem(name, bytesToBase64(pako.deflate(compressedData, { level: 1 })));
                return parsed;
            } catch (fallbackError) {
                console.error("Error reading state (tried both compressed and uncompressed):", error);
                return null;
            }
        }
    },
    setItem: (name, value) => {
        const write = (v) => {
            // Stringify, Gzip compress, then convert to Base64
            const jsonString = JSON.stringify(v);

            // Skip the write entirely if nothing changed since the last write for this key — most
            // writes to a shared zustand store are unrelated slices touching the same persisted
            // blob (e.g. a page-turn setting tableSelectedTab also re-serializes allCollections).
            const hash = fnv1aHash(jsonString);
            if (lastWriteHashByName.get(name) === hash) {
                return;
            }

            // Pre-flight the quota instead of discovering it by throwing: compressing a ~30-48MB
            // JSON string just to find out it doesn't fit wastes a full deflate pass.
            if (jsonString.length > QUOTA_PREFLIGHT_BYTES && v?.state?.allCollections?.length) {
                const withoutCollections = { ...v, state: { ...v.state, allCollections: [] } };
                write(withoutCollections);
                return;
            }

            // level: 1 — this data is compressed for browser storage quota, not transmitted over
            // the wire, so level 9's CPU cost buys single-digit percent size reduction for no
            // benefit that matters here.
            const compressed = pako.deflate(jsonString, { level: 1 });
            const base64Encoded = bytesToBase64(compressed);
            storage.setItem(name, base64Encoded);
            lastWriteHashByName.set(name, hash);
        };
        try {
            write(value);
        } catch (error) {
            // allCollections is the only persisted field large enough to blow sessionStorage's quota
            // (real accounts can have tens of thousands of collections, each carrying a urls array) —
            // on a large account, EVERY subsequent state change re-attempts compressing that same
            // oversized blob and fails again, wasting a full stringify+gzip pass each time for nothing.
            // Retry once with allCollections dropped from just this persisted write; the in-memory
            // state (and thus the app's current behavior) is untouched, and the next full page load
            // simply refetches collections fresh — the same cold-start path a first-ever visit takes.
            if (error?.name === "QuotaExceededError" && value?.state?.allCollections?.length) {
                try {
                    write({ ...value, state: { ...value.state, allCollections: [] } });
                    return;
                } catch (retryError) {
                    console.error("Error compressing state (retry without allCollections also failed):", retryError);
                    return;
                }
            }
            console.error("Error compressing state:", error);
        }
    },
    removeItem: (name) => {
        lastWriteHashByName.delete(name);
        storage.removeItem(name);
    },
});


// Custom Storage with Gzip Compression for sessionStorage
const gzipStorage = createGzipStorage(sessionStorage);

const initialState = {
    quickstartTasksCompleted: 0,
    subCategoryFromSourceConfigMap: {},
    active: '',
    allCollections: [], // Persist only this
    collectionsMap: {},
    collectionsRegistryStatusMap: {},// Keep in memory (not persisted)
    tagCollectionsMap: {},// Keep in memory (not persisted)
    hostNameMap: {}, // Keep in memory (not persisted)
    skillRiskScoreCache: { data: {}, ts: 0 }, // skillName -> maxRiskScore, in-memory only
    agenticCollectionsCache: { data: null, ts: 0 }, // {collections, trafficMap, riskScoreMap}, in-memory only
    agenticTrafficRiskCache: { data: null, ts: 0 }, // {trafficMap, riskScoreMap} only (no collections), in-memory only
    agenticSensitiveInfoCache: { data: null, ts: 0 }, // sensitiveMap, in-memory only
    guardrailPolicyNames: { data: [], ts: 0 },
    lastFetchedInfo: { lastRiskScoreInfo: 0, lastSensitiveInfo: 0 },
    lastFetchedResp: { criticalUrls: 0, riskScoreMap: {} },
    lastFetchedSeverityResp: {},
    lastCalledSensitiveInfo: 0,
    lastFetchedSensitiveResp: [],
    lastFetchedUntrackedResp: [],
    totalAPIs: 0,
    selectedSampleApi: {},
    coverageMap: {},
    trafficMap: {},
    filtersMap: {},
    tableInitialState: {},
    trafficAlerts: [],
    sendEventOnLogin: false,
    tableSelectedTab: {},
    dashboardCategory: getInitialDashboardCategory(), // Persisted across page reloads
    selectedCollectionScope: null,
};

let persistStore = (set, get) => ({
    ...initialState,
    setQuickstartTasksCompleted: (quickstartTasksCompleted) => {
        try {
            set({ quickstartTasksCompleted });
        } catch (error) {
            console.error("Error setting quickstartTasksCompleted:", error);
        }
    },
    setSubCategoryFromSourceConfigMap: (subCategoryFromSourceConfigMap) => {
        try {
            set({ subCategoryFromSourceConfigMap });
        } catch (error) {
            console.error("Error setting subCategoryFromSourceConfigMap:", error);
        }
    },
    setActive: (selected) => {
        try {
            set({ active: selected });
        } catch (error) {
            console.error("Error setting active:", error);
        }
    },
    setAllCollections: (allCollections) => {
        try {
            const optimizedCollections = allCollections.map(({ id, displayName, urlsCount, deactivated, type, automated, startTs, hostName, name, description, envType, isOutOfTestingScope, urls, skills}) => ({
                id,
                displayName,
                urlsCount,
                deactivated,
                type,
                automated,
                startTs,
                hostName,
                name,
                description,
                envType,
                isOutOfTestingScope,
                urls,
                skills,
            }));
            set({ allCollections: optimizedCollections });
        } catch (error) {
            console.error("Error setting allCollections:", error);
        }
    },
   setCollectionsMap: (collectionsMap) => {
        try {
            set({ collectionsMap });
        } catch (error) {
            console.error("Error setting collectionsMap:", error);
        }
    },

    setCollectionsRegistryStatusMap: (collectionsRegistryStatusMap) => {
        try {
            set({ collectionsRegistryStatusMap });
        } catch (error) {
            console.error("Error setting collectionsRegistryStatusMap:", error);
        }
    },

    setTagCollectionsMap: (tagCollectionsMap) => {
       try {
            set({ tagCollectionsMap });
        } catch (error) {
            console.error("Error setting tagCollectionsMap:", error);
        }
    },
    setHostNameMap: (hostNameMap) => {
        try {
            set({ hostNameMap });
        } catch (error) {
            console.error("Error setting hostNameMap:", error);
        }
    },
    setSkillRiskScoreCache: (skillRiskScoreCache) => {
        try {
            set({ skillRiskScoreCache });
        } catch (error) {
            console.error("Error setting skillRiskScoreCache:", error);
        }
    },
    setAgenticCollectionsCache: (agenticCollectionsCache) => {
        try {
            set({ agenticCollectionsCache });
        } catch (error) {
            console.error("Error setting agenticCollectionsCache:", error);
        }
    },
    setAgenticTrafficRiskCache: (agenticTrafficRiskCache) => {
        try {
            set({ agenticTrafficRiskCache });
        } catch (error) {
            console.error("Error setting agenticTrafficRiskCache:", error);
        }
    },
    setAgenticSensitiveInfoCache: (agenticSensitiveInfoCache) => {
        try {
            set({ agenticSensitiveInfoCache });
        } catch (error) {
            console.error("Error setting agenticSensitiveInfoCache:", error);
        }
    },
    setGuardrailPolicyNames: (data) => {
        try {
            set({ guardrailPolicyNames: { data, ts: Date.now() } });
        } catch (error) {
            console.error("Error setting guardrailPolicyNames:", error);
        }
    },
    clearGuardrailPolicyNames: () => {
        try {
            set({ guardrailPolicyNames: { data: [], ts: 0 } });
        } catch (error) {
            console.error("Error clearing guardrailPolicyNames:", error);
        }
    },
    setLastFetchedInfo: (lastFetchedInfo) => {
        try {
            set({ lastFetchedInfo });
        } catch (error) {
            console.error("Error setting lastFetchedInfo:", error);
        }
    },
    setLastFetchedResp: (lastFetchedResp) => {
        try {
            set({ lastFetchedResp });
        } catch (error) {
            console.error("Error setting lastFetchedResp:", error);
        }
    },
    setLastFetchedSeverityResp: (lastFetchedSeverityResp) => {
        try {
            set({ lastFetchedSeverityResp });
        } catch (error) {
            console.error("Error setting lastFetchedSeverityResp:", error);
        }
    },
    setLastCalledSensitiveInfo: (lastCalledSensitiveInfo) => {
        try {
            set({ lastCalledSensitiveInfo });
        } catch (error) {
            console.error("Error setting lastCalledSensitiveInfo:", error);
        }
    },
    setLastFetchedSensitiveResp: (lastFetchedSensitiveResp) => {
        try {
            set({ lastFetchedSensitiveResp });
        } catch (error) {
            console.error("Error setting lastFetchedSensitiveResp:", error);
        }
    },
    setLastFetchedUntrackedResp: (lastFetchedUntrackedResp) => {
        try {
            set({ lastFetchedUntrackedResp });
        } catch (error) {
            console.error("Error setting lastFetchedUntrackedResp:", error);
        }
    },
    setTotalAPIs: (totalAPIs) => {
        try {
            set({ totalAPIs });
        } catch (error) {
            console.error("Error setting totalAPIs:", error);
        }
    },
    setSelectedSampleApi: (selectedSampleApi) => {
        try {
            set({ selectedSampleApi });
        } catch (error) {
            console.error("Error setting selectedSampleApi:", error);
        }
    },
    setCoverageMap: (coverageMap) => {
        try {
            set({ coverageMap });
        } catch (error) {
            console.error("Error setting coverageMap:", error);
        }
    },
    setTrafficMap: (trafficMap) => {
        try {
            set({ trafficMap });
        } catch (error) {
            console.error("Error setting trafficMap:", error);
        }
    },
    setFiltersMap: (filtersMap) => {
        try {
            set({ filtersMap });
        } catch (error) {
            console.error("Error setting filtersMap:", error);
        }
    },
    setTableInitialState: (tableInitialState) => {
        try {
            set({ tableInitialState });
        } catch (error) {
            console.error("Error setting tableInitialState:", error);
        }
    },
    setTrafficAlerts: (trafficAlerts) => {
        try {
            set({ trafficAlerts });
        } catch (error) {
            console.error("Error setting trafficAlerts:", error);
        }
    },
    setTableSelectedTab: (tableSelectedTab) => {
        try {
            set({ tableSelectedTab });
        } catch (error) {
            console.error("Error setting tableSelectedTab:", error);
        }
    },
    setSelectedCollectionScope: (selectedCollectionScope) => {
        try {
            set({ selectedCollectionScope });
        } catch (error) {
            console.error("Error setting selectedCollectionScope:", error);
        }
    },
    resetAll: () => {
        try {
            // structuredClone, not a bare `set(initialState)` — otherwise every reset re-shares the
            // same nested objects (lastFetchedInfo, skillRiskScoreCache, ...) by reference, and a
            // mutation anywhere after one reset silently reaches back into what should be a fresh
            // initial state for the next account.
            set(structuredClone(initialState));
        } catch (error) {
            console.error("Error resetting store:", error);
        }
    },
    setDashboardCategory: (dashboardCategory) => {
        try {
            set({ dashboardCategory });
        } catch (error) {
            console.error("Error setting dashboardCategory:", error);
        }
    },
});

persistStore = devtools(persistStore, devtoolsOptions("PersistStore", (state) => ({
    ...state,
    allCollections: `<<${state.allCollections?.length ?? 0} collections>>`,
    collectionsMap: `<<${Object.keys(state.collectionsMap || {}).length} entries>>`,
    hostNameMap: `<<${Object.keys(state.hostNameMap || {}).length} entries>>`,
    tagCollectionsMap: `<<${Object.keys(state.tagCollectionsMap || {}).length} entries>>`,
    lastFetchedUntrackedResp: `<<${state.lastFetchedUntrackedResp?.length ?? 0} entries>>`,
    lastFetchedSensitiveResp: `<<redacted, ${JSON.stringify(state.lastFetchedSensitiveResp || {}).length} chars>>`,
})));
persistStore = persist(persistStore, {
    name: "Akto-data",
    storage: gzipStorage,
    partialize: (state) => ({
        allCollections: state.allCollections, // Persist only allCollections
        lastFetchedInfo: state.lastFetchedInfo,
        lastFetchedResp: state.lastFetchedResp,
        lastFetchedSeverityResp: state.lastFetchedSeverityResp,
        lastCalledSensitiveInfo: state.lastCalledSensitiveInfo,
        lastFetchedSensitiveResp: state.lastFetchedSensitiveResp,
        // lastFetchedUntrackedResp deliberately NOT persisted: it holds prebuilt React
        // elements (see ApiCollections.jsx untracked-collections handling), and JSON.stringify-ing
        // a React element tree into this codec's stringify+deflate pass is both wasted work and
        // liable to blow the sessionStorage quota on large accounts for no benefit — nothing
        // reads this back across a reload path that matters.
        totalAPIs: state.totalAPIs,
        selectedSampleApi: state.selectedSampleApi,
        coverageMap: state.coverageMap,
        trafficMap: state.trafficMap,
        filtersMap: state.filtersMap,
        tableInitialState: state.tableInitialState,
        trafficAlerts: state.trafficAlerts,
        sendEventOnLogin: state.sendEventOnLogin,
        tableSelectedTab: state.tableSelectedTab,
        dashboardCategory: state.dashboardCategory, // Persist dashboard category selection across page reloads
        selectedCollectionScope: state.selectedCollectionScope,
    })
});

const PersistStore = create(persistStore);

export default PersistStore;

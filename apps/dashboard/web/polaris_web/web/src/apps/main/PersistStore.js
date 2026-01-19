import { create } from "zustand";
import { devtools, persist } from "zustand/middleware";

import pako from "pako"; // Gzip Compression

// Custom Storage with Gzip Compression
const gzipStorage = {
    getItem: (name) => {
        const compressedData = sessionStorage.getItem(name);
        if (!compressedData) return null;

        try {
            // Decode base64 & Gunzip (decompress)
            const binaryData = atob(compressedData);
            const uint8Array = new Uint8Array(binaryData.length);
            for (let i = 0; i < binaryData.length; i++) {
                uint8Array[i] = binaryData.charCodeAt(i);
            }
            const decompressed = pako.inflate(uint8Array, { to: "string" });
            return JSON.parse(decompressed);
        } catch (error) {
            console.error("Error decompressing state:", error);
            return null;
        }
    },
    setItem: (name, value) => {
        try {
            // Stringify, Gzip compress, then convert to Base64
            const jsonString = JSON.stringify(value);
            const compressed = pako.deflate(jsonString, { level: 9 });
            const binaryString = Array.from(compressed)
                .map((byte) => String.fromCharCode(byte))
                .join("");
            const base64Encoded = btoa(binaryString);
            sessionStorage.setItem(name, base64Encoded);
        } catch (error) {
            console.error("Error compressing state:", error);
        }
    },
    removeItem: (name) => sessionStorage.removeItem(name),
};

const initialState = {
    quickstartTasksCompleted: 0,
    subCategoryFromSourceConfigMap: {},
    active: '',
    allCollections: [], // Persist only this
    collectionsMap: {},
    collectionsRegistryStatusMap: {},// Keep in memory (not persisted)
    tagCollectionsMap: {},// Keep in memory (not persisted)
    hostNameMap: {}, // Keep in memory (not persisted)
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
    dashboardCategory: 'API Security',
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
            const optimizedCollections = allCollections.map(({ id, displayName, urlsCount, deactivated, type, automated, startTs, hostName, name, description, envType, isOutOfTestingScope, urls}) => ({
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
    resetAll: () => {
        try {
            set(initialState);
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

persistStore = devtools(persistStore);
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
        lastFetchedUntrackedResp: state.lastFetchedUntrackedResp,
        totalAPIs: state.totalAPIs,
        selectedSampleApi: state.selectedSampleApi,
        coverageMap: state.coverageMap,
        trafficMap: state.trafficMap,
        filtersMap: state.filtersMap,
        tableInitialState: state.tableInitialState,
        trafficAlerts: state.trafficAlerts,
        sendEventOnLogin: state.sendEventOnLogin,
        tableSelectedTab: state.tableSelectedTab,
        dashboardCategory: state.dashboardCategory
    })
});

const PersistStore = create(persistStore);

export default PersistStore;

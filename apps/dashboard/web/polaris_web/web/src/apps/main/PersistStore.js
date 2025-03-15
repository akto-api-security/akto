import { create } from "zustand";
import { devtools, persist, createJSONStorage } from "zustand/middleware";

const initialState = {
    quickstartTasksCompleted: 0,
    subCategoryFromSourceConfigMap: {},
    active: '',
    allCollections: [], // Persist only this
    collectionsMap: {}, // Keep in memory (not persisted)
    hostNameMap: {}, // Keep in memory (not persisted)
    lastFetchedInfo: { lastRiskScoreInfo: 0, lastSensitiveInfo: 0 },
    lastFetchedResp: { criticalUrls: 0, riskScoreMap: {} },
    lastFetchedSeverityResp: {},
    lastCalledSensitiveInfo: 0,
    lastFetchedSensitiveResp: [],
    selectedSampleApi: {},
    coverageMap: {},
    filtersMap: {},
    tableInitialState: {},
    trafficAlerts: [],
    sendEventOnLogin: false,
    tableSelectedTab: {},
    threatFiltersMap: null,
};

let persistStore = (set, get) => ({
    ...initialState,
    accessToken: null,
    storeAccessToken: (accessToken) => set({ accessToken }),
    setQuickstartTasksCompleted: (quickstartTasksCompleted) => set({ quickstartTasksCompleted }),
    setSubCategoryFromSourceConfigMap: (subCategoryFromSourceConfigMap) => set({ subCategoryFromSourceConfigMap }),
    setActive: (selected) => set({ active: selected }),

    setAllCollections: (allCollections) => {
        const optimizedCollections = allCollections.map(({ id, displayName, urlsCount, deactivated, type, automated, startTs }) => ({
            id,
            displayName,
            urlsCount,
            deactivated, 
            type,
            automated,
            startTs
        }));
        set({ allCollections: optimizedCollections });
    },
    setCollectionsMap: (collectionsMap) => set({ collectionsMap }),
    setHostNameMap: (hostNameMap) => set({ hostNameMap }),
    setLastFetchedInfo: (lastFetchedInfo) => set({ lastFetchedInfo }),
    setLastFetchedResp: (lastFetchedResp) => set({ lastFetchedResp }),
    setLastFetchedSeverityResp: (lastFetchedSeverityResp) => set({ lastFetchedSeverityResp }),
    setLastCalledSensitiveInfo: (lastCalledSensitiveInfo) => set({ lastCalledSensitiveInfo }),
    setLastFetchedSensitiveResp: (lastFetchedSensitiveResp) => set({ lastFetchedSensitiveResp }),
    setSelectedSampleApi: (selectedSampleApi) => set({ selectedSampleApi }),
    setCoverageMap: (coverageMap) => set({ coverageMap }),
    setFiltersMap: (filtersMap) => set({ filtersMap }),
    setTableInitialState: (tableInitialState) => set({ tableInitialState }),
    setTrafficAlerts: (trafficAlerts) => set({ trafficAlerts }),
    setTableSelectedTab: (tableSelectedTab) => set({ tableSelectedTab }),
    setThreatFiltersMap: (threatFiltersMap) => set({ threatFiltersMap }),

    resetAll: () => set(initialState), // Reset function
});

persistStore = devtools(persistStore);
persistStore = persist(persistStore, { 
    name: "persistedStore",
    storage: createJSONStorage(() => sessionStorage),
    partialize: (state) => ({
        allCollections: state.allCollections, // Persist only allCollections
        lastFetchedInfo: state.lastFetchedInfo,
        lastFetchedResp: state.lastFetchedResp,
        lastFetchedSeverityResp: state.lastFetchedSeverityResp,
        lastCalledSensitiveInfo: state.lastCalledSensitiveInfo,
        lastFetchedSensitiveResp: state.lastFetchedSensitiveResp,
        selectedSampleApi: state.selectedSampleApi,
        coverageMap: state.coverageMap,
        filtersMap: state.filtersMap,
        tableInitialState: state.tableInitialState,
        trafficAlerts: state.trafficAlerts,
        sendEventOnLogin: state.sendEventOnLogin,
        tableSelectedTab: state.tableSelectedTab,
        threatFiltersMap: state.threatFiltersMap,
    }) 
});

const PersistStore = create(persistStore);

export default PersistStore;

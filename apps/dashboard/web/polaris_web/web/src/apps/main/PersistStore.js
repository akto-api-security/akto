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
    storeAccessToken: (accessToken) => {
        try {
            set({ accessToken });
        } catch (error) {
            console.error("Error setting accessToken:", error);
        }
    },
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
    setThreatFiltersMap: (threatFiltersMap) => {
        try {
            set({ threatFiltersMap });
        } catch (error) {
            console.error("Error setting threatFiltersMap:", error);
        }
    },
    resetAll: () => {
        try {
            set(initialState);
        } catch (error) {
            console.error("Error resetting store:", error);
        }
    },
});

persistStore = devtools(persistStore);
persistStore = persist(persistStore, { 
    name: "Akto-data",
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

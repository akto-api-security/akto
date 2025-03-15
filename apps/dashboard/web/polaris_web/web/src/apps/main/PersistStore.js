import {create} from "zustand"
import {devtools, persist, createJSONStorage} from "zustand/middleware"

const initialState = {
    quickstartTasksCompleted: 0,
    subCategoryFromSourceConfigMap: {},
    active: '',
    collectionsMap: {},
    allCollections: [],
    hostNameMap: {},
    lastFetchedInfo: {
      lastRiskScoreInfo: 0,
      lastSensitiveInfo: 0,
    },
    lastFetchedResp: {
      criticalUrls: 0,
      riskScoreMap: {},
    },
    lastFetchedSeverityResp: {},
    lastCalledSensitiveInfo: 0,
    lastFetchedSensitiveResp: [],
    selectedSampleApi: {},
    coverageMap:{},
    filtersMap:{},
    tableInitialState: {},
    trafficAlerts: [],
    sendEventOnLogin: false,
    tableSelectedTab: {},
    threatFiltersMap: null,
};

let persistStore = (set) => ({
    ...initialState,
    accessToken: null,
    storeAccessToken: (accessToken) => set({ accessToken: accessToken }),
    setQuickstartTasksCompleted: (quickstartTasksCompleted) => set({ quickstartTasksCompleted }),
    setSubCategoryFromSourceConfigMap: (subCategoryFromSourceConfigMap) => set({ subCategoryFromSourceConfigMap }),
    setActive: (selected) => set({ active: selected }),
    setCollectionsMap: (collectionsMap) => set({ collectionsMap }),
    setAllCollections: (allCollections) => {
        const optimizedCollections = allCollections.map(({ id, displayName, urlsCount, deactivated, type, automated }) => ({
            id,
            displayName,
            urlsCount,
            deactivated, 
            type,
            automated
        }));
        set({ allCollections: optimizedCollections });
    },
    setHostNameMap: (hostNameMap) => set({ hostNameMap }),
    setLastFetchedInfo: (lastFetchedInfo) => set({ lastFetchedInfo }),
    setLastFetchedResp: (lastFetchedResp) => set({ lastFetchedResp }),
    setLastFetchedSeverityResp: (lastFetchedSeverityResp) => set({ lastFetchedSeverityResp }),
    setLastCalledSensitiveInfo: (lastCalledSensitiveInfo) => set({ lastCalledSensitiveInfo }),
    setLastFetchedSensitiveResp: (lastFetchedSensitiveResp) => set({ lastFetchedSensitiveResp }),
    setSelectedSampleApi: (selectedSampleApi) => set({selectedSampleApi: selectedSampleApi}),
    setCoverageMap:(coverageMap)=>{set({coverageMap: coverageMap})},
    setFiltersMap: (filtersMap) => set({ filtersMap }),
    setTableInitialState: (tableInitialState) => set({ tableInitialState }),
    setTrafficAlerts: (trafficAlerts) =>set ({trafficAlerts}),
    setTableSelectedTab: (tableSelectedTab) => set({tableSelectedTab}),
    setThreatFiltersMap: (threatFiltersMap) => set({threatFiltersMap}),

    resetAll: () => set(initialState), // Reset function
})

persistStore = devtools(persistStore)
persistStore = persist(persistStore,{storage: createJSONStorage(() => sessionStorage)})

const PersistStore = create(persistStore);

export default PersistStore


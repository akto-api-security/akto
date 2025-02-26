import {create} from "zustand"
import {devtools, persist, createJSONStorage} from "zustand/middleware"

const initialState = {
    quickstartTasksCompleted: 0,
    subCategoryMap: {},
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
};

let persistStore = (set) => ({
    ...initialState,
    accessToken: null,
    storeAccessToken: (accessToken) => set({ accessToken: accessToken }),
    setQuickstartTasksCompleted: (quickstartTasksCompleted) => set({ quickstartTasksCompleted }),
    setSubCategoryMap: (subCategoryMap) => set({ subCategoryMap }),
    setSubCategoryFromSourceConfigMap: (subCategoryFromSourceConfigMap) => set({ subCategoryFromSourceConfigMap }),
    setActive: (selected) => set({ active: selected }),
    setCollectionsMap: (collectionsMap) => set({ collectionsMap }),
    setAllCollections: (allCollections) => set({ allCollections }),
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

    resetAll: () => set(initialState), // Reset function
})

persistStore = devtools(persistStore)
persistStore = persist(persistStore,{storage: createJSONStorage(() => sessionStorage)})

const PersistStore = create(persistStore);

export default PersistStore


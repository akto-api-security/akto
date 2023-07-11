import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    issues: [],
    currentPage: 1,
    limit: 20,
    totalIssuesCount: 0,
    filterStatus : ['OPEN'],
    filterCollectionsId : [],
    filterSeverity : [],
    filterSubCategory1 : [],
    startEpoch : 0,
    selectedIssueIds : [],
    testingRunResult: {},
    subCatogoryMap: {},
    subCategoryFromSourceConfigMap: {},
    testCategoryMap: {}
}

const issues = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE(state) {
            state.loading = false
            state.issues = []
            state.limit = 20
            state.totalIssuesCount = 0
        },
        SAVE_ISSUES(state, { issues, totalIssuesCount }) {
            state.issues = issues
            state.totalIssuesCount = totalIssuesCount
        },
        updateCurrentPage(state, {pageIndex}) {
            state.currentPage = pageIndex
        },
        updateSelectedIssueIds(state, {selectedIssueIds}) {
            state.selectedIssueIds = selectedIssueIds
        },
        updateFilters(state, {filterStatus, filterCollectionsId, filterSeverity, filterSubCategory1, startEpoch}) {
            if (filterStatus !== undefined) {
                state.filterStatus = filterStatus
            }
            if (filterCollectionsId !== undefined) {
                state.filterCollectionsId = filterCollectionsId
            }
            if (filterSeverity !== undefined) {
                state.filterSeverity = filterSeverity
            }
            if (filterSubCategory1 !== undefined) {
                state.filterSubCategory1 = filterSubCategory1
            }
            if (startEpoch !== undefined) {
                state.startEpoch = startEpoch
            }
        },
        SAVE_TESTING_RESULT(state, {testingRunResult}) {
            state.testingRunResult = testingRunResult
        }
    },
    actions: {
        emptyState({ commit }) {
            commit('EMPTY_STATE')
        },
        loadTestingResult({commit}, {issueId}) {
            return api.fetchTestingRunResult(issueId).then((resp) => {
                commit('SAVE_TESTING_RESULT',resp)
            }).catch(() => {
            })
        },
        loadIssues({ commit }) {
            commit('EMPTY_STATE')
            state.loading = true
            return api.fetchIssues((state.currentPage - 1) * state.limit
            , state.limit
            , state.filterStatus
            , state.filterCollectionsId
            , state.filterSeverity
            , state.filterSubCategory1
            , state.startEpoch)
            .then((resp) => {
                commit('SAVE_ISSUES', resp)
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        updateIssueStatus({ commit }, { selectedIssueId, selectedStatus, ignoreReason }) {
            state.loading = true
            return api.updateIssueStatus(selectedIssueId, selectedStatus, ignoreReason).then((resp) => {
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        bulkUpdateIssueStatus({ commit }, { selectedIssueIds, selectedStatus, ignoreReason }) {
            state.loading = true
            return api.bulkUpdateIssueStatus(selectedIssueIds, selectedStatus, ignoreReason).then((resp) => {
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        async fetchAllSubCategories() {
            return await api.fetchAllSubCategories().then((resp) => {
                state.subCatogoryMap = {}
                resp.subCategories.forEach((x) => {
                    state.subCatogoryMap[x.name] = x
                })
                state.subCategoryFromSourceConfigMap = {}
                resp.testSourceConfigs.forEach((x) => {
                    state.subCategoryFromSourceConfigMap[x.id] = x
                })
                state.testCategoryMap = {}
                resp.categories.forEach((x) => {
                    state.testCategoryMap[x.name] = x
                })
            }).catch((err) => { 
                console.log(err);
            })
        },
    },
    getters: {
        getLoading: (state) => state.loading,
        getIssues: (state) => state.issues,
        getCurrentPage: (state) => state.currentPage,
        getLimit: (state) => state.limit,
        getTotalIssuesCount: (state) => state.totalIssuesCount,
        getSelectedIssueIds: (state) => state.selectedIssueIds,
        getTestingRunResult: (state) => state.testingRunResult
    }
}

export default issues
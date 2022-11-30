import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    issues: [],
    currentPage: 1,
    limit: 20,
    totalIssuesCount: 0
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
        }
    },
    actions: {
        emptyState({ commit }) {
            commit('EMPTY_STATE')
        },
        loadIssues({ commit }) {
            commit('EMPTY_STATE')
            state.loading = true
            return api.fetchIssues((state.currentPage - 1) * state.limit, state.limit).then((resp) => {
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
        }
    },
    getters: {
        getLoading: (state) => state.loading,
        getIssues: (state) => state.issues,
        getCurrentPage: (state) => state.currentPage,
        getLimit: (state) => state.limit,
        getTotalIssuesCount: (state) => state.totalIssuesCount
    }
}

export default issues
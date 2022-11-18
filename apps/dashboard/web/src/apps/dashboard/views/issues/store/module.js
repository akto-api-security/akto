import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    issues: [],
    collections: []
}

const issues = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.issues = []
            state.collections = []
        },
        SAVE_ISSUES (state, {issues, collections}) {
            state.issues = issues
            state.collections = collections
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadIssues({commit}) {
            commit('EMPTY_STATE')
            state.loading = true
            return api.fetchIssues().then((resp) => {
                commit('SAVE_ISSUES', resp)

                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        }
    },
    getters: {
        getLoading: (state) => state.loading,
        getIssues: (state) => state.issues,
        getCollections:(state) => state.collections
    }
}

export default issues
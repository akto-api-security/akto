import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

var state = {
    loading: false,
    issues: []
}

const issues = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.issues = []
        },
        SAVE_ISSUES (state, {issues}) {
            state.issues = issues
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
        getIssues: (state) => state.issues
    }
}

export default issues
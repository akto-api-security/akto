import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

var state = {
    endpoints: {},
    websiteHostName: ''
}

const endpoints = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.endpoints = {}
            state.websiteHostName = ''
        },
        SAVE_ENDPOINTS (state, info) {
            state.endpoints = info.endpoints
            state.websiteHostName = info.websiteHostName
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        saveEndpoints({commit}, payload, options) {
            commit('SAVE_API_COLLECTION', payload, options)
        }
    }
}

export default endpoints

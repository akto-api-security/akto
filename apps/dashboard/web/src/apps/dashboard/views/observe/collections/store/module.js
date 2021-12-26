import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

var state = {
    loading: false,
    fetchTs: 0,
    apiCollections:[]
}

const collections = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.fetchTs = 0
            state.apiCollections = []
        },
        SAVE_API_COLLECTION (state, {apiCollections}) {
            state.apiCollections = apiCollections
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadAllApiCollections({commit}, options) {
            commit('EMPTY_STATE')
            return api.getAllCollections().then((resp) => {
                commit('SAVE_API_COLLECTION', resp, options)
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        }
    },
    getters: {
        getFetchTs: (state) => state.fetchTs,
        getLoading: (state) => state.loading,
        getAPICollections: (state) => state.apiCollections
    }
}

export default collections
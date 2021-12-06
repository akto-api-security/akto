import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

var state = {
    loading: false,
    fetchTs: 0,
    apiCollectionId: 0,
    apiCollectionName: '',
    apiCollection: []
}

const inventory = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.fetchTs = 0
            state.apiCollectionId = 0
            state.apiCollection = []
            state.apiCollectionName = ''
        },
        SAVE_API_COLLECTION (state, info) {
            state.apiCollectionId = info.apiCollectionId
            state.apiCollection = info.data.endpoints.filter(x => x.subType !== "NULL")
            state.apiCollectionName = info.data.name
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadAPICollection({commit}, {apiCollectionId}, options) {
            return api.getAPICollection(apiCollectionId).then((resp) => {
                commit('SAVE_API_COLLECTION', {data: resp.data, apiCollectionId: apiCollectionId}, options)
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        }
    },
    getters: {
        getFetchTs: (state) => state.fetchTs,
        getLoading: (state) => state.loading,
        getAPICollection: (state) => state.apiCollection,
        getAPICollectionId: (state) => state.apiCollectionId,
        getAPICollectionName: (state) => state.apiCollectionName
    }
}

export default inventory
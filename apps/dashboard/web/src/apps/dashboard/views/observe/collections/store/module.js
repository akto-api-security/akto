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
        },
        CREATE_COLLECTION (state, {apiCollections}) {
            state.apiCollections.push(apiCollections[0])
        },
        DELETE_COLLECTION (state, apiCollectionId) {
            const index = state.apiCollections.findIndex(collection => collection.id === apiCollectionId)
            state.apiCollections.splice(index,1)
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadAllApiCollections({commit}, options) {
            commit('EMPTY_STATE')
            state.loading = true
            return api.getAllCollections().then((resp) => {
                commit('SAVE_API_COLLECTION', resp, options)
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        createCollection({commit}, {name}, options) {
            return api.createCollection(name).then((resp) => {
                commit('CREATE_COLLECTION', resp, options)
            }).catch(() => {
            })
        },
        deleteCollection({commit}, apiCollection, options) {
            return api.deleteCollection(apiCollection.apiCollection.id).then((resp) => {
                commit('DELETE_COLLECTION', apiCollection.apiCollection.id, options)
            }).catch(() => {
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
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
            state.apiCollections.splice(state.apiCollections.indexOf(apiCollectionId),1)
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
            //console.log(apiCollection.apiCollection.id)
            console.log(state.apiCollections)
            console.log(state.apiCollections[1])
            console.log(apiCollection.apiCollection)
            console.log(state.apiCollections.indexOf(apiCollection.apiCollection))
            return api.deleteCollection(apiCollection.apiCollection.id).then((resp) => {
                console.log(indexOf(apiCollection.apiCollection))
                state.apiCollections.splice(state.apiCollections.indexOf(apiCollection.apiCollection),1)
                // console.log(state.apiCollections)
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
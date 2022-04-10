import Vue from 'vue'
import Vuex from 'vuex'
import api from '../../inventory/api'
import func from "@/util/func"


Vue.use(Vuex)

let functionCompareParamObj = (x, p) => {
    return x.url === p.url && x.method === p.method && x.apiCollectionId === p.apiCollectionId
}

var state = {
    loading: false,
    fetchTs: 0,
    apiCollection: [],
    sensitiveParams: [],
    apiInfoList: [],
    lastFetched: 0
}

const changes = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.fetchTs = 0
            state.apiCollection = []
            state.sensitiveParams = []
        },
        SAVE_API_COLLECTION (state, info) {
            state.apiCollection = info.data.endpoints.map(x => {return {...x._id, startTs: x.startTs}})
            state.apiInfoList = info.data.apiInfoList
        },
        SAVE_SENSITIVE (state, fields) {
            state.sensitiveParams = fields
            
            fields.forEach(p => {
                let apiCollectionIndex = state.apiCollection.findIndex(x => {
                    return functionCompareParamObj(x, p)
                })
                
                if (apiCollectionIndex > -1) {
                    if (!state.apiCollection[apiCollectionIndex].sensitive) {
                        state.apiCollection[apiCollectionIndex].sensitive = new Set()
                    }
                    state.apiCollection[apiCollectionIndex].sensitive.add(p.subType || "Custom")
                }
            })
            state.apiCollection = [...state.apiCollection]
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadRecentEndpoints({commit}, options) {
            commit('EMPTY_STATE')
            state.loading = true
            state.lastFetched = new Date() / 1000
            return api.loadRecentEndpoints().then((resp) => {
                commit('SAVE_API_COLLECTION', {data: resp.data}, options)
                api.loadSensitiveParameters().then(allSensitiveFields => {
                    commit('SAVE_SENSITIVE', allSensitiveFields.data.endpoints)
                })
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
    },
    getters: {
        getFetchTs: (state) => state.fetchTs,
        getLoading: (state) => state.loading,
        getAPICollection: (state) => state.apiCollection,
        isSensitive: (state) => p => state.sensitiveParams && state.sensitiveParams.findIndex(x => {
            return functionCompareParamObj(x, p)
        }) > 0
    }
}

export default changes
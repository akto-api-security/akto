import Vue from 'vue'
import Vuex from 'vuex'
import api from '../../inventory/api'
import func from "@/util/func"


Vue.use(Vuex)

let functionCompareParamObj = (x, p) => {
    return x.param === p.param && x.url === p.url && x.method === p.method && x.isHeader === p.isHeader && x.responseCode === p.responseCode && x.apiCollectionId === p.apiCollectionId
}

var state = {
    loading: false,
    fetchTs: 0,
    apiCollection: [],
    sensitiveParams: []
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
            state.apiCollection = info.data.endpoints.filter(x => x.subType !== "NULL")
            state.apiCollection.forEach(e => e.url = func.parameterizeUrl(e.url));
        },
        SAVE_SENSITIVE (state, fields) {
            state.sensitiveParams = fields
            
            fields.forEach(p => {
                let apiCollectionIndex = state.apiCollection.findIndex(x => {
                    return functionCompareParamObj(x, p)
                })
                
                state.apiCollection[apiCollectionIndex].savedAsSensitive = true
            })
            state.apiCollection = [...state.apiCollection]
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadRecentParameters({commit}, options) {
            commit('EMPTY_STATE')
            state.loading = true
            return api.loadRecentParameters().then((resp) => {
                commit('SAVE_API_COLLECTION', {data: resp.data}, options)
                api.listAllSensitiveFields().then(allSensitiveFields => {
                    commit('SAVE_SENSITIVE', allSensitiveFields.data)
                })
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
        isSensitive: (state) => p => state.sensitiveParams && state.sensitiveParams.findIndex(x => {
            return functionCompareParamObj(x, p)
        }) > 0
    }
}

export default changes
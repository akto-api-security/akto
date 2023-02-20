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
    lastFetched: 0,
    data_type_names: []

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
        SAVE_DATA_TYPE_NAMES(state, resp) {
            state.data_type_names = [].concat(resp.allDataTypes)
        },
        SAVE_SENSITIVE (state, fields) {
            state.sensitiveParams = fields
            
            fields = fields.reduce((z,e) => {
                let key = [e.apiCollectionId + "-" + e.url + "-" + e.method]
                z[key] = z[key] || new Set()
                z[key].add(e.subType || {"name": "CUSTOM"})
                return z
            },{})

            Object.entries(fields).forEach(p => {
                let apiCollectionIndex = state.apiCollection.findIndex(e => {
                    return (e.apiCollectionId + "-" + e.url + "-" + e.method) === p[0]
                })
                
                if (apiCollectionIndex > -1) {
                    state.apiCollection[apiCollectionIndex].sensitive = p[1]
                }
            })
            state.apiCollection = [...state.apiCollection]
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadRecentEndpoints({commit}, {startTimestamp, endTimestamp}, options) {
            commit('EMPTY_STATE')
            state.loading = true
            state.lastFetched = new Date() / 1000
            return api.loadRecentEndpoints(startTimestamp, endTimestamp).then((resp) => {
                resp.data.endpoints.sort((x, y)  => x.startTs > y.startTs ? 1 : -1)
                commit('SAVE_API_COLLECTION', {data: resp.data}, options)
                api.fetchSensitiveParamsForEndpoints(resp.data.endpoints.map(x => x._id.url)).then(allSensitiveFields => {
                    commit('SAVE_SENSITIVE', allSensitiveFields.data.endpoints)
                })
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        fetchDataTypeNames({commit}) {
            return api.fetchDataTypeNames().then((resp) => {
                commit('SAVE_DATA_TYPE_NAMES', resp)
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
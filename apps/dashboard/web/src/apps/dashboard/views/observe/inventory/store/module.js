import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

var state = {
    loading: false,
    fetchTs: 0,
    apiCollectionId: 0,
    apiCollectionName: '',
    apiCollection: [],
    sensitiveParams: []
}

let functionCompareParamObj = (x, p) => {
    return x.param === p.param && x.url === p.url && x.method === p.method && x.isHeader === p.isHeader && x.responseCode === p.responseCode && x.apiCollectionId === p.apiCollectionId
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
        },
        TOGGLE_SENSITIVE (state, p) {
            let sensitiveParamIndex = state.sensitiveParams.findIndex(x => {
                return functionCompareParamObj(x, p)
            })

            let apiCollectionIndex = state.apiCollection.findIndex(x => {
                return functionCompareParamObj(x, p)
            })

            let savedAsSensitive = sensitiveParamIndex < 0
            if (savedAsSensitive) {
                state.sensitiveParams.push(p)
            } else {
                state.sensitiveParams.splice(sensitiveParamIndex, 1)
            }

            state.apiCollection[apiCollectionIndex].savedAsSensitive = savedAsSensitive
            state.apiCollection = [...state.apiCollection]
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
        loadAPICollection({commit}, {apiCollectionId}, options) {
            commit('EMPTY_STATE')
            state.loading = true
            return api.getAPICollection(apiCollectionId).then((resp) => {
                commit('SAVE_API_COLLECTION', {data: resp.data, apiCollectionId: apiCollectionId}, options)
                api.listAllSensitiveFields().then(allSensitiveFields => {
                    commit('SAVE_SENSITIVE', allSensitiveFields.data)
                })
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        toggleSensitiveParam({commit}, paramInfo) {
            return api.addSensitiveField(paramInfo).then(resp => {
                commit('TOGGLE_SENSITIVE', paramInfo)
                return resp
            })
        },
        uploadHarFile({commit,state},{content,filename}) {
            return api.uploadHarFile(content,state.apiCollectionId).then(resp => {
                return resp
            })
        }
    },
    getters: {
        getFetchTs: (state) => state.fetchTs,
        getLoading: (state) => state.loading,
        getAPICollection: (state) => state.apiCollection,
        getAPICollectionId: (state) => state.apiCollectionId,
        getAPICollectionName: (state) => state.apiCollectionName,
        isSensitive: (state) => p => state.sensitiveParams && state.sensitiveParams.findIndex(x => {
            return functionCompareParamObj(x, p)
        }) > 0
    }
}

export default inventory
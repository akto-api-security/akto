import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'
import func from "@/util/func"


Vue.use(Vuex)

var state = {
    parametersLoading: false,
    endpointsLoading: false,
    fetchTs: 0,
    apiCollectionId: 0,
    apiCollectionName: '',
    apiCollection: [],
    sensitiveParams: [],
    swaggerContent : null,
    apiInfoList: [],
    filters: [],
    lastFetched: 0,
    parameters: [],
    url: '',
    method: '',
    unusedEndpoints: [],
    filteredItems:[],
    allSamples:[]
}

let functionCompareEndpoint = (x, p) => {
    return x.url === p.url && x.method === p.method && x.apiCollectionId === p.apiCollectionId
}

let functionCompareParameter = (x, p) => {
    return x.responseCode === p.responseCode && x.isHeader === p.isHeader && x.param === p.param
}

const inventory = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.parametersLoading = false
            state.endpointsLoading = false
            state.fetchTs = 0
            state.apiCollectionId = 0
            state.apiCollection = []
            state.apiCollectionName = ''
            state.swaggerContent = null
            state.parameters = []
            state.url = ''
            state.method = ''
            state.allSamples = []
            state.filteredItems = []
        },
        EMPTY_PARAMS (state) {
            state.parametersLoading = false
            state.parameters = []
            state.url = ''
            state.method = ''
        },
        FILTERED_ITEMS(state,info){
            state.filteredItems = info
        },
        ALL_SAMPLED_DATA(state,info){
            state.allSamples = info
        },
        SAVE_API_COLLECTION (state, info) {
            state.apiCollectionId = info.apiCollectionId
            state.apiCollectionName = info.data.name
            state.apiCollection = info.data.endpoints.map(x => {return {...x._id, startTs: x.startTs, changesCount: x.changesCount, shadow: x.shadow ? x.shadow : false}})
            state.apiInfoList = info.data.apiInfoList
            state.unusedEndpoints = info.unusedEndpoints
        },
        TOGGLE_SENSITIVE (state, p) {
            let sensitiveParamIndex = state.sensitiveParams.findIndex(x => {
                return functionCompareParameter(x, p) && functionCompareEndpoint(x, p)
            })

            let savedAsSensitive = sensitiveParamIndex < 0
            if (savedAsSensitive) {
                p.savedAsSensitive = p.sensitive
                state.sensitiveParams.push(p)
            } else {
                state.sensitiveParams.splice(sensitiveParamIndex, 1)
            }
            
            let paramIndex = state.parameters.findIndex(x => {
                return functionCompareParameter(x, p)
            })

            if (paramIndex > -1) {
                state.parameters[paramIndex].sensitive = p.sensitive
                state.parameters[paramIndex].savedAsSensitive = p.sensitive
            }

            state.parameters = [...state.parameters]
        },
        SAVE_SENSITIVE (state, fields) {
            state.sensitiveParams = fields
            
            fields.forEach(p => {
                let apiCollectionIndex = state.apiCollection.findIndex(x => {
                    return functionCompareEndpoint(x, p)
                })
                
                if (apiCollectionIndex > -1) {
                    if (!state.apiCollection[apiCollectionIndex].sensitive) {
                        state.apiCollection[apiCollectionIndex].sensitive = new Set()
                    }
                    if (!p.subType) {
                        p.subType = {"name": "CUSTOM"}
                    }
                    state.apiCollection[apiCollectionIndex].sensitive.add(p.subType)
                }
            })
            state.apiCollection = [...state.apiCollection]
        },
        SAVE_PARAMS(state, {method, url, parameters}) {
            state.method = method
            state.url = url
            state.parameters = parameters
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadAPICollection({commit}, {apiCollectionId, shouldLoad}, options) {
            state.lastFetched = new Date() / 1000
            commit('EMPTY_STATE')
            if (shouldLoad) {
                state.endpointsLoading = true
            }
            return api.fetchAPICollection(apiCollectionId).then((resp) => {
                commit('SAVE_API_COLLECTION', {data: resp.data, apiCollectionId: apiCollectionId, unusedEndpoints: resp.unusedEndpoints}, options)
                api.loadSensitiveParameters(apiCollectionId).then(allSensitiveFields => {
                    commit('SAVE_SENSITIVE', allSensitiveFields.data.endpoints)
                })
                api.loadContent(apiCollectionId).then(resp => {
                    if(resp && resp.data && resp.data.content)
                        state.swaggerContent = JSON.parse(resp.data.content)
                })
                api.fetchFilters().then(resp => {
                    let a = resp.runtimeFilters
                    a.forEach(x => {
                        state.filters[x.customFieldName] = x
                    })
                })
                state.endpointsLoading = false
            }).catch(() => {
                state.endpointsLoading = false
            })
        },
        loadParamsOfEndpoint({commit}, {apiCollectionId, url, method}) {
            commit('EMPTY_PARAMS')
            state.parametersLoading = true    
            return api.loadParamsOfEndpoint(apiCollectionId, url, method).then(resp => {
                api.loadSensitiveParameters(apiCollectionId, url, method).then(allSensitiveFields => {
                    allSensitiveFields.data.endpoints.filter(x => x.sensitive).forEach(sensitive => {
                        let index = resp.data.params.findIndex(x => 
                            x.param === sensitive.param && 
                            x.isHeader === sensitive.isHeader && 
                            x.responseCode === sensitive.responseCode    
                        )

                        if (index > -1 && !sensitive.subType) {
                            resp.data.params[index].savedAsSensitive = true
                            if (!resp.data.params[index].subType) {
                                resp.data.params[index].subType = {"name": "CUSTOM"}
                            } else {
                                resp.data.params[index].subType = JSON.parse(JSON.stringify(resp.data.params[index].subType))
                            }
                        }

                    })
                    commit('SAVE_PARAMS', {parameters: resp.data.params, apiCollectionId, url, method})
                    state.parametersLoading = false
                })
            })

        },
        toggleSensitiveParam({commit}, paramInfo) {
            return api.addSensitiveField(paramInfo).then(resp => {
                commit('TOGGLE_SENSITIVE', paramInfo)
                return resp
            })
        },
        uploadHarFile({commit,state},{formData}) {
            formData.append("apiCollectionId",state.apiCollectionId);
            return api.uploadHarFile(formData).then(resp => {
                return resp
            })
        },
        downloadOpenApiFile({commit,state}, {lastFetchedUrl, lastFetchedMethod}) {
            return api.downloadOpenApiFile(state.apiCollectionId, lastFetchedUrl, lastFetchedMethod).then(resp => {
                return resp
            })
        },
        exportToPostman({commit,state}) {
            return api.exportToPostman(state.apiCollectionId).then(resp => {
                return resp
            })
        },
        saveContent({ commit, dispatch, state }, {swaggerContent, filename, apiCollectionId}) {
            state.endpointsLoading = true
            api.saveContent({swaggerContent, filename, apiCollectionId}).then(resp => {
                state.filename = filename
                state.swaggerContent = swaggerContent
                state.apiCollectionId = apiCollectionId
                state.endpointsLoading = false
            }).catch(() => {
                state.endpointsLoading = false
            })
        },
        fetchApiInfoList({commit,dispatch, state}, {apiCollectionId}) {
            api.fetchApiInfoList(apiCollectionId).then(resp => {
              state.apiInfoList = resp.apiInfoList
            })
        },
        fetchFilters({commit, dispatch, state}) {
            api.fetchFilters().then(resp => {
              let a = resp.runtimeFilters
              a.forEach(x => {
                state.filters[x.customFieldName] = x
              })
            })
        },
        uploadWorkflowJson({commit, dispatch, state}, {content}) {
            return api.uploadWorkflowJson(content, state.apiCollectionId);
        }
    },
    getters: {
        getFetchTs: (state) => state.fetchTs,
        getAPICollection: (state) => state.apiCollection,
        getAPICollectionId: (state) => state.apiCollectionId,
        getAPICollectionName: (state) => state.apiCollectionName,
        isSensitive: (state) => p => state.sensitiveParams && state.sensitiveParams.findIndex(x => {
            return functionCompareParameter(x, p)
        }) > 0,
        getApiInfoList: (state) => state.apiInfoList,
        getFilters: (state) => state.filters,
        getUnusedEndpoints: (state) => state.unusedEndpoints,
        getUrl: (state) => state.url,
        getMethod: (state) => state.method,
    }
}

export default inventory
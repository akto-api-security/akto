import Vue from 'vue'
import Vuex from 'vuex'
import func from '@/util/func'
import api from '../api'


Vue.use(Vuex)

const data_types = {
    namespaced: true,
    state: {
        data_types: null,
        usersMap: {},
        data_type: null,
        reviewData: null,
        current_sample_data_count: 0,
        total_sample_data_count: 100,
    },
    getters: {
        getDataTypes: (state) => state.data_types,
        getDataType: (state) => state.data_type,
        getReviewData: (state) => state.reviewData,
        getCurrentSampleDataCount: (state) => state.current_sample_data_count,
        getTotalSampleDataCount: (state) => state.total_sample_data_count
    },
    mutations: {
        SET_DATA_TYPES(state, result) {
            state.usersMap= result["dataTypes"]["usersMap"]
            state.data_types = result["dataTypes"]["customDataTypes"]
            // not needed for akto types
            state.data_types = state.data_types.concat(result["dataTypes"]["aktoDataTypes"])
            func.prepareDataTypes(state.data_types)

            if (state.data_types && state.data_types.length > 0) {
                state.data_type = state.data_types[0]
            }
        },
        SET_NEW_DATA_TYPE(state) {
            state.data_type = {
                "name": "",
                "operator": "OR",
                "keyConditions": {"operator": "AND", "predicates":[
                    {
                        "type": "EQUALS_TO",
                        "value": null
                    }
                ]},
                "sensitiveAlways": true,
                "valueConditions": {"operator": "AND", "predicates": [{
                    "type": "EQUALS_TO",
                    "value": null
                }]},
                "active": true,
                "sensitivePosition": [],
                "createNew": true
            }

        },
        SET_NEW_DATA_TYPE_BY_AKTOGPT(state, payload) {
            console.log('SET_NEW_DATA_TYPE_BY_AKTOGPT', 'payload', payload)
            state.data_type = {
                "name": payload.name.toUpperCase(),
                "operator": "AND",
                "keyConditions": {
                  "operator": "AND",
                  "predicates": []
                },
                "sensitiveAlways": true,
                "valueConditions": {
                  "operator": "AND",
                  "predicates": [
                    {
                          "type": "REGEX",
                          "value": payload.regex
                    }
                  ]
                },
                "active": true,
                "sensitivePosition": [],
                "createNew": true
              }

        },
        SET_REVIEW_DATA(state, result){
            if (!state.reviewData) {
                state.reviewData = []
            }
            state.reviewData = state.reviewData.concat(result["customSubTypeMatches"])
            state.total_sample_data_count = result["totalSampleDataCount"]
            state.current_sample_data_count += result["currentProcessed"]
        },
        RESET_REVIEW_DATA(state){
            state.reviewData = []
            state.total_sample_data_count = 100
            state.current_sample_data_count= 0
        },
        UPDATE_DATA_TYPES(state,result) {
            let flag = false
            state.data_types.forEach((data_type,index) => {
                if (data_type["name"] === result["name"]) {
                    flag = true
                    state.data_types[index] = result
                    state.data_type = result
                }
            })

            if (!flag) {
                state.data_types = [result].concat(state.data_types)
                state.data_type = result
            }

            func.prepareDataTypes(state.data_types)
            state.data_types = [].concat(state.data_types)

        }
    },
    actions: {
        setNewDataTypeByAktoGpt({commit}, payload) {
            commit("SET_NEW_DATA_TYPE_BY_AKTOGPT", payload)
        },
        setNewDataType({commit, dispatch}) {
            commit("SET_NEW_DATA_TYPE")
        },
        toggleActiveParam({commit, dispatch}, item) {
            return api.toggleActiveParam(item.name, !item.active).then((resp) => {
                commit("UPDATE_DATA_TYPES", resp["customDataType"]);
            })
        },
        fetchDataTypes({commit, dispatch}) {
            return api.fetchDataTypes().then((resp) => {
                commit('SET_DATA_TYPES', resp)
            })
        },
        async createCustomDataType({commit, dispatch, state}, { data_type, save}) {
            let id = data_type["id"]
            let name = data_type["name"]
            let sensitiveAlways = data_type["sensitiveAlways"]
            let sensitivePosition = data_type["sensitivePosition"]
            let operator = data_type["operator"]
            let keyOperator = data_type["keyConditions"]["operator"]
            let keyConditionFromUsers =  func.preparePredicatesForApi(data_type["keyConditions"]["predicates"])
            let valueOperator = data_type["valueConditions"]["operator"]
            let valueConditionFromUsers = func.preparePredicatesForApi(data_type["valueConditions"]["predicates"])
            let createNew = data_type["createNew"] ? data_type["createNew"] : false
            let active = data_type["active"]
            if (save) {
                return api.saveCustomDataType(id,name,sensitiveAlways,sensitivePosition,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew, active).then((resp) => {
                    commit("UPDATE_DATA_TYPES", resp["customDataType"]);
                })
            } else {
                commit("RESET_REVIEW_DATA")
                let idx = 0;
                let batchSize = 1000
                while (idx < 20 && batchSize >= 1000) {
                    if (state.reviewData && state.reviewData.length > 1000) {
                        break
                    }
                    idx += 1
                    let resp = await api.reviewCustomDataType(id,name,sensitiveAlways,sensitivePosition,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers,active,idx)
                    commit("SET_REVIEW_DATA", resp);
                    batchSize = resp["currentProcessed"]
                }
            }
        },
        async updateAktoDataType({commit,dispatch,state},{data_type}){
            let name = data_type["name"]
            let sensitiveAlways = data_type["sensitiveAlways"]
            let sensitivePosition = data_type["sensitivePosition"]

            return api.saveAktoDataType(name,sensitiveAlways,sensitivePosition).then((resp) => {
                commit("UPDATE_DATA_TYPES", resp["aktoDataType"]);
            })
        }
    }
}

export default data_types
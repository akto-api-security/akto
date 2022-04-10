import Vue from 'vue'
import Vuex from 'vuex'
import func from '../../../../../../../util/func'
import api from '../api'


Vue.use(Vuex)

const data_types = {
    namespaced: true,
    state: {
        data_types: null,
        usersMap: {},
        data_type: null
    },
    getters: {
        getDataTypes: (state) => state.data_types,
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
        UPDATE_DATA_TYPES(state,result) {
            let flag = false
            state.data_types.forEach((data_type,index) => {
                if (data_type["name"] === result["name"]) {
                    flag = true
                    state.data_types[index] = result
                }
            })

            if (!flag) {
                state.data_types = [result].concat(state.data_types)
            }

            func.prepareDataTypes(state.data_types)
            state.data_types = [].concat(state.data_types)

            if (state.data_types && state.data_types.length > 0) {
                state.data_type = state.data_types[0]
            }
        }
    },
    actions: {
        fetchDataTypes({commit, dispatch}) {
            return api.fetchDataTypes().then((resp) => {
                commit('SET_DATA_TYPES', resp)
            })
        },
        createCustomDataType({commit, dispatch}, { data_type, save}) {
            let id = data_type["id"]
            let name = data_type["name"]
            let sensitiveAlways = data_type["sensitiveAlways"]
            let operator = data_type["operator"]
            let keyOperator = data_type["keyConditions"]["operator"]
            let keyConditionFromUsers =  func.preparePredicatesForApi(data_type["keyConditions"]["predicates"])
            let valueOperator = data_type["valueConditions"]["operator"]
            let valueConditionFromUsers = func.preparePredicatesForApi(data_type["valueConditions"]["predicates"])
            let createNew = data_type["createNew"] ? data_type["createNew"] : false
            if (save) {
                return api.saveCustomDataType(id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew).then((resp) => {
                    commit("UPDATE_DATA_TYPES", resp["customDataType"]);
                })
            } else {
                return api.reviewCustomDataType(id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew).then((resp) => {
                    return resp["customSubTypeMatches"]
                })
            }
        },
    }
}

export default data_types
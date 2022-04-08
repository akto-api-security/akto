import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const data_types = {
    namespaced: true,
    state: {
        data_types: null
    },
    getters: {
        getDataTypes: (state) => state.data_types,
    },
    mutations: {
        SET_DATA_TYPES(state, result) {
            state.data_types = result["dataTypes"]["customDataTypes"]
            state.data_types = state.data_types.concat(result["dataTypes"]["aktoDataTypes"])
        },
    },
    actions: {
        fetchDataTypes({commit, dispatch}) {
            return api.fetchDataTypes().then((resp) => {
                console.log(resp["dataTypes"]["customDataTypes"])
                commit('SET_DATA_TYPES', resp)
            })
        },
    }
}

export default data_types
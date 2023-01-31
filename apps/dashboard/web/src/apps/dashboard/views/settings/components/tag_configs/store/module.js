import Vue from 'vue'
import Vuex from 'vuex'
import func from '@/util/func'
import api from '../api'


Vue.use(Vuex)

const tag_configs = {
    namespaced: true,
    state: {
        tag_configs: null,
        usersMap: {},
        tag_config: null,
        reviewData: null,
        current_sample_data_count: 0,
        total_sample_data_count: 100,
    },
    getters: {
        getTagConfigs: (state) => state.tag_configs,
        getTagConfig: (state) => state.tag_config,
        getReviewData: (state) => state.reviewData,
        getCurrentSampleDataCount: (state) => state.current_sample_data_count,
        getTotalSampleDataCount: (state) => state.total_sample_data_count
    },
    mutations: {
        SET_TAG_CONFIGS(state, result) {
            state.usersMap= result["tagConfigs"]["usersMap"]
            state.tag_configs = result["tagConfigs"]["tagConfigs"]
            func.prepareDataTypes(state.tag_configs)

            if (state.tag_configs && state.tag_configs.length > 0) {
                state.tag_config = state.tag_configs[0]
            }
        },
        SET_NEW_TAG_CONFIG(state) {
            state.tag_config = {
                "name": "",
                "keyConditions": {"operator": "AND", "predicates": []},
                "active": true,
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
        UPDATE_TAG_CONFIGS(state,result) {
            let flag = false
            state.tag_configs.forEach((tag_config,index) => {
                if (tag_config["name"] === result["name"]) {
                    flag = true
                    state.tag_configs[index] = result
                    state.tag_config = result
                }
            })

            if (!flag) {
                state.tag_configs = [result].concat(state.tag_configs)
                state.tag_config = result
            }

            func.prepareDataTypes(state.tag_configs)
            state.tag_configs = [].concat(state.tag_configs)

        }
    },
    actions: {
        setNewTagConfig({commit, dispatch}) {
            commit("SET_NEW_TAG_CONFIG")
        },
        toggleActiveTagConfig({commit, dispatch}, item) {
            return api.toggleActiveTagConfig(item.name, !item.active).then((resp) => {
                commit("UPDATE_TAG_CONFIGS", resp["tagConfig"]);
            })
        },
        fetchTagConfigs({commit, dispatch}) {
            return api.fetchTagConfigs().then((resp) => {
                commit('SET_TAG_CONFIGS', resp)
            })
        },
        async createTagConfig({commit, dispatch, state}, { tag_config, save}) {
            let id = tag_config["id"]
            let name = tag_config["name"]
            let keyOperator = tag_config["keyConditions"]["operator"]
            let keyConditionFromUsers =  func.preparePredicatesForApi(tag_config["keyConditions"]["predicates"])
            let createNew = tag_config["createNew"] ? tag_config["createNew"] : false
            let active = tag_config["active"]
            if (save) {
                return api.saveTagConfig(id,name, keyOperator, keyConditionFromUsers, createNew, active).then((resp) => {
                    commit("UPDATE_TAG_CONFIGS", resp["tagConfig"]);
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
                    let resp = await api.reviewTagConfig(id,name, keyOperator, keyConditionFromUsers,active,idx)
                    commit("SET_REVIEW_DATA", resp);
                    batchSize = resp["currentProcessed"]
                }
            }
        },
    }
}

export default tag_configs
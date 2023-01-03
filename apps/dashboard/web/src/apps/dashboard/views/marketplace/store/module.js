import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'

Vue.use(Vuex)

const state = {
    loading: false,
    defaultSubcategories: [],
    userSubcategories: []
}

const marketplace = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE(state) {
            state.loading = false
            state.defaultSubcategories = []
            state.userSubcategories = []
        }
    },
    actions: {
        emptyState({ commit }) {
            commit('EMPTY_STATE')
        },
        fetchAllMarketplaceSubcategories({commit}) {
            commit('EMPTY_STATE')
            return api.fetchAllMarketplaceSubcategories().then((resp) => {
                for(let index in resp.testSourceConfigs) {
                    let tsc = resp.testSourceConfigs[index]

                    let subcategoryContainer = tsc.creator === "default" ? state.defaultSubcategories : state.userSubcategories
                    if (subcategoryContainer.indexOf(tsc.subcategory) == -1) {
                        subcategoryContainer.push(tsc.subcategory)
                    }
                }
            })
        }
    },
    getters: {
        getLoading: (state) => state.loading,
        getDefaultSubcategories: (state) => state.defaultSubcategories,
        getUserSubcategories: (state) => state.userSubcategories
    }
}

export default marketplace
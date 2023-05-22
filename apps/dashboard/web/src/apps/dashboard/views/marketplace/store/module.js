import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'

Vue.use(Vuex)

const state = {
    loading: false,
    defaultSubcategories: [],
    userSubcategories: [],
    searchText: "",
}

const marketplace = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE(state) {
            state.loading = false
            state.defaultSubcategories = []
            state.userSubcategories = []
        },
        searchTestResults(state, {searchText}){
            state.searchText = searchText
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
                    let entry = tsc.category+"/"+tsc.subcategory
                    if (state.defaultSubcategories.indexOf(entry) == -1) {
                        state.defaultSubcategories.push(entry)
                    }

                    if( tsc.creator !== "default"  && state.userSubcategories.indexOf(entry) == -1) {
                        state.userSubcategories.push(entry)
                    }
                }
            })
        }
    },
    getters: {
        getLoading: (state) => state.loading,
        getDefaultSubcategories: (state) => state.defaultSubcategories,
        getUserSubcategories: (state) => state.userSubcategories,
        getSearchText: (state)=>state.searchText
    }
}

export default marketplace
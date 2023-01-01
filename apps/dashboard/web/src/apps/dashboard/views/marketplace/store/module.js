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
        fetchAllSubcategories({commit}) {
            commit('EMPTY_STATE')
            return api.fetchAllSubcategories().then((resp) => {
                for(let key in resp.subcategories) {
                    let creator = resp.subcategories[key]
                    if (creator === "default") {
                        state.defaultSubcategories.push(key)
                    } else {
                        state.userSubcategories.push(key)
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
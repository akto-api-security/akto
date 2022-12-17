import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    testRoles: [],
    selectedRole: {},
     
}

const test_roles = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.testRoles = []
            state.selectedRole = {}
        },
        SAVE_TEST_ROLES(state, {testRoles}) {
            state.testRoles = testRoles
        },
        SET_NEW_SELECTED_ROLE(state) {
            state.selectedRole = {
                'createNew' :true
            }
        }
    },
    actions: {
        emptyState({ commit }) {
            commit('EMPTY_STATE')
        },
        loadTestRoles({commit}) {
            state.loading = true
            return api.fetchTestRoles().then((resp) => {
                commit('SAVE_TEST_ROLES', resp)
                state.loading = false
            }).catch((resp) => {
                state.loading = false
            })
        },
        addTestRoles ({commit}, {roleName, regex, includedApiList, excludedApiList}) {
            state.loading = true
            api.addTestRoles(roleName, regex, includedApiList, excludedApiList).then((resp) => {
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
    },
    getters: {
        getLoading: (state) => state.loading,
        getTestRoles: (state) => state.testRoles,
        getSelectedRole: (state) => state.selectedRole
    }
}

export default test_roles
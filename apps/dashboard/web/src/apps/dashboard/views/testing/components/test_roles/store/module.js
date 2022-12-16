import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    testRoles: [],
    selectedRole: {},
    listOfEndpointsInCollection: [],
    conditions: [],
    createNew : false
}

const test_roles = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.testRoles = []
            state.selectedRole = {}
            state.listOfEndpointsInCollection = []
        },
        SAVE_TEST_ROLES(state, {testRoles}) {
            state.testRoles = testRoles
        },
        SAVE_SELECTED_ROLE(state, {selectedRole}) {
            state.selectedRole = selectedRole
        },
        SAVE_LIST_OF_ENDPOINTS_IN_COLLECTION(state, {listOfEndpointsInCollection}) {
            state.listOfEndpointsInCollection = listOfEndpointsInCollection;
        },
        SAVE_CREATE_NEW(state, {createNew}) {
            state.createNew = createNew;
        },
        SAVE_CONDITIONS (state, {conditions}) {
            debugger
            state.conditions = conditions
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
        addTestRoles ({commit}, {roleName, andConditions, orConditions, includedApiList, excludedApiList}) {
            state.loading = true
            api.addTestRoles(roleName, andConditions, orConditions, includedApiList, excludedApiList).then((resp) => {
                commit('SAVE_SELECTED_ROLE', resp)
                commit('SAVE_CREATE_NEW', {createNew: false})
                state.loading = false

            }).catch(() => {
                state.loading = false
            })
        },
        fetchCollectionWiseApiEndpoints ({commit}, {listOfEndpointsInCollection}) {
            state.loading = true
            api.addTestRoles(listOfEndpointsInCollection).then((resp) => {
                commit('SAVE_LIST_OF_ENDPOINTS_IN_COLLECTION', resp)
                state.loading = false

            }).catch(() => {
                state.loading = false
            })
        }
    },
    getters: {
        getLoading: (state) => state.loading,
        getTestRoles: (state) => state.testRoles,
        getSelectedRole: (state) => state.selectedRole,
        getListOfEndpointsInCollection: (state) => state.listOfEndpointsInCollection
    }
}

export default test_roles
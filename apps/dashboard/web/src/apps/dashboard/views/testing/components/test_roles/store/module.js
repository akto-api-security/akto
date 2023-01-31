import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    testRoles: [],
    selectedRole: {},
    conditions: [],
    createNew : false,
    collectionWiseApiInfoKeyMap: {}
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
        SAVE_SELECTED_ROLE(state, {selectedRole}) {
            state.selectedRole = selectedRole
        },
        updateTestRoleWithNewRole(state, {selectedRole}) {
            state.testRoles.push(selectedRole)
        },
        SAVE_CREATE_NEW(state, {createNew}) {
            state.createNew = createNew;
        },
        SAVE_CONDITIONS (state, {conditions}) {
            state.conditions = conditions
        },
        SAVE_COLLECTION_WISE_INFO_KEY_MAP(state, {collectionWiseApiInfoKeyMap}) {
            state.collectionWiseApiInfoKeyMap = collectionWiseApiInfoKeyMap
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
        addTestRoles ({commit}, {roleName, andConditions, orConditions}) {
            state.loading = true
            api.addTestRoles(roleName, andConditions, orConditions).then((resp) => {
                commit('SAVE_SELECTED_ROLE', resp)
                commit('updateTestRoleWithNewRole', resp)
                commit('SAVE_CREATE_NEW', {createNew: false})
                state.loading = false

            }).catch(() => {
                state.loading = false
            })
        },
        updateTestRoles ({commit}, {roleName, andConditions, orConditions}) {
            state.loading = true
            api.updateTestRoles(roleName, andConditions, orConditions).then((resp) => {
                state.loading = false

            }).catch(() => {
                state.loading = false
            })
        },
        async fetchApiInfoKeyForCollection ({commit}, {collectionId}) {
            if (!(collectionId in state.collectionWiseApiInfoKeyMap)) {
                state.loading = true
                await api.fetchCollectionWiseApiEndpoints(parseInt(collectionId)).then((resp) => {
                    let collectionWiseApiInfoKeyMap = {}
                    collectionWiseApiInfoKeyMap[collectionId] = resp['listOfEndpointsInCollection']
                    commit('SAVE_COLLECTION_WISE_INFO_KEY_MAP', {collectionWiseApiInfoKeyMap})
                    this.loading=false
                }).catch(() => {
                    this.loading = false
                })
                }
        }
    },
    getters: {
        getLoading: (state) => state.loading,
        getTestRoles: (state) => state.testRoles,
        getSelectedRole: (state) => state.selectedRole
    }
}

export default test_roles
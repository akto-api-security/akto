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
        UPDATE_TESTROLES(state, {selectedRole}){
            state.testRoles.forEach((testRole,i) => {
                if(JSON.stringify(testRole.id)===JSON.stringify(selectedRole.id)){
                    state.testRoles[i]=selectedRole;
                }
            })
            state.testRoles = [...state.testRoles]
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
        addTestRoles ({commit}, {roleName, andConditions, orConditions, authParamData}) {
            state.loading = true
            api.addTestRoles(roleName, andConditions, orConditions, authParamData).then((resp) => {
                commit('SAVE_SELECTED_ROLE', resp)
                commit('updateTestRoleWithNewRole', resp)
                commit('SAVE_CREATE_NEW', {createNew: false})
                state.loading = false

            }).catch(() => {
                state.loading = false
            })
        },
        updateTestRoles ({commit}, {roleName, andConditions, orConditions, authParamData}) {
            state.loading = true
            api.updateTestRoles(roleName, andConditions, orConditions, authParamData).then((resp) => {
                commit('SAVE_SELECTED_ROLE', resp);
                commit('UPDATE_TESTROLES', resp);
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
        },
        async addAuthToRole({commit}, {roleName, apiCond, authParamData, authAutomationType, reqData}) {
            state.loading = true
            let resp = await api.addAuthToRole(roleName, apiCond, authParamData, authAutomationType, reqData)
            commit('SAVE_SELECTED_ROLE', resp);
            commit('UPDATE_TESTROLES', resp);

            state.loading = false
        },
        async deleteAuthFromRole({commit}, {roleName, index}) {
            state.loading = true
            let resp = await api.deleteAuthFromRole(roleName, index)
            commit('SAVE_SELECTED_ROLE', resp);
            commit('UPDATE_TESTROLES', resp);
            state.loading = false
        }        
    },
    getters: {
        getLoading: (state) => state.loading,
        getTestRoles: (state) => state.testRoles,
        getSelectedRole: (state) => state.selectedRole
    }
}

export default test_roles
import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'
import router from "@/apps/main/router";


Vue.use(Vuex)

const getDefaultState = () => {
    return {
        loading: false,
        selectedCollection: null,
        selectedTestSuite: null,
        authMechanismLoading: false,
        authKey: null,
        authValue: null,
        runTestLoading: false,
        testSuites: null,
        testingRunHexId: null
    }
}


const state = getDefaultState()

const onboarding = {
    namespaced: true,
    state: state,
    mutations: {
        SELECT_COLLECTION (state, selectedCollection) {
            state.selectedCollection = selectedCollection
        },
        SELECT_TEST_SUITE(state, selectedTestSuite) {
            state.selectedTestSuite = selectedTestSuite
        },
        UPDATE_AUTH_MECHANISM(state, authMechanism) {
            state.authMechanism = authMechanism
            let res = authMechanism && authMechanism.authParams && authMechanism.type == "HARDCODED" && authMechanism.authParams[0]
            if (res) {
                state.authKey = res.key
                state.authValue = res.value
            }
        },
        UPDATE_AUTH_MECHANISM_LOADING(state, authMechanismLoading) {
            state.authMechanismLoading = authMechanismLoading
        },
        UPDATE_RUN_TEST_LOADING(state, runTestLoading) {
            state.runTestLoading = runTestLoading
        },
        UPDATE_TEST_SUITES(state, testSuites) {
            state.testSuites = testSuites
        }
    },
    actions: {
        collectionSelected({commit}, selectedCollection) {
            commit('SELECT_COLLECTION', selectedCollection)
            commit('UPDATE_AUTH_MECHANISM_LOADING', true)
            api.fetchAuthMechanismData().then((resp)=>{
                commit('UPDATE_AUTH_MECHANISM', resp.authMechanism)
                commit('UPDATE_AUTH_MECHANISM_LOADING', false)
            }).catch((e) => {
                commit('UPDATE_AUTH_MECHANISM_LOADING', false)
            })
        },
        testSuiteSelected({commit}, selectedTestSuite) {
            commit('SELECT_TEST_SUITE', selectedTestSuite)
        },
        fetchTestSuites() {
            api.fetchTestSuites().then((resp) => {
                state.testSuites = resp.testSuites
            })
        },
        runTestOnboarding({commit}) {
            commit('UPDATE_RUN_TEST_LOADING', true)
            return api.runTestOnboarding(
                [{"key": state.authKey, "value": state.authValue, "where": "HEADER"}],
                state.selectedCollection.id,
                state.selectedTestSuite
            ).then(resp => {
                commit('UPDATE_RUN_TEST_LOADING', false)
                state.testingRunHexId = resp.testingRunHexId
            }).catch(e => {
                commit('UPDATE_RUN_TEST_LOADING', false)
            })
        },
        skipOnboarding() {
            return api.skipOnboarding()
        },
        unsetValues() {
            Object.assign(state, getDefaultState())
        }

    },
    getters: {
    }
}

export default onboarding
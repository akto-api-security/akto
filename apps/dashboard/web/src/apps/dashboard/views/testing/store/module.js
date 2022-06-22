import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

var state = {
    loading: false,
    testingRuns: [],
    authMechanism: null,
    testingRunResults: [],
    testingSchedules: []
}

const testing = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.testingRuns = []
            state.authMechanism = null
            state.testingRunResults = []
            state.testingSchedules = []
        },
        SAVE_DETAILS (state, {authMechanism, testingRuns}) {
            state.authMechanism = authMechanism
            state.testingRuns = testingRuns
        },
        SAVE_TESTING_RUNS (state, {testingRuns}) {
            state.testingRuns = testingRuns
        },
        SAVE_TESTING_SCHEDULES (state, {testingSchedules}) {
            state.testingSchedules = testingSchedules
        },
        SAVE_AUTH_MECHANISM (state, {authMechanism}) {
            state.authMechanism = authMechanism
        },
        SAVE_TESTING_RUN_RESULTS(state, {testingRunResults}) {
            state.testingRunResults = testingRunResults
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadTestingDetails({commit}, options) {
            commit('EMPTY_STATE')
            state.loading = true
            return api.fetchTestingDetails().then((resp) => {
                commit('SAVE_DETAILS', resp)
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        startTestForCollection({commit}, apiCollectionId) {
            return api.startTestForCollection(apiCollectionId).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        scheduleTestForCollection({commit}, {apiCollectionId, startTimestamp, recurringDaily} ) {
            return api.scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily).then((resp) => {
                commit('SAVE_TESTING_SCHEDULES', resp)
            })
        },
        stopTestForCollection({commit}, apiCollectionId) {
            return api.stopTestForCollection(apiCollectionId).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        stopScheduleForCollection({commit}, apiCollectionId) {
            return api.stopScheduleForCollection(apiCollectionId).then((resp) => {
                commit('SAVE_TESTING_SCHEDULES', resp)
            })
        },
        addAuthMechanism({commit}, {key, value, location}) {
            return api.addAuthMechanism(key, value, location).then(resp => {
                commit('SAVE_AUTH_MECHANISM', resp)
            })
        },
        loadTestingRunResults({commit}) {
            api.fetchTestingRunResults().then(resp => {
                commit('SAVE_TESTING_RUN_RESULTS', resp)
            })
        }
    },
    getters: {
        getLoading: (state) => state.loading,
        getTestingRuns: (state) => state.testingRuns,
        getAuthMechanism: (state) => state.authMechanism
    }
}

export default testing
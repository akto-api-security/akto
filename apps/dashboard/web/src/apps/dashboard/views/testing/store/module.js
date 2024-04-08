import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'
import func from "@/util/func"


Vue.use(Vuex)

const state = { 
    loading: false,
    testingRuns: [],
    pastTestingRuns: [],
    cicdTestingRuns: [],
    authMechanism: null,
    testingRunResults: []
}

function addMoreItem(arr, size, link){
    if (size > 50) {
        arr.push({
            id: "More button",
            title: `See all`,
            link: link,
            class: "no-style",
            active: true
        })
    }
    return arr;
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
            state.pastTestingRuns = []
            state.cicdTestingRuns = []
        },
        SAVE_DETAILS (state, {authMechanism, testingRuns, testingRunsCount}) {
            testingRuns = addMoreItem(testingRuns, testingRunsCount, "/dashboard/testing/active")
            state.authMechanism = authMechanism
            state.testingRuns = testingRuns
        },        
        SAVE_CICD_DETAILS (state, {testingRuns, testingRunsCount}) {
            testingRuns = addMoreItem(testingRuns, testingRunsCount, "/dashboard/testing/cicd")
            state.cicdTestingRuns = testingRuns
        },
        SAVE_PAST_DETAILS (state, {testingRuns, testingRunsCount}) {
            testingRuns = addMoreItem(testingRuns, testingRunsCount, "/dashboard/testing/inactive")
            state.pastTestingRuns = testingRuns
        },
        SAVE_TESTING_RUNS (state, {testingRuns}) {
            state.testingRuns = testingRuns
        },
        SAVE_AUTH_MECHANISM (state, {key, value, location}) {
            state.authMechanism.authParams[0] = {key, value, where: location}
        },
        SAVE_TESTING_RUN_RESULTS(state, {testingRunResults}) {
            state.testingRunResults = testingRunResults
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        async loadTestingDetails({commit}, {startTimestamp, endTimestamp}) {
            commit('EMPTY_STATE')
            state.loading = true
            await api.fetchTestingDetails({startTimeStamp:0, endTimeStamp:0, fetchCicd:false}).then((resp) => {
                commit('SAVE_DETAILS', resp)
            }).catch(() => {
                state.loading = false
            })
            await api.fetchTestingDetails({startTimestamp, endTimestamp, fetchCicd:false}).then(resp2 => {
                commit('SAVE_PAST_DETAILS', resp2)
            }).catch(() => {
                state.loading = false
            })
            await api.fetchTestingDetails({startTimeStamp:0, endTimeStamp:0, fetchCicd:true}).then(resp3 => {
                commit('SAVE_CICD_DETAILS',resp3)
            }).catch(() => {
                state.loading = false
            })
            state.loading = false
        },
        startTestForCollection({commit}, {apiCollectionId, testName}) {
            return api.startTestForCollection(apiCollectionId, testName).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        scheduleTestForCollection({commit}, {apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName,testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId} ) {
            return api.scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        startTestForCustomEndpoints({commit}, {apiInfoKeyList, testName}) {
            return api.startTestForCustomEndpoints(apiInfoKeyList, testName).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        scheduleTestForCustomEndpoints({commit}, {apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId} ) {
            return api.scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        rerunTest({commit}, {testingRunHexId} ) {
            return api.rerunTest(testingRunHexId).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
                func.showSuccessSnackBar('Test has been scheduled for rerun')
            })
        },
        addAuthMechanism({commit}, {type, requestData, authParamData}) {
            return api.addAuthMechanism(type, requestData, authParamData).then(resp => {

            })
        },
        addTestTemplate({commit},{content, originalTestId}) {
            return api.addTestTemplate(content, originalTestId).then(resp => {
                return resp
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
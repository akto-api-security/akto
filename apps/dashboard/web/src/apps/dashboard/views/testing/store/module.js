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
    testingRunResults: [],
    latestTestingRuns: []
}

function setTestingRunIcon(testingRun, testingRunResultSummary){
    if(testingRunResultSummary.metadata!=null){
        return '$far_check-circle/#56bca6';
    }
    if(testingRun.endTimestamp == -1 || testingRun.pickedupTimestamp == -1){
        return '$fas_spinner/var(--themeColorDark)';
    }
    return '$far_check-circle/#56bca6';
}

function getTestingRunType(testingRun, testingRunResultSummary){
    if(testingRunResultSummary.metadata!=null){
        return 'CI/CD';
    }
    if(testingRun.periodInSeconds==0){
        return 'One-time'
    }
    return 'Recurring';
}

function getTotalSeverity(countIssues){
    let ts = 0;
    if(countIssues==null){
        return 0;
    }
    ts = countIssues['HIGH']*1000 + countIssues['MEDIUM']*10 + countIssues['LOW']
    return ts;
}

function getRuntime(scheduleTimestamp ,endTimestamp){
    const currTime = Date.now();

    if(endTimestamp == -1){
        if(currTime > scheduleTimestamp){
            return "Was scheduled for " + func.prettifyEpoch(scheduleTimestamp)
        } else {
            return "Next run in " + func.prettifyEpoch(scheduleTimestamp)
        }
    }
    return 'Last run ' + func.prettifyEpoch(endTimestamp);
}

function getAlternateTestsInfo(state){
    console.log(state._name);
    switch(state._name){
        case "RUNNING": return "Tests are still running";
        case "SCHEDULED": return "Tests have been scheduled";
        case "STOPPED": return "Tests have been stopper";
        default: return "Information unavilable";
    }
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
            state.latestTestingRuns = []
        },
        SAVE_TESTING_DETAILS(state, {testingRuns, lastestTestingRunResultSummaries}) {
            testingRuns.forEach((data)=>{
                let obj={};
                let testingRunResultSummary = lastestTestingRunResultSummaries[data['hexId']];
                if(testingRunResultSummary==null){
                    testingRunResultSummary = {};
                }
                if(testingRunResultSummary.countIssues!=null){
                    testingRunResultSummary.countIssues['High'] = testingRunResultSummary.countIssues['HIGH']
                    testingRunResultSummary.countIssues['Medium'] = testingRunResultSummary.countIssues['MEDIUM']
                    testingRunResultSummary.countIssues['Low'] = testingRunResultSummary.countIssues['LOW']
                    delete testingRunResultSummary.countIssues['HIGH']
                    delete testingRunResultSummary.countIssues['MEDIUM']
                    delete testingRunResultSummary.countIssues['LOW']
                }
                obj['icon'] = setTestingRunIcon(data, testingRunResultSummary);
                obj['name'] = data.name;
                obj['number_of_tests_str'] = (testingRunResultSummary.testResultsCount == null) ? getAlternateTestsInfo(data.state) : lastestTestingRunResultSummaries[data['hexId']].testResultsCount + " Tests";
                obj['run_type'] = getTestingRunType(data, testingRunResultSummary);
                obj['run_time_epoch'] = data.endTimestamp == -1 ? data.scheduleTimestamp : data.endTimestamp
                obj['run_time'] = getRuntime(data.scheduleTimestamp ,data.endTimestamp)
                obj['severity'] = testingRunResultSummary.countIssues == null ? [] : Object.entries(testingRunResultSummary.countIssues).map(([key, value]) => ({ [key]: value })).filter(obj => Object.values(obj)[0] > 0);
                obj['total_severity'] = getTotalSeverity(testingRunResultSummary.countIssues);
                obj['severityTags'] = testingRunResultSummary.countIssues == null ? new Set() : Object.entries(testingRunResultSummary.countIssues)
                    .reduce((acc, [key, value]) => {
                        if (value > 0) {
                            acc.add(key);
                        }
                        return acc;
                    }, new Set());
                state.latestTestingRuns.push(obj);
            })
        },
        SAVE_DETAILS (state, {authMechanism, testingRuns}) {
            state.authMechanism = authMechanism
            state.testingRuns = testingRuns
        },        
        SAVE_CICD_DETAILS (state, {testingRuns}) {
            state.cicdTestingRuns = testingRuns
        },
        SAVE_PAST_DETAILS (state, {testingRuns}) {
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

            await api.fetchTestRunTableInfo().then((resp) => {
                commit('SAVE_TESTING_DETAILS', resp)
            }).catch(() => {
                state.loading = false
            })

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
        scheduleTestForCollection({commit}, {apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName,testRunTime, maxConcurrentRequests} ) {
            return api.scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        startTestForCustomEndpoints({commit}, {apiInfoKeyList, testName}) {
            return api.startTestForCustomEndpoints(apiInfoKeyList, testName).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        scheduleTestForCustomEndpoints({commit}, {apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, source} ) {
            return api.scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, source).then((resp) => {
                commit('SAVE_TESTING_RUNS', resp)
            })
        },
        addAuthMechanism({commit}, {type, requestData, authParamData}) {
            return api.addAuthMechanism(type, requestData, authParamData).then(resp => {

            })
        },
        addTestTemplate({commit},{content, testId, testCategory}) {
            return api.addTestTemplate(content, testId, testCategory).then(resp => {
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
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

const MAX_SEVERITY_THRESHOLD = 100000;

function getOrderPriority(state){
    switch(state._name){
        case "RUNNING": return 1;
        case "SCHEDULED": return 2;
        case "STOPPED": return 4;
        default: return 3;
    }
}

function getTestingRunIcon(state){
    switch(state._name){
        case "RUNNING": return '$fas_spinner/var(--themeColorDark)';
        case "SCHEDULED": return '$far_calendar/var(--themeColor)';
        case "STOPPED": return '$far_times-circle/#EA392C';
        default: return '$far_check-circle/#56bca6';
    }
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
    ts = MAX_SEVERITY_THRESHOLD*(countIssues['High']*MAX_SEVERITY_THRESHOLD + countIssues['Medium']) + countIssues['Low']
    return ts;
}

function getRuntime(scheduleTimestamp ,endTimestamp, state){
    if(state._name=='RUNNING'){
        return "Currently running";
    }
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
    switch(state._name){
        case "RUNNING": return "Tests are still running";
        case "SCHEDULED": return "Tests have been scheduled";
        case "STOPPED": return "Tests have been stopper";
        default: return "Information unavailable";
    }
}

function getTestsInfo(testResultsCount, state){
    return (testResultsCount == null) ? getAlternateTestsInfo(state) : testResultsCount + " tests"
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
        SAVE_TESTING_RUNS_DETAILS(state, {testingRuns, latestTestingRunResultSummaries}) {
            state.latestTestingRuns = []
            testingRuns.forEach((data)=>{
                let obj={};
                let testingRunResultSummary = latestTestingRunResultSummaries[data['hexId']];
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
                obj['hexId'] = data.hexId;
                obj['orderPriority'] = getOrderPriority(data.state)
                obj['icon'] = getTestingRunIcon(data.state);
                obj['name'] = data.name
                obj['number_of_tests_str'] = getTestsInfo(testingRunResultSummary.testResultsCount, data.state)
                obj['run_type'] = getTestingRunType(data, testingRunResultSummary);
                obj['run_time_epoch'] = data.endTimestamp == -1 ? data.scheduleTimestamp : data.endTimestamp
                obj['scheduleTimestamp'] = data.scheduleTimestamp
                obj['run_time'] = getRuntime(data.scheduleTimestamp ,data.endTimestamp, data.state)
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
            state.latestTestingRuns.sort(function(a,b){
                if (a.orderPriority==1 & b.orderPriority!=1)
                    return -1;
                if (b.orderPriority==1 & a.orderPriority!=1)
                    return 1;
                return a.run_time_epoch > b.run_time_epoch ? -1 : 1;
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
        async loadTestingRunDetails({commit}){
            await api.fetchTestRunTableInfo().then((resp) => {
                commit('SAVE_TESTING_RUNS_DETAILS', resp)
            }).catch(() => {
                
            })
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
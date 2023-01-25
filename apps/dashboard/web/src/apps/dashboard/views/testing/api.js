import request from '@/util/request'

export default {
    fetchActiveTestingDetails() {
        return request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    fetchPastTestingDetails({startTimestamp, endTimestamp}) {
        return request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {
                startTimestamp, 
                endTimestamp
            }
        }).then((resp) => {
            return resp
        })
    },

    fetchTestingRunResultSummaries(startTimestamp, endTimestamp, testingRunHexId) {
        return request({
            url: '/api/fetchTestingRunResultSummaries',
            method: 'post',
            data: {
                startTimestamp, 
                endTimestamp,
                testingRunHexId
            }
        })
    },
    startTestForCustomEndpoints(apiInfoKeyList, testName) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM", testName}
        }).then((resp) => {
            return resp
        })
    },

    startTestForCollection(apiCollectionId, testName) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiCollectionId, type: "COLLECTION_WISE", testName}
        }).then((resp) => {
            return resp
        })        
    },

    scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM", startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests}
        }).then((resp) => {
            return resp
        })        
    },

    scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiCollectionId, type: "COLLECTION_WISE", startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests}
        }).then((resp) => {
            return resp
        })        
    },

    addAuthMechanism(type, requestData, authParamData) {
        return request({
            url: '/api/addAuthMechanism',
            method: 'post',
            data: {type, requestData, authParamData}
        }).then((resp) => {
            return resp
        })        
    },

    fetchTestingRunResults(testingRunResultSummaryHexId) {
        return request({
            url: '/api/fetchTestingRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId
            }
        }).then((resp) => {
            return resp
        })        
    },

    stopAllTests() {
        return request({
            url: '/api/stopAllTests',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })        
    },

    fetchTestRunResultDetails(testingRunResultHexId) {
        return request({
            url: '/api/fetchTestRunResultDetails',
            method: 'post',
            data: {
                testingRunResultHexId
            }
        })
    },

    fetchIssueFromTestRunResultDetails(testingRunResultHexId) {
        return request({
            url: '/api/fetchIssueFromTestRunResultDetails',
            method: 'post',
            data: {
                testingRunResultHexId
            }
        })
    },

    triggerLoginSteps(type, requestData, authParamData) {
        return request({
            url: 'api/triggerLoginSteps',
            method: 'post',
            data: {type, requestData, authParamData}
        }).then((resp) => {
            return resp
        })
    },

    triggerSingleStep(type, nodeId, requestData) {
        return request({
            url: 'api/triggerSingleLoginFlow',
            method: 'post',
            data: {type, nodeId, requestData}
        }).then((resp) => {
            return resp
        })
    },

    fetchAuthMechanismData() {
        return request({
            url: '/api/fetchAuthMechanismData',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },

    fetchOtpData(url) {
        return request({
            url: url,
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },

    uploadRecordedLoginFlow(content, tokenFetchCommand) {
        return request({
            url: '/api/uploadRecordedFlow',
            method: 'post',
            data: {content, tokenFetchCommand}
        }).then((resp) => {
            return resp
        })
    },

    fetchRecordedLoginFlow(nodeId) {
        return request({
            url: '/api/fetchRecordedFlowOutput',
            method: 'post',
            data: {nodeId}
        }).then((resp) => {
            return resp
        })
    }
}
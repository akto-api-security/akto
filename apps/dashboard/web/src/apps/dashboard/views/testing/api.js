import request from '@/util/request'

export default {
    fetchTestingDetails({startTimestamp, endTimestamp, fetchCicd, sortKey, sortOrder, skip, limit, filters}) {
        return request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {
                startTimestamp, endTimestamp, fetchCicd , sortKey, sortOrder, skip, limit, filters
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

    scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM", startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId}
        }).then((resp) => {
            return resp
        })        
    },

    rerunTest(testingRunHexId) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {"testingRunHexId": testingRunHexId}
        }).then((resp) => {
            return resp
        })        
    },

    scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiCollectionId, type: "COLLECTION_WISE", startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId}
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
    addTestTemplate(content,originalTestId) {
        return request({
            url: '/api/saveTestEditorFile',
            method: 'post',
            data:{content, originalTestId}
        }).then((resp) => {
            return resp
        })
    },
    runTestForTemplate(content, apiInfoKey, sampleDataList) {
        return request({
            url: '/api/runTestForGivenTemplate',
            method: 'post',
            data:{content, apiInfoKey, sampleDataList}
        }).then((resp) => {
            return resp
        })
    },
    runTestForTemplateAnonymous(content, apiInfoKey, sampleDataList) {
        return request({
            url: '/tools/runTestForGivenTemplate',
            method: 'post',
            data:{content, apiInfoKey, sampleDataList}
        }).then((resp) => {
            return resp
        })
    },
    setCustomSampleApi(sampleRequestString, sampleResponseString) {
        return request({
            url: '/tools/createSampleDataJson',
            method: 'post',
            data:{sampleRequestString, sampleResponseString}
        }).then((resp) => {
            return resp
        })
    },
    fetchTestingRunResults(testingRunResultSummaryHexId, fetchOnlyVulnerable) {
        return request({
            url: '/api/fetchTestingRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId, fetchOnlyVulnerable
            }
        }).then((resp) => {
            return resp
        })        
    },

    fetchVulnerableTestingRunResults(testingRunResultSummaryHexId, skip) {
        return request({
            url: '/api/fetchVulnerableTestRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId,
                skip
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

    fetchTestingRunResultFromTestingRun(testingRunHexId) {
        return request({
            url: '/api/fetchTestingRunResultFromTestingRun',
            method: 'post',
            data: {
                testingRunHexId
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

    fetchIssueFromTestRunResultDetailsForTestEditor(testingRunResultHexId, isTestRunByTestEditor) {
        return request({
            url: '/api/fetchIssueFromTestRunResultDetails',
            method: 'post',
            data: {
                testingRunResultHexId, isTestRunByTestEditor
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
    },

    fetchTestingLogs(logFetchStartTime, logFetchEndTime) {
        return request({
            url: '/api/fetchTestingLogs',
            method: 'post',
            data: {logFetchStartTime, logFetchEndTime}
        }).then((resp) => {
            return resp
        })
    },

    async fetchMetadataFilters() {
        const resp = await request({
            url: '/api/fetchMetadataFilters',
            method: 'post',
            data: {}
        })
        return resp
    },
    
    setTestInactive(testId, inactive){
        return request({
            url: '/api/setTestInactive',
            method: 'post',
            data: {originalTestId: testId, inactive: inactive}
        }).then((resp) => {
            return resp
        })
    }
}
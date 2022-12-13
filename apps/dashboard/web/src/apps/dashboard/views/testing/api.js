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
    startTestForCustomEndpoints(apiInfoKeyList) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM"}
        }).then((resp) => {
            return resp
        })
    },

    startTestForCollection(apiCollectionId) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiCollectionId, type: "COLLECTION_WISE"}
        }).then((resp) => {
            return resp
        })        
    },

    scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM", startTimestamp, recurringDaily}
        }).then((resp) => {
            return resp
        })        
    },

    scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiCollectionId, type: "COLLECTION_WISE", startTimestamp, recurringDaily}
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

    triggerLoginSteps(type, requestData, authParamData) {
        return request({
            url: 'api/triggerLoginSteps',
            method: 'post',
            data: {type, requestData, authParamData}
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
}
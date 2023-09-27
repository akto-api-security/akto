import request from "../../../../util/request"

export default {
    async fetchTestingDetails(startTimestamp, endTimestamp, fetchCicd, fetchAll, sortKey, sortOrder, skip, limit, filters) {
        const resp = await request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {
                startTimestamp, endTimestamp, fetchCicd, fetchAll, sortKey, sortOrder, skip, limit, filters
            }
        })
        return resp
    },

    async fetchTestingRunResultSummaries(testingRunHexId, startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchTestingRunResultSummaries',
            method: 'post',
            data: {
                testingRunHexId,
                startTimestamp, 
                endTimestamp
            }
        })
        return resp
    },
    async fetchTestingRunResults(testingRunResultSummaryHexId, fetchOnlyVulnerable) {
        const resp = await request({
            url: '/api/fetchTestingRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId, fetchOnlyVulnerable
            }
        })
        return resp        
    },
    async fetchAllSubCategories (fetchOnlyActive) {
        const resp = await request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: {fetchOnlyActive}
        })
        return resp
    },
    async stopTest(testingRunHexId) {
        const resp = await request({
            url: '/api/stopTest',
            method: 'post',
            data: { testingRunHexId }
        })
        return resp        
    },
    async rerunTest(testingRunHexId){
        const resp = await request({
            url: '/api/startTest',
            method: 'post',
            data: { testingRunHexId }
        })
        return resp        
    },
    fetchAffectedEndpoints (issueId) {
        return request({
            url: 'api/fetchAffectedEndpoints',
            method: 'post',
            data: {issueId}
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
    async fetchCollectionWiseApiEndpoints (apiCollectionId) {
        const resp = await request({
            url: '/api/fetchCollectionWiseApiEndpoints',
            method: 'post',
            data: { apiCollectionId }
        })
        return resp        
    },
    async fetchTestRoles() {
        const resp = await request({
            url: '/api/fetchTestRoles',
            method: 'post',
            data: {}
        })
        return resp
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
    addAuthMechanism(type, requestData, authParamData) {
        return request({
            url: '/api/addAuthMechanism',
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
        })},
    triggerSingleStep(type, nodeId, requestData) {
        return request({
            url: 'api/triggerSingleLoginFlow',
            method: 'post',
            data: {type, nodeId, requestData}
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
    async addTestRoles (roleName, andConditions, orConditions) {
        const resp = await request({
            url: '/api/addTestRoles',
            method: 'post',
            data: { roleName, andConditions, orConditions }
        })
        return resp        
    },
    async updateTestRoles (roleName, andConditions, orConditions) {
        const resp = await request({
            url: '/api/updateTestRoles',
            method: 'post',
            data: { roleName, andConditions, orConditions }
        })
        return resp        
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
    async fetchMetadataFilters() {
        const resp = await request({
            url: '/api/fetchMetadataFilters',
            method: 'post',
            data: {}
        })
        return resp
    },

    async getCountsMap(){
        return await request({
            url: '/api/getAllTestsCountMap',
            method: 'post',
            data: {}
        })
    }
}
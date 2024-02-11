import request from "../../../../util/request"

export default {
    async fetchTestingDetails(startTimestamp, endTimestamp,  sortKey, sortOrder, skip, limit, filters, testingRunType) {
        const resp = await request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {
                startTimestamp, endTimestamp,  sortKey, sortOrder, skip, limit, filters, testingRunType
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
    createJiraTicket(hostStr, endPointStr, issueUrl, issueDescription, issueTitle, testingIssueId) {
        return request({
            url: '/api/createJiraIssue',
            method: 'post',
            data: {
                hostStr, endPointStr, issueUrl, issueDescription, issueTitle, testingIssueId
            }
        })
    },
    attachFileToIssue(origReq, testReq, issueId) {
        return request({
            url: '/api/attachFileToIssue',
            method: 'post',
            data: {
                origReq, testReq, issueId
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

    async getCountsMap(startTimestamp, endTimestamp){
        return await request({
            url: '/api/getAllTestsCountMap',
            method: 'post',
            data: {startTimestamp, endTimestamp}
        })
    },

    async getSummaryInfo(startTimestamp, endTimestamp){
        return await request({
            url: '/api/getIssueSummaryInfo',
            method: 'post',
            data: {
                startTimestamp: startTimestamp,
                endTimestamp: endTimestamp,
            }
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
        })
    },

        addAuthToRole(roleName, apiCond, authParamData, authAutomationType, reqData) {
            return request({
                url: '/api/addAuthToRole',
                method: 'post',
                data: {roleName, apiCond, authParamData, authAutomationType, reqData}
            })
        },
        deleteAuthFromRole(roleName, index) {
            return request({
                url: '/api/deleteAuthFromRole',
                method: 'post',
                data: {roleName, index}
            })
        },

    deleteTestRuns(testRunIds){
        return request({
            url: '/api/deleteTestRuns',
            method: 'post',
            data: {
               testRunIds
            }
        })
    },

    deleteTestRunsFromSummaries(latestSummaryIds){
        return request({
            url: '/api/deleteTestRunsFromSummaries',
            method: 'post',
            data: {
                latestSummaryIds
            }
        })
    },
    
    buildDependencyTable(apiCollectionIds, skip){
        return request({
            url: '/api/buildDependencyTable',
            method: 'post',
            data: {
                apiCollectionIds, skip
            }
        })
    },

    getUserTestRuns(){
        return request({
            url: '/api/fetchUsageTestRuns',
            method: 'post',
            data: {}
        })
    },
    invokeDependencyTable(apiCollectionIds){
        return request({
            url: '/api/invokeDependencyTable',
            method: 'post',
            data: {
                apiCollectionIds
            }
        })
    },

    saveReplaceDetails(apiCollectionId, url, method, kvPairs){
        return request({
            url: '/api/saveReplaceDetails',
            method: 'post',
            data: {
                apiCollectionId, url, method, kvPairs
            }
        })
    },

    fetchGlobalVars(apiCollectionIds){
        return request({
            url: '/api/fetchGlobalVars',
            method: 'post',
            data: {
                apiCollectionIds
            }
        })
    },

    saveGlobalVars(modifyHostDetails){
        return request({
            url: '/api/saveGlobalVars',
            method: 'post',
            data: {
                modifyHostDetails
            }
        })
    },

    fetchValuesForParameters(apiCollectionId, url, method, params){
        if (typeof apiCollectionId === "string") {
            apiCollectionId = parseInt(apiCollectionId)
        }
        return request({
            url: '/api/fetchValuesForParameters',
            method: 'post',
            data: {
                apiCollectionId, url, method, params
            }
        })
    },
}
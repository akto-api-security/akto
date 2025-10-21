import request from "@/util/request"

export default {
    async fetchTestingDetails(startTimestamp, endTimestamp,  sortKey, sortOrder, skip, limit, filters, testingRunType, searchString) {
        const resp = await request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {
                startTimestamp, endTimestamp,  sortKey, sortOrder, skip, limit, filters, testingRunType, searchString
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
    async fetchTestingRunResults(testingRunResultSummaryHexId, queryMode, sortKey, sortOrder, skip, limit, reportFilterList, queryValue) {
        const resp = await request({
            url: '/api/fetchTestingRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId, queryMode, sortKey, sortOrder, skip, limit, reportFilterList, queryValue
            }
        })
        return resp        
    },
    async fetchTestRunResultsCount(testingRunResultSummaryHexId, filters) {
        const resp = await request({
            url: '/api/fetchTestRunResultsCount',
            method: 'post',
            data: {
                testingRunResultSummaryHexId,
                filters
            }
        })
        return resp        
    },
    async fetchTestResultsStatsCount(data) {
        const resp = await request({
            url: '/api/fetchTestResultsStatsCount',
            method: 'post',
            data: data
        })
        return resp        
    },
    async fetchRemediationInfo(testId) {
        const resp = await request({
            url: 'api/fetchRemediationInfo',
            method: 'post',
            data: {testId}
        })
        return resp
    },
    async analyzeVulnerability(responseOutput, analysisType = 'redteaming') {
        const resp = await request({
            url: '/api/analyze_vulnerability',
            method: 'post',
            data: {
                requestData: responseOutput,
                analysisType: analysisType
            }
        })
        return resp
    },
    async fetchAllSubCategories(fetchOnlyActive, mode, skip, limit) {
        const resp = await request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: { fetchOnlyActive, mode, skip, limit }
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
    async rerunTest(testingRunHexId, selectedTestRunForRerun, testingRunResultSummaryHexId ){
        if (selectedTestRunForRerun === []) {
            const resp = await request({
                url: '/api/startTest',
                method: 'post',
                data: { testingRunHexId }
            })
            return resp        
        }
        let selectedTestRunResultHexIds = selectedTestRunForRerun
        const resp = await request({
            url: '/api/startTest',
            method: 'post',
            data: { testingRunHexId, testingRunResultSummaryHexId,  selectedTestRunResultHexIds}
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
    createJiraTicket(jiraMetaData, projId, issueType) {
        return request({
            url: '/api/createJiraIssue',
            method: 'post',
            data: {
                jiraMetaData, issueType, projId
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
            data: { roleName, andConditions, orConditions}
        })
        return resp        
    },
    async saveTestRoleMeta(roleName, scopeRoles) {
        if(scopeRoles && scopeRoles.length === 0){
            return;
        }
        const resp = await request({
            url: '/api/saveTestRoleMeta',
            method: 'post',
            data: { roleName, scopeRoles}
        })
        return resp        
    },
    async deleteTestRole (roleName) {
        const resp = await request({
            url: 'api/deleteTestRole',
            method: 'post',
            data: { roleName }
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

    async getCountsMap(startTimestamp, endTimestamp, filters){
        return await request({
            url: '/api/getAllTestsCountMap',
            method: 'post',
            data: {startTimestamp, endTimestamp, filters}
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
    fetchVulnerableTestingRunResults(testingRunResultSummaryHexId, skip, reportFilterList) {
        return request({
            url: '/api/fetchVulnerableTestRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId,
                skip,
                reportFilterList
            }
        })
    },
    addAuthToRole(roleName, apiCond, authParamData, authAutomationType, reqData, recordedLoginFlowInput) {
        return request({
            url: '/api/addAuthToRole',
            method: 'post',
            data: {roleName, apiCond, authParamData, authAutomationType, reqData, recordedLoginFlowInput}
        })
    },
    deleteAuthFromRole(roleName, index) {
        return request({
            url: '/api/deleteAuthFromRole',
            method: 'post',
            data: {roleName, index}
        })
    },
    updateAuthInRole(roleName, apiCond ,index, authParamData, authAutomationType, reqData, recordedLoginFlowInput) {
        return request({
            url: '/api/updateAuthInRole',
            method: 'post',
            data: {roleName, apiCond, index, authParamData, authAutomationType, reqData, recordedLoginFlowInput}
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
    invokeDependencyTable(apiCollectionIds, sourceCodeApis){
        return request({
            url: '/api/invokeDependencyTable',
            method: 'post',
            data: {
                apiCollectionIds,
                sourceCodeApis
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
    updateGlobalRateLimit(globalRateLimit) {
        return request({
            url: '/api/updateGlobalRateLimit',
            method: 'post',
            data: {
                globalRateLimit
            }
        })
    },
    fetchAccessMatrixUrlToRoles(){
        return request({
            url: '/api/fetchAccessMatrixUrlToRoles',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    createMultipleAccessMatrixTasks(roleName){
        return request({
            url: '/api/createMultipleAccessMatrixTasks',
            method: 'post',
            data: {roleName}
        }).then((resp) => {
            return resp
        })
    },
    deleteAccessMatrix(roleName){
        return request({
            url: '/api/deleteAccessMatrix',
            method: 'post',
            data: {roleName}
        })
    },

    fetchTestingRunStatus(){
        return request({
            url: '/api/fetchActiveTestRunsStatus',
            method: 'post',
            data: {}
        })
    },
    fetchTestCollectionConfiguration(apiCollectionId) {
        return request({
            url: '/api/fetchTestCollectionConfiguration',
            method: 'post',
            data: {apiCollectionId}
        })
    },
    fetchTestCollectionProperty(tcpId) {
        return request({
            url: '/api/fetchTestCollectionProperty',
            method: 'post',
            data: {tcpId}
        })
    },
    fetchPropertyIds() {
        return request({
            url: '/api/fetchPropertyIds',
            method: 'post',
            data: {}
        })
    },
    downloadReportPDF(reportId, organizationName, reportDate, reportUrl, username, firstPollRequest) {
        return request({
            url: '/api/downloadReportPDF',
            method: 'post',
            data: {reportId, organizationName, reportDate, reportUrl, username, firstPollRequest}
        })
    },
    fetchScript() {
        return request({
            url: '/api/fetchScript',
            method: 'post',
            data: {}
        })
    },
    addScript({javascript}) {
        return request({
            url: '/api/addScript',
            method: 'post',
            data: {testScript:{javascript}}
        })
    },
    updateScript(id, javascript) {
        return request({
            url: '/api/updateScript',
            method: 'post',
            data: {testScript:{id, javascript}}
        })
    },
    updateDeltaTimeForSummaries(deltaTimeForScheduledSummaries){
        return request({
            url: '/api/updateIgnoreTimeForSummaries',
            method: 'post',
            data: {deltaTimeForScheduledSummaries}
        })
    },
    modifyTestingRunConfig(testingRunConfigId, editableTestingRunConfig) {
        const requestData = { testingRunConfigId, editableTestingRunConfig }
        return request({
            url: '/api/modifyTestingRunConfig',
            method: 'post',
            data: requestData
        })
    },
    async fetchTestingRunResultsSummary(testingRunSummaryId) {
        const resp = await request({
            url: '/api/fetchTestingRunResultsSummary',
            method: 'post',
            data: {
                testingRunSummaryId
            }
        })
        return resp
    },
    generatePDFReport(reportFilterList, issuesIdsForReport){
        return request({
            url: '/api/generateReportPDF',
            method: 'post',
            data: { reportFilterList, issuesIdsForReport }
        })
    },
    getReportFilters(generatedReportId){
        return request({
            url: '/api/getReportFilters',
            method: 'post',
            data: { generatedReportId }
        })
    },
    fetchSeverityInfoForIssues(filters, issueIds, endTimeStamp) {
        return request({
            url: '/api/fetchSeverityInfoForIssues',
            method: 'post',
            data: {...filters, issueIds, endTimeStamp}
        })
    },
    handleRefreshTableCount(testingRunResultSummaryHexId) {
        return request({
            url: '/api/handleRefreshTableCount',
            method: 'post',
            data: {testingRunResultSummaryHexId}
        })
    },
    createNewTestSuite(testSuiteName,subCategoryList) {
        return request({
            url: '/api/createTestSuite',
            method: 'post',
            data: {testSuiteName,subCategoryList}
        })
    },
    fetchAllTestSuites() {
        return request({
            url: '/api/fetchAllTestSuites',
            method: 'post',
            data: {}
        })
    },
    modifyTestSuite(testSuiteHexId, testSuiteName, subCategoryList) {
        return request({
            url: '/api/modifyTestSuite',
            method: 'post',
            data: {testSuiteHexId, testSuiteName, subCategoryList}
        })
    },
    deleteTestSuite(testSuiteHexId) {
        return request({
            url: '/api/deleteTestSuite',
            method: 'post',
            data: {testSuiteHexId}
        })
    },
    fetchMiniTestingServiceNames() {
        return request({
            url: '/api/fetchMiniTestingServiceNames',
            method: 'post',
            data: {}
        })
    },
    updateIssueDescription(issueId, description) {
        return request({
            url: '/api/updateIssueDescription',
            method: 'post',
            data: { issueId, description }
        })
    },
    fetchCommonTestTemplate() {
        return request({
            url: '/api/fetchCommonTestTemplate',
            method: 'post',
            data: {}
        })
    },
    saveCommonTestTemplate(content) {
        return request({
            url: '/api/saveCommonTestTemplate',
            method: 'post',
            data: { content }
        })
    },
    allTestsCountsRanges() {
        return request({
            url: '/api/fetchTestingRunsRanges',
            method: 'post',
            data: {}
        })
    },
    getUniqueHostsTested(testingRunId) {
        return request({
            url: '/api/getUniqueHostsTested',
            method: 'post',
            data: { testingRunId }
        })
    },
    async fetchCategoryWiseScores(startTimestamp, endTimestamp, dashboardCategory, dataSource = 'testing') {
        const resp = await request({
            url: '/api/fetchCategoryWiseScores',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp,
                dashboardCategory,
                dataSource
            }
        })
        return resp
    },
    async fetchConversationsFromConversationId(conversationId) {
        const resp = await request({
            url: '/api/fetchConversationsFromConversationId',
            method: 'post',
            data: {
                conversationId
            }
        })
        return resp
    },
}
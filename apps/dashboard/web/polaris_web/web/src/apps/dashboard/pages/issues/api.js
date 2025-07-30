import request from "../../../../util/request"

export default {
    fetchIssues(skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, sortKey, sortOrder, startEpoch, endTimeStamp, activeCollections, filterCompliance) {
        return request({
            url: 'api/fetchAllIssues',
            method: 'post',
            data: {skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, sortKey, sortOrder, startEpoch, endTimeStamp, activeCollections, filterCompliance}
        })
    },
    fetchVulnerableTestingRunResultsFromIssues(filters, issuesIds , skip) {
        filters['skip'] = skip
        return request({
            url: 'api/fetchVulnerableTestingRunResultsFromIssues',
            method: 'post',
            data: {...filters, issuesIds}
        })
    },
    fetchIssuesFromResultIds(issuesIds, issueStatusQuery) {
        return request({
            url: 'api/fetchIssuesFromResultIds',
            method: 'post',
            data: {issuesIds, issueStatusQuery}
        })
    },
    bulkUpdateIssueStatus (issueIdArray, statusToBeUpdated, ignoreReason, testingRunResultHexIdsMap) {
        return request({
            url: 'api/bulkUpdateIssueStatus',
            method: 'post',
            data: {issueIdArray, statusToBeUpdated, ignoreReason, testingRunResultHexIdsMap}
        })
    },
    fetchTestingRunResult (issueId) {
        return request({
            url: 'api/fetchTestingRunResult',
            method: 'post',
            data: {issueId}
        })
    },
    findTotalIssuesByDay (startTimeStamp, endTimeStamp) {
        return request({
            url: 'api/findTotalIssuesByDay',
            method: 'post',
            data: {startEpoch: startTimeStamp, endTimeStamp}
        })
    },
    fetchTestCoverageData (startTimeStamp, endTimeStamp) {
        return request({
            url: 'api/fetchTestCoverageData',
            method: 'post',
            data: {startTimeStamp, endTimeStamp}
        })
    },
    bulkCreateJiraTickets(issuesIds, aktoDashboardHost, projId, issueType, jiraMetaData){
        return request({
            url: 'api/bulkCreateJiraTickets',
            method: 'post',
            data: {issuesIds, aktoDashboardHost, projId, issueType, jiraMetaData}
        })
    },
    fetchCreateJiraIssueFieldMetaData() {
        return request({
            url: 'api/fetchCreateJiraIssueFieldMetaData',
            method: 'post',
            data: {}
        })
    },
    createAzureBoardsWorkItem(testingIssuesId, projectName, workItemType, aktoDashboardHostName) {
        return request({
            url: 'api/createAzureBoardsWorkItem',
            method: 'post',
            data: {testingIssuesId, projectName, workItemType, aktoDashboardHostName}
        })
    },
    bulkCreateAzureWorkItems(testingIssuesIdList, projectName, workItemType, aktoDashboardHostName) {
        return request({
            url: 'api/bulkCreateAzureWorkItems',
            method: 'post',
            data: {testingIssuesIdList, projectName, workItemType, aktoDashboardHostName}
        })
    },
    createGeneralJiraTicket(payload) {
        return request({
            url: 'api/createGeneralJiraTicket',
            method: 'post',
            data: payload
        })
    },
    fetchIssuesByApis() {
        return request({
            url: 'api/fetchIssuesByApis',
            method: 'post',
            data: {}
        })
    }
}
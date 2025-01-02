import request from "../../../../util/request"

export default {
    fetchIssues(skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, sortKey, sortOrder, startEpoch, endTimeStamp) {
        return request({
            url: 'api/fetchAllIssues',
            method: 'post',
            data: {skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, sortKey, sortOrder, startEpoch, endTimeStamp}
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
    bulkCreateJiraTickets(issuesIds, aktoDashboardHost, projId, issueType){
        return request({
            url: 'api/bulkCreateJiraTickets',
            method: 'post',
            data: {issuesIds, aktoDashboardHost, projId, issueType}
        })
    }
}
import request from "../../../../util/request"

export default {
    fetchIssues(skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, startEpoch) {
        return request({
            url: 'api/fetchAllIssues',
            method: 'post',
            data: {skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, startEpoch}
        })
    },
    fetchVulnerableTestingRunResultsFromIssues(filters, skip) {
        filters['skip'] = skip
        return request({
            url: 'api/fetchVulnerableTestingRunResultsFromIssues',
            method: 'post',
            data: filters
        })
    },
    bulkUpdateIssueStatus (issueIdArray, statusToBeUpdated, ignoreReason) {
        return request({
            url: 'api/bulkUpdateIssueStatus',
            method: 'post',
            data: {issueIdArray, statusToBeUpdated, ignoreReason}
        })
    },
    fetchTestingRunResult (issueId) {
        return request({
            url: 'api/fetchTestingRunResult',
            method: 'post',
            data: {issueId}
        })
    },
}
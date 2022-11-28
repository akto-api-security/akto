import request from '@/util/request'

export default {
    fetchIssues(skip, limit) {
        return request({
            url: 'api/fetchAllIssues',
            method: 'post',
            data: {skip, limit}
        })
    },
    updateIssueStatus(issueId, statusToBeUpdated, ignoreReason) {
        return request({
            url: 'api/updateIssueStatus',
            method: 'post',
            data: {issueId, statusToBeUpdated, ignoreReason}
        })
    }
}
import request from "../../../../util/request"

export default {
    fetchIssues(skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, startEpoch) {
        return request({
            url: 'api/fetchAllIssues',
            method: 'post',
            data: {skip, limit, filterStatus, filterCollectionsId, filterSeverity, filterSubCategory, startEpoch}
        })
    },
}
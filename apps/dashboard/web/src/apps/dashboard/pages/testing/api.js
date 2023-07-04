import request from "../../../../util/request"

export default {
    async fetchTestRunTableInfo() {
        const resp = await request({
            url: '/api/fetchTestRunTableInfo',
            method: 'post',
            data: {}
        })
        return resp
    },
    fetchTestingRunResultSummaries(testingRunHexId) {
        return request({
            url: '/api/fetchTestingRunResultSummaries',
            method: 'post',
            data: {
                testingRunHexId
            }
        })
    },
    async fetchTestingRunResults(testingRunResultSummaryHexId) {
        const resp = await request({
            url: '/api/fetchTestingRunResults',
            method: 'post',
            data: {
                testingRunResultSummaryHexId
            }
        })
        return resp        
    },
    fetchAllSubCategories () {
        return request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: {}
        })
    }
}
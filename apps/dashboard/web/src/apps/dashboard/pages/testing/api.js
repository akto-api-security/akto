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
    async fetchTestingRunResultSummaries(testingRunHexId) {
        const resp = await request({
            url: '/api/fetchTestingRunResultSummaries',
            method: 'post',
            data: {
                testingRunHexId
            }
        })
        return resp
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
    async fetchAllSubCategories () {
        const resp = await request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: {}
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
}
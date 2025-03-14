import request from "@/util/request"

const threatDetectionRequests = {
    fetchFilterYamlTemplate() {
        return request({
            url: '/api/fetchFilterYamlTemplate',
            method: 'post',
            data: {}
        })
    },
    saveFilterYamlTemplate(content) {
        return request({
            url: 'api/saveFilterYamlTemplate',
            method: 'post',
            data: { content: content }
        })
    },

    fetchSuspectSampleData(skip, ips, apiCollectionIds, urls, types, sort, startTimestamp, endTimestamp, subCategory) {
        return request({
            url: '/api/fetchSuspectSampleData',
            method: 'post',
            data: {
                skip: skip,
                ips: ips,
                urls: urls,
                types: types,
                apiCollectionIds: apiCollectionIds,
                sort: sort,
                startTimestamp: startTimestamp,
                endTimestamp: endTimestamp,
                subCategory: subCategory
            }
        })
    },
    fetchFiltersThreatTable() {
        return request({
            url: '/api/fetchFiltersThreatTable',
            method: 'post',
            data: {}
        })
    },
    fetchThreatActors(skip, sort, latestAttack, country) {
        return request({
            url: '/api/fetchThreatActors',
            method: 'post',
            data: {
                skip: skip,
                sort: sort,
                latestAttack: latestAttack,
                country: country
            }
        })
    },
    fetchThreatApis(skip, sort) {
        return request({
            url: '/api/fetchThreatApis',
            method: 'post',
            data: {
                skip: skip,
                sort: sort
            }
        })
    },
    getActorsCountPerCounty() {
        return request({
            url: '/api/getActorsCountPerCounty',
            method: 'get',
            data: {}
        })
    },
    fetchThreatCategoryCount() {
        return request({
            url: '/api/fetchThreatCategoryCount',
            method: 'get',
            data: {}
        })
    },
    fetchMaliciousRequest(refId){
        return request({
            url: '/api/fetchAggregateMaliciousRequests',
            method: 'post',
            data: {refId}
        })
    },
    fetchCountBySeverity() {
        return request({
            url: '/api/fetchCountBySeverity',
            method: 'post',
            data: {}
        })
    },
    getThreatActivityTimeline(startTs, endTs) {
        return request({
            url: '/api/getThreatActivityTimeline',
            method: 'post',
            data: {startTs, endTs}
        })
    },
    getDailyThreatActorsCount(startTs, endTs) {
        return request({
            url: '/api/getDailyThreatActorsCount',
            method: 'post',
            data: {startTs, endTs}
        })
    },
    fetchSensitiveParamsForEndpoints (urls) {
        return request({
            url: '/api/fetchSensitiveParamsForEndpoints',
            method: 'post',
            data: {urls}
        }).then((resp) => {
            return resp
        })
    },
    getAccessTypes(urls) {
        return request({
            url: '/api/getAccessTypes',
            method: 'post',
            data: {urls}
        })
    }
}
export default threatDetectionRequests
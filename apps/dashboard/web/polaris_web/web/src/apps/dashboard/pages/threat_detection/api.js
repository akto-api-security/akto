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

    fetchSuspectSampleData(skip, ips, apiCollectionIds, urls, types, sort, startTimestamp, endTimestamp) {
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
                endTimestamp: endTimestamp
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
    fetchThreatActors(skip, sort) {
        return request({
            url: '/api/fetchThreatActors',
            method: 'post',
            data: {
                skip: skip,
                sort: sort
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
    }
}

export default threatDetectionRequests
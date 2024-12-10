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

    fetchSuspectSampleData(skip, ips, apiCollectionIds, urls, sort, startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchSuspectSampleDataV2',
            method: 'post',
            data: {
                skip: skip,
                ips: ips,
                urls: urls,
                apiCollectionIds: apiCollectionIds,
                sort: sort,
                startTimestamp: startTimestamp,
                endTimestamp: endTimestamp
            }
        })
    },
    fetchFiltersThreatTable() {
        return request({
            url: '/api/fetchFiltersThreatTableV2',
            method: 'post',
            data: {}
        })
    },
}

export default threatDetectionRequests
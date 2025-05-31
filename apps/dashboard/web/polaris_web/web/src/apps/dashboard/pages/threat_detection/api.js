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

    fetchSuspectSampleData(skip, ips, apiCollectionIds, urls, types, sort, startTimestamp, endTimestamp, subCategory, severity) {
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
                subCategory: subCategory,
                severity: severity
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
    fetchThreatActors(skip, sort, latestAttack, country, startTs, endTs, actorId) {
        return request({
            url: '/api/fetchThreatActors',
            method: 'post',
            data: {
                skip: skip,
                sort: sort,
                latestAttack: latestAttack,
                country: country,
                startTs: startTs,
                endTs: endTs,
                actorId: actorId
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
    fetchThreatConfiguration() {
        return request({
            url: '/api/fetchThreatConfiguration',
            method: 'get',
        })
    },
    modifyThreatConfiguration(data) {
        return request({
            url: '/api/modifyThreatConfiguration',
            method: 'post',
            data: { threatConfiguration: data}
        })
    },
    fetchThreatCategoryCount(startTs, endTs) {
        return request({
            url: '/api/fetchThreatCategoryCount',
            method: 'post',
            data: {startTs, endTs}
        })
    },
    fetchMaliciousRequest(refId, eventType){
        return request({
            url: '/api/fetchAggregateMaliciousRequests',
            method: 'post',
            data: {refId, eventType}
        })
    },
    fetchCountBySeverity(startTs, endTs) {
        return request({
            url: '/api/fetchCountBySeverity',
            method: 'post',
            data: {startTs, endTs}
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
    },
    fetchThreatActorFilters() {
        return request({
            url: '/api/fetchFiltersForThreatActors',
            method: 'post',
            data: {}
        })
    },
    modifyThreatActorStatus(actorIp, status) {
        return request({
            url: '/api/modifyThreatActorStatus',
            method: 'post',
            data: {actorIp, status}
        })
    },
    modifyThreatActorStatusCloudflare(actorIp, status) {
        return request({
            url: '/api/modifyThreatActorStatusCloudflare',
            method: 'post',
            data: {actorIp, status}
        })
    }
}
export default threatDetectionRequests
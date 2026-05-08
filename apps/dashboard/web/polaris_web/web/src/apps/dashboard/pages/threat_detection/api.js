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

    deleteFilterYamlTemplate(templateId) {
        return request({
            url: '/api/deleteFilterYamlTemplate',
            method: 'post',
            data: { templateId }
        })
    },

    fetchSuspectSampleData(skip, ips, apiCollectionIds, urls, types, sort, startTimestamp, endTimestamp, latestAttack, limit, statusFilter, successfulExploit, label, hosts, latestApiOrigRegex) {
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
                latestAttack: latestAttack || [],
                limit: limit || 50,
                statusFilter: statusFilter,
                ...(typeof successfulExploit === 'boolean' ? { successfulExploit } : {}),
                ...(label ? { label } : {}),
                ...(hosts && hosts.length > 0 ? { hosts } : {}),
                ...(latestApiOrigRegex ? { latestApiOrigRegex } : {})
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
    fetchThreatActors(skip, sort, latestAttack, country, startTs, endTs, actorId, host) {
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
                actorId: actorId,
                host: host
            }
        })
    },
    fetchThreatApis(skip, sort, latestAttack) {
        return request({
            url: '/api/fetchThreatApis',
            method: 'post',
            data: {
                skip: skip,
                sort: sort,
                latestAttack: latestAttack || []
            }
        })
    },
    getActorsCountPerCounty(startTs, endTs) {
        return request({
            url: '/api/getActorsCountPerCounty',
            method: 'post',
            data: {startTs, endTs}
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
    fetchMaliciousRequest(refId, eventType, actor, filterId) {
        return request({
            url: '/api/fetchAggregateMaliciousRequests',
            method: 'post',
            data: {refId, eventType, actor, filterId}
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
    getDailyThreatActorsCount(startTs, endTs, latestAttack) {
        return request({
            url: '/api/getDailyThreatActorsCount',
            method: 'post',
            data: {startTs, endTs, latestAttack: latestAttack || []}
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
    },
    updateMaliciousEventStatus(data) {
        // Handles all cases: single event (eventId), bulk (eventIds), or filter-based
        return request({
            url: '/api/updateMaliciousEventStatus',
            method: 'post',
            data: data
        })
    },
    deleteMaliciousEvents(data) {
        // Handles both bulk delete (eventIds) and filter-based delete
        return request({
            url: '/api/deleteMaliciousEvents',
            method: 'post',
            data: data
        })
    },
    fetchThreatTopNData(startTs, endTs, latestAttack, limit = 5) {
        return request({
            url: '/api/fetchThreatTopNData',
            method: 'post',
            data: {startTs, endTs, latestAttack: latestAttack || [], limit}
        })
    }
}
export default threatDetectionRequests
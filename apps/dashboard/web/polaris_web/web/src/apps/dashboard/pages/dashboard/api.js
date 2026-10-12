import request from "@/util/request";
const api = {
    getIssuesTrend: async (startTimeStamp, endTimeStamp) => {
        return await request({
            url: '/api/getIssuesTrend',
            method: 'post',
            data: { startTimeStamp, endTimeStamp }
        })
    },

    // the api numbers of the range: charts, action item counts, missing data (see PostureSnapshotService)
    fetchPostureSnapshot: async (startTimestamp, endTimestamp, force) => {
        return await request({
            url: '/api/fetchPostureSnapshot',
            method: 'post',
            data: { startTimestamp, endTimestamp, force: !!force }
        })
    },

    // the issue and test numbers of the range (see IssuesSnapshotService)
    fetchIssuesSnapshot: async (startTimeStamp, endTimeStamp) => {
        return await request({
            url: '/api/fetchIssuesSnapshot',
            method: 'post',
            data: { startTimeStamp, endTimeStamp }
        })
    },

    fetchMcpPostureCounts: async () => {
        return await request({
            url: '/api/fetchMcpPostureCounts',
            method: 'post',
            data: {}
        })
    },

    // one page of an MCP list widget: section is RECENT_ALERTS or TOP_APPLICATIONS
    fetchMcpPostureData: async (section, skip, limit) => {
        return await request({
            url: '/api/fetchMcpPostureData',
            method: 'post',
            data: { section, skip, limit }
        })
    },

    // api calls of the MCP or GUARDRAIL collections, summed per time bucket
    fetchTaggedApiCallSeries: async (tag, startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchTaggedApiCallSeries',
            method: 'post',
            data: { tag, startTimestamp, endTimestamp }
        })
    },

    // one page of the list behind an action item (see PostureActionItemsService.Type)
    fetchPostureActionItems: async (itemType, skip, limit) => {
        return await request({
            url: '/api/fetchPostureActionItems',
            method: 'post',
            data: { itemType, skip, limit }
        })
    },

    fetchEndpointsCount: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchEndpointsCount',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
    },

    fetchCriticalIssuesTrend: async (startTimeStamp, endTimeStamp, severityToFetch = [], apiCollectionIds) => {
        const data = { startTimeStamp, endTimeStamp, severityToFetch }
        if (apiCollectionIds && apiCollectionIds.length > 0) {
            data.apiCollectionIds = apiCollectionIds
        }
        return await request({
            url: '/api/fetchCriticalIssuesTrend',
            method: 'post',
            data
        })
    },

    fetchSubTypeCountMap(startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchSubTypeCountMap',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    fetchRecentFeed(skip) {
        return request({
            url: '/api/getRecentActivities',
            method: 'post',
            data: {
                skip
            }
        })
    },

    getIntegratedConnections: async () => {
        return await request({
            url: '/api/getIntegratedConnectionsInfo',
            method: 'post',
            data: {}
        })
    },

    skipConnection: async (skippedConnection) => {
        return request({
            url: '/api/markConnectionAsSkipped',
            method: 'post',
            data: {
                connectionSkipped: skippedConnection
            }
        })
    },

    saveDashboardLayout: async (screenName, layout) => {
        return await request({
            url: '/api/saveDashboardLayout',
            method: 'post',
            data: {
                screenName,
                layout
            }
        })
    },

    fetchDashboardLayout: async (screenName) => {
        return await request({
            url: '/api/fetchDashboardLayout',
            method: 'post',
            data: {
                screenName
            }
        })
    },


    // Consolidated APIs
    fetchEndpointDiscoveryData: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchEndpointDiscoveryData',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    fetchIssuesData: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchIssuesData',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    fetchTestingData: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchTestingData',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    fetchThreatData: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchThreatData',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    fetchGuardrailData: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchGuardrailData',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    fetchArgusPostureSummary: async (startTimestamp, endTimestamp, environment) => {
        return await request({
            url: '/api/fetchArgusPostureSummary',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp,
                environment
            }
        })
    },

    fetchArgusPostureChanges: async (startTimestamp, endTimestamp, environment) => {
        return await request({
            url: '/api/fetchArgusPostureChanges',
            method: 'post',
            data: { startTimestamp, endTimestamp, environment }
        })
    },

    // The 5 Argus posture insight cards — fast, Java-only data, no LLM call.
    fetchArgusPostureInsights: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchArgusPostureInsights',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    // Meant to be called after fetchArgusPostureInsights has already rendered — this one does a
    // real LLM call per card on a cache miss, so it must never block the card data itself.
    fetchArgusPostureInsightSummaries: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchArgusPostureInsightSummaries',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    triggerArgusPostureRegenerate: async () => {
        return await request({ url: '/api/triggerArgusPostureRegenerate', method: 'post', data: {} })
    },

    fetchArgusPostureRegenerateStatus: async () => {
        return await request({ url: '/api/fetchArgusPostureRegenerateStatus', method: 'post', data: {} })
    },

    fetchArgusPostureDrill: async (drillId, path, startTimestamp, endTimestamp, environment, skip, limit) => {
        return await request({
            url: '/api/fetchArgusPostureDrill',
            method: 'post',
            data: {
                drillId,
                path: path || '',
                startTimestamp,
                endTimestamp,
                environment,
                skip: skip || 0,
                limit: limit || 20
            }
        })
    },

    fetchPostureSummary: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchPostureSummary',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },

    // One page of one panel's drilldown flyout — see PostureService#fetchDrill's own javadoc.
    // The risk score breakdown (formerly its own fetchRiskScoreBreakdown call) is now just
    // drillId=PostureService.DRILL_RISK_SCORE on this same endpoint.
    // `path` is empty/undefined for the root (group) level, or one prior level's row id to drill
    // into its members.
    fetchPostureDrill: async (drillId, path, startTimestamp, endTimestamp, skip, limit) => {
        return await request({
            url: '/api/fetchPostureDrill',
            method: 'post',
            data: {
                drillId,
                path: path || '',
                startTimestamp,
                endTimestamp,
                skip: skip || 0,
                limit: limit || 20
            }
        })
    },
}

export default api;
import request from "@/util/request";
const api = {
    getIssuesTrend: async (startTimeStamp, endTimeStamp) => {
        return await request({
            url: '/api/getIssuesTrend',
            method: 'post',
            data: { startTimeStamp, endTimeStamp }
        })
    },

    findTotalIssues: async (startTimeStamp, endTimeStamp) => {
        return await request({
            url: '/api/findTotalIssues',
            method: 'post',
            data: { startTimeStamp, endTimeStamp }
        })
    },

    fetchEndpointsCount: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchEndpointsCount',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
    },

    fetchApiStats: async (startTimestamp, endTimestamp) => {
        return await request({
            url: '/api/fetchApiStats',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
    },

    fetchCriticalIssuesTrend: async (startTimeStamp, endTimeStamp, severityToFetch = []) => {
        return await request({
            url: '/api/fetchCriticalIssuesTrend',
            method: 'post',
            data: { startTimeStamp, endTimeStamp, severityToFetch }
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

    fetchMcpdata: async (filterType) => {
        return await request({
            url: '/api/fetchMcpdata',
            method: 'post',
            data: { filterType }
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

    getApiInfoForMissingData: async (startTimeStamp, endTimeStamp) => {
        return request({
            url: '/api/getAPIInfosForMissingData',
            method: 'post',
            data: {
                startTimeStamp,
                endTimeStamp
            }
        })
    },

    fetchSensitiveAndUnauthenticatedValue: async (showApiInfo) => {
        return await request({
            url: '/api/getSensitiveAndUnauthenticatedValue',
            method: 'post',
            data: { showApiInfo}
        })
    },

    fetchHighRiskThirdPartyValue: async (showApiInfo) => {
        return await request({
            url: '/api/getHighRiskThirdPartyValue',
            method: 'post',
            data: { showApiInfo}
        })
    },

    fetchShadowApisValue: async (showApiInfo) => {
        return await request({
            url: '/api/getShadowApis',
            method: 'post',
            data: { showApiInfo}
        })
    },
    fetchUnauthenticatedApis: async (showApiInfo) => {
        return await request({
            url: '/api/fetchAllUnauthenticatedApis',
            method: 'post',
            data: { showApiInfo }
        })
    },

    fetchActionItemsApiInfo: async (filterType) => {
        return await request({
            url: '/api/fetchActionItemsApiInfo',
            method: 'post',
            data: { filterType }
        })
    },

    getNotTestedAPICount: async (showApiInfo) => {
        return await request({
            url: '/api/getNotTestedAPICount',
            method: 'post',
            data: { showApiInfo }
        })
    },

    getOnlyOnceTestedAPICount: async (showApiInfo) => {
        return await request({
            url: '/api/getOnlyOnceTestedAPICount',
            method: 'post',
            data: { showApiInfo }
        })
    },

    getMisConfiguredTestsCount: async (showApiInfo) => {
        return await request({
            url: '/api/getMisConfiguredTestsCount',
            method: 'post',
            data: { showApiInfo }
        })
    },

    getVulnerableApiCount: async (showApiInfo) => {
        return await request({
            url: '/api/getBUACategoryCount',
            method: 'post',
            data: {
                filterSeverity: ["CRITICAL"],
                filterStatus: ["OPEN"],
                showApiInfo
            }
        })
    },

    fetchBrokenAuthenticationIssues: async (filterSubCategory, showApiInfo) => {
        return await request({
            url: '/api/getBUACategoryCount',
            method: 'post',
            data: {
                filterStatus: ["OPEN"],
                filterSubCategory,
                showApiInfo
            }
        })
    },

    fetchIssuesByApis: async (params) => {
        let data = {};
        if (typeof params === 'boolean') {
            data = { showIssues: params };
        } else if (typeof params === 'object' && params !== null) {
            data = {
                showIssues: params.showIssues || false,
                categoryTypes: Array.isArray(params.categoryTypes)
                    ? params.categoryTypes
                    : (params.categoryType ? [params.categoryType] : null)
            };
        }
        
        return await request({
            url: '/api/fetchIssuesByApis',
            method: 'post',
            data
        })
    },

    fetchUrlsByIssues: async (showTestSubCategories) => {
        const data = (typeof showTestSubCategories === 'boolean') ? { showTestSubCategories } : {};
        return await request({
            url: '/api/fetchUrlsByIssues',
            method: 'post',
            data
        })
    }
}

export default api;
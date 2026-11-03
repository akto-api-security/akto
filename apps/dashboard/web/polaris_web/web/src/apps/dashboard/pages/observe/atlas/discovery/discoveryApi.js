import request from "@/util/request";

export default {
    async fetchAtlasDiscoveryOverview(startTimestamp, endTimestamp, deviceScope) {
        return await request({
            url: '/api/fetchAtlasDiscoveryOverview',
            method: 'post',
            data: { startTimestamp, endTimestamp, deviceScope }
        })
    },
    // One page of the "needs a decision" list: { startTimestamp, endTimestamp, deviceScope, skip, limit }.
    async fetchAtlasDecisions(params) {
        return await request({
            url: '/api/fetchAtlasDecisions',
            method: 'post',
            data: params
        })
    },
    // Paged, filterable tool list.
    async fetchAtlasTools(params) {
        return await request({
            url: '/api/fetchAtlasTools',
            method: 'post',
            data: params
        })
    },
    // One tool for the flyout: its row, top employees, connections and violation counts.
    async fetchAtlasToolDetail(assetKey, startTimestamp, endTimestamp, deviceScope) {
        return await request({
            url: '/api/fetchAtlasToolDetail',
            method: 'post',
            data: { assetKey, startTimestamp, endTimestamp, deviceScope }
        })
    },
    async updateAtlasAssetStatus(assetKeys, status) {
        return await request({
            url: '/api/updateAtlasAssetStatus',
            method: 'post',
            data: { assetKeys, status }
        })
    },
}

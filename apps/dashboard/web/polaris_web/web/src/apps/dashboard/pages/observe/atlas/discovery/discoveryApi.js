import request from "@/util/request";

export default {
    async fetchAtlasDiscoveryOverview(deviceScope) {
        return await request({
            url: '/api/fetchAtlasDiscoveryOverview',
            method: 'post',
            data: { deviceScope }
        })
    },
    // One page of the "needs a decision" list: { deviceScope, skip, limit }.
    async fetchAtlasDecisions(params) {
        return await request({
            url: '/api/fetchAtlasDecisions',
            method: 'post',
            data: params
        })
    },
    // Paged, filterable tool list: { deviceScope, skip, limit }.
    async fetchAtlasTools(params) {
        return await request({
            url: '/api/fetchAtlasTools',
            method: 'post',
            data: params
        })
    },
    // One tool for the flyout: its row, top employees, connections and violation counts.
    async fetchAtlasToolDetail(assetKey, deviceScope) {
        return await request({
            url: '/api/fetchAtlasToolDetail',
            method: 'post',
            data: { assetKey, deviceScope }
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

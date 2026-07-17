import request from "../../../../util/request"

export default {
    async fetchGuardrailPolicies({ skip = 0, limit = 50 } = {}) {
        const resp = await request({
            url: '/api/fetchGuardrailPolicies',
            method: 'post',
            data: { skip, limit }
        })
        return resp
    },

    async fetchAllGuardrailPolicies() {
        const PAGE_SIZE = 50;
        const MAX_PAGES = 40; // cap: 2000 policies
        let skip = 0;
        let policies = [];
        for (let page = 0; page < MAX_PAGES; page++) {
            const resp = await this.fetchGuardrailPolicies({ skip, limit: PAGE_SIZE });
            const batch = resp?.guardrailPolicies || [];
            policies = policies.concat(batch);
            if (batch.length < PAGE_SIZE) break;
            skip += PAGE_SIZE;
        }
        return policies;
    },

    async createGuardrailPolicy(policyData) {
        const resp = await request({
            url: '/api/createGuardrailPolicy',
            method: 'post',
            data: policyData
        })
        return resp
    },

    async deleteGuardrailPolicies(policyIds) {
        const resp = await request({
            url: '/api/deleteGuardrailPolicies',
            method: 'post',
            data: { policyIds }
        })
        return resp
    },

    async guardrailPlayground(testInput, policyData) {
        const resp = await request({
            url: '/api/guardrailPlayground',
            method: 'post',
            data: {
                testInput,
                policy: policyData
            }
        })
        return resp
    },

    async fetchBrowserExtensionConfigs() {
        const resp = await request({
            url: '/api/fetchBrowserExtensionConfigs',
            method: 'post'
        })
        return resp
    },

    async saveBrowserExtensionConfig(browserExtensionConfig, hexId) {
        const resp = await request({
            url: '/api/saveBrowserExtensionConfig',
            method: 'post',
            data: { browserExtensionConfig, hexId }
        })
        return resp
    },

    async deleteBrowserExtensionConfigs(configIds) {
        const resp = await request({
            url: '/api/deleteBrowserExtensionConfigs',
            method: 'post',
            data: { configIds }
        })
        return resp
    },

}
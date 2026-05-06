import request from "../../../../util/request"

export default {
    async fetchGuardrailPolicies() {
        const resp = await request({
            url: '/api/fetchGuardrailPolicies',
            method: 'post'
        })
        return resp
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
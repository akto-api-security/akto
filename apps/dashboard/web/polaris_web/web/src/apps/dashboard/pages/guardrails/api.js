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

    // Approve a server to bypass a policy's "approval" behaviour.
    // Identify the policy by hexId (preferred) or policyName (the threat event's filterId).
    // mode: 'ALWAYS' | 'DURATION' | 'COUNT'; value: days (DURATION) or times (COUNT), ignored for ALWAYS.
    async approveServerForPolicy({ hexId, policyName, approvedServerId, approvedServerName, approvalMode, approvalValue = 0 }) {
        const resp = await request({
            url: '/api/approveServerForPolicy',
            method: 'post',
            data: { hexId, policyName, approvedServerId, approvedServerName, approvalMode, approvalValue }
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

    // Kick off one background comparison of this policy's recent violations: the saved policy
    // versus the edited draft, over the same events. Returns a runId to poll.
    // policyName must be the name the violations were recorded under (the pre-edit name), since a
    // guardrail event's filterId *is* the policy name.
    // source: 'VIOLATIONS' (this policy's recorded violations) or 'TRACES' (recent agent traffic,
    // blocked or not). Traces cover traffic the policy never matched, so an edit's false positives
    // show up there; violations can only show detections an edit loses.
    async startPolicyReplay({ policy, policyName, hexId, source = 'VIOLATIONS' }) {
        const resp = await request({
            url: '/api/startPolicyReplay',
            method: 'post',
            data: { policy, policyName, hexId, source }
        })
        return resp
    },

    async pollPolicyReplay(runId) {
        const resp = await request({
            url: '/api/pollPolicyReplay',
            method: 'post',
            data: { runId }
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

    async fetchBrowserExtensionConfigsCommon() {
        const resp = await request({
            url: '/api/fetchBrowserExtensionConfigsCommon',
            method: 'post'
        })
        return resp
    },

    async setBrowserExtensionConfigActive(host, active) {
        const resp = await request({
            url: '/api/setBrowserExtensionConfigActive',
            method: 'post',
            data: { browserExtensionConfig: { host, active } }
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

    async fetchConfigFieldPolicies({ skip = 0, limit = 50 } = {}) {
        const resp = await request({
            url: '/api/fetchConfigFieldPolicies',
            method: 'post',
            data: { skip, limit }
        })
        return resp
    },

    async createConfigFieldPolicy(policyData, hexId) {
        const resp = await request({
            url: '/api/createConfigFieldPolicy',
            method: 'post',
            data: { policy: policyData, hexId }
        })
        return resp
    },

    async deleteConfigFieldPolicies(policyIds) {
        const resp = await request({
            url: '/api/deleteConfigFieldPolicies',
            method: 'post',
            data: { policyIds }
        })
        return resp
    },

}
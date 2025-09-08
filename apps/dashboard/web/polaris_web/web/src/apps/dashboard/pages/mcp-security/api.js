import request from '@/util/request'

const mcpGuardrailApi = {
    /**
     * Get current guardrail policy
     */
    getPolicy() {
        return request({
            url: '/api/mcp/guardrail-policy/get',
            method: 'GET'
        })
    },

    /**
     * Toggle a specific guardrail on/off
     */
    toggleGuardrail(guardrailType, enabled) {
        return request({
            url: '/api/mcp/guardrail-policy/toggle',
            method: 'POST',
            data: {
                guardrailType: guardrailType,
                enabled: enabled
            }
        })
    },

    /**
     * Update guardrail action
     */
    updateGuardrailAction(guardrailType, action) {
        return request({
            url: '/api/mcp/guardrail-policy/update-action',
            method: 'POST',
            data: {
                guardrailType: guardrailType,
                action: action
            }
        })
    },

    /**
     * Toggle entire policy on/off
     */
    togglePolicy() {
        return request({
            url: '/api/mcp/guardrail-policy/toggle-policy',
            method: 'POST',
            data: {}
        })
    },

    /**
     * Get available guardrail types
     */
    getGuardrailTypes() {
        return request({
            url: '/api/mcp/guardrail-policy/types',
            method: 'GET'
        })
    },

    /**
     * Get available actions
     */
    getActions() {
        return request({
            url: '/api/mcp/guardrail-policy/actions',
            method: 'GET'
        })
    }
}

export default mcpGuardrailApi

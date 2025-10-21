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

}
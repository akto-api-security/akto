import request from "../../../../util/request"

export default {
    async fetchGuardrailPolicies(skip = 0, limit = 20) {
        const resp = await request({
            url: '/api/fetchGuardrailPolicies',
            method: 'post',
            data: { skip, limit }
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

    async updateGuardrailPolicy(hexId, policyData) {
        const resp = await request({
            url: '/api/updateGuardrailPolicy',
            method: 'post',
            data: { hexId, ...policyData }
        })
        return resp
    },
}
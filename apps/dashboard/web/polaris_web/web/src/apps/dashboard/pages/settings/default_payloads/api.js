import request from "@/util/request"

const defaultPayloadsApi = {
    fetchDefaultPayload(domain) {
        return request({
            url: '/api/fetchDefaultPayload',
            method: 'post',
            data: {domain}
        })
    },

    fetchAllDefaultPayloads() {
        return request({
            url: '/api/fetchAllDefaultPayloads',
            method: 'post',
            data: {}
        })
    },

    saveDefaultPayload(domain, pattern) {
        return request({
            url: '/api/saveDefaultPayload',
            method: 'post',
            data: {domain, pattern}
        })
    }
}

export default defaultPayloadsApi
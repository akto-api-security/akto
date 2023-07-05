import request from "@/util/request"

const settingRequests = {
    fetchApiTokens: async function() {
        const resp = await request({
            url: '/api/fetchApiTokens',
            method: 'post',
            data: {}
        })
        return resp
    },
    addApiToken(tokenUtility) {
        return request({
            url: '/api/addApiToken',
            method: 'post',
            data: {tokenUtility}
        }).then((resp) => {
            return resp
        })
    },
    deleteApiToken(apiTokenId) {
        return request({
            url: '/api/deleteApiToken',
            method: 'post',
            data: {apiTokenId}
        }).then((resp) => {
            return resp
        })
    },
}

export default settingRequests
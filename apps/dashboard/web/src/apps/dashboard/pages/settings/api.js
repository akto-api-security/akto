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
}

export default settingRequests
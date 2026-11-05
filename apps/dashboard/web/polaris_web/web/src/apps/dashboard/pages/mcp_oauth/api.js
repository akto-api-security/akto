import request from "@/util/request"

const mcpOAuthRequests = {
    fetchAuthorizeRequest(requestId) {
        return request({
            url: '/api/mcpOAuth/fetchAuthorizeRequest',
            method: 'post',
            data: { requestId }
        })
    },
    approveAuthorizeRequest(requestId, contextSource) {
        return request({
            url: '/api/mcpOAuth/approveAuthorizeRequest',
            method: 'post',
            data: { requestId, contextSource }
        })
    },
    denyAuthorizeRequest(requestId) {
        return request({
            url: '/api/mcpOAuth/denyAuthorizeRequest',
            method: 'post',
            data: { requestId }
        })
    }
}

export default mcpOAuthRequests

import request from "@/util/request"

const billingApi = {
    syncUsage() {
        return request({
            url: '/api/syncUsage',
            method: 'post',
            data: {}
        })
    }
}

export default billingApi
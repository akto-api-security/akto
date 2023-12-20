import request from "@/util/request"

const billingApi = {
    syncUsage() {
        return request({
            url: '/api/syncUsage',
            method: 'post',
            data: {}
        })
    },

    provisionSubscription({billingPeriod, customerId, planId, successUrl, cancelUrl}) {
        return request({
            url: '/api/provisionSubscription',
            method: 'post',
            data: {billingPeriod, customerId, planId, successUrl, cancelUrl}
        })
    },

    getCustomerStiggDetails({customerId}) {
        return request({
            url: '/api/getCustomerStiggDetails',
            method: 'post',
            data: {customerId}
        })
    }
}

export default billingApi
import request from '@/util/request'

export default {
    addCustomWebhook(webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections) {
        return request({
            url: '/api/addCustomWebhook',
            method: 'post',
            data: {
                webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections
            }
        })
    },
    updateCustomWebhook(id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections) {
        return request({
            url: '/api/updateCustomWebhook',
            method: 'post',
            data: {
                id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections
            }
        })
    },
    fetchCustomWebhooks() {
        return request({
            url: '/api/fetchCustomWebhooks',
            method: 'post',
            data: {}
        })
    },
    changeStatus(id, activeStatus) {
        return request({
            url: '/api/changeStatus',
            method: 'post',
            data: {id, activeStatus}
        })
    },
    runOnce(id) {
        return request({
            url: '/api/runOnce',
            method: 'post',
            data: {id}
        })
    },
    fetchLatestWebhookResult(id) {
        return request({
            url: '/api/fetchLatestWebhookResult',
            method: 'post',
            data: {id}
        })
    },
}
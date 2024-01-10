import request from '@/util/request'

export default {
    deactivateCollections(items) {
        return request({
            url: '/api/deactivateCollections',
            method: 'post',
            data: { apiCollections: items }
        })
    },
    activateCollections(items) {
        return request({
            url: '/api/activateCollections',
            method: 'post',
            data: { apiCollections: items }
        })
    },
    forceActivateCollections(items) {
        return request({
            url: '/api/forceActivateCollections',
            method: 'post',
            data: { apiCollections: items }
        })
    }
}
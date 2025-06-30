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
    fetchCountForHostnameDeactivatedCollections(){
        return request({
            url: '/api/getCountForHostnameDeactivatedCollections',
            method: 'post',
            data: {}
        })
    },
    getCollection(apiCollectionId){
        return  request({
            url: '/api/getCollection',
            method: 'post',
            data: {apiCollectionId}
        })
    },
    markCollectionsOutOfTestScope(items){
        return request({
            url: '/api/markCollectionsOutOfTestScope',
            method: 'post',
            data: { apiCollectionsForOutOfTestScope: items }
        })
    },
    unmarkCollectionsOutOfTestScope(items){
        return request({
            url: 'api/unmarkCollectionsOutOfTestScope',
            method: 'post',
            data: { apiCollectionsForOutOfTestScope: items }
        })
    }
}
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
    fetchCountForUningestedApis(){
        return request({
            url: '/api/getCountForUningestedApis',
            method: 'post',
            data: {}
        })
    },
    fetchUningestedApis(){
        return request({
            url: '/api/fetchUningestedApis',
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
    toggleCollectionsOutOfTestScope(apiCollectionIds, currentIsOutOfTestingScopeVal){
        return request({
            url: '/api/toggleCollectionsOutOfTestScope',
            method: 'post',
            data: { apiCollectionIds, currentIsOutOfTestingScopeVal }
        })
    },
    fetchAllDastScans(){
        return request({
            url: '/api/fetchAllDastScans',
            method: 'post',
            data: {}
        })
    },
    stopCrawler(crawlId) {
        return request({
            url: '/api/stopCrawler',
            method: 'post',
            data: { crawlId }
        })
    },
    fetchDastScan(crawlId){
        return request({
            url: '/api/fetchDastScan',
            method: 'post',
            data: { crawlId }
        })
    },
    getLatestCrawlerFrame(crawlId) {
        return request({
            url: '/api/getLatestCrawlerFrame',
            method: 'post',
            data: { crawlId }
        }).then(resp => {
            // Parse the JSON string response
            return typeof resp === 'string' ? JSON.parse(resp) : resp
        })
    },
    findMissingUrls(missingUrls){
        return request({
            url: '/api/findMissingUrls',
            method: 'post',
            data: { missingUrls }
        })
    },
}
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
    // one page of the collections table, sorted/filtered on the server. sortOrder: 1 asc, -1 desc
    fetchApiCollectionsPage({ skip, limit, sortKey, sortOrder, tab, queryValue, filters, tagFilters, force }){
        return request({
            url: '/api/fetchApiCollectionsPage',
            method: 'post',
            data: { skip, limit, sortKey, sortOrder, tab, queryValue, filters, tagFilters, force: !!force }
        })
    },
    // how many collections each tab holds under the table's search and filters
    fetchApiCollectionsTabCounts({ queryValue, filters, tagFilters }){
        return request({
            url: '/api/fetchApiCollectionsTabCounts',
            method: 'post',
            data: { queryValue, filters, tagFilters }
        })
    },
    // coverage of the collections of a page, fetched after the rows are shown
    fetchApiCollectionsPageDetails(apiCollectionIds){
        return request({
            url: '/api/fetchApiCollectionsPageDetails',
            method: 'post',
            data: { apiCollectionIds }
        })
    },
    // tab counts, summary card numbers and tag filter choices
    fetchApiCollectionsPageMeta(){
        return request({
            url: '/api/fetchApiCollectionsPageMeta',
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
    updateSkillBlockStatus(apiCollectionIds, skillName, isSkillBlocked, mcpHosts) {
        return request({
            url: '/api/updateSkillBlockStatus',
            method: 'post',
            data: { apiCollectionIds, skillName, isSkillBlocked, mcpHosts: mcpHosts || [] }
        })
    },
    fetchBlockedSkillCollections() {
        return request({
            url: '/api/fetchBlockedSkillCollections',
            method: 'post',
            data: {}
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
    getCrawlerGraph(crawlId) {
        return request({
            url: '/api/getCrawlerGraph',
            method: 'post',
            data: { crawlId }
        }).then(resp => {
            const obj = typeof resp === 'string' ? JSON.parse(resp) : resp
            return (obj && obj.navigationGraph) ? obj.navigationGraph : ''
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
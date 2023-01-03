import request from '@/util/request'

export default {
    fetchAllMarketplaceSubcategories() {
        return request({
            url: 'api/fetchAllMarketplaceSubcategories',
            method: 'post',
            data: {}
        })
    },
    fetchTestingSources(defaultCreator, subcategory) {
        return request({
            url: 'api/fetchTestingSources',
            method: 'post',
            data: {
                defaultCreator, 
                subcategory
            }
        })
    }
}
import request from '@/util/request'

export default {
    fetchAllSubcategories() {
        return request({
            url: 'api/fetchAllSubcategories',
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
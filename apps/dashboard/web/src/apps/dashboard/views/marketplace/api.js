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
    },
    addCustomTest({url, category, subcategory, severity, description}) {
        return request({
            url: "api/addCustomTest",
            method: "post",
            data: {
                url, category, subcategory, severity, description
            }
        })
    },
    searchTestResults(searchText){
        return request({
            url: 'api/fetchSearchTestResults',
            method: 'post',
            data:{
                searchText
            }
        })
    }
}
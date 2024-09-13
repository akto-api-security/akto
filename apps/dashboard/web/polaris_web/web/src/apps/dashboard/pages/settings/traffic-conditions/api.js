import request from "@/util/request"

const trafficFiltersRequest = {
    getAdvancedFiltersForTraffic() {
        return request({
            url: '/api/fetchAdvancedFiltersForTraffic',
            method: 'post',
            data: {}
        })
    },
    updateAdvancedFiltersForTraffic(yamlContent) {
        return request({
            url: 'api/addAdvancedFiltersForTraffic',
            method: 'post',
            data: {yamlContent}
        })
    },
    deleteFilter(id) {
        return request({
            url: 'api/deleteAdvancedFilter',
            method: 'post',
            data: {templateId: id}
        })
    },
    changeStateOfFilter(id, inactive) {
        return request({
            url: 'api/changeStateOfFilter',
            method: 'post',
            data: {templateId: id, inactive: inactive}
        })
    }
}

export default trafficFiltersRequest
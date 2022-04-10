import request from '@/util/request'

export default {
    bulkMarkSensitive(sensitive, items) {
        return request({
            url: '/api/bulkMarkSensitive',
            method: 'post',
            data: {
                items,
                sensitive
            }
        })
    },
    fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators) {
        return request({
            url: '/api/fetchChanges',
            method: 'post',
            data: {
                sortKey, 
                sortOrder,
                limit,
                skip,
                filters: Object.entries(filters).reduce((z, e) => {
                    z[e[0]] = [...e[1]]
                    return z
                }, {}), 
                filterOperators
            }
        }).then(resp => {
            return resp.response.data
        })
    },
    fetchNewParametersTrend() {
        return request({
            url: '/api/fetchNewParametersTrend',
            method: 'post',
            data: {}
        }).then(resp => {
            return resp.data.endpoints
        })
    }
}
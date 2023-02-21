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
    fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, sensitive, isRequest) {
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
                filterOperators,
                startTimestamp, 
                endTimestamp,
                sensitive: sensitive,
                request: isRequest
            }
        }).then(resp => {
            return resp.response.data
        })
    },
    fetchNewParametersTrend(startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchNewParametersTrend',
            method: 'post',
            data: {startTimestamp, endTimestamp}
        }).then(resp => {
            return resp.data.endpoints
        })
    },
    fetchSubTypeCountMap(startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchSubTypeCountMap',
            method: 'post',
            data: {
                startTimestamp, 
                endTimestamp
            }
        })
    },
}
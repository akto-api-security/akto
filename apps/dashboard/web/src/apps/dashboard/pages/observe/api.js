import request from "../../../../util/request"


export default {
    async fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, sensitive, isRequest) {
        const resp = await request({
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
        })
        return resp.response.data
    },
    async fetchDataTypeNames() {
        const resp = await request({
            url: '/api/fetchDataTypeNames',
            method: 'post',
            data: {}
        })
        return resp
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
    async fetchSampleData (url, apiCollectionId, method) {
        const resp = await request({
            url: '/api/fetchSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        })
        return resp
    },
    async fetchSensitiveSampleData(url, apiCollectionId, method) {
        const resp = await request({
            url: '/api/fetchSensitiveSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        })
        return resp
    },
    fetchDataTypes() {
        return request({
            url: '/api/fetchDataTypes',
            method: 'post',
            data: { }
        })
    },
    async loadSensitiveParameters (apiCollectionId, url, method, subType) {
        const resp = await request({
            url: '/api/loadSensitiveParameters',
            method: 'post',
            data: {
                apiCollectionId,
                url,
                method,
                subType
            }
        })
        return resp
    },

    saveCustomDataType(dataObj) {
        return request({
            url: '/api/saveCustomDataType',
            method: 'post',
            data: dataObj
        })
    },

    saveAktoDataType(dataObj) {
        return request({
            url:'/api/saveAktoDataType',
            method:'post',
            data: dataObj
        })
    },
}
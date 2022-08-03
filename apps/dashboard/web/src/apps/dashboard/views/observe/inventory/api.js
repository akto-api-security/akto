import request from '@/util/request'

export default {
    saveContent(apiSpec) {
        return request({
            url: '/api/saveContent',
            method: 'post',
            data: {
                apiSpec: apiSpec.swaggerContent,
                filename: apiSpec.filename,
                apiCollectionId: apiSpec.apiCollectionId
            }
        })
    },
    loadContent(apiCollectionId) {
        return request({
            url: '/api/loadContent',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
    },
    uploadHarFile(content, apiCollectionId, skipKafka) {
        return request({
            url: '/api/uploadHar',
            method: 'post',
            data: {
                content, apiCollectionId, skipKafka
            }
        })
    },
    uploadTcpFile(content, apiCollectionId, skipKafka) {
        return request({
            url: '/api/uploadTcp',
            method: 'post',
            data: {
                tcpContent: content, apiCollectionId, skipKafka
            }
        })
    },
    downloadOpenApiFile(apiCollectionId) {
        return request({
            url: '/api/generateOpenApiFile',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    exportToPostman(apiCollectionId) {
        return request({
            url: '/api/createPostmanApi',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    fetchAPICollection (apiCollectionId) {
        return request({
            url: '/api/fetchAPICollection',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
                useHost: !!window.useHost
            }
        }).then((resp) => {
            return resp
        })
    },

    fetchAllUrlsAndMethods (apiCollectionId) {
        return request({
            url: '/api/fetchAllUrlsAndMethods',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        }).then((resp) => {
            return resp
        })
    },

    addSensitiveField (x) {
        return request({
            url: 'api/addSensitiveField',
            method: 'post',
            data: {
                ...x
            }
        })
    },
    listAllSensitiveFields () {
        return request({
            url: 'api/listAllSensitiveFields',
            method: 'post',
            data: {}
        })
    },
    loadRecentEndpoints (startTimestamp, endTimestamp) {
        return request({
            url: '/api/loadRecentEndpoints',
            method: 'post',
            data: {startTimestamp, endTimestamp}
        }).then((resp) => {
            return resp
        })
    },
    loadSensitiveParameters (apiCollectionId, url, method) {
        return request({
            url: '/api/loadSensitiveParameters',
            method: 'post',
            data: {
                apiCollectionId,
                url, 
                method
            }
        }).then((resp) => {
            return resp
        })
    },
    loadParamsOfEndpoint (apiCollectionId, url, method) {
        return request({
            url: '/api/loadParamsOfEndpoint',
            method: 'post',
            data: {
                apiCollectionId,
                url,
                method
            }
        }).then((resp) => {
            return resp
        })
    },
    fetchEndpointTrafficData (url, apiCollectionId, method, startEpoch, endEpoch) {
        return request({
            url: '/api/fetchEndpointTrafficData',
            method: 'post',
            data: {
                url, apiCollectionId, method, startEpoch, endEpoch
            }
        }).then((resp) => {
            return resp
        })
    },
    fetchSampleData (url, apiCollectionId, method) {
        return request({
            url: '/api/fetchSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        }).then((resp) => {
            return resp
        })
    },

    fetchSensitiveSampleData(url, apiCollectionId, method) {
        return request({
            url: '/api/fetchSensitiveSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        }).then((resp) => {
            return resp
        })
    },

    fetchApiInfoList(apiCollectionId) {
        return request({
            url: '/api/fetchApiInfoList',
            method: 'post',
            data: {
                apiCollectionId
            }
        }).then((resp) => {
            return resp
        })
    },
    fetchFilters() {
        return request({
            url: '/api/fetchFilters',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    convertSampleDataToCurl(sampleData) {
        return request({
            url: '/api/convertSampleDataToCurl',
            method: 'post',
            data: {sampleData}
        }).then((resp) => {
            return resp
        })
    },

    fetchDataTypeNames() {
        return request({
            url: '/api/fetchDataTypeNames',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    }
}
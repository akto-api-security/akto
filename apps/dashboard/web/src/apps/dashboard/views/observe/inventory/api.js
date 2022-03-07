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
    getAPICollection (apiCollectionId) {
        return request({
            url: '/api/getAPICollection',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        }).then((resp) => {
            return resp
        })
    },

    getAllUrlsAndMethods (apiCollectionId) {
        return request({
            url: '/api/getAllUrlsAndMethods',
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
    loadRecentParameters () {
        return request({
            url: '/api/loadRecentParameters',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    loadSensitiveParameters () {
        return request({
            url: '/api/loadSensitiveParameters',
            method: 'post',
            data: {}
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
    fetchApiInfoListForRecentEndpoints() {
        return request({
            url: '/api/fetchApiInfoListForRecentEndpoints',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    }
}
import request from '@/util/request'

export default {
    uploadHarFile(content, apiCollectionId) {
        return request({
            url: '/api/uploadHar',
            method: 'post',
            data: {
                content, apiCollectionId
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
    }
}
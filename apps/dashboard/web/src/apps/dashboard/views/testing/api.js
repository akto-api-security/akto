import request from '@/util/request'

export default {
    fetchTestingDetails() {
        return request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },

    startTestForCollection(apiCollectionId) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiCollectionId, type: "COLLECTION_WISE"}
        }).then((resp) => {
            return resp
        })        
    },

    stopTestForCollection(apiCollectionId) {
        return request({
            url: '/api/stopTest',
            method: 'post',
            data: {apiCollectionId, type: "COLLECTION_WISE"}
        }).then((resp) => {
            return resp
        })        
    },

    addAuthMechanism(key, value, location) {
        return request({
            url: '/api/addAuthMechanism',
            method: 'post',
            data: {key, value, location}
        }).then((resp) => {
            return resp
        })        
    },

    fetchTestingRunResults() {
        return request({
            url: '/api/fetchTestingRunResults',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })        
    },

    fetchRequestAndResponseForTest(x) {
        return request({
            url: '/api/fetchRequestAndResponseForTest',
            method: 'post',
            data: {testingRunResults:[x]}
        }).then((resp) => {
            return resp
        })        
    }
}
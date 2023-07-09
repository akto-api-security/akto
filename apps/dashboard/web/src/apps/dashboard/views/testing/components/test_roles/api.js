import request from '@/util/request'

export default {
    fetchTestRoles() {
        return request({
            url: '/api/fetchTestRoles',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    addTestRoles (roleName, andConditions, orConditions, authParamData) {
        return request({
            url: '/api/addTestRoles',
            method: 'post',
            data: {roleName, andConditions, orConditions, authParamData}
        }).then((resp) => {
            return resp
        })        
    },
    updateTestRoles (roleName, andConditions, orConditions, authParamData) {
        return request({
            url: '/api/updateTestRoles',
            method: 'post',
            data: {roleName, andConditions, orConditions, authParamData}
        }).then((resp) => {
            return resp
        })        
    },
    fetchAccessMatrixUrlToRoles(){
        return request({
            url: '/api/fetchAccessMatrixUrlToRoles',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    createMultipleAccessMatrixTasks(apiCollectionIds){
        return request({
            url: '/api/createMultipleAccessMatrixTasks',
            method: 'post',
            data: {apiCollectionIds}
        }).then((resp) => {
            return resp
        })
    },
    analyzeApiSamples(apiCollectionIds, headerNames){
        return request({
            url: '/api/analyzeApiSamples',
            method: 'post',
            data: {apiCollectionIds, headerNames}
        }).then((resp) => {
            return resp
        })
    },
    fetchCollectionWiseApiEndpoints (apiCollectionId) {
        return request({
            url: '/api/fetchCollectionWiseApiEndpoints',
            method: 'post',
            data: {apiCollectionId}
        }).then((resp) => {
            return resp
        })        
    }
}
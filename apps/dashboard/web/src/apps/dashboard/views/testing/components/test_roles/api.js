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
    addTestRoles (roleName, andConditions, orConditions) {
        return request({
            url: '/api/addTestRoles',
            method: 'post',
            data: {roleName, andConditions, orConditions}
        }).then((resp) => {
            return resp
        })        
    },
    deleteTestRole(roleName) {
        return request({
            url: '/api/deleteTestRole',
            method: 'post',
            data: {roleName}
        })
    },
    updateTestRoles (roleName, andConditions, orConditions) {
        return request({
            url: '/api/updateTestRoles',
            method: 'post',
            data: {roleName, andConditions, orConditions}
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
    createMultipleAccessMatrixTasks(roleName){
        return request({
            url: '/api/createMultipleAccessMatrixTasks',
            method: 'post',
            data: {roleName}
        }).then((resp) => {
            return resp
        })
    },
    deleteAccessMatrix(roleName){
        return request({
            url: '/api/deleteAccessMatrix',
            method: 'post',
            data: {roleName}
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
    addAuthToRole(roleName, apiCond, authParamData, authAutomationType, reqData) {
        return request({
            url: '/api/addAuthToRole',
            method: 'post',
            data: {roleName, apiCond, authParamData, authAutomationType, reqData}
        })
    },
    deleteAuthFromRole(roleName, index) {
        return request({
            url: '/api/deleteAuthFromRole',
            method: 'post',
            data: {roleName, index}
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
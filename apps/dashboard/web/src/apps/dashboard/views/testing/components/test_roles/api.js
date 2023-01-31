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
    updateTestRoles (roleName, andConditions, orConditions) {
        return request({
            url: '/api/updateTestRoles',
            method: 'post',
            data: {roleName, andConditions, orConditions}
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
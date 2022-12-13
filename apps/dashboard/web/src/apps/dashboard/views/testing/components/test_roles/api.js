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
    addTestRoles (roleName, regex, includedApiList, excludedApiList) {
        return request({
            url: '/api/addTestRoles',
            method: 'post',
            data: {roleName, regex, includedApiList, excludedApiList}
        }).then((resp) => {
            return resp
        })        
    },
}
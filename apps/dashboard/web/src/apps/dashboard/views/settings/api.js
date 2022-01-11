import request from '@/util/request'

export default {
    inviteUsers(apiSpec) {
        return request({
            url: '/api/inviteUsers',
            method: 'post',
            data: { 
                inviteeName: apiSpec.inviteeName,
                inviteeEmail: apiSpec.inviteeEmail,
                websiteHostName: apiSpec.websiteHostName,

            }
        })
    },
    getTeamData() {
        return request({
            url: '/api/getTeamData',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    removeUser (email) {
        return request({
            url: '/api/removeUser',
            method: 'post',
            data: {
                email: email
            }
        }).then((resp) => {
            return resp
        })
    },
    health() {
        return request({
            url: '/api/health',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    logout() {
        return request({
            url: '/api/logout',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    }
}
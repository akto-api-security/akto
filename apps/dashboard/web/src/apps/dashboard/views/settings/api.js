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
    fetchPostmanWorkspaces(api_key) {
        return request({
            url: '/api/fetchPostmanWorkspaces',
            method: 'post',
            data: {api_key}
        }).then((resp) => {
            return resp
        })
    },
    addOrUpdatePostmanCred(api_key, workspace_id) {
        return request({
            url: '/api/addOrUpdatePostmanCred',
            method: 'post',
            data: {api_key,workspace_id}
        }).then((resp) => {
            return resp
        })
    },
    getPostmanCredentials() {
        return request({
            url: '/api/getPostmanCredential',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    addBurpToken() {
        return request({
            url: '/api/addBurpToken',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    addExternalApiToken() {
        return request({
            url: '/api/addExternalApiToken',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    deleteApiToken(apiTokenId) {
        return request({
            url: '/api/deleteApiToken',
            method: 'post',
            data: {apiTokenId}
        }).then((resp) => {
            return resp
        })
    },
    fetchApiTokens() {
        return request({
            url: '/api/fetchApiTokens',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    toggleRedactFeature(redactPayload) {
        return request({
            url: '/api/toggleRedactFeature',
            method: 'post',
            data: {
                redactPayload
            }
        }).then((resp) => {
            return resp
        })
    },

    fetchAdminSettings() {
        return request({
            url: '/api/fetchAdminSettings',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    }


}
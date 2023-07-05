import request from "@/util/request"

const settingRequests = {
    inviteUsers: async (apiSpec) => {
        const res = request({
            url: '/api/inviteUsers',
            method: 'post',
            data: { 
                inviteeName: apiSpec.inviteeName,
                inviteeEmail: apiSpec.inviteeEmail,
                websiteHostName: apiSpec.websiteHostName,

            }
        })
        return res
    },
    getTeamData: async () => {
        const res = await request({
            url: '/api/getTeamData',
            method: 'post',
            data: {}
        })
        return res
    },
    removeUser: (email) => {
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
    fetchApiTokens: async function() {
        const resp = await request({
            url: '/api/fetchApiTokens',
            method: 'post',
            data: {}
        })
        return resp
    },
    addApiToken(tokenUtility) {
        return request({
            url: '/api/addApiToken',
            method: 'post',
            data: {tokenUtility}
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
}

export default settingRequests
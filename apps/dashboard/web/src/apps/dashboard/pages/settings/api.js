import request from "@/util/request"

const settingRequests = {
    inviteUsers: (apiSpec) => {
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
}

export default settingRequests
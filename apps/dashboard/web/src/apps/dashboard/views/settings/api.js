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
    getTeamData (teamId) {
        return request({
            url: '/api/getTeamData',
            method: 'post',
            data: {
                id: teamId
            }
        }).then((resp) => {
            return resp
        })
    }    
}
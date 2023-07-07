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
        })
    },
    deleteApiToken(apiTokenId) {
        return request({
            url: '/api/deleteApiToken',
            method: 'post',
            data: {apiTokenId}
        })
    },

    fetchPostmanWorkspaces(api_key) {
        return request({
            url: '/api/fetchPostmanWorkspaces',
            method: 'post',
            data: {api_key}
        })
    },
    addOrUpdatePostmanCred(api_key, workspace_id) {
        return request({
            url: '/api/addOrUpdatePostmanCred',
            method: 'post',
            data: {api_key,workspace_id}
        })
    },
    getPostmanCredentials() {
        return request({
            url: '/api/getPostmanCredential',
            method: 'post',
            data: {}
        })
    },

    fetchAktoGptConfig(){
        return request({
            url: '/api/fetchAktoGptConfig',
            method: 'post',
            data: {
                "apiCollectionId": -1
            }
        })
    },
    saveAktoGptConfig(aktoConfigList){
        return request({
            url: '/api/saveAktoGptConfig',
            method: 'post',
            data: {
                "currentState": aktoConfigList
            }
        })
    },
}

export default settingRequests
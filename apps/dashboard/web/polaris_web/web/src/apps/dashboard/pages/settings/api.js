import request from "@/util/request"

const settingRequests = {
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
        })
    },
    removeUser(email) {
        return request({
            url: '/api/removeUser',
            method: 'post',
            data: {
                email: email
            }
        })
    },

    
    fetchApiTokens() {
        return request({
            url: '/api/fetchApiTokens',
            method: 'post',
            data: {}
        })
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
    

    fetchAktoGptConfig(apiCollectionId){
        return request({
            url: '/api/fetchAktoGptConfig',
            method: 'post',
            data: {
                "apiCollectionId": apiCollectionId
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
    fetchLogsFromDb(startTime, endTime, logDb) {
        return request({
            url: '/api/fetchLogsFromDb',
            method: 'post',
            data: {
                startTime,
                endTime,
                logDb
            }
        })
    },
    fetchAdminSettings() {
        return request({
            url: '/api/fetchAdminSettings',
            method: 'post',
            data: {}
        })
    },
    fetchUserLastLoginTs() {
        return request({
            url: '/api/fetchUserLastLoginTs',
            method: 'post',
            data: {}
        })
    },
    fetchTrafficMetricsDesciptions() {
        return request({
            url: '/api/fetchTrafficMetricsDesciptions',
            method: 'post',
            data: {}
        })
    },
    fetchTrafficMetrics(groupBy, startTimestamp, endTimestamp, names, host) {
        return request({
            url: '/api/fetchTrafficMetrics',
            method: 'post',
            data: {groupBy, startTimestamp, endTimestamp, names, host}
        })
    },

    addCustomWebhook(webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections) {
        return request({
            url: '/api/addCustomWebhook',
            method: 'post',
            data: {
                webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections
            }
        })
    },
    updateCustomWebhook(id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections) {
        return request({
            url: '/api/updateCustomWebhook',
            method: 'post',
            data: {
                id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections
            }
        })
    },
    fetchCustomWebhooks() {
        return request({
            url: '/api/fetchCustomWebhooks',
            method: 'post',
            data: {}
        })
    },
    changeStatus(id, activeStatus) {
        return request({
            url: '/api/changeStatus',
            method: 'post',
            data: {id, activeStatus}
        })
    },
    runOnce(id) {
        return request({
            url: '/api/runOnce',
            method: 'post',
            data: {id}
        })
    },
    fetchLatestWebhookResult(id) {
        return request({
            url: '/api/fetchLatestWebhookResult',
            method: 'post',
            data: {id}
        })
    },
    addSlackWebhook(webhookUrl) {
        return request({
            url: '/api/addSlackWebhook',
            method: 'post',
            data: {webhookUrl}
        })
    },
    deleteSlackWebhook(apiTokenId) {
        return request({
            url: '/api/deleteSlackWebhook',
            method: 'post',
            data: {apiTokenId}
        })
    },
    deleteGithubSso() {
        return request({
            url: '/api/deleteGithubSso',
            method: 'post',
            data: {}
        })
    },

    addGithubSso(githubClientId, githubClientSecret) {
        return request({
            url: '/api/addGithubSso',
            method: 'post',
            data: {githubClientId, githubClientSecret}
        })
    },

    fetchGithubSso() {
        return request({
            url: '/api/fetchGithubSso',
            method: 'post',
            data: {}
        })
    }
}

export default settingRequests
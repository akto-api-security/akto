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
                inviteeRole: apiSpec.inviteeRole,
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
    makeAdmin(email, roleVal) {
        return request({
            url: '/api/makeAdmin',
            method: 'post',
            data: {
                email: email,
                userRole: roleVal
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

    addCustomWebhook(webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections, batchSize, webhookType, sendInstantly) {
        return request({
            url: '/api/addCustomWebhook',
            method: 'post',
            data: {
                webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections, batchSize, webhookType, sendInstantly
            }
        })
    },
    updateCustomWebhook(id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections, batchSize, sendInstantly) {
        return request({
            url: '/api/updateCustomWebhook',
            method: 'post',
            data: {
                id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections, batchSize, sendInstantly
            }
        })
    },
    fetchCustomWebhooks(customWebhookId) {
        return request({
            url: '/api/fetchCustomWebhooks',
            method: 'post',
            data: {customWebhookId}
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

    addGithubSso(githubClientId, githubClientSecret, githubUrl, githubApiUrl) {
        return request({
            url: '/api/addGithubSso',
            method: 'post',
            data: {githubClientId, githubClientSecret, githubUrl, githubApiUrl}
        })
    },

    fetchGithubSso() {
        return request({
            url: '/api/fetchGithubSso',
            method: 'post',
            data: {}
        })
    },

    testJiraIntegration(userEmail, apiToken, baseUrl, projId) {
        return request({
            url: '/api/testIntegration',
            method: 'post',
            data: {userEmail, apiToken, baseUrl, projId}
        })
    },

    fetchJiraIntegration() {
        return request({
            url: '/api/fetchIntegration',
            method: 'post',
            data: {}
        })
    },

    fetchOktaSso() {
        return request({
            url: '/api/fetchOktaSso',
            method: 'post',
            data: {}
        })
    },

    addJiraIntegration(userEmail, apiToken, baseUrl, projId, projectAndIssueMap) {
        return request({
            url: '/api/addIntegration',
            method: 'post',
            data: {userEmail, apiToken, baseUrl, projId, projectAndIssueMap}
        })
    },

    addOktaSso(clientId, clientSecret, authorisationServerId, oktaDomain, redirectUri) {
        return request({
            url: '/api/addOktaSso',
            method: 'post',
            data: {clientId, clientSecret, authorisationServerId, oktaDomain, redirectUri}
        })
    },

    deleteOktaSso() {
        return request({
            url: '/api/deleteOktaSso',
            method: 'post',
            data: {}
        })
    },

    fetchAzureSso(configType) {
        return request({
            url: '/api/fetchSAMLSso',
            method: 'post',
            data: {configType}
        })
    },

    addAzureSso(loginUrl, x509Certificate, ssoEntityId, applicationIdentifier, acsUrl, configType) {
        return request({
            url: '/api/addSAMLSso',
            method: 'post',
            data: {loginUrl, x509Certificate, ssoEntityId, applicationIdentifier, acsUrl, configType}
        })
    },

    deleteAzureSso(configType) {
        return request({
            url: '/api/deleteSamlSso',
            method: 'post',
            data: {configType}
        })
    },

    addGithubAppSecretKey(githubAppSecretKey, githubAppId) {
        return request({
            url: '/api/addGithubAppSecretKey',
            method: 'post',
            data: {githubAppSecretKey, githubAppId}
        })
    },

    deleteGithubAppSettings() {
        return request({
            url: '/api/deleteGithubAppSecretKey',
            method: 'post',
            data: {},
        })
    },

    fetchGithubAppId() {
        return request({
            url: '/api/fetchGithubAppId',
            method: 'post',
            data: {}
        })
    },

    toggleRedactFeature(redactPayload) {
        return request({
            url: '/api/toggleRedactFeature',
            method: 'post',
            data: {
                redactPayload
            }
        })
    },
    toggleNewMergingEnabled(newMergingEnabled) {
        return request({
            url: '/api/toggleNewMergingEnabled',
            method: 'post',
            data: {
                newMergingEnabled
            }
        });
    },
    updateSetupType(setupType) {
        return request({
            url: '/api/updateSetupType',
            method: 'post',
            data: {
                setupType
            }
        })
    },
    updateTrafficAlertThresholdSeconds(trafficAlertThresholdSeconds) {
        return request({
            url: '/api/updateTrafficAlertThresholdSeconds',
            method: 'post',
            data: {trafficAlertThresholdSeconds}
        })
    },
    toggleTelemetry(enableTelemetry) {
        return request({
            url: '/api/toggleTelemetry',
            method: 'post',
            data: {
                enableTelemetry
            }
        });
    },
    addFilterHeaderValueMap(filterHeaderValueMap){
        return request({
            url: '/api/addFilterHeaderValueMap',
            method: 'post',
            data: {
                filterHeaderValueMap
            }
        })
    },

    addApiCollectionNameMapper(regex, newName, headerName) {
        return request ({
            url: '/api/addApiCollectionNameMapper',
            method: 'post',
            data: {
                regex, 
                newName,
                headerName
            }
        })
    },

    deleteApiCollectionNameMapper(regex) {
        return request ({
            url: '/api/deleteApiCollectionNameMapper',
            method: 'post',
            data: {
                regex
            }
        })
    },
    
    configPrivateCidr(privateCidrList){
        return request({
            url: '/api/updatePrivateCidrIps',
            method: 'post',
            data: {privateCidrList}
        })
    },
    configPartnerIps(partnerIpList){
        return request({
            url: '/api/updatePartnerIps',
            method: 'post',
            data: {partnerIpList}
        })
    },
    applyAccessType(){
        return request({
            url: '/api/applyAccessType',
            method: 'post',
            data: {}
        })
    },
    handleRedundantUrls(allowRedundantEndpointsList) {
        return request({
            url: '/api/updateUrlSettings',
            method: 'post',
            data: {
                allowRedundantEndpointsList
            }
        });
    },
    getRoleHierarchy(){
        return request({
            url: '/api/getRoleHierarchy',
            method: 'post',
            data: {
            }
        });
    },
    updateAccountSettings(accountPermission,modifiedValueForAccount){
        return request({
            url: '/api/modifyAccountSettings',
            method: "post",
            data: {
                accountPermission, modifiedValueForAccount
            }
        })
    },
    updateApisCaseInsensitive(toggleCaseSensitiveApis){
        return request({
            url: '/api/toggleCaseSensitiveApis',
            method: "post",
            data: {
                toggleCaseSensitiveApis
            }
        })
    },
    resetUserPassword(userEmail) {
        return request({
            url: '/api/resetUserPassword',
            method: 'post',
            data: {userEmail}
        })
    },
    fetchApiAuditLogsFromDb(skip, limit, sortOrder, startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchApiAuditLogsFromDb',
            method: 'post',
            data: {skip, limit, sortOrder, startTimestamp, endTimestamp}
        })
    },
    getCustomRoles() {
        return request({
            url: '/api/getCustomRoles',
            method: 'post',
            data: {}
        })
    },
    createCustomRole(apiCollectionIds, roleName, baseRole, defaultInviteRole) {
        return request({
            url: '/api/createCustomRole',
            method: 'post',
            data: { apiCollectionIds, roleName, baseRole, defaultInviteRole }
        })
    },
    updateCustomRole(apiCollectionIds, roleName, baseRole, defaultInviteRole) {
        return request({
            url: '/api/updateCustomRole',
            method: 'post',
            data: {apiCollectionIds, roleName, baseRole, defaultInviteRole}
        })
    },
    deleteCustomRole(roleName) {
        return request({
            url: '/api/deleteCustomRole',
            method: 'post',
            data: {roleName}
        })
    },
    addAwsWafIntegration(awsAccessKey, awsSecretKey, region, ruleSetId, ruleSetName) {
        return request({
            url: '/api/addAwsWafIntegration',
            method: 'post',
            data: {awsAccessKey, awsSecretKey, region, ruleSetId, ruleSetName}
        })
    },
    fetchAwsWafIntegration() {
        return request({
            url: '/api/fetchAwsWafIntegration',
            method: 'post',
            data: {}
        })
    },
    addSplunkIntegration(splunkUrl, splunkToken) {
        return request({
            url: '/api/addSplunkIntegration',
            method: 'post',
            data: {splunkUrl, splunkToken}
        })
    },
    fetchSplunkIntegration() {
        return request({
            url: '/api/fetchSplunkIntegration',
            method: 'post',
            data: {}
        })
    },

    fetchAzureBoardsIntegration() {
        return request({
            url: '/api/fetchAzureBoardsIntegration',
            method: 'post',
            data: {}
        })
    },

    addAzureBoardsIntegration(azureBoardsBaseUrl, organization, projectList, personalAuthToken) {
        return request({
            url: '/api/addAzureBoardsIntegration',
            method: 'post',
            data: {azureBoardsBaseUrl, organization, projectList, personalAuthToken}
        })
    },
    removeAzureBoardsIntegration() {
        return request({
            url: '/api/removeAzureBoardsIntegration',
            method: 'post',
            data: {}
        })
    }
}

export default settingRequests
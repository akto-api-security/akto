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
    fetchAllMetricsDesciptions() {
        return request({
            url: '/api/allMetricsDescription',
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
    fetchMetrics(startTimestamp, endTimestamp) {
        return request({
            url: '/api/metrics',
            method: 'post',
            data: {startTime:startTimestamp, endTime:endTimestamp}
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
    addSlackWebhook(webhookUrl, webhookName) {
        return request({
            url: '/api/addSlackWebhook',
            method: 'post',
            data: {webhookUrl, webhookName}
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

    fetchJiraStatusMapping(projId, baseUrl, userEmail, apiToken){
        return request({
            url: '/api/jira/fetchProjectStatuses',
            method: 'post',
            data: {projId, baseUrl, userEmail, apiToken}
        })
    },

    addJiraIntegrationV2(data) {
        return request({
            url: '/api/jira/add',
            method: 'post',
            data: {...data}
        })
    },


    deleteJiraIntegratedProject(projId) {
        return request({
            url: '/api/jira/delete',
            method: 'post',
            data: {projId}
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
    switchTestingModule(miniTestingEnabled){
        return request({
            url: '/api/switchTestingModule',
            method: "post",
            data: {
                miniTestingEnabled
            }
        })
    },
    enableMergingOnVersions(enableMergingOnVersions, allowRetrospectiveMerging) {
        return request({
            url: '/api/enableMergingOnVersionsInApis',
            method: "post",
            data: {
                enableMergingOnVersions, allowRetrospectiveMerging
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
    createCustomRole(apiCollectionIds, roleName, baseRole, defaultInviteRole, allowedFeaturesForUser) {
        return request({
            url: '/api/createCustomRole',
            method: 'post',
            data: { apiCollectionIds, roleName, baseRole, defaultInviteRole, allowedFeaturesForUser }
        })
    },
    updateCustomRole(apiCollectionIds, roleName, baseRole, defaultInviteRole, allowedFeaturesForUser) {
        return request({
            url: '/api/updateCustomRole',
            method: 'post',
            data: {apiCollectionIds, roleName, baseRole, defaultInviteRole, allowedFeaturesForUser}
        })
    },
    deleteCustomRole(roleName) {
        return request({
            url: '/api/deleteCustomRole',
            method: 'post',
            data: {roleName}
        })
    },
    addAwsWafIntegration(awsAccessKey, awsSecretKey, region, ruleSetId, ruleSetName,severityLevels) {
        return request({
            url: '/api/addAwsWafIntegration',
            method: 'post',
            data: {awsAccessKey, awsSecretKey, region, ruleSetId, ruleSetName,severityLevels}
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
    },

    fetchServiceNowIntegration() {
        return request({
            url: '/api/fetchServiceNowIntegration',
            method: 'post',
            data: {}
        })
    },

    fetchServiceNowTables(instanceUrl, clientId, clientSecret) {
        return request({
            url: '/api/fetchServiceNowTables',
            method: 'post',
            data: {instanceUrl, clientId, clientSecret}
        })
    },

    addServiceNowIntegration(instanceUrl, clientId, clientSecret, tableNames) {
        return request({
            url: '/api/addServiceNowIntegration',
            method: 'post',
            data: {instanceUrl, clientId, clientSecret, tableNames}
        })
    },

    removeServiceNowIntegration() {
        return request({
            url: '/api/removeServiceNowIntegration',
            method: 'post',
            data: {}
        })
    },
    removeInvitation(email) {
        return request({
            url: '/api/removeInvitation',
            method: 'post',
            data: {email}
        })
    },
    async fetchModuleInfo(filter = {}) {
        return await request({
            url: '/api/fetchModuleInfo',
            method: 'post',
            data: { filter }
        })
    },
    async deleteModuleInfo(moduleIds) {
        return await request({
            url: '/api/deleteModuleInfo',
            method: 'post',
            data: { moduleIds }
        })
    },
    async fetchCloudflareWafIntegration() {
        return await request({
            url: '/api/fetchCloudflareWafIntegration',
            method: 'post',
            data: {}
        })
    },
    async deleteCloudflareWafIntegration() {
        return await request({
            url: '/api/deleteCloudflareWafIntegration',
            method: 'post',
            data: {}
        })
    },
    async addCloudflareWafIntegration(accountOrZoneId, apiKey, email, integrationType,severityLevels) {
        return await request({
            url: '/api/addCloudflareWafIntegration',
            method: 'post',
            data: {accountOrZoneId, apiKey, email, integrationType,severityLevels}
        })
    },
    async getDeMergedApis() {
        return await request({
            url: '/api/getDeMergedApis',
            method: 'post',
            data: {}
        })
    },
    async undoDemergedApis(mergedApis) {
        return await request({
            url: '/api/undoDemergedApis',
            method: 'post',
            data: {mergedUrls: mergedApis}
        })
    },
    async downloadSamplePdf() {
        return await request({
            url: '/api/downloadSamplePdf',
            method: 'post',
            data: {}
        })
    },
    async deleteAllMaliciousEvents() {
        return await request({
            url: '/api/deleteAllMaliciousEvents',
            method: 'post',
            data: {}
        })
    },
    getAllowedFeaturesForRBAC() {
        return request({
            url: '/api/allowedFeaturesForRBAC',
            method: 'post',
            data: {}
        })
    },
    async deleteDuplicateEntries() {
        return await request({
            url: '/api/deleteDuplicateEntries',
            method: 'post',
            data: {}
        })
    },
    updateCompulsoryDescription(compulsoryDescription) {
        return request({
            url: '/api/updateCompulsoryDescription',
            method: 'post',
            data: {
                compulsoryDescription
            }
        })
    },
    getMcpServersByAgent(agentId, deviceId) {
        return request({
            url: '/api/getMcpServersByAgent',
            method: 'post',
            data: {
                agentId,
                deviceId
            }
        })
    },
    getAgentLogs(agentId, startTime, endTime) {
        return request({
            url: '/api/getAgentLogs',
            method: 'post',
            data: {
                agentId,
                startTime,
                endTime
            }
        })
    },
    addMcpRegistryIntegration(registries) {
        return request({
            url: '/api/addMcpRegistryIntegration',
            method: 'post',
            data: {registries}
        })
    }
}

export default settingRequests
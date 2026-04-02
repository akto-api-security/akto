import request from '@/util/request'

const api = {
    fetchQuickStartPageState() {
        return request({
            url: '/api/fetchQuickStartPageState',
            method: 'post',
            data: {}
        })
    },
    
    fetchBurpPluginInfo() {
        return request({
            url: '/api/fetchBurpPluginInfo',
            method: 'post',
            data: {}
        })
    },
    downloadBurpPluginJar() {
        return request({
            url: '/api/downloadBurpPluginJar',
            method: 'post',
            data: {},
            responseType: 'blob'
        })
    },

    importDataFromPostmanFile(postmanCollectionFile, allowReplay, miniTestingName) {
        return request({
            url: '/api/importDataFromPostmanFile',
            method: 'post',
            data: {postmanCollectionFile, allowReplay, miniTestingName}
        })
    },
    importPostmanWorkspace(workspace_id, allowReplay, api_key, miniTestingName) {
        return request({
            url: '/api/importPostmanWorkspace',
            method: 'post',
            data: {workspace_id, allowReplay, api_key, miniTestingName}
        })
    },

    fetchLBs(data){
        return request({
            url: '/api/fetchLoadBalancers',
            method: 'post',
            data: data,
        })
    },
    saveLBs(selectedLBs){
        return request({
            url: 'api/saveLoadBalancers',
            method: 'post',
            data: {selectedLBs}
        })
    },
    fetchStackCreationStatus(data){
        return request({
            url: 'api/checkStackCreationProgress',
            method: 'post',
            data: data,
        })
    },
    createRuntimeStack(deploymentMethod) {
        return request({
            url: '/api/createRuntimeStack',
            method: 'post',
            data: {deploymentMethod}
        })
    },
    fetchBurpPluginDownloadLink() {
        return request({
            url: '/api/fetchBurpPluginDownloadLink',
            method: 'post',
            data: {},
        }).then((resp) => {
            return resp
        })
    },
    fetchBurpCredentials() {
        return request({
            url: '/api/fetchBurpCredentials',
            method: 'post',
            data: {},
        }).then((resp) => {
            return resp
        })
    },
    importDataFromOpenApiSpec(formData) {
        return request({
            url: '/api/importDataFromOpenApiSpec',
            method: 'post',
            data: formData,
        })
    },
    fetchPostmanImportLogs(uploadId){
        return request({
            url: '/api/fetchPostmanImportLogs',
            method: 'post',
            data: {uploadId},
        })
    },
    fetchSwaggerImportLogs(uploadId){
        return request({
            url: '/api/fetchSwaggerImportLogs',
            method: 'post',
            data: {uploadId},
        })
    },
    ingestPostman(uploadId, importType){
        return request({
            url: '/api/ingestPostman',
            method: 'post',
            data: {uploadId, importType},
        })
    },
    ingestSwagger(uploadId, importType){
        return request({
            url: '/api/importSwaggerLogs',
            method: 'post',
            data: {uploadId, importType},
        })
    },

    deleteImportedPostman(uploadId){
        return request({
            url: '/api/deletePostmanImportLogs',
            method: 'post',
            data: {uploadId},
        })
    },

    fetchRuntimeHelmCommand(expiryTimeInMonth) {
        return request({
            url: '/api/fetchRuntimeHelmCommand',
            method: 'post',
            data: {expiryTimeInMonth}
        })
    },

    addCodeAnalysisRepo(codeAnalysisRepos) {
        return request({
            url: '/api/addCodeAnalysisRepo',
            method: 'post',
            data: {codeAnalysisRepos}
        })
    },

    runCodeAnalysisRepo(codeAnalysisRepos) {
        return request({
            url: '/api/runCodeAnalysisRepo',
            method: 'post',
            data: {codeAnalysisRepos}
        })
    },

    deleteCodeAnalysisRepo(codeAnalysisRepo) {
        return request({
            url: '/api/deleteCodeAnalysisRepo',
            method: 'post',
            data: {codeAnalysisRepo}
        })
    },

    fetchCodeAnalysisRepos(sourceCodeType) {
        return request({
            url: '/api/fetchCodeAnalysisRepos',
            method: 'post',
            data: {sourceCodeType}
        })
    },

    initiateCrawler(hostname, username, password, apiKey, dashboardUrl, testRoleHexId, outscopeUrls, crawlingTime, selectedModuleName, customHeaders, runTestAfterCrawling, selectedMiniTestingService, urlTemplatePatterns, applicationPages, collectionName) {
        return request({
            url: '/api/initiateCrawler',
            method: 'post',
            data: {hostname, username, password, apiKey, dashboardUrl, testRoleHexId, outscopeUrls, crawlingTime, selectedModuleName, customHeaders, runTestAfterCrawling, selectedMiniTestingService, urlTemplatePatterns, applicationPages, collectionName},
            timeout: 360000
        })
    },

    fetchAvailableDastModules() {
        return request({
            url: '/api/fetchAvailableDastModules',
            method: 'post',
            data: {}
        })
    },

    initiateMCPScan(serverUrl, authKey, authValue, dashboardUrl) {
        return request({
            url: '/api/initiateMCPScan',
            method: 'post',
            data: {serverUrl, authKey, authValue, dashboardUrl}
        })
    },

    initiateAIAgentConnectorImport(connectorType, connectorConfig, dataIngestionUrl, recurringIntervalSeconds) {
        return request({
            url: '/api/initiateAIAgentConnectorImport',
            method: 'post',
            data: {
                connectorType,
                dataIngestionUrl,
                recurringIntervalSeconds,
                ...connectorConfig
            }
        })
    },

    importFromUrl(url, testRoleId, requestBody) {
        return request({
            url: '/api/importFromUrl',
            method: 'post',
            data: {url, testRoleId, requestBody}
        })
    },

    initiateMCPRecon(ipRange) {
        return request({
            url: '/api/initiateMCPRecon',
            method: 'post',
            data: {ipRange}
        })
    },

    addAwsAccountIdsForApiGatewayLogging(awsAccountIds) {
        return request({
            url: '/api/addAwsAccountIdsForApiGatewayLogging',
            method: 'post',
            data: { awsAccountIds }
        })
    },

    fetchAwsAccountIdsForApiGatewayLogging() {
        return request({
            url: '/api/fetchAwsAccountIdsForApiGatewayLogging',
            method: 'post',
            data: { }
        })
    },

    importImpervaSchema(formData) {
        return request({
            url: '/api/importImpervaSchema',
            method: 'post',
            data: formData,
        })
    },

    initiateAIAgentConnectorImport(connectorType, connectorConfig, dataIngestionUrl, recurringIntervalSeconds) {
        return request({
            url: '/api/initiateAIAgentConnectorImport',
            method: 'post',
            data: {
                connectorType,
                dataIngestionUrl,
                recurringIntervalSeconds,
                ...connectorConfig
            }
        })
    },

    saveDataDogConnector(datadogApiKey, datadogAppKey, datadogSite, serviceNames) {
        return request({
            url: '/api/saveDataDogConfigs',
            method: 'post',
            data: {datadogApiKey, datadogAppKey, datadogSite, serviceNames}
        })
    },

    fetchMicrosoftDefenderIntegration() {
        return request({
            url: '/api/fetchMicrosoftDefenderIntegration',
            method: 'post',
            data: {}
        })
    },

    addMicrosoftDefenderIntegration(tenantId, clientId, clientSecret, dataIngestionUrl, recurringIntervalSeconds) {
        return request({
            url: '/api/addMicrosoftDefenderIntegration',
            method: 'post',
            data: {tenantId, clientId, clientSecret, dataIngestionUrl, recurringIntervalSeconds}
        })
    },

    removeMicrosoftDefenderIntegration() {
        return request({
            url: '/api/removeMicrosoftDefenderIntegration',
            method: 'post',
            data: {}
        })
    },

    fetchDefenderDevices() {
        return request({
            url: '/api/fetchDefenderDevices',
            method: 'post',
            data: {}
        })
    },

    uploadDefenderScript(scriptContent, scriptName) {
        return request({
            url: '/api/uploadDefenderScript',
            method: 'post',
            data: { scriptContent, scriptName }
        })
    },

    runDefenderKqlQuery(kqlQuery) {
        return request({
            url: '/api/runDefenderKqlQuery',
            method: 'post',
            data: { kqlQuery }
        })
    },

    listDefenderLibraryScripts() {
        return request({
            url: '/api/listDefenderLibraryScripts',
            method: 'post',
            data: {}
        })
    },

    runDefenderLiveResponse(deviceIds, scriptName, scriptParameters) {
        return request({
            url: '/api/runDefenderLiveResponse',
            method: 'post',
            data: { deviceIds, scriptName, scriptParameters },
            timeout: 360000
        })
    },

    ingestDefenderKqlResults(kqlResults, agentName) {
        return request({
            url: '/api/ingestDefenderKqlResults',
            method: 'post',
            data: { kqlResults, agentName }
        })
    },

    fetchSentinelOneIntegration() {
        return request({
            url: '/api/fetchSentinelOneIntegration',
            method: 'post',
            data: {}
        })
    },

    addSentinelOneIntegration(apiToken, consoleUrl, dataIngestionUrl, recurringIntervalSeconds) {
        return request({
            url: '/api/addSentinelOneIntegration',
            method: 'post',
            data: { apiToken, consoleUrl, dataIngestionUrl, recurringIntervalSeconds }
        })
    },

    removeSentinelOneIntegration() {
        return request({
            url: '/api/removeSentinelOneIntegration',
            method: 'post',
            data: {}
        })
    },

    fetchSentinelOneAgents() {
        return request({
            url: '/api/fetchSentinelOneAgents',
            method: 'post',
            data: {}
        })
    },

    getGuardrailTypes() {
        return request({
            url: '/api/getGuardrailTypes',
            method: 'post',
            data: {}
        })
    },

    saveGuardrailsConfig(guardrailType, guardrailEnvVars, guardrailTargetMode, guardrailAgentIds) {
        return request({
            url: '/api/saveGuardrailsConfig',
            method: 'post',
            data: { guardrailType, guardrailEnvVars, guardrailTargetMode, guardrailAgentIds }
        })
    },

}

export default api
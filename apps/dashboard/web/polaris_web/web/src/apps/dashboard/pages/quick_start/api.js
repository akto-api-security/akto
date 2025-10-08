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

    initiateCrawler(hostname, username, password, apiKey, dashboardUrl, testRoleHaxId, outscopeUrls) {
        return request({
            url: '/api/initiateCrawler',
            method: 'post',
            data: {hostname, username, password, apiKey, dashboardUrl, testRoleHaxId, outscopeUrls}
        })
    },

    initiateMCPScan(serverUrl, authKey, authValue, dashboardUrl) {
        return request({
            url: '/api/initiateMCPScan',
            method: 'post',
            data: {serverUrl, authKey, authValue, dashboardUrl}
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

}

export default api
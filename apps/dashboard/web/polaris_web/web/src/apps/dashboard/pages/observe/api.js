import request from "../../../../util/request"


export default {
    async fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, sensitive, isRequest, queryValue) {
        const resp = await request({
            url: '/api/fetchChanges',
            method: 'post',
            data: {
                sortKey,
                sortOrder,
                limit,
                skip,
                filters: Object.entries(filters).reduce((z, e) => {
                    z[e[0]] = [...e[1]]
                    return z
                }, {}),
                filterOperators,
                startTimestamp,
                endTimestamp,
                sensitive: sensitive,
                request: isRequest,
                searchString:queryValue
            }
        })
        return resp?.data
    },
    fetchRecentParams(startTimestamp, endTimestamp){
        return request({
            url: '/api/fetchRecentParams',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
    },
    async fetchAuditData(sortKey, sortOrder, skip, limit, filters, filterOperators) {
        const resp = await request({
            url: '/api/fetchAuditData',
            method: 'post',
            data: { sortKey, sortOrder, skip, limit, filters, filterOperators }
        });
        return resp;
    },
    async updateAuditData(hexId, remarks, approvalData = null) {
        const data = { hexId };
        if (approvalData) {
            data.approvalData = approvalData;
        } else {
            data.remarks = remarks;
        }
        
        const resp = await request({
            url: '/api/updateAuditData',
            method: 'post',
            data: data
        });
        return resp;
    },

    async fetchDataTypeNames() {
        const resp = await request({
            url: '/api/fetchDataTypeNames',
            method: 'post',
            data: {}
        })
        return resp
    },
    fetchSubTypeCountMap(startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchSubTypeCountMap',
            method: 'post',
            data: {
                startTimestamp,
                endTimestamp
            }
        })
    },
    resetSampleData() {
        return request({
            url: '/api/resetSampleData',
            method: 'post',
            data: {}
        })
    },
    fillSensitiveDataTypes() {
        return request({
            url: '/api/fillSensitiveDataTypes',
            method: 'post',
            data: {}
        })
    },
    async fetchSampleData(url, apiCollectionId, method) {
        const resp = await request({
            url: '/api/fetchSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        })
        return resp
    },
    async fetchSensitiveSampleData(url, apiCollectionId, method) {
        const resp = await request({
            url: '/api/fetchSensitiveSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        })
        return resp
    },
    fetchDataTypes() {
        return request({
            url: '/api/fetchDataTypes',
            method: 'post',
            data: {}
        })
    },
    async loadSensitiveParameters(apiCollectionId, url, method, subType) {
        const resp = await request({
            url: '/api/loadSensitiveParameters',
            method: 'post',
            data: {
                apiCollectionId,
                url,
                method,
                subType
            }
        })
        return resp
    },

    saveCustomDataType(dataObj) {
        return request({
            url: '/api/saveCustomDataType',
            method: 'post',
            data: dataObj
        })
    },

    saveAktoDataType(dataObj) {
        return request({
            url: '/api/saveAktoDataType',
            method: 'post',
            data: dataObj
        })
    },
    async convertSampleDataToCurl(sampleData) {
        const resp = await request({
            url: '/api/convertSampleDataToCurl',
            method: 'post',
            data: { sampleData }
        })
        return resp
    },
    async convertSampleDataToBurpRequest(sampleData) {
        const resp = await request({
            url: '/api/convertSamleDataToBurpRequest',
            method: 'post',
            data: { sampleData }
        })
        return resp
    },

    async getAllCollections() {
        return await request({
            url: '/api/getAllCollections',
            method: 'post',
            data: {}
        })
    },
    async getAllCollectionsBasic() {
        return await request({
            url: '/api/getAllCollectionsBasic',
            method: 'post',
            data: {}
        })
    },
    async createCollection(name) {
        return await request({
            url: '/api/createCollection',
            method: 'post',
            data: { collectionName: name }
        })
    },

    async deleteCollection(apiCollectionId) {
        return await request({
            url: '/api/deleteCollection',
            method: 'post',
            data: { apiCollectionId }
        })
    },

    async deleteMultipleCollections(items) {
        return await request({
            url: '/api/deleteMultipleCollections',
            method: 'post',
            data: { apiCollections: items }
        })
    },
    
    async updateUserCollections(userCollectionMap) {
        return await request({
            url: '/api/updateUserCollections',
            method: 'post',
            data: {
                userCollectionMap: userCollectionMap,
            }
        })
    },

    async getAllUsersCollections() {
        return await request({
            url: '/api/getAllUsersCollections',
            method: 'post',
            data: {}
        })
    },
    saveContent(apiSpec) {
        return request({
            url: '/api/saveContent',
            method: 'post',
            data: {
                apiSpec: apiSpec.swaggerContent,
                filename: apiSpec.filename,
                apiCollectionId: apiSpec.apiCollectionId
            }
        })
    },
    loadContent(apiCollectionId) {
        return request({
            url: '/api/loadContent',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
    },
    uploadHarFile(formData) {
        return request({
            url: '/api/uploadHar',
            method: 'post',
            data: formData,
        })
    },
    uploadOpenApiFile(formData) {
        return request({
            url: '/api/importDataFromOpenApiSpec',
            method: 'post',
            data: formData,
        })
    },
    uploadTcpFile(content, apiCollectionId, skipKafka) {
        return request({
            url: '/api/uploadTcp',
            method: 'post',
            data: {
                tcpContent: content, apiCollectionId, skipKafka
            }
        })
    },
    downloadOpenApiFile(apiCollectionId, lastFetchedUrl, lastFetchedMethod) {
        return request({
            url: '/api/generateOpenApiFile',
            method: 'post',
            data: {
                apiCollectionId, lastFetchedUrl, lastFetchedMethod
            }
        })
    },
    downloadOpenApiFileForSelectedApis(apiInfoKeyList, apiCollectionId) {
        return request({
            url: '/api/generateOpenApiFile',
            method: 'post',
            data: {
                apiInfoKeyList, apiCollectionId
            }
        })
    },
    exportToPostman(apiCollectionId) {
        return request({
            url: '/api/createPostmanApi',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    exportToPostmanForSelectedApis(apiInfoKeyList, apiCollectionId) {
        return request({
            url: '/api/createPostmanApi',
            method: 'post',
            data: {
                apiInfoKeyList, apiCollectionId
            }
        })
    },

    async fetchAPIsFromSourceCode(apiCollectionId) {
        return await request({
            url: '/api/fetchCodeAnalysisApiInfos',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
            }
        })
    },

    async fetchApisFromStis(apiCollectionId) {
        return await request({
            url: '/api/fetchApiInfosFromSTIs',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
            }
        })
    },

    async fetchApiInfosForCollection(apiCollectionId) {
        return await request({
            url: '/api/fetchApiInfosForCollection',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
            }
        })
    },
    redactCollection(apiCollectionId, redacted){
        return request({
            url: '/api/redactCollection',
            method: 'post',
            data:{
                apiCollectionId,redacted
            }
        })
    },

    deleteApis(apiList){
        return request({
            url: '/api/deleteApis',
            method: 'post',
            data: {
                apiList
            }
        })
    },

    async fetchAllUrlsAndMethods (apiCollectionId) {
        const resp = await request({
            url: '/api/fetchAllUrlsAndMethods',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
        return resp
    },

    addSensitiveField (x) {
        return request({
            url: 'api/addSensitiveField',
            method: 'post',
            data: {
                ...x
            }
        })
    },
    listAllSensitiveFields() {
        return request({
            url: 'api/listAllSensitiveFields',
            method: 'post',
            data: {}
        })
    },
    async loadRecentEndpoints (startTimestamp, endTimestamp, skip, limit, filters, filterOperators, searchString) {
        const resp = await request({
            url: '/api/loadRecentEndpoints',
            method: 'post',
            data: { startTimestamp, endTimestamp, skip, limit, filters, filterOperators, searchString}
        })
        return resp
    },
    async getSummaryInfoForChanges (startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/getSummaryInfoForChanges',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp
    },
    async fetchNewEndpointsTrendForHostCollections (startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNewEndpointsTrendForHostCollections',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp
    },
    async fetchNewEndpointsTrendForNonHostCollections (startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNewEndpointsTrendForNonHostCollections',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp
    },
    async fetchSensitiveParamsForEndpoints (urls) {
        const resp = await request({
            url: '/api/fetchSensitiveParamsForEndpoints',
            method: 'post',
            data: { urls }
        })
        return resp
    },
    async fetchEndpointTrafficData (url, apiCollectionId, method, startEpoch, endEpoch) {
        const resp = await request({
            url: '/api/fetchEndpointTrafficData',
            method: 'post',
            data: {
                url, apiCollectionId, method, startEpoch, endEpoch
            }
        })
        return resp
    },
    async fetchApiInfoList(apiCollectionId) {
        const resp = await request({
            url: '/api/fetchApiInfoList',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
        return resp
    },
    async fetchFilters() {
        const resp = await request({
            url: '/api/fetchFilters',
            method: 'post',
            data: {}
        })
        return resp
    },
    fetchWorkflowTests() {
        return request({
            url: '/api/fetchWorkflowTests',
            method: 'post',
            data: {}
        })
    },
    createWorkflowTest(nodes, edges, mapNodeIdToWorkflowNodeDetails, state, apiCollectionId) {
        return request({
            url: '/api/createWorkflowTest',
            method: 'post',
            data: {nodes, edges, mapNodeIdToWorkflowNodeDetails, state, apiCollectionId}
        })
    },

    editWorkflowTest(id, nodes, edges, mapNodeIdToWorkflowNodeDetails) {
        return request({
            url: '/api/editWorkflowTest',
            method: 'post',
            data: {id, nodes, edges, mapNodeIdToWorkflowNodeDetails}
        })
    },

    setWorkflowTestState(id, state) {
        return request({
            url: '/api/setWorkflowTestState',
            method: 'post',
            data: {id, state}
        })
    },

    exportWorkflowTestAsString(id) {
        return request({
            url: '/api/exportWorkflowTestAsString',
            method: 'post',
            data: {id}
        })
    },
    editWorkflowNodeDetails(id, nodeId, workflowNodeDetails) {
        let mapNodeIdToWorkflowNodeDetails = {}
        mapNodeIdToWorkflowNodeDetails[nodeId] = workflowNodeDetails
        return request({
            url: '/api/editWorkflowNodeDetails',
            method: 'post',
            data: {id, mapNodeIdToWorkflowNodeDetails}
        })
    },

    runWorkflowTest(id) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {
                "testIdConfig" : 1,
                "workflowTestId": id,
                "type": "WORKFLOW",
                testName: id
            }
        })
    },

    scheduleWorkflowTest(id, recurringDaily, startTimestamp) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {
                "testIdConfig" : 1,
                "workflowTestId": id,
                "type": "WORKFLOW",
                "recurringDaily": recurringDaily,
                "startTimestamp": startTimestamp,
                testName: id
            }
        })
    },

    fetchWorkflowTestingRun(workflowId) {
        return request({
            url: '/api/fetchWorkflowTestingRun',
            method: 'post',
            data: {
                "workflowTestId" : workflowId
            }
        })
    },

    deleteScheduledWorkflowTests(workflowId) {
        return request({
            url: '/api/deleteScheduledWorkflowTests',
            method: 'post',
            data: {
                "workflowTestId" : workflowId
            }
        })
    },

    fetchWorkflowResult(id) {
        return request({
            url: '/api/fetchWorkflowResult',
            method: 'post',
            data: {
                "workflowTestId": id,
            }
        })
    },

    downloadWorkflowAsJson(id) {
        return request({
            url: '/api/downloadWorkflowAsJson',
            method: 'post',
            data: {
                "id": id,
            }
        })
    },

    uploadWorkflowJson(workflowTestJson, apiCollectionId) {
        return request({
            url: '/api/uploadWorkflowJson',
            method: 'post',
            data: { workflowTestJson, apiCollectionId }
        })
    },

    async setFalsePositives (falsePositives) {
        const resp = await request({
            url: '/api/setFalsePositives',
            method: 'post',
            data: { falsePositives: falsePositives }
        })
        return resp
    },
    fetchAktoGptConfig(apiCollectionId) {
        return request({
            url: '/api/fetchAktoGptConfig',
            method: 'post',
            data: { apiCollectionId }
        }).then((resp) => {
            return resp
        })
    },
    fetchAllMarketplaceSubcategories() {
        return request({
            url: 'api/fetchAllMarketplaceSubcategories',
            method: 'post',
            data: {}
        })
    },
    scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily, recurringWeekly, recurringMonthly, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds = [], selectedMiniTestingServiceName, selectedSlackWebhook, autoTicketingDetails) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: { apiCollectionId, type: "COLLECTION_WISE", startTimestamp, recurringDaily,  recurringWeekly, recurringMonthly,selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds, selectedMiniTestingServiceName, selectedSlackWebhook, autoTicketingDetails}
        }).then((resp) => {
            return resp
        })
    },
    scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, recurringWeekly, recurringMonthly, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds = [], selectedMiniTestingServiceName, selectedSlackWebhook, autoTicketingDetails) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM", startTimestamp, recurringDaily,  recurringWeekly, recurringMonthly,selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source, testRoleId, continuousTesting, sendSlackAlert, sendMsTeamsAlert, testConfigsAdvancedSettings, cleanUpTestingResources, testSuiteIds, selectedMiniTestingServiceName, selectedSlackWebhook, autoTicketingDetails}
        }).then((resp) => {
            return resp
        })        
    },
    async loadParamsOfEndpoint (apiCollectionId, url, method) {
        const resp = await request({
            url: '/api/loadParamsOfEndpoint',
            method: 'post',
            data: {
                apiCollectionId,
                url,
                method
            }
        })
        return resp
    },

    async fetchSlackWebhooks() {
        const resp = await request({
            url: '/api/fetchSlackWebhooks',
            method: 'post',
            data: {}
        })
        return resp
    },

    async checkWebhook(webhookType, webhookOption) {
        const resp = await request({
            url: '/api/checkWebhook',
            method: 'post',
            data: { webhookType, webhookOption }
        })
        return resp
    },

    async fetchNewParametersTrend(startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/fetchNewParametersTrend',
            method: 'post',
            data: { startTimestamp, endTimestamp }
        })
        return resp.data.endpoints
    },
    saveContent(apiSpec) {
        return request({
            url: '/api/saveContent',
            method: 'post',
            data: {
                apiSpec: apiSpec.swaggerContent,
                filename: apiSpec.filename,
                apiCollectionId: apiSpec.apiCollectionId
            }
        })
    },
    loadContent(apiCollectionId) {
        return request({
            url: '/api/loadContent',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
    },

    async addApisToCustomCollection(apiList, collectionName) {
        return await request({
            url: '/api/addApisToCustomCollection',
            method: 'post',
            data: {
                apiList, collectionName
            }
        })
    },
    async syncExtractedAPIs(apiCollectionName, projectDir, codeAnalysisApisList) {
        return await request({
            url: '/api/syncExtractedAPIs',
            method: 'post',
            data: {
                apiCollectionName, projectDir, codeAnalysisApisList
            }
        })
    },
    async removeApisFromCustomCollection(apiList, collectionName) {
        return await request({
            url: '/api/removeApisFromCustomCollection',
            method: 'post',
            data: {
                apiList, collectionName
            }
        })
    },
    async computeCustomCollections(collectionName) {
        return await request({
            url: '/api/computeCustomCollections',
            method: 'post',
            data: {
                collectionName
            }
        })
    },
    async createCustomCollection(collectionName, conditions) {
        return await request({
            url: '/api/createCustomCollection',
            method: 'post',
            data: {
                collectionName, conditions
            }
        })
    },
    async updateCustomCollection(apiCollectionId, conditions) {
        return await request({
            url: '/api/updateCustomCollection',
            method: 'post',
            data: {
                apiCollectionId, conditions
            }
        })
    },
    async getEndpointsListFromConditions(conditions, skipTagsMismatch = false) {
        return await request({
            url: '/api/getEndpointsListFromConditions',
            method: 'post',
            data: {
                conditions,
                skipTagsMismatch
            }
        }).then((resp) => {
            return resp
        })
    },
    async getEndpointsFromConditions(conditions) {
        return await request({
            url: '/api/getEndpointsFromConditions',
            method: 'post',
            data: {
                conditions
            }
        })
    },
    fetchApiDependencies(apiCollectionId, url, method) {
        return request({
            url: '/api/fetchApiDependencies',
            method: 'post',
            data: {
                apiCollectionId, url, method
            }
        })
    },

    async getSensitiveInfoForCollections(){
        return await request({
            url: '/api/getSensitiveInfoForCollections',
            method: 'post',
            data:{},
        })
    },

    async getCoverageInfoForCollections(){
        return await request({
            url: '/api/getCoverageInfoForCollections',
            method: 'post',
            data:{},
        })
    },

    async getSeverityInfoForCollections(){
        return await request({
            url: '/api/getSeverityInfoForCollections',
            method: 'post',
            data:{},
        })
    },

    async getSensitiveInfoForCollections(type){
        const data = (typeof type !== 'undefined' && type !== null) ? { type } : {}
        return await request({
            url: '/api/getSensitiveInfoForCollections',
            method: 'post',
            data,
        })
    },

    async getLastTrafficSeen(){
        return await request({
            url: '/api/getLastSeenTrafficInfoForCollections',
            method: 'post',
            data:{},
        })
    },

    async getRiskScoreInfo() {
        return await request({
            url: '/api/getRiskScoreInfo',
            method: 'post',
            data: {}
        })
    },

    async lastUpdatedInfo() {
        return await request({
            url: '/api/getLastCalculatedInfo',
            method: 'post',
            data: {}
        })
    },
    
    async deMergeApi(apiCollectionId, url, method){
        return await request({
            url: '/api/deMergeApi',
            method: 'post',
            data: {apiCollectionId, url, method}
        })
    },
    async getUserEndpoints(){
        return await request({
            url: '/api/getCustomerEndpoints',
            method: 'post',
            data:{},
        })
    },
    async updateEnvTypeOfCollection(envType, apiCollectionIds,resetEnvTypes){
        return await request({
            url: '/api/updateEnvType',
            method: 'post',
            data: {envType, apiCollectionIds,resetEnvTypes}
        })
    },
    fetchEndpoint(apiInfoKey){
        return request({
            url: '/api/getSingleEndpoint',
            method: 'post',
            data: {
                url: apiInfoKey.url,
                method: apiInfoKey.method,
                apiCollectionId: apiInfoKey.apiCollectionId
            }
        })
    },
    fetchCountMapOfApis(){
        return request({
            url: "/api/fetchCountMapOfApis",
            method: "post",
            data: {}
        })
    },
    resetDataTypeRetro(name){
        return request({
            url: '/api/resetDataTypeRetro',
            method: 'post',
            data: { name }
        })
    },

    async saveEndpointDescription(apiCollectionId, url, method, description) {
        const resp = await request({
            url: '/api/saveEndpointDescription',
            method: 'post',
            data: { apiCollectionId, url, method, description }
        })
        return resp
    },

    async fetchApiCallStats(apiCollectionId, url, method, startEpoch, endEpoch) {
        const resp = await request({
            url: '/api/fetchApiCallStats',
            method: 'post',
            data: { apiCollectionId, url, method, startEpoch, endEpoch }
        })
        return resp
    },

    async fetchIpLevelApiCallStats(apiCollectionId, url, method, startWindow, endWindow) {
        //url = "v1/api/test/orders"
        //method = "POST"
        // startWindow = 29189000
        // endWindow = 29199000
        const resp = await request({
            url: '/api/fetchIpLevelApiCallStats',
            method: 'post',
            data: {apiCollectionId, url, method, startWindow, endWindow }
        })
        return resp
    },

    async checkIfDependencyGraphAvailable(apiCollectionId, url, method) {
        return await request({
            url: '/api/checkIfDependencyGraphAvailable',
            method: 'post',
            data: {
                apiCollectionId, url, method
            }
        })
    },

    async editCollectionName(apiCollectionId, collectionName) {
        return await request({
            url: '/api/editCollectionName',
            method: 'post',
            data: {
                apiCollectionId, collectionName
            }
        })
    },

    async getSeveritiesCountPerCollection(apiCollectionId) {
        return await request({
            url: '/api/getSeveritiesCountPerCollection',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    
    async saveCollectionDescription(apiCollectionId, description) {
        return await request({
            url: '/api/saveCollectionDescription',
            method: 'post',
            data: {
                apiCollectionId, description
            }
        })
    },

    async findSvcToSvcGraphEdges() {
        return await request({
            url: '/api/findSvcToSvcGraphEdges',
            method: 'post',
            data: {
                startTimestamp: 0,
                endTimestamp: 0,
                skip: 0,
                limit: 1000
            }
        })
    },

    async findSvcToSvcGraphNodes() {
        return await request({
            url: '/api/findSvcToSvcGraphNodes',
            method: 'post',
            data: {
                startTimestamp: 0,
                endTimestamp: 0,
                skip: 0,
                limit: 1000
            }
        })
    },
    allApisTestedRanges() {
        return request({
            url: '/api/fetchTestedApisRanges',
            method: 'post',
            data: {}
        })
    },

    async getApiSequences(apiCollectionId) {
        const resp = await request({
            url: '/api/getApiSequences',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
        return resp
    },

    async fetchMcpToolsApiCalls(apiCollectionId) {
        const resp = await request({
            url: '/api/fetchMcpToolsApiCalls',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        })
        return resp
    }

}
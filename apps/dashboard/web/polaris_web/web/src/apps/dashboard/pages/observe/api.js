import request from "../../../../util/request"


export default {
    async fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, sensitive, isRequest) {
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
                request: isRequest
            }
        })
        return resp.response.data
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
    askAi(data) {
        return request({
            url: '/api/ask_ai',
            method: 'post',
            data: data
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
    exportToPostman(apiCollectionId) {
        return request({
            url: '/api/createPostmanApi',
            method: 'post',
            data: {
                apiCollectionId
            }
        })
    },
    fetchAPICollection(apiCollectionId) {
        return request({
            url: '/api/fetchAPICollection',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId,
                useHost: !!window.useHost
            }
        }).then((resp) => {
            return resp
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
    async loadRecentEndpoints (startTimestamp, endTimestamp) {
        const resp = await request({
            url: '/api/loadRecentEndpoints',
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
    scheduleTestForCollection(apiCollectionId, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: { apiCollectionId, type: "COLLECTION_WISE", startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl }
        }).then((resp) => {
            return resp
        })
    },
    scheduleTestForCustomEndpoints(apiInfoKeyList, startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source) {
        return request({
            url: '/api/startTest',
            method: 'post',
            data: {apiInfoKeyList, type: "CUSTOM", startTimestamp, recurringDaily, selectedTests, testName, testRunTime, maxConcurrentRequests, overriddenTestAppUrl, source}
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
}
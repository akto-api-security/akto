import request from '@/util/request'

export default {
    askAi(data){
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
    downloadOpenApiFile(apiCollectionId,lastFetchedUrl, lastFetchedMethod) {
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
    fetchAPICollection (apiCollectionId) {
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

    fetchAllUrlsAndMethods (apiCollectionId) {
        return request({
            url: '/api/fetchAllUrlsAndMethods',
            method: 'post',
            data: {
                apiCollectionId: apiCollectionId
            }
        }).then((resp) => {
            return resp
        })
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
    listAllSensitiveFields () {
        return request({
            url: 'api/listAllSensitiveFields',
            method: 'post',
            data: {}
        })
    },
    loadRecentEndpoints (startTimestamp, endTimestamp) {
        return request({
            url: '/api/loadRecentEndpoints',
            method: 'post',
            data: {startTimestamp, endTimestamp}
        }).then((resp) => {
            return resp
        })
    },
    fetchSensitiveParamsForEndpoints (urls) {
        return request({
            url: '/api/fetchSensitiveParamsForEndpoints',
            method: 'post',
            data: {urls}
        }).then((resp) => {
            return resp
        })
    },
    loadSensitiveParameters (apiCollectionId, url, method, subType) {
        return request({
            url: '/api/loadSensitiveParameters',
            method: 'post',
            data: {
                apiCollectionId,
                url, 
                method,
                subType
            }
        }).then((resp) => {
            return resp
        })
    },
    loadParamsOfEndpoint (apiCollectionId, url, method) {
        return request({
            url: '/api/loadParamsOfEndpoint',
            method: 'post',
            data: {
                apiCollectionId,
                url,
                method
            }
        }).then((resp) => {
            return resp
        })
    },
    fetchEndpointTrafficData (url, apiCollectionId, method, startEpoch, endEpoch) {
        return request({
            url: '/api/fetchEndpointTrafficData',
            method: 'post',
            data: {
                url, apiCollectionId, method, startEpoch, endEpoch
            }
        }).then((resp) => {
            return resp
        })
    },
    fetchSampleData (url, apiCollectionId, method) {
        return request({
            url: '/api/fetchSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        }).then((resp) => {
            return resp
        })
    },

    fetchSensitiveSampleData(url, apiCollectionId, method) {
        return request({
            url: '/api/fetchSensitiveSampleData',
            method: 'post',
            data: {
                url, apiCollectionId, method
            }
        }).then((resp) => {
            return resp
        })
    },

    fetchApiInfoList(apiCollectionId) {
        return request({
            url: '/api/fetchApiInfoList',
            method: 'post',
            data: {
                apiCollectionId
            }
        }).then((resp) => {
            return resp
        })
    },
    fetchFilters() {
        return request({
            url: '/api/fetchFilters',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    convertSampleDataToCurl(sampleData) {
        return request({
            url: '/api/convertSampleDataToCurl',
            method: 'post',
            data: {sampleData}
        }).then((resp) => {
            return resp
        })
    },
    convertSampleDataToBurpRequest(sampleData) {
        return request({
            url: '/api/convertSamleDataToBurpRequest',
            method: 'post',
            data: {sampleData}
        }).then((resp) => {
            return resp
        })
    },

    fetchDataTypeNames() {
        return request({
            url: '/api/fetchDataTypeNames',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
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

    setFalsePositives (falsePositives) {
        return request({
            url: '/api/setFalsePositives',
            method: 'post',
            data: {falsePositives:falsePositives}
        }).then((resp) => {
            return resp
        })
    },
    fetchAktoGptConfig(apiCollectionId){
        return request({
            url: '/api/fetchAktoGptConfig',
            method: 'post',
            data: {apiCollectionId}
        }).then((resp) => {
            return resp
        })
    },
    deMergeApi(apiCollectionId, url, method){
        return request({
            url: '/api/deMergeApi',
            method: 'post',
            data: {apiCollectionId, url, method}
        }).then((resp) => {
            return resp
        })
    }

}
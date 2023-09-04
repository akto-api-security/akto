import request from "@/util/request"

export default {
    fetchWorkflowTestingRun(workflowId) {
        return request({
            url: '/api/fetchWorkflowTestingRun',
            method: 'post',
            data: {
                "workflowTestId" : workflowId
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
    deleteScheduledWorkflowTests(workflowId) {
        return request({
            url: '/api/deleteScheduledWorkflowTests',
            method: 'post',
            data: {
                "workflowTestId" : workflowId
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
    fetchWorkflowResult(id) {
        return request({
            url: '/api/fetchWorkflowResult',
            method: 'post',
            data: {
                "workflowTestId": id,
            }
        })
    },

}
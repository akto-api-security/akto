import request from "@/util/request"

const testEditorRequests = {
    fetchSampleData: async (collectionId, apiEndpointUrl, apiEndpointMethod) => {
        const res = await request({
            url: '/api/fetchSampleData',
            method: 'post',
            data: {
                apiCollectionId: collectionId, 
                url: apiEndpointUrl, 
                method: apiEndpointMethod
            }
        })
        return res
    },
    fetchAllSubCategories: async function () {
        return request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: {}
        })
    },
    
    runTestForTemplate: async function (content, apiInfoKey, sampleDataList) {
        return request({
            url: '/api/runTestForGivenTemplate',
            method: 'post',
            data:{content, apiInfoKey, sampleDataList}
        })
    },

    addTestTemplate: async function (content,originalTestId) {
        return request({
            url: '/api/saveTestEditorFile',
            method: 'post',
            data:{content, originalTestId}
        }).then((resp) => {
            return resp
        })
    }
}

export default testEditorRequests
import request from "@/util/request"

const testEditorRequests = {
    fetchSampleData(collectionId, apiEndpointUrl, apiEndpointMethod) {
        return request({
            url: '/api/fetchSampleData',
            method: 'post',
            data: {
                apiCollectionId: collectionId, 
                url: apiEndpointUrl, 
                method: apiEndpointMethod
            }
        })
    },
    fetchAllSubCategories() {
        return request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: {}
        })
    },
    
    runTestForTemplate(content, apiInfoKey, sampleDataList) {
        return request({
            url: '/api/runTestForGivenTemplate',
            method: 'post',
            data:{content, apiInfoKey, sampleDataList}
        })
    },

    addTestTemplate(content,originalTestId) {
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
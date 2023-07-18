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
}

export default testEditorRequests
import request from "@/util/request"

const testEditorRequests = {
    fetchAllSubCategories: async function () {
        return request({
            url: 'api/fetchAllSubCategories',
            method: 'post',
            data: {}
        })
    }
}

export default testEditorRequests
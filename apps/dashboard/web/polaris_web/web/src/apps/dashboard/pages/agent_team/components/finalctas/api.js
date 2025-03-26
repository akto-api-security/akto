import request from "@/util/request";
const api = {
    createSensitiveResponseDataTypes: async (data) => {
        return await request({
            url: '/api/createSensitiveResponseDataTypes',
            method: 'post',
            data: data 
        })
    },

    getSourceCodeCollectionsForDirectories: async (data) => {
        return await request({
            url: '/api/getSourceCodeCollectionsForDirectories',
            method: 'post',
            data: data 
        })
    },

    saveVulnerabilities: async (data) => {
        return await request({
            url: '/api/saveVulnerabilities',
            method: 'post',
            data: data 
        })
    }
}

export default api;
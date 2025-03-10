import request from "@/util/request";
const api = {
    getAgentModels: async () => {
        return await request({
            url: '/api/getAgentModels',
            method: 'post',
            data: {}
        })
    },
    getMemberAgents: async () => {
        return await request({
            url: '/api/getMemberAgents',
            method: 'post',
            data: {}
        })
    },

    createAgentRun: async (data) => {
        return await request({
            url: '/api/createAgentRun',
            method: 'post',
            data: data 
        })
    },

    getAllSubProcesses: async (data) => {
        return await request({
            url: '/api/getAllSubProcesses',
            method: 'post',
            data: data 
        })
    },

    getSubProcess: async (data) => {
        return await request({
            url: '/api/getSubProcess',
            method: 'post',
            data: data 
        })
    },

    updateAgentSubprocess: async (data) => {
        return await request({
            url: '/api/updateAgentSubprocess',
            method: 'post',
            data:  data 
        })
    },

    getAllAgentRuns: async () => {
        return await request({
            url: '/api/getAllAgentRuns',
            method: 'post',
            data: {}
        })
    }
}

export default api;
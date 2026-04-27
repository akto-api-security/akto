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

    getAllAgentRuns: async (agent) => {
        return await request({
            url: '/api/getAllAgentRuns',
            method: 'post',
            data: {agent}
        })
    },

    updateAgentRun: async (data) => {
        return await request({
            url: '/api/updateAgentRun',
            method: 'post',
            data: data 
        })
    },
    getAllAgentRunsObject: async (agent) => {
        return await request({
            url: '/api/getAllAgentRunsObject',
            method: 'post',
            data: {agent} 
        })
    },
    checkAgentRunModule: async (data) => {
        return await request({
            url: '/api/checkAgentRunModule',
            method: 'post',
            data: data 
        })
    },
    saveAgentModel: async (data) => {
        return await request({
            url: '/api/saveAgentModel',
            method: 'post',
            data: data 
        })
    },
    deleteAgentModel: async (data) => {
        return await request({
            url: '/api/deleteAgentModel',
            method: 'post',
            data: data 
        })
    },
    createDiscoveryAgentRunSubprocess: async (data) => {
        return await request({
            url: '/api/createSubProcessNew',
            method: 'post',
            data: data 
        })
    },
    deleteAgentRun: async (data) => {
        return await request({
            url: '/api/deleteAgentRun',
            method: 'post',
            data: data 
        })
    }

}

export default api;
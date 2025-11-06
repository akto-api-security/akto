import request from "@/util/request"

const promptHardeningApi = {
    fetchAllPrompts: async () => {
        return await request({
            url: '/api/fetchAllPrompts',
            method: 'post',
            data: {}
        })
    },

    savePrompt: async (templateId, content, category, inactive) => {
        return await request({
            url: '/api/savePrompt',
            method: 'post',
            data: { templateId, content, category, inactive }
        })
    },

    deletePrompt: async (templateId) => {
        return await request({
            url: '/api/deletePrompt',
            method: 'post',
            data: { templateId },
        })
    },

    togglePromptStatus: async (templateId) => {
        return await request({
            url: '/api/togglePromptStatus',
            method: 'post',
            data: { templateId }
        })
    }
}

export default promptHardeningApi


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
    },

    testSystemPrompt: async (systemPrompt, userInput, attackPatterns = null, detectionRules = null) => {
        // Build request data, only including non-null optional fields
        const data = { 
            systemPrompt, 
            userInput
        }
        
        // Only include attackPatterns if it's not null and has items
        if (attackPatterns && attackPatterns.length > 0) {
            data.attackPatterns = attackPatterns
        }
        
        // Only include detectionRules if it's not null
        // Frontend parses YAML using js-yaml and sends structured data
        if (detectionRules) {
            data.detectionRules = detectionRules
        }
        
        return await request({
            url: '/api/testSystemPrompt',
            method: 'post',
            data
        })
    },

    generateMaliciousUserInput: async (attackPatterns) => {
        return await request({
            url: '/api/generateMaliciousUserInput',
            method: 'post',
            data: { attackPatterns }
        })
    },

    hardenSystemPrompt: async (systemPrompt, vulnerabilityContext = null) => {
        const data = { systemPrompt }
        
        if (vulnerabilityContext) {
            data.vulnerabilityContext = vulnerabilityContext
        }
        
        return await request({
            url: '/api/hardenSystemPrompt',
            method: 'post',
            data
        })
    }
}

export default promptHardeningApi


import request from "@/util/request"

export const sendQuery = async (query, conversationId, metaData) => {
    return await request({
        url: '/api/chatAndStore',
        method: 'post',
        data: {
            message: query,
            ...(conversationId && { conversationId }),
            ...(metaData && { metaData })
        }
    })
};

export const clearConversationFromLocal = async (conversationId) => {
    try {
        await request({
            url: '/api/deleteConversationHistory',
            method: 'post',
            data: {conversationId}
        })
    } catch (error) {
        console.error('Error clearing conversation from localStorage:', error);
    }
};

export const getConversationsList = async (limit = 10, searchQuery = "") => {
    return await request({
        url: '/api/fetchHistory',
        method: 'post',
        data: {limit, searchQuery}
    })
};

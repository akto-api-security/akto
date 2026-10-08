import request from "@/util/request"

export const sendQuery = async (query, conversationId, conversationType, metaData) => {
    return await request({
        url: '/api/chatAndStore',
        method: 'post',
        data: {
            conversationType: conversationType || "ASK_AKTO",
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

// History list: titles and timestamps only, without messages
export const getConversationsList = async (limit = 10, searchQuery = "") => {
    return await request({
        url: '/api/fetchHistory',
        method: 'post',
        data: {limit, searchQuery, includeMessages: false}
    })
};

// Single conversation with all turns, oldest first; null if not found
export const getConversationById = async (conversationId) => {
    const res = await request({
        url: '/api/fetchHistory',
        method: 'post',
        data: {conversationId}
    })
    return res?.history?.[0] || null;
};

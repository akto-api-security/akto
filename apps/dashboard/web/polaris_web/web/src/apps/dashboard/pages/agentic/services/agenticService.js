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

// History list: one row per conversation (title, user, type, counts), without messages. Response has total for pagination
export const getConversationsList = async (limit = 10, searchQuery = "", skip = 0, userIds = []) => {
    return await request({
        url: '/api/fetchHistory',
        method: 'post',
        data: {limit, skip, searchQuery, userIds, includeMessages: false}
    })
};

// Users who have conversations in the current dashboard, as [{id, email}], to fill the user filter
export const getConversationUsers = async () => {
    const res = await request({
        url: '/api/fetchHistory',
        method: 'post',
        data: {limit: 1, includeMessages: false, includeUsers: true}
    })
    return res?.users || [];
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

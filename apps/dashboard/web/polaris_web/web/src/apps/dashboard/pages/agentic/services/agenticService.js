import request from "@/util/request"

export const sendQuery = async (query, conversationId) => {
    return await request({
        url: '/api/chatAndStore',
        method: 'post',
        data: {
            conversationType: "ASK_AKTO",
            message: query,
            ...(conversationId && { conversationId })
        }
    })
};

export const clearConversationFromLocal = (conversationId) => {
    try {
        const key = `agentic_conversation_${conversationId}`;
        localStorage.removeItem(key);
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

import request from "@/util/request"

export const sendQuery = async (query, conversationId, conversationType) => {
    return await request({
        url: '/api/chatAndStoreConversation',
        method: 'post',
        data: {conversationId, conversationType, message: query}
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

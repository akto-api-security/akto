import request from "@/util/request"
const API_BASE_URL = '/api/agentic'; // Update with your actual API endpoint

export const createConversation = async (initialQuery) => {
    try {
        const response = await fetch(`${API_BASE_URL}/conversations`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ initialQuery }),
        });

        if (!response.ok) {
            throw new Error('Failed to create conversation');
        }

        const data = await response.json();
        return data.conversationId;
    } catch (error) {
        console.error('Error creating conversation:', error);
        throw error;
    }
};

/**
 * Send a query to the AI
 * @param {string} conversationId - The conversation ID
 * @param {string} query - User's query
 * @returns {Promise<Object>} - Query result with metadata
 */
export const sendQuery = async (conversationId, query) => {
    try {
        const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/query`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ query }),
        });

        if (!response.ok) {
            throw new Error('Failed to send query');
        }

        return await response.json();
    } catch (error) {
        console.error('Error sending query:', error);
        throw error;
    }
};

/**
 * Stream thinking items from the AI
 * @param {string} conversationId - The conversation ID
 * @param {Function} onThinkingItem - Callback for each thinking item
 * @param {Function} onComplete - Callback when thinking is complete
 * @param {Function} onError - Callback for errors
 */
export const streamThinkingItems = async (conversationId, onThinkingItem, onComplete, onError) => {
    try {
        const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/thinking`, {
            method: 'GET',
            headers: {
                'Accept': 'text/event-stream',
            },
        });

        if (!response.ok) {
            throw new Error('Failed to stream thinking items');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();

        while (true) {
            const { done, value } = await reader.read();

            if (done) {
                onComplete();
                break;
            }

            const chunk = decoder.decode(value);
            const lines = chunk.split('\n').filter(line => line.trim());

            for (const line of lines) {
                if (line.startsWith('data: ')) {
                    const data = JSON.parse(line.slice(6));
                    onThinkingItem(data.content);
                }
            }
        }
    } catch (error) {
        console.error('Error streaming thinking items:', error);
        onError(error);
    }
};

/**
 * Stream response content from the AI
 * @param {string} conversationId - The conversation ID
 * @param {Function} onChunk - Callback for each content chunk
 * @param {Function} onComplete - Callback when streaming is complete
 * @param {Function} onError - Callback for errors
 * @returns {Promise<Object>} - Complete response data
 */
export const streamResponse = async (conversationId, onChunk, onComplete, onError) => {
    try {
        const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/response`, {
            method: 'GET',
            headers: {
                'Accept': 'text/event-stream',
            },
        });

        if (!response.ok) {
            throw new Error('Failed to stream response');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        const startTime = Date.now();

        while (true) {
            const { done, value } = await reader.read();

            if (done) {
                const duration = Math.round((Date.now() - startTime) / 1000);
                const completeResponse = {
                    timeTaken: duration,
                    timestamp: new Date().toISOString()
                };
                onComplete(completeResponse);
                return completeResponse;
            }

            const chunk = decoder.decode(value);
            const lines = chunk.split('\n').filter(line => line.trim());

            for (const line of lines) {
                if (line.startsWith('data: ')) {
                    const data = JSON.parse(line.slice(6));
                    onChunk(data);
                }
            }
        }
    } catch (error) {
        console.error('Error streaming response:', error);
        onError(error);
        throw error;
    }
};

/**
 * Get follow-up suggestions
 * @param {string} conversationId - The conversation ID
 * @returns {Promise<Array>} - Array of suggestion strings
 */
export const getSuggestions = async (conversationId) => {
    try {
        const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/suggestions`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        });

        if (!response.ok) {
            throw new Error('Failed to get suggestions');
        }

        const data = await response.json();
        return data.suggestions;
    } catch (error) {
        console.error('Error getting suggestions:', error);
        throw error;
    }
};

/**
 * Get conversation history
 * @param {string} conversationId - The conversation ID
 * @returns {Promise<Array>} - Array of messages
 */
export const getConversationHistory = async (conversationId) => {
    try {
        const response = await fetch(`${API_BASE_URL}/conversations/${conversationId}/history`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        });

        if (!response.ok) {
            throw new Error('Failed to get conversation history');
        }

        return await response.json();
    } catch (error) {
        console.error('Error getting conversation history:', error);
        throw error;
    }
};

/**
 * Save conversation to localStorage for persistence
 * @param {string} conversationId - The conversation ID
 * @param {Array} messages - Array of messages
 */
export const saveConversationToLocal = (conversationId, messages) => {
    try {
        const key = `agentic_conversation_${conversationId}`;
        localStorage.setItem(key, JSON.stringify({
            conversationId,
            messages,
            lastUpdated: new Date().toISOString()
        }));
    } catch (error) {
        console.error('Error saving conversation to localStorage:', error);
    }
};

/**
 * Clear conversation from localStorage
 * @param {string} conversationId - The conversation ID
 */
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

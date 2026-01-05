/**
 * Agentic AI Service - Simplified for AI SDK
 *
 * Now that we use AI SDK for all chat/streaming functionality,
 * this service only handles utility functions like conversation IDs
 * and history management.
 */

const API_BASE_URL = '/api/agentic';
const USE_MOCK_DATA = false; // Set to true to use localStorage mock data

/**
 * Generate a unique conversation ID
 */
export const generateConversationId = () => {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
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

/**
 * Get list of recent conversations for history
 * @param {number} limit - Maximum number of conversations to return
 * @returns {Promise<Array>} - Array of conversation summaries
 */
export const getConversationsList = async (limit = 10) => {
    if (USE_MOCK_DATA) {
        // Load from localStorage
        try {
            const conversations = [];

            // Scan localStorage for conversation keys
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key && key.startsWith('agentic_conversation_')) {
                    try {
                        const data = JSON.parse(localStorage.getItem(key));
                        if (data && data.messages && data.messages.length > 0) {
                            const firstMessage = data.messages[0];
                            const lastUpdated = new Date(data.lastUpdated);
                            const now = new Date();
                            const diffTime = Math.abs(now - lastUpdated);
                            const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
                            const diffHours = Math.floor(diffTime / (1000 * 60 * 60));
                            const diffMinutes = Math.floor(diffTime / (1000 * 60));
                            const diffSeconds = Math.floor(diffTime / 1000);

                            let timeAgo;
                            if (diffDays > 0) {
                                timeAgo = `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
                            } else if (diffHours > 0) {
                                timeAgo = `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
                            } else if (diffMinutes > 0) {
                                timeAgo = `${diffMinutes} min${diffMinutes > 1 ? 's' : ''} ago`;
                            } else if (diffSeconds > 10) {
                                timeAgo = `${diffSeconds} sec${diffSeconds > 1 ? 's' : ''} ago`;
                            } else {
                                timeAgo = 'Just now';
                            }

                            conversations.push({
                                id: data.conversationId,
                                title: firstMessage.content,
                                time: timeAgo,
                                timestamp: lastUpdated.getTime(),
                                messageCount: data.messages.length
                            });
                        }
                    } catch (err) {
                        console.error('Error parsing conversation:', err);
                    }
                }
            }

            // Sort by timestamp (most recent first) and limit
            conversations.sort((a, b) => b.timestamp - a.timestamp);
            return conversations.slice(0, limit);
        } catch (error) {
            console.error('Error loading conversations from localStorage:', error);
            return [];
        }
    }

    // Load from API
    try {
        const response = await fetch(`${API_BASE_URL}/conversations?limit=${limit}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        });

        if (!response.ok) {
            throw new Error('Failed to get conversations list');
        }

        return await response.json();
    } catch (error) {
        console.error('Error getting conversations list:', error);
        return [];
    }
};

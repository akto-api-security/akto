// Agentic AI Service - Handles all API communication
// This service can be configured to use mock data or real API endpoints

import {
    getMockThinkingItems,
    getMockResponseContent,
    getMockSuggestions,
    mockStreamThinkingItems,
    mockStreamResponse,
    mockDelay
} from './mockData';

// Configuration
const USE_MOCK_DATA = true; // Set to false when real API is ready
const API_BASE_URL = '/api/agentic'; // Update with your actual API endpoint

/**
 * Generate a unique conversation ID
 */
export const generateConversationId = () => {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
};

/**
 * Generate a unique message ID
 */
export const generateMessageId = () => {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
};

/**
 * Create a new conversation
 * @param {string} initialQuery - The first user query
 * @returns {Promise<string>} - Conversation ID
 */
export const createConversation = async (initialQuery) => {
    if (USE_MOCK_DATA) {
        await mockDelay(100);
        return generateConversationId();
    }

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
    if (USE_MOCK_DATA) {
        await mockDelay(500);
        return {
            success: true,
            messageId: generateMessageId(),
            timestamp: new Date().toISOString()
        };
    }

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
    if (USE_MOCK_DATA) {
        try {
            await mockStreamThinkingItems(onThinkingItem, 300);
            onComplete();
        } catch (error) {
            onError(error);
        }
        return;
    }

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
    if (USE_MOCK_DATA) {
        try {
            const startTime = Date.now();
            await mockStreamResponse(onChunk, 200);
            const duration = Math.round((Date.now() - startTime) / 1000);
            const completeResponse = {
                content: getMockResponseContent(),
                timeTaken: duration,
                timestamp: new Date().toISOString()
            };
            onComplete(completeResponse);
            return completeResponse;
        } catch (error) {
            onError(error);
            throw error;
        }
    }

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
    if (USE_MOCK_DATA) {
        await mockDelay(300);
        return getMockSuggestions();
    }

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
    if (USE_MOCK_DATA) {
        // Mock doesn't store history, return empty
        return [];
    }

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
 * Load conversation from localStorage
 * @param {string} conversationId - The conversation ID
 * @returns {Object|null} - Conversation data or null
 */
export const loadConversationFromLocal = (conversationId) => {
    try {
        const key = `agentic_conversation_${conversationId}`;
        const data = localStorage.getItem(key);
        return data ? JSON.parse(data) : null;
    } catch (error) {
        console.error('Error loading conversation from localStorage:', error);
        return null;
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

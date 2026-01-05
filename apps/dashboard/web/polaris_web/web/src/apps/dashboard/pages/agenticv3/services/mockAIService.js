/**
 * Mock AI Service - Simulates streaming AI responses
 * No real API calls - everything is mocked for development
 */

// Mock response templates
const MOCK_RESPONSES = {
    'vulnerabilities': {
        title: 'Security Vulnerabilities Found',
        sections: [
            {
                header: 'Critical Issues',
                items: [
                    'SQL Injection vulnerability in /api/users endpoint',
                    'Cross-Site Scripting (XSS) in search functionality',
                    'Missing authentication on /api/admin endpoints'
                ]
            },
            {
                header: 'Recommendations',
                items: [
                    'Implement parameterized queries for database operations',
                    'Add input sanitization and output encoding',
                    'Enable authentication middleware on all protected routes'
                ]
            }
        ]
    },
    'api-inventory': {
        title: 'API Inventory Summary',
        sections: [
            {
                header: 'Discovered Endpoints',
                items: [
                    'GET /api/users - Returns user list',
                    'POST /api/auth/login - User authentication',
                    'PUT /api/profile - Update user profile',
                    'DELETE /api/users/:id - Remove user'
                ]
            },
            {
                header: 'Statistics',
                items: [
                    'Total APIs: 47',
                    'Authenticated: 32',
                    'Public: 15',
                    'Last updated: 2 hours ago'
                ]
            }
        ]
    },
    'default': {
        title: 'Analysis Complete',
        sections: [
            {
                header: 'Results',
                items: [
                    'Analyzed your security posture',
                    'Found 3 critical issues',
                    'Generated 5 recommendations'
                ]
            },
            {
                header: 'Next Steps',
                items: [
                    'Review the findings in detail',
                    'Prioritize critical vulnerabilities',
                    'Schedule remediation tasks'
                ]
            }
        ]
    }
};

// Mock suggestions
const MOCK_SUGGESTIONS = [
    'Show me more details about SQL injection',
    'What are the affected endpoints?',
    'How do I fix these vulnerabilities?',
    'Generate a security report'
];

/**
 * Simulates streaming AI response
 * @param {string} userMessage - The user's message
 * @param {Function} onChunk - Callback for each chunk
 * @param {Function} onComplete - Callback when complete
 * @param {Function} onError - Callback on error
 */
export const streamMockResponse = async (userMessage, onChunk, onComplete, onError) => {
    try {
        // Determine response based on message content
        let response = MOCK_RESPONSES.default;

        if (userMessage.toLowerCase().includes('vulnerabilit')) {
            response = MOCK_RESPONSES.vulnerabilities;
        } else if (userMessage.toLowerCase().includes('api') || userMessage.toLowerCase().includes('endpoint')) {
            response = MOCK_RESPONSES['api-inventory'];
        }

        // Simulate streaming by sending response in chunks
        const responseStr = JSON.stringify(response);
        const chunkSize = 10; // Characters per chunk
        const delayMs = 50; // Delay between chunks

        for (let i = 0; i < responseStr.length; i += chunkSize) {
            const chunk = responseStr.slice(i, i + chunkSize);
            onChunk(chunk);

            // Simulate network delay
            await new Promise(resolve => setTimeout(resolve, delayMs));
        }

        // Complete the stream
        onComplete(response);
    } catch (error) {
        onError(error);
    }
};

/**
 * Get mock suggestions for follow-up questions
 */
export const getMockSuggestions = () => {
    return Promise.resolve(MOCK_SUGGESTIONS);
};

/**
 * Generate a unique conversation ID
 */
export const generateConversationId = () => {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
};

/**
 * Mock save conversation (no-op for now)
 */
export const saveConversation = (conversationId, messages) => {
    console.log('Mock: Saving conversation', conversationId, messages);
    return Promise.resolve({ success: true });
};

/**
 * Mock load conversation (returns empty for now)
 */
export const loadConversation = (conversationId) => {
    console.log('Mock: Loading conversation', conversationId);
    return Promise.resolve({ messages: [] });
};

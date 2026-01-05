/**
 * Mock AI Adapter - Simplified for AI SDK
 *
 * Provides mock streaming responses compatible with AI SDK's useChat hook.
 * Switch USE_MOCK_DATA to false when your real backend is ready.
 */

import { getMockResponseContent, mockDelay } from './mockData';

/**
 * Configuration
 */
const USE_MOCK_DATA = true; // Set to false when real API is ready

/**
 * Mock streaming response handler for AI SDK
 *
 * AI SDK expects streaming text responses. This creates a compatible Response
 * object that streams mock data in the format AI SDK expects.
 */
export async function handleMockChatRequest(request) {
    const { messages, conversationId } = await request.json();

    if (!USE_MOCK_DATA) {
        // Forward to real API
        return fetch('/api/agentic/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ messages, conversationId }),
        });
    }

    // Create AI SDK compatible streaming response
    const stream = new ReadableStream({
        async start(controller) {
            const encoder = new TextEncoder();

            try {
                // Get mock response
                const mockResponse = getMockResponseContent();

                // Stream as JSON (simplest approach)
                // Your parseMessageContent will parse this on the frontend
                const jsonResponse = JSON.stringify(mockResponse);

                // Simulate streaming by chunking the response
                const chunkSize = 20;
                for (let i = 0; i < jsonResponse.length; i += chunkSize) {
                    await mockDelay(50); // Simulate network delay
                    const chunk = jsonResponse.slice(i, i + chunkSize);
                    controller.enqueue(encoder.encode(chunk));
                }

                controller.close();
            } catch (error) {
                controller.error(error);
            }
        },
    });

    // Return Response with conversation ID header
    return new Response(stream, {
        headers: {
            'Content-Type': 'text/plain; charset=utf-8',
            'X-Conversation-Id': conversationId || `conv_mock_${Date.now()}`,
        },
    });
}

/**
 * Setup mock fetch interceptor
 *
 * Intercepts /api/agentic/chat calls and returns mock data.
 * Call once during app initialization.
 */
export function setupMockAIAdapter() {
    if (!USE_MOCK_DATA) {
        console.log('[Mock AI Adapter] Disabled - using real API');
        return;
    }

    const originalFetch = window.fetch;

    // Intercept chat endpoint calls
    window.fetch = async function (url, options = {}) {
        if (typeof url === 'string' && url.includes('/api/agentic/chat')) {
            console.log('[Mock AI Adapter] Intercepted chat request');
            const mockRequest = new Request(url, options);
            return handleMockChatRequest(mockRequest);
        }

        // Pass through all other requests
        return originalFetch(url, options);
    };

    console.log('[Mock AI Adapter] Initialized successfully');
}

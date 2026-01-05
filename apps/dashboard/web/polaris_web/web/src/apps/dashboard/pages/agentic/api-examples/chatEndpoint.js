/**
 * Backend API Endpoint Example for AI SDK Integration
 *
 * This file shows how to create a streaming chat endpoint that works with the AI SDK.
 * You'll need to implement this in your actual backend (Java/Node.js/etc.)
 *
 * The AI SDK expects responses in Server-Sent Events (SSE) format.
 */

// ============================================================================
// Node.js/Express Example
// ============================================================================

/**
 * Example Express.js route handler
 * Install required packages: npm install ai express
 */
export async function handleChatRequest_Express(req, res) {
    const { messages, conversationId } = req.body;

    // Set headers for Server-Sent Events
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');

    // Optional: Send conversation ID in header
    if (conversationId) {
        res.setHeader('X-Conversation-Id', conversationId);
    }

    try {
        // Get the last user message
        const lastMessage = messages[messages.length - 1];
        const userQuery = lastMessage.content;

        // Step 1: Stream thinking items (optional)
        const thinkingItems = [
            'Analyzing your request...',
            'Searching relevant data...',
            'Processing results...',
        ];

        for (const item of thinkingItems) {
            // Send thinking updates (custom event type)
            res.write(`event: thinking\n`);
            res.write(`data: ${JSON.stringify({ content: item })}\n\n`);
            await sleep(500);
        }

        // Step 2: Stream the main response
        // The AI SDK expects data in the format: data: <content>\n\n

        // Example: Stream response chunks
        const responseChunks = [
            '## Executive Report\n\n',
            '### Security Overview\n',
            '- Risk Level: Medium\n',
            '- Active Threats: 3\n',
            '- Resolved Issues: 12\n',
        ];

        for (const chunk of responseChunks) {
            res.write(`data: ${JSON.stringify({ content: chunk, type: 'text' })}\n\n`);
            await sleep(100);
        }

        // Step 3: Send completion signal
        res.write(`data: [DONE]\n\n`);
        res.end();
    } catch (error) {
        console.error('Error in chat endpoint:', error);
        res.write(`data: ${JSON.stringify({ error: error.message })}\n\n`);
        res.end();
    }
}

// ============================================================================
// Using Vercel AI SDK's StreamingTextResponse (Recommended)
// ============================================================================

import { StreamingTextResponse, streamText } from 'ai';

/**
 * Example using AI SDK's built-in streaming utilities
 * This is the recommended approach as it handles all the SSE formatting for you
 */
export async function handleChatRequest_AISDKFormat(req, res) {
    const { messages, conversationId } = req.body;

    try {
        // Create your custom stream
        const stream = new ReadableStream({
            async start(controller) {
                const encoder = new TextEncoder();

                // Step 1: Send thinking items
                const thinkingItems = [
                    'Analyzing request...',
                    'Fetching data...',
                    'Generating response...',
                ];

                for (const item of thinkingItems) {
                    controller.enqueue(encoder.encode(item + '\n'));
                    await sleep(300);
                }

                // Step 2: Stream main content
                const responseText = `## Executive Report

### Security Overview
- Risk Level: Medium
- Active Threats: 3
- Resolved Issues: 12

### Recommendations
1. Update authentication mechanisms
2. Review API endpoints for vulnerabilities
3. Enable rate limiting on sensitive routes`;

                // Stream word by word or chunk by chunk
                const words = responseText.split(' ');
                for (const word of words) {
                    controller.enqueue(encoder.encode(word + ' '));
                    await sleep(50);
                }

                controller.close();
            },
        });

        // Return streaming response
        return new StreamingTextResponse(stream, {
            headers: {
                'X-Conversation-Id': conversationId || generateConversationId(),
            },
        });
    } catch (error) {
        console.error('Error:', error);
        return new Response(JSON.stringify({ error: error.message }), {
            status: 500,
            headers: { 'Content-Type': 'application/json' },
        });
    }
}

// ============================================================================
// Custom Streaming with Structured Data
// ============================================================================

/**
 * Stream structured data (your custom format) that works with AI SDK
 */
export async function handleChatRequest_CustomFormat(req, res) {
    const { messages, conversationId } = req.body;

    // Set SSE headers
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');

    try {
        const lastMessage = messages[messages.length - 1];

        // Your custom response structure
        const structuredResponse = {
            title: 'Executive Report on Agentic Risks',
            sections: [
                {
                    header: 'Critical Risks',
                    items: [
                        'SQL Injection vulnerabilities in 3 endpoints',
                        'Unvalidated JWT tokens in authentication flow',
                        'Missing rate limiting on public APIs',
                    ],
                },
                {
                    header: 'Recommendations',
                    items: [
                        'Implement parameterized queries',
                        'Add JWT signature verification',
                        'Configure rate limiting middleware',
                    ],
                },
            ],
        };

        // Option 1: Stream the entire object as JSON
        // The AI SDK will receive this as the message content
        const jsonString = JSON.stringify(structuredResponse);
        res.write(`data: ${jsonString}\n\n`);
        res.write(`data: [DONE]\n\n`);
        res.end();

        // Option 2: Stream piece by piece (title, then sections)
        // Uncomment below for gradual streaming:
        /*
        res.write(`data: ${JSON.stringify({ type: 'title', content: structuredResponse.title })}\n\n`);
        await sleep(200);

        for (const section of structuredResponse.sections) {
            res.write(`data: ${JSON.stringify({ type: 'section', content: section })}\n\n`);
            await sleep(300);
        }

        res.write(`data: [DONE]\n\n`);
        res.end();
        */
    } catch (error) {
        console.error('Error:', error);
        res.write(`data: ${JSON.stringify({ error: error.message })}\n\n`);
        res.end();
    }
}

// ============================================================================
// Java Spring Boot Example (Pseudocode)
// ============================================================================

/**
 * Java Spring Boot example for SSE streaming
 *
 * @RestController
 * @RequestMapping("/api/agentic")
 * public class AgenticChatController {
 *
 *     @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 *     public Flux<ServerSentEvent<String>> handleChat(@RequestBody ChatRequest request) {
 *         return Flux.create(emitter -> {
 *             try {
 *                 // Get conversation ID
 *                 String conversationId = request.getConversationId();
 *
 *                 // Stream thinking items
 *                 String[] thinkingItems = {
 *                     "Analyzing request...",
 *                     "Fetching data...",
 *                     "Generating response..."
 *                 };
 *
 *                 for (String item : thinkingItems) {
 *                     emitter.next(
 *                         ServerSentEvent.<String>builder()
 *                             .event("thinking")
 *                             .data(new ObjectMapper().writeValueAsString(Map.of("content", item)))
 *                             .build()
 *                     );
 *                     Thread.sleep(300);
 *                 }
 *
 *                 // Stream main response
 *                 String response = generateResponse(request.getMessages());
 *                 String[] chunks = response.split(" ");
 *
 *                 for (String chunk : chunks) {
 *                     emitter.next(
 *                         ServerSentEvent.<String>builder()
 *                             .data(chunk + " ")
 *                             .build()
 *                     );
 *                     Thread.sleep(50);
 *                 }
 *
 *                 // Send completion
 *                 emitter.next(
 *                     ServerSentEvent.<String>builder()
 *                         .data("[DONE]")
 *                         .build()
 *                 );
 *                 emitter.complete();
 *             } catch (Exception e) {
 *                 emitter.error(e);
 *             }
 *         });
 *     }
 * }
 */

// ============================================================================
// Helper Functions
// ============================================================================

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function generateConversationId() {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

// ============================================================================
// Testing Your Endpoint
// ============================================================================

/**
 * You can test your endpoint with curl:
 *
 * curl -N -X POST http://localhost:3000/api/agentic/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "messages": [
 *       {"role": "user", "content": "Generate a report"}
 *     ],
 *     "conversationId": "conv_test_123"
 *   }'
 *
 * Expected output (SSE format):
 * data: {"content": "Analyzing request..."}
 *
 * data: {"content": "Generating response..."}
 *
 * data: ## Executive Report
 *
 * data: [DONE]
 */

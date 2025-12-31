import React from 'react';
import { Box, VerticalStack } from '@shopify/polaris';
import ChatMessage from './ChatMessage';

function ConversationHistory({ trafficData, conversations, isVulnerable }) {
    // trafficData is expected to be { request: string, response: string, requestTime: number, responseTime: number }

    return (
        <Box paddingBlockStart="4">
            <VerticalStack gap="4">
                {/* Tested Interaction (Traffic) */}
                {trafficData && (
                    <Box>
                        <ChatMessage
                            type="request"
                            content={trafficData.request}
                            timestamp={trafficData.requestTime}
                            isVulnerable={isVulnerable}
                        />
                        <ChatMessage
                            type="response"
                            content={trafficData.response}
                            timestamp={trafficData.responseTime}
                            isVulnerable={isVulnerable}
                        />
                    </Box>
                )}

                {/* Chat History - Rendered as ChatMessage rows */}
                {conversations && conversations.map((msg, index) => {
                    // Skip welcome message if needed, or render all
                    if (msg.role === 'system' && msg.message.includes("You have selected the")) {
                        // Optionally skip generic welcome message if redundancy is an issue
                        // return null;
                    }

                    console.log("Rendering chat message:", msg);

                    const isUser = msg.role === 'user';
                    return (
                        <ChatMessage
                            key={msg._id || index}
                            type={isUser ? 'request' : 'response'}
                            content={msg.message}
                            timestamp={msg.creationTimestamp / 1000} // Convert ms to s if needed by ChatMessage
                            customLabel={isUser ? 'Tested interaction' : 'HR agent response'}
                            isVulnerable={msg.validation} // Chat messages usually not vulnerable unless flagged
                            isCode={false} // Render chat messages as text/markdown, never code
                        />
                    )
                })}
            </VerticalStack>
        </Box>
    );
}

export default ConversationHistory;

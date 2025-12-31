import React from 'react';
import { Box, VerticalStack } from '@shopify/polaris';
import AiAnalysisCard from './components/AiAnalysisCard';
import ConversationHistory from './components/ConversationHistory';

function TestRunResultChat({ analysis, trafficData, conversations, onSendMessage, isStreaming, isVulnerable }) {
    return (
        <Box padding="4" minHeight="500px">
            <VerticalStack gap="4">
                <AiAnalysisCard
                    summary={analysis}
                    onSendMessage={onSendMessage}
                    isStreaming={isStreaming}
                />

                <ConversationHistory
                    trafficData={trafficData}
                    conversations={conversations}
                    isVulnerable={isVulnerable}
                />
            </VerticalStack>
        </Box>
    );
}

export default TestRunResultChat;

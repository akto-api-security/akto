import React from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack } from '@shopify/polaris';
import AiAnalysisCard from './components/AiAnalysisCard';
import ConversationHistory from './components/ConversationHistory';

function TestRunResultChat({ analysis, conversations, onSendMessage, isStreaming }) {
    return (
        <Box padding="4" minHeight="500px">
            <VerticalStack gap="4">
                {/* <AiAnalysisCard
                    summary={analysis}
                    onSendMessage={onSendMessage}
                    isStreaming={isStreaming}
                /> */}

                <ConversationHistory
                    conversations={conversations}
                />
            </VerticalStack>
        </Box>
    );
}

TestRunResultChat.propTypes = {
    analysis: PropTypes.string,
    conversations: PropTypes.array,
    onSendMessage: PropTypes.func,
    isStreaming: PropTypes.bool,
};

TestRunResultChat.defaultProps = {
    analysis: null,
    conversations: [],
    onSendMessage: () => {},
    isStreaming: false,
};

export default TestRunResultChat;

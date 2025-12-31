import React from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack } from '@shopify/polaris';
import ChatMessage from './ChatMessage';
import { MESSAGE_LABELS } from './chatConstants';
import { normalizeTimestamp } from './dateHelpers';

function ConversationHistory({ conversations }) {
    return (
        <Box paddingBlockStart="4">
            <VerticalStack gap="4">
                {/* Chat History - Rendered as ChatMessage rows */}
                {conversations && conversations.map((msg, index) => {
                    const isUser = msg.role === 'user';
                    return (
                        <ChatMessage
                            key={msg._id || index}
                            type={isUser ? 'request' : 'response'}
                            content={msg.message}
                            timestamp={normalizeTimestamp(msg.creationTimestamp) / 1000} // Normalize then convert to seconds for ChatMessage
                            customLabel={isUser ? MESSAGE_LABELS.TESTED_INTERACTION : MESSAGE_LABELS.HR_AGENT_RESPONSE}
                            isVulnerable={msg.validation}
                            isCode={false}
                        />
                    )
                })}
            </VerticalStack>
        </Box>
    );
}

ConversationHistory.propTypes = {
    conversations: PropTypes.arrayOf(PropTypes.shape({
        _id: PropTypes.string,
        role: PropTypes.string.isRequired,
        message: PropTypes.string.isRequired,
        creationTimestamp: PropTypes.number,
        validation: PropTypes.bool,
    })),
};

ConversationHistory.defaultProps = {
    conversations: [],
};

export default ConversationHistory;

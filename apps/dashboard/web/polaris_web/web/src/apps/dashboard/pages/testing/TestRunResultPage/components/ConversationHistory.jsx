import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack } from '@shopify/polaris';
import ChatMessage from './ChatMessage';
import { MESSAGE_LABELS } from './chatConstants';
import ChatInfoModal from './ChatInfoModal';

function ConversationHistory({ conversations, isInventory = false, testResults = [] }) {
    const label = isInventory ? MESSAGE_LABELS.INVENTORY_ANALYSIS : MESSAGE_LABELS.TESTED_INTERACTION;

    const [httpModalOpen, setHttpModalOpen] = useState(false);
    const [httpModalData, setHttpModalData] = useState({ title: '', sampleData: null });

    const handleOpenAttempt = (messageIndex) => {
        // Each conversation round produces 2 messages (user + system) with the same timestamp.
        // testResults[k] corresponds to conversations[2k] and conversations[2k+1].
        const testResultIndex = Math.floor(messageIndex / 2);
        const testResult = testResults[testResultIndex];
        if (!testResult || !testResult.message) return;

        setHttpModalData({
            title: "Attempt",
            sampleData: {
                message: testResult.message,
                originalMessage: testResult.message,
            },
        });
        setHttpModalOpen(true);
    };

    return (
        <Box paddingBlockStart="4">
            <VerticalStack gap="4">
                {/* Chat History - Rendered as ChatMessage rows */}
                {conversations && conversations.map((msg, index) => {
                    const isUser = msg.role === 'user';
                    const testResultIndex = Math.floor(index / 2);
                    const hasAttemptData = !isInventory && testResults.length > 0 && testResults[testResultIndex]?.message;
                    return (
                        <ChatMessage
                            key={msg._id ? `conv-${msg._id}-${index}` : `conv-${index}`}
                            type={isUser ? 'request' : 'response'}
                            content={msg.message}
                            timestamp={msg.creationTimestamp} // Normalize then convert to seconds for ChatMessage
                            customLabel={isInventory ? label : isUser ? MESSAGE_LABELS.TESTED_INTERACTION : MESSAGE_LABELS.AKTO_AI_AGENT_RESPONSE}
                            isVulnerable={msg.validation}
                            isCode={false}
                            onOpenAttempt={hasAttemptData ? () => handleOpenAttempt(index) : null}
                            originalPrompt={msg.originalPrompt}
                        />
                    )
                })}
            </VerticalStack>

            <ChatInfoModal
                open={httpModalOpen}
                onClose={() => setHttpModalOpen(false)}
                title={httpModalData.title}
                type="http"
                sampleData={httpModalData.sampleData}
            />
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
        originalPrompt: PropTypes.string,
    })),
    isInventory: PropTypes.bool,
    testResults: PropTypes.array,
};

ConversationHistory.defaultProps = {
    conversations: [],
    isInventory: false,
    testResults: [],
};

export default ConversationHistory;

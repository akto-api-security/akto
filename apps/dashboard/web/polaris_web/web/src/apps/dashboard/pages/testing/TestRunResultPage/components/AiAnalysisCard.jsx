import React, { useState } from 'react';
import { Box, Text, Icon, TextField, VerticalStack, HorizontalStack, Button } from '@shopify/polaris';
import { ChevronDownMinor, SendMajor } from '@shopify/polaris-icons';
const AktoLogo = '/public/akto.svg';

function AiAnalysisCard({ summary, onSendMessage, isStreaming }) {
    const [inputValue, setInputValue] = useState('');
    const [isExpanded, setIsExpanded] = useState(true);

    const handleSend = () => {
        if (inputValue.trim()) {
            onSendMessage(inputValue);
            setInputValue('');
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter') {
            handleSend();
        }
    };

    return (
        <Box
            background="bg-surface"
            borderRadius="3"
            borderWidth="1"
            borderColor="border"
            padding="4"
            shadow="card"
        >
            <VerticalStack gap="4">
                {/* Header */}
                <div
                    style={{ cursor: 'pointer', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                    onClick={() => setIsExpanded(!isExpanded)}
                >
                    <HorizontalStack gap="2" align="center">
                        <div style={{ height: '20px', width: '20px' }}>
                            <img src={AktoLogo} alt="Akto Logo" style={{ height: '100%', width: '100%', objectFit: 'contain' }} />
                        </div>
                        <Text variant="headingSm" as="h3">Akto AI Overview</Text>
                    </HorizontalStack>
                    <Icon source={ChevronDownMinor} color="subdued" />
                </div>

                {/* Content */}
                {isExpanded && (
                    <VerticalStack gap="4">
                        <Text variant="bodyMd" as="p">
                            {summary || "Analyzing interaction..."}
                        </Text>

                        {/* Action section */}
                        <HorizontalStack gap="3" align="center" blockAlign="center">
                            {!isStreaming && (
                                <div style={{ flex: 1 }}>
                                    <TextField
                                        value={inputValue}
                                        onChange={setInputValue}
                                        placeholder="Ask a follow up..."
                                        autoComplete="off"
                                        connectedRight={
                                            <Button
                                                icon={SendMajor}
                                                disabled={!inputValue.trim()}
                                                onClick={handleSend}
                                                accessibilityLabel="Send"
                                            />
                                        }
                                        onKeyPress={(e) => {
                                            if (e.key === 'Enter') handleSend();
                                        }}
                                    />
                                </div>
                            )}
                        </HorizontalStack>

                        {/* Status */}
                        {isStreaming && (
                            <Text variant="bodySm" color="subdued">
                                Pondering, stand by...
                            </Text>
                        )}
                    </VerticalStack>
                )}
            </VerticalStack>
        </Box>
    );
}

export default AiAnalysisCard;

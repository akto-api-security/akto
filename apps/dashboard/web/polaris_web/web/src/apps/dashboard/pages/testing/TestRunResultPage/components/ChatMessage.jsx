import { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack, HorizontalStack, Text, Badge } from '@shopify/polaris';
import MarkdownViewer from '../../../../components/shared/MarkdownViewer';
import { CHAT_ASSETS, MESSAGE_LABELS, MESSAGE_TYPES, VULNERABILITY_BADGE } from './chatConstants';
import { formatChatTimestamp } from './dateHelpers';

function ChatMessage({ type, content, timestamp, isVulnerable, customLabel, isCode }) {
    const isRequest = type === MESSAGE_TYPES.REQUEST;

    // Icon
    const iconSrc = isRequest ? CHAT_ASSETS.AKTO_LOGO : CHAT_ASSETS.FRAME_LOGO;
    const iconAlt = isRequest ? 'Akto Logo' : 'Agent Logo';

    // Label
    const label = customLabel || (isRequest ? MESSAGE_LABELS.TESTED_INTERACTION : MESSAGE_LABELS.HR_AGENT_RESPONSE);

    // Format timestamp with memoization
    const formattedTime = useMemo(() => formatChatTimestamp(timestamp), [timestamp]);

    // Determine if content should be rendered as code
    const shouldRenderAsCode = isCode !== undefined ? isCode : isRequest;

    return (
        <Box padding="3">
            <Box style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                {/* Icon */}
                <Box style={{ flexShrink: 0, width: '20px', height: '20px' }}>
                    <img
                        src={iconSrc}
                        alt={iconAlt}
                        style={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }}
                    />
                </Box>

                {/* Divider */}
                <Box style={{ width: '2px', flexShrink: 0, alignSelf: 'stretch', backgroundColor: isVulnerable ? '#D72C0D' : '#E1E3E5' }} />

                {/* Content - Takes remaining space */}
                <Box style={{ flex: 1, minWidth: 0 }}>
                    <VerticalStack gap="1">
                        {/* Header */}
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="bodyMd" fontWeight="semibold" color="subdued">
                                {label}
                            </Text>
                            <Text variant="bodySm" color="subdued">{formattedTime}</Text>
                        </HorizontalStack>

                        {/* Message Content */}
                        {shouldRenderAsCode ? (
                            <Box paddingBlockStart="1">
                                <Box style={{
                                    whiteSpace: 'pre-wrap',
                                    fontFamily: 'monospace',
                                    fontSize: '13px',
                                    color: '#202223',
                                    margin: 0
                                }}>
                                    {content}
                                </Box>
                            </Box>
                        ) : (
                            <MarkdownViewer markdown={content} />
                        )}

                        {/* Vulnerability Badge */}
                        {isVulnerable && !isRequest && (
                            <Box paddingBlockStart="2">
                                <Badge status="critical">{VULNERABILITY_BADGE.SYSTEM_PROMPT_LEAK}</Badge>
                            </Box>
                        )}
                    </VerticalStack>
                </Box>
            </Box>
        </Box>
    );
}

ChatMessage.propTypes = {
    type: PropTypes.oneOf([MESSAGE_TYPES.REQUEST, MESSAGE_TYPES.RESPONSE]).isRequired,
    content: PropTypes.string.isRequired,
    timestamp: PropTypes.number,
    isVulnerable: PropTypes.bool,
    customLabel: PropTypes.string,
    isCode: PropTypes.bool,
};

ChatMessage.defaultProps = {
    timestamp: null,
    isVulnerable: false,
    customLabel: null,
    isCode: undefined,
};

export default ChatMessage;

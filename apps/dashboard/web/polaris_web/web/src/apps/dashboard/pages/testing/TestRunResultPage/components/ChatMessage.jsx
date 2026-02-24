import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack, HorizontalStack, Text, Badge, Modal } from '@shopify/polaris';
import { ExternalMinor } from '@shopify/polaris-icons';
import MarkdownViewer from '../../../../components/shared/MarkdownViewer';
import SampleData from '../../../../components/shared/SampleData';
import { CHAT_ASSETS, MESSAGE_LABELS, MESSAGE_TYPES, VULNERABILITY_BADGE } from './chatConstants';
import func from "@/util/func";

function ChatMessage({ type, content, timestamp, isVulnerable, customLabel, isCode }) {
    const isRequest = type === MESSAGE_TYPES.REQUEST;

    // Icon
    const iconSrc = isRequest ? CHAT_ASSETS.AKTO_LOGO : CHAT_ASSETS.BOT_LOGO;
    const iconAlt = isRequest ? 'Akto Logo' : 'Agent Logo';

    // Label
    const label = customLabel || (isRequest ? MESSAGE_LABELS.TESTED_INTERACTION : MESSAGE_LABELS.AKTO_AI_AGENT_RESPONSE);

    // Format timestamp with memoization
    const formattedTime = useMemo(() => func.formatChatTimestamp(timestamp), [timestamp]);

    // Determine if content should be rendered as code
    const shouldRenderAsCode = isCode !== undefined ? isCode : isRequest;

    const [expanded, setExpanded] = useState(false);
    let prettyJson = null;
    if (!shouldRenderAsCode) {
        try { prettyJson = JSON.stringify(JSON.parse(content), null, 2); } catch {}
    }

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
                            <HorizontalStack gap="1" blockAlign="center">
                                <Text variant="bodySm" color="subdued">{formattedTime}</Text>
                                {prettyJson && (
                                    <span onClick={() => setExpanded(true)} title="Expand" style={{ cursor: 'pointer', display: 'flex', color: '#6d7175' }}>
                                        <ExternalMinor width={16} height={16} />
                                    </span>
                                )}
                            </HorizontalStack>
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
                        ) : prettyJson ? (
                            <SampleData key={content} data={{ message: prettyJson }} readOnly={true} editorLanguage="json" minHeight="200px" />
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

            {prettyJson && expanded && (
                <Modal open onClose={() => setExpanded(false)} title={label} large>
                    <Modal.Section>
                        <SampleData key={content + '_modal'} data={{ message: prettyJson }} readOnly={true} editorLanguage="json" minHeight="600px" />
                    </Modal.Section>
                </Modal>
            )}
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

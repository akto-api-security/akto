import { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack, HorizontalStack, Text, Badge } from '@shopify/polaris';
import MarkdownViewer from '../../../../components/shared/MarkdownViewer';
import SampleDataComponent from '../../../../components/shared/SampleDataComponent';
import { CHAT_ASSETS, MESSAGE_LABELS, MESSAGE_TYPES, VULNERABILITY_BADGE } from './chatConstants';
import func from "@/util/func";

// This is done for Hybrid messages -> Markdown + JSON 
function extractPrettyJson(content) {
    try {
        if (!content) {
            return { prettyJson: null, prefix: null, beforeText: null, afterText: null };
        }

        try {
            const parsed = JSON.parse(content);
            // If parsed is empty object or array, treat as plain text
            if (
                (typeof parsed === 'object' && parsed !== null &&
                    ((Array.isArray(parsed) && parsed.length === 0) ||
                     (!Array.isArray(parsed) && Object.keys(parsed).length === 0)))
            ) {
                return {
                    prettyJson: null,
                    prefix: content,
                    beforeText: null,
                    afterText: null,
                };
            }
            return {
                prettyJson: JSON.stringify(parsed, null, 2),
                prefix: null,
                beforeText: null,
                afterText: null,
            };
        } catch {}

        const len = content.length;

        // Scan for first valid embedded JSON block: object ({...}) or array ([...])
        for (let start = 0; start < len; start += 1) {
            const open = content[start];
            if (open !== '{' && open !== '[') {
                continue;
            }

            const close = open === '{' ? '}' : ']';
            let depth = 0;
            let inString = false;
            let escape = false;

            for (let i = start; i < len; i += 1) {
                const ch = content[i];

                if (inString) {
                    if (escape) {
                        escape = false;
                    } else if (ch === '\\') {
                        escape = true;
                    } else if (ch === '"') {
                        inString = false;
                    }
                    continue;
                }

                if (ch === '"') {
                    inString = true;
                    continue;
                }

                if (ch === open) {
                    depth += 1;
                } else if (ch === close) {
                    depth -= 1;

                    if (depth === 0) {
                        const embeddedJson = content.slice(start, i + 1);
                        try {
                            const parsed = JSON.parse(embeddedJson);
                            const before = content.slice(0, start).trim();
                            const after = content.slice(i + 1).trim();
                            // If parsed is empty object or array, treat as plain text
                            if (
                                (typeof parsed === 'object' && parsed !== null &&
                                    ((Array.isArray(parsed) && parsed.length === 0) ||
                                     (!Array.isArray(parsed) && Object.keys(parsed).length === 0)))
                            ) {
                                return {
                                    prettyJson: null,
                                    prefix: embeddedJson,
                                    beforeText: before || null,
                                    afterText: after || null,
                                };
                            }
                            return {
                                prettyJson: JSON.stringify(parsed, null, 2),
                                prefix: null,
                                beforeText: before || null,
                                afterText: after || null,
                            };
                        } catch {
                            break;
                        }
                    } else if (depth < 0) {
                        break;
                    }
                }
            }
        }

        return { prettyJson: null, prefix: content, beforeText: null, afterText: null };
    } catch (err) {
        // Global catch: return fallback
        return { prettyJson: null, prefix: content, beforeText: null, afterText: null };
    }
}

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

    const { prettyJson, prefix, beforeText, afterText } = useMemo(() => {
        if (shouldRenderAsCode) {
            return { prettyJson: null, prefix: null, beforeText: null, afterText: null };
        }
        return extractPrettyJson(content);
    }, [shouldRenderAsCode, content]);

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
                        ) : prettyJson ? (
                            <VerticalStack gap="2">
                                {beforeText && <MarkdownViewer markdown={beforeText} />}
                                <SampleDataComponent
                                    type="response"
                                    sampleData={{ message: prettyJson }}
                                    minHeight="200px"
                                    readOnly={true}
                                    simpleJson={true}
                                />
                                {afterText && <MarkdownViewer markdown={afterText} />}
                            </VerticalStack>
                        ) : (
                            <MarkdownViewer markdown={prefix || content} />
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

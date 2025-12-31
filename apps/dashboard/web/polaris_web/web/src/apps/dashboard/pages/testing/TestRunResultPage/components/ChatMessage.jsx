import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack, HorizontalStack, Text, Badge } from '@shopify/polaris';
import { MarkdownRenderer, markdownStyles } from '../../../../components/shared/MarkdownComponents';
import styles from './ChatMessage.module.css';

// Asset paths
const ASSETS = {
    AKTO_LOGO: '/public/akto.svg',
    FRAME_LOGO: '/public/Frame.svg',
    DIVIDER: '/public/Divider.svg',
    DIVIDER_ALERT: '/public/Divider_alert.svg',
};

// Default labels
const DEFAULT_LABELS = {
    REQUEST: 'Tested interaction',
    RESPONSE: 'HR agent response',
};

// Message types
const MESSAGE_TYPES = {
    REQUEST: 'request',
    RESPONSE: 'response',
};

// Helper to auto-link URLs in markdown text (since remark-gfm is not available)
const autoLinkText = (text) => {
    if (!text) return "";
    // Regex to match URLs that are NOT already part of a markdown link
    // Negative lookbehind (?<!]\() ensures we don't match url in [text](url)
    const urlRegex = /(?<!\]\()(?<!href=")(https?:\/\/[^\s<)]+)/g;
    return text.replace(urlRegex, '[$1]($1)');
};

function ChatMessage({ type, content, timestamp, isVulnerable, customLabel, isCode }) {
    const isRequest = type === MESSAGE_TYPES.REQUEST;

    // Icon
    const iconSrc = isRequest ? ASSETS.AKTO_LOGO : ASSETS.FRAME_LOGO;
    const iconAlt = isRequest ? 'Akto Logo' : 'Agent Logo';

    // Divider
    const dividerSrc = isVulnerable ? ASSETS.DIVIDER_ALERT : ASSETS.DIVIDER;

    // Label
    const label = customLabel || (isRequest ? DEFAULT_LABELS.REQUEST : DEFAULT_LABELS.RESPONSE);

    // Format timestamp with memoization
    const formattedTime = useMemo(() => {
        if (!timestamp) return '';
        return new Date(timestamp * 1000).toLocaleString('en-US', {
            month: 'numeric',
            day: 'numeric',
            year: '2-digit',
            hour: 'numeric',
            minute: 'numeric',
            hour12: true
        });
    }, [timestamp]);

    // Memoize auto-linked content
    const linkedContent = useMemo(() => autoLinkText(content), [content]);

    // Determine if content should be rendered as code
    const shouldRenderAsCode = isCode !== undefined ? isCode : isRequest;

    return (
        <Box padding="3">
            <HorizontalStack gap="3" align="start" blockAlign="start">
                {/* Icon */}
                <Box>
                    <img
                        src={iconSrc}
                        alt={iconAlt}
                        style={{ width: '20px', height: '20px', objectFit: 'contain' }}
                    />
                </Box>

                {/* Divider */}
                <Box style={{ width: '2px', alignSelf: 'stretch' }}>
                    <img
                        src={dividerSrc}
                        alt=""
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                </Box>

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
                        <Box paddingBlockStart="1">
                            {shouldRenderAsCode ? (
                                <div className={styles.codeContent}>
                                    {content}
                                </div>
                            ) : (
                                <div className={`markdown-content ${styles.markdownContent}`}>
                                    <MarkdownRenderer>{linkedContent}</MarkdownRenderer>
                                </div>
                            )}
                        </Box>

                        {/* Vulnerability Badge */}
                        {isVulnerable && !isRequest && (
                            <Box paddingBlockStart="2">
                                <Badge status="critical">System Prompt Leak</Badge>
                            </Box>
                        )}
                    </VerticalStack>
                </Box>
            </HorizontalStack>
            <style jsx>{`
                ${markdownStyles}
            `}</style>
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

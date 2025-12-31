import React from 'react';
import { Box, Text, Icon, Badge, VerticalStack, HorizontalStack } from '@shopify/polaris';
import { SendMajor, AutomationMajor } from '@shopify/polaris-icons';
import { MarkdownRenderer, markdownStyles } from '../../../../components/shared/MarkdownComponents';
const AktoLogo = '/public/akto.svg';
const FrameLogo = '/public/Frame.svg';
const Divider = '/public/Divider.svg';
const DividerAlert = '/public/Divider_alert.svg';

function TrafficMessage({ type, content, timestamp, isVulnerable, customLabel, isCode }) {
    const isRequest = type === 'request';

    // Icons
    // Purple plane for request (using SendMajor as proxy, styled purple)
    // Gray robot for response (using AutomationMajor)
    const iconSource = isRequest ? SendMajor : AutomationMajor;

    // Styling
    // Request label style: monospace-ish
    const label = customLabel || (isRequest ? 'Tested interaction' : 'HR agent response');

    // Format timestamp
    const formattedTime = timestamp ? new Date(timestamp * 1000).toLocaleString('en-US', {
        month: 'numeric', day: 'numeric', year: '2-digit', hour: 'numeric', minute: 'numeric', hour12: true
    }) : '';

    // Helper to auto-link URLs in markdown text (since remark-gfm is not available)
    const autoLink = (text) => {
        if (!text) return "";
        // Regex to match URLs that are NOT already part of a markdown link
        // Negative lookbehind (?<!]\() ensures we don't match url in [text](url)
        const urlRegex = /(?<!\]\()(?<!href=")(https?:\/\/[^\s<)]+)/g;
        return text.replace(urlRegex, '[$1]($1)');
    };

    return (
        <Box padding="3">
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                <div style={{
                    color: isRequest ? '#9C6ADE' : '#6D7175', // Custom purple and standard subdued gray
                    height: '20px',
                    width: '20px',
                    flexShrink: 0
                }}>
                    {isRequest ? (
                        <img src={AktoLogo} alt="Akto Logo" style={{ height: '100%', width: '100%', objectFit: 'contain' }} />
                    ) : (
                        <img src={FrameLogo} alt="Agent Logo" style={{ height: '100%', width: '100%', objectFit: 'contain' }} />
                    )}
                </div>

                <div style={{
                    display: 'flex',
                    width: '2px',
                    flexDirection: 'column',
                    alignItems: 'flex-start',
                    alignSelf: 'stretch'
                }}>
                    <img src={isVulnerable ? DividerAlert : Divider} alt="" style={{ height: '100%', width: '100%', objectFit: 'cover' }} />
                </div>

                <div style={{ flex: 1, minWidth: 0 }}>
                    <VerticalStack gap="1">
                        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                            <span style={{
                                color: 'var(--Text-Subdued, #6D7175)',
                                fontFeatureSettings: "'liga' off, 'clig' off",
                                fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, sans-serif',
                                fontSize: '12px',
                                fontStyle: 'normal',
                                fontWeight: 600,
                                lineHeight: '16px'
                            }}>{label}</span>
                            <Text variant="bodySm" color="subdued">{formattedTime}</Text>
                        </div>

                        <Box paddingBlockStart="1">
                            {/* Use whitespace-pre-wrap for requests/traffic (code font) to preserve formatting */}
                            {/* Use MarkdownRenderer for AI responses/chat (standard font) */}
                            {/* Allow isCode to override default behavior based on request type */}
                            {(isCode !== undefined ? isCode : isRequest) ? (
                                <div style={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: '13px', color: '#202223' }}>
                                    {content}
                                </div>
                            ) : (
                                <div className="markdown-content">
                                    <MarkdownRenderer>{autoLink(content)}</MarkdownRenderer>
                                </div>
                            )}
                        </Box>

                        {isVulnerable && !isRequest && (
                            <Box paddingBlockStart="2">
                                <Badge status="critical">System Prompt Leak</Badge>
                            </Box>
                        )}
                    </VerticalStack>
                </div>
            </div>
            <style jsx>{`
                ${markdownStyles}
            `}</style>
        </Box>
    );
}

export default TrafficMessage;

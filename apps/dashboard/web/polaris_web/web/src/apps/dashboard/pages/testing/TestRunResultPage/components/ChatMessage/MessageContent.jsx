import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Box } from '@shopify/polaris';
import { MarkdownRenderer, markdownStyles } from '../../../../../components/shared/MarkdownComponents';
import { MESSAGE_TYPES } from './constants';
import styles from './ChatMessage.module.css';

// Helper to auto-link URLs in markdown text (since remark-gfm is not available)
const autoLinkText = (text) => {
    if (!text) return "";
    // Regex to match URLs that are NOT already part of a markdown link
    // Negative lookbehind (?<!]\() ensures we don't match url in [text](url)
    const urlRegex = /(?<!\]\()(?<!href=")(https?:\/\/[^\s<)]+)/g;
    return text.replace(urlRegex, '[$1]($1)');
};

function MessageContent({ content, type, isCode }) {
    const isRequest = type === MESSAGE_TYPES.REQUEST;

    // Memoize the auto-linked content to avoid re-processing on every render
    const linkedContent = useMemo(() => autoLinkText(content), [content]);

    // Determine if content should be rendered as code
    // Allow isCode to override default behavior based on request type
    const shouldRenderAsCode = isCode !== undefined ? isCode : isRequest;

    return (
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
            <style jsx>{`
                ${markdownStyles}
            `}</style>
        </Box>
    );
}

MessageContent.propTypes = {
    content: PropTypes.string.isRequired,
    type: PropTypes.oneOf([MESSAGE_TYPES.REQUEST, MESSAGE_TYPES.RESPONSE]).isRequired,
    isCode: PropTypes.bool,
};

MessageContent.defaultProps = {
    isCode: undefined,
};

export default MessageContent;

import React from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack } from '@shopify/polaris';
import MessageIcon from './MessageIcon';
import MessageDivider from './MessageDivider';
import MessageHeader from './MessageHeader';
import MessageContent from './MessageContent';
import VulnerabilityBadge from './VulnerabilityBadge';
import { MESSAGE_TYPES } from './constants';
import styles from './ChatMessage.module.css';

function ChatMessage({ type, content, timestamp, isVulnerable, customLabel, isCode }) {
    return (
        <Box padding="3">
            <div className={styles.container}>
                <MessageIcon type={type} />
                <MessageDivider isVulnerable={isVulnerable} />

                <div className={styles.content}>
                    <VerticalStack gap="1">
                        <MessageHeader
                            type={type}
                            customLabel={customLabel}
                            timestamp={timestamp}
                        />
                        <MessageContent
                            content={content}
                            type={type}
                            isCode={isCode}
                        />
                        <VulnerabilityBadge
                            isVulnerable={isVulnerable}
                            type={type}
                        />
                    </VerticalStack>
                </div>
            </div>
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

import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
import { Text } from '@shopify/polaris';
import { DEFAULT_LABELS, MESSAGE_TYPES } from './constants';
import styles from './ChatMessage.module.css';

function MessageHeader({ type, customLabel, timestamp }) {
    const isRequest = type === MESSAGE_TYPES.REQUEST;
    const label = customLabel || (isRequest ? DEFAULT_LABELS.REQUEST : DEFAULT_LABELS.RESPONSE);

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

    return (
        <div className={styles.header}>
            <span className={styles.label}>{label}</span>
            <Text variant="bodySm" color="subdued">{formattedTime}</Text>
        </div>
    );
}

MessageHeader.propTypes = {
    type: PropTypes.oneOf([MESSAGE_TYPES.REQUEST, MESSAGE_TYPES.RESPONSE]).isRequired,
    customLabel: PropTypes.string,
    timestamp: PropTypes.number,
};

MessageHeader.defaultProps = {
    customLabel: null,
    timestamp: null,
};

export default MessageHeader;

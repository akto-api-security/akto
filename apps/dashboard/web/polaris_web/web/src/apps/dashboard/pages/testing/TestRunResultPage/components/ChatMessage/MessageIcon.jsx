import React from 'react';
import PropTypes from 'prop-types';
import { ASSETS, MESSAGE_TYPES } from './constants';
import styles from './ChatMessage.module.css';

function MessageIcon({ type }) {
    const isRequest = type === MESSAGE_TYPES.REQUEST;
    const iconSrc = isRequest ? ASSETS.AKTO_LOGO : ASSETS.FRAME_LOGO;
    const alt = isRequest ? 'Akto Logo' : 'Agent Logo';

    return (
        <div className={styles.icon}>
            <img
                src={iconSrc}
                alt={alt}
                className={styles.iconImage}
            />
        </div>
    );
}

MessageIcon.propTypes = {
    type: PropTypes.oneOf([MESSAGE_TYPES.REQUEST, MESSAGE_TYPES.RESPONSE]).isRequired,
};

export default MessageIcon;

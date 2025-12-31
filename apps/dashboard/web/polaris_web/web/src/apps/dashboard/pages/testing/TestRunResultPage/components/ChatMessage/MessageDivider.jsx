import React from 'react';
import PropTypes from 'prop-types';
import { ASSETS } from './constants';
import styles from './ChatMessage.module.css';

function MessageDivider({ isVulnerable }) {
    const dividerSrc = isVulnerable ? ASSETS.DIVIDER_ALERT : ASSETS.DIVIDER;

    return (
        <div className={styles.divider}>
            <img
                src={dividerSrc}
                alt=""
                className={styles.dividerImage}
            />
        </div>
    );
}

MessageDivider.propTypes = {
    isVulnerable: PropTypes.bool,
};

MessageDivider.defaultProps = {
    isVulnerable: false,
};

export default MessageDivider;

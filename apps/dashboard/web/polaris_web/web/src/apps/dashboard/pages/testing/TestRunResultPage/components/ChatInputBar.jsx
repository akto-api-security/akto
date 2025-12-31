import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { Box, TextField, Button } from '@shopify/polaris';
import { SendMajor } from '@shopify/polaris-icons';
import { PLACEHOLDER_TEXT } from './chatConstants';

function ChatInputBar({ onSendMessage, isStreaming }) {
    const [inputValue, setInputValue] = useState('');

    const handleSend = () => {
        if (inputValue.trim()) {
            onSendMessage(inputValue);
            setInputValue('');
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <Box
            background="bg-surface"
            borderRadius="3"
            borderWidth="1"
            borderColor="border"
            padding="4"
            shadow="card"
        >
            {!isStreaming ? (
                <TextField
                    value={inputValue}
                    onChange={setInputValue}
                    placeholder={PLACEHOLDER_TEXT.INPUT_DEFAULT}
                    autoComplete="off"
                    connectedRight={
                        <Button
                            icon={SendMajor}
                            disabled={!inputValue.trim()}
                            onClick={handleSend}
                            accessibilityLabel="Send"
                        />
                    }
                    onKeyDown={handleKeyDown}
                />
            ) : (
                <TextField
                    value=""
                    disabled
                    placeholder={PLACEHOLDER_TEXT.INPUT_LOADING}
                    autoComplete="off"
                />
            )}
        </Box>
    );
}

ChatInputBar.propTypes = {
    onSendMessage: PropTypes.func.isRequired,
    isStreaming: PropTypes.bool,
};

ChatInputBar.defaultProps = {
    isStreaming: false,
};

export default ChatInputBar;

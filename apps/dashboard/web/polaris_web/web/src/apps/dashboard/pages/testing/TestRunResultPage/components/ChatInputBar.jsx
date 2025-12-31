import React, { useState } from 'react';
import { Box, TextField, Button } from '@shopify/polaris';
import { SendMajor } from '@shopify/polaris-icons';

function ChatInputBar({ onSendMessage, isStreaming }) {
    const [inputValue, setInputValue] = useState('');

    const handleSend = () => {
        if (inputValue.trim()) {
            onSendMessage(inputValue);
            setInputValue('');
        }
    };

    const handleKeyPress = (e) => {
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
                    placeholder="Ask a follow up..."
                    autoComplete="off"
                    connectedRight={
                        <Button
                            icon={SendMajor}
                            disabled={!inputValue.trim()}
                            onClick={handleSend}
                            accessibilityLabel="Send"
                        />
                    }
                    onKeyPress={handleKeyPress}
                />
            ) : (
                <TextField
                    value=""
                    disabled
                    placeholder="Pondering, stand by..."
                    autoComplete="off"
                />
            )}
        </Box>
    );
}

export default ChatInputBar;

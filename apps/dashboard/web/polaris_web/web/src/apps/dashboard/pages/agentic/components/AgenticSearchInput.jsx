import { useState, useCallback } from 'react';
import { Box, TextField, Icon } from '@shopify/polaris';
import { ArrowUpMinor } from '@shopify/polaris-icons';

function AgenticSearchInput({ value: externalValue, onChange, onSubmit, placeholder = "How can I help you today?" }) {
    const [internalValue, setInternalValue] = useState('');

    const value = externalValue !== undefined ? externalValue : internalValue;
    const setValue = onChange || setInternalValue;

    const handleChange = useCallback((newValue) => setValue(newValue), [setValue]);

    const handleSubmit = useCallback(() => {
        if (value.trim() && onSubmit) {
            onSubmit(value);
        }
    }, [value, onSubmit]);

    const handleKeyPress = useCallback((e) => {
        if (e.key === 'Enter') {
            handleSubmit();
        }
    }, [handleSubmit]);

    return (
        <Box style={{ marginBottom: '24px', display: 'flex', justifyContent: 'center' }}>
            <Box
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    width: '520px',
                    padding: '8px 12px',
                    borderRadius: '12px',
                    border: '1px solid rgba(98, 0, 234, 0.67)',
                    background: '#FFF',
                    boxShadow: '0 0 5px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.15)'
                }}
            >
                <Box style={{ flex: 1 }}>
                    <TextField
                        value={value}
                        onChange={handleChange}
                        placeholder={placeholder}
                        autoComplete="off"
                        onKeyPress={handleKeyPress}
                        borderless
                    />
                </Box>
                <Box
                    onClick={handleSubmit}
                    style={{
                        padding: '8px',
                        borderRadius: '8px',
                        background: value.trim() ? 'rgba(109, 59, 239, 1)' : 'rgba(109, 59, 239, 0.2)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        flexShrink: 0,
                        marginLeft: '8px',
                        transition: 'background 0.2s ease'
                    }}
                >
                    <Box style={{
                        color: 'rgba(255, 255, 255, 1)',
                        display: 'flex',
                        filter: 'brightness(0) invert(1)'
                    }}>
                        <Icon source={ArrowUpMinor} />
                    </Box>
                </Box>
            </Box>
        </Box>
    );
}

export default AgenticSearchInput;

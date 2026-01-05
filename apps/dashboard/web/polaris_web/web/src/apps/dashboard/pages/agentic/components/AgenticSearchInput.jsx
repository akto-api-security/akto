import { useState, useCallback, useRef } from 'react';
import { Box, Icon, TextField } from '@shopify/polaris';
import { ArrowUpMinor } from '@shopify/polaris-icons';

function AgenticSearchInput({
    value: externalValue,
    onChange,
    onSubmit,
    placeholder = "How can I help you today?",
    isStreaming = false,
    isFixed = false,
    containerStyle = {}
}) {
    const [internalValue, setInternalValue] = useState('');
    const inputRef = useRef(null);

    const value = externalValue !== undefined ? externalValue : internalValue;
    const setValue = onChange || setInternalValue;

    const handleChange = useCallback((newValue) => setValue(newValue), [setValue]);

    const handleSubmit = useCallback(() => {
        if (value.trim() && onSubmit) {
            onSubmit(value);

            // Scroll to the bottom of the page smoothly
            setTimeout(() => {
                // Scroll to the absolute bottom of the page
                window.scrollTo({
                    top: document.documentElement.scrollHeight,
                    behavior: 'smooth'
                });

                // Focus the input field
                if (inputRef.current) {
                    const input = inputRef.current.querySelector('input');
                    if (input) {
                        input.focus();
                    }
                }
            }, 100);
        }
    }, [value, onSubmit]);

    const handleKeyDown = useCallback((e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handleSubmit();
        }
    }, [handleSubmit]);

    // Wrapper styles for fixed vs normal positioning
    const wrapperStyle = isFixed ? {
        position: 'fixed',
        bottom: '0',
        left: '300px',
        right: '0',
        paddingTop: '40px',
        paddingBottom: '28px',
        background: 'linear-gradient(to top, rgba(250, 250, 250, 1) 70%, rgba(250, 250, 250, 0) 100%)',
        zIndex: 100
    } : {
        marginBottom: '24px',
        display: 'flex',
        justifyContent: 'center'
    };

    // Inner container styles
    const innerContainerStyle = isFixed ? {
        width: '520px',
        marginLeft: '218px'
    } : {};

    return (
        <>
            <style>{`
                @keyframes shimmer {
                    0% {
                        background-position: -1000px 0;
                    }
                    100% {
                        background-position: 1000px 0;
                    }
                }

                .agentic-search-input .Polaris-TextField {
                    border: none !important;
                    box-shadow: none !important;
                }
                .agentic-search-input .Polaris-TextField__Input {
                    border: none !important;
                    box-shadow: none !important;
                    padding: 8px !important;
                    background: transparent !important;
                }
                .agentic-search-input .Polaris-TextField__Input:focus {
                    border: none !important;
                    box-shadow: none !important;
                    outline: none !important;
                }
                .agentic-search-input .Polaris-TextField__Backdrop {
                    border: none !important;
                    box-shadow: none !important;
                    background: transparent !important;
                }
                .agentic-search-input .Polaris-TextField__Backdrop::before,
                .agentic-search-input .Polaris-TextField__Backdrop::after {
                    border: none !important;
                    box-shadow: none !important;
                }

                .shimmer-container {
                    position: relative;
                    overflow: hidden;
                }

                .shimmer-container::before {
                    content: '';
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    border-radius: 12px;
                    padding: 1px;
                    background: linear-gradient(
                        90deg,
                        rgba(98, 0, 234, 0.67) 0%,
                        rgba(98, 0, 234, 0.67) 40%,
                        rgba(200, 150, 255, 1) 50%,
                        rgba(98, 0, 234, 0.67) 60%,
                        rgba(98, 0, 234, 0.67) 100%
                    );
                    background-size: 200% 100%;
                    -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
                    -webkit-mask-composite: xor;
                    mask-composite: exclude;
                    animation: shimmer 3s linear infinite;
                    pointer-events: none;
                }
            `}</style>
            <Box style={{ ...wrapperStyle, ...containerStyle }}>
                <Box style={innerContainerStyle}>
                    <Box
                        className="agentic-search-input shimmer-container"
                        ref={inputRef}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            width: isFixed ? '100%' : '520px',
                            padding: '8px 12px',
                            borderRadius: '12px',
                            background: '#FFF',
                            boxShadow: '0 0 5px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.15)'
                        }}
                        onKeyDown={handleKeyDown}
                    >
                        <Box style={{ flex: 1 }}>
                            <TextField
                                value={value}
                                onChange={handleChange}
                                placeholder={placeholder}
                                autoComplete="off"
                                borderless
                            />
                        </Box>
                    <Box
                        onClick={handleSubmit}
                        style={{
                            padding: '8px',
                            borderRadius: '8px',
                            background: (isStreaming || value.trim()) ? 'rgba(109, 59, 239, 1)' : 'rgba(109, 59, 239, 0.2)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: isStreaming ? 'default' : 'pointer',
                            flexShrink: 0,
                            marginLeft: '8px',
                            transition: 'background 0.2s ease',
                            width: '32px',
                            height: '32px',
                            pointerEvents: isStreaming ? 'none' : 'auto'
                        }}
                    >
                        {isStreaming ? (
                            <img
                                src="/public/stream.svg"
                                alt="Processing"
                                style={{
                                    width: '16px',
                                    height: '16px',
                                    filter: 'brightness(0) invert(1)',
                                    color: 'rgba(255, 255, 255, 1)',
                                    animation: 'spin 1s linear infinite'
                                }}
                            />
                        ) : (
                            <Box style={{
                                color: 'rgba(255, 255, 255, 1)',
                                display: 'flex',
                                filter: 'brightness(0) invert(1)'
                            }}>
                                <Icon source={ArrowUpMinor} />
                            </Box>
                        )}
                    </Box>
                </Box>
            </Box>
        </Box>
        </>
    );
}

export default AgenticSearchInput;

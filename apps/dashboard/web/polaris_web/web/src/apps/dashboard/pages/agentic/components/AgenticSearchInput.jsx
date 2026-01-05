import { useCallback } from 'react';
import { Box, Icon, TextField, Form, Button } from '@shopify/polaris';
import { ArrowUpMinor } from '@shopify/polaris-icons';

/**
 * AgenticSearchInput - AI SDK Compatible
 *
 * Works with AI SDK's useChat hook by accepting:
 * - input: from useChat's input state
 * - handleInputChange: from useChat's handleInputChange
 * - handleSubmit: from useChat's handleSubmit
 * - isLoading: from useChat's isLoading
 */
function AgenticSearchInput({
    input = '',
    handleInputChange = () => {},
    handleSubmit = () => {},
    isLoading = false,
    placeholder = "How can I help you today?",
    isFixed = false,
    containerStyle = {},
}) {
    // Handle input changes - AI SDK expects (e) => void
    const handleChange = useCallback((newValue) => {
        if (typeof newValue === 'string') {
            // Polaris TextField passes string directly
            handleInputChange({ target: { value: newValue } });
        } else {
            // Already an event object
            handleInputChange(newValue);
        }
    }, [handleInputChange]);

    // Handle form submission - AI SDK handles the actual submission
    const onFormSubmit = useCallback((e) => {
        e.preventDefault();
        if (input.trim()) {
            handleSubmit(e);
        }
    }, [input, handleSubmit]);

    const handleKeyDown = useCallback((e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            onFormSubmit(e);
        }
    }, [onFormSubmit]);

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
            `}</style>
            <Box style={{ ...wrapperStyle, ...containerStyle }}>
                <Box style={innerContainerStyle}>
                    <Form onSubmit={onFormSubmit}>
                        <Box
                            className="agentic-search-input"
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                width: isFixed ? '100%' : '520px',
                                padding: '8px 12px',
                                borderRadius: '12px',
                                border: '1px solid rgba(98, 0, 234, 0.67)',
                                background: '#FFF',
                                boxShadow: '0 0 5px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.15)'
                            }}
                            onKeyDown={handleKeyDown}
                        >
                            <Box style={{ flex: 1 }}>
                                <TextField
                                    value={input}
                                    onChange={handleChange}
                                    placeholder={placeholder}
                                    autoComplete="off"
                                    borderless
                                />
                            </Box>
                            <Button
                                submit
                                disabled={isLoading || !input.trim()}
                                plain
                            >
                                <Box
                                    style={{
                                        padding: '8px',
                                        borderRadius: '8px',
                                        background: (isLoading || input.trim()) ? 'rgba(109, 59, 239, 1)' : 'rgba(109, 59, 239, 0.2)',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        transition: 'background 0.2s ease',
                                        width: '32px',
                                        height: '32px'
                                    }}
                                >
                                    {isLoading ? (
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
                            </Button>
                        </Box>
                    </Form>
                </Box>
            </Box>
        </>
    );
}

export default AgenticSearchInput;

import { useState, useEffect } from 'react';
import { Page, Box, Text } from '@shopify/polaris';

function AgenticConversationPage({ initialQuery }) {
    const [isLoading, setIsLoading] = useState(true);
    const [followUpValue, setFollowUpValue] = useState('');
    const [timeTaken, setTimeTaken] = useState(null);
    const [isStreaming, setIsStreaming] = useState(false);
    const [streamedContent, setStreamedContent] = useState({ sections: [] });
    const [streamedThinkingItems, setStreamedThinkingItems] = useState([]);
    const [streamTrigger, setStreamTrigger] = useState(0);
    const [messages, setMessages] = useState([
        {
            type: 'user',
            content: initialQuery || 'Generate an executive report on agentic risks this week'
        }
    ]);

    // Function to get AI response items (hardcoded for now, should come from API)
    const getAIResponseItems = () => {
        return [
            'Assessing agent permissions, tool usage, API access, and guardrail coverage to identify unsafe or high impact execution paths.',
            'Identifying prompt injection attempts, anomalous agent behavior, and unprotected access to sensitive data.',
            'Prioritizing risks by business impact and producing a concise, board ready summary with recommended actions.'
        ];
    };

    // Function to get formatted response content (hardcoded for now, should come from API)
    const getResponseContent = () => {
        return {
            title: 'Weekly Agentic Risk Summary',
            sections: [
                {
                    header: 'Scope analyzed',
                    items: [
                        '14 active agent workflows',
                        '7 day observation window'
                    ]
                },
                {
                    header: 'Key risks identified',
                    items: [
                        '2 agents (14%) executing tools or APIs without execution guardrails',
                        '1 agent (7%) accessing sensitive customer data without output redaction',
                        '1 newly discovered agent with elevated execution privileges',
                        '1 shadow agent without an assigned owner'
                    ]
                },
                {
                    header: 'Threat activity observed',
                    items: [
                        '3 prompt injection attempts detected',
                        '100% of attempts blocked before execution'
                    ]
                },
                {
                    header: 'Posture change',
                    items: [
                        'Overall agentic risk increased week over week',
                        'Primary drivers: new agents and expanded execution scope'
                    ]
                },
                {
                    header: 'Top priorities',
                    items: [
                        'Apply execution guardrails to the 2 highest risk agents',
                        'Enforce output redaction on agents handling sensitive data',
                        'Assign ownership and review permissions for shadow agents'
                    ]
                }
            ]
        };
    };

    // Function to get conversation suggestions (hardcoded for now, should come from API)
    const getConversationSuggestions = () => {
        return [
            'Which agents should I secure first and why?',
            'What actions can these high risk agents perform if compromised?',
            'Apply recommended guardrails to the highest risk agents'
        ];
    };

    // Trigger initial streaming on mount
    useEffect(() => {
        setStreamTrigger(1);
    }, []);

    // Stream thinking items first
    useEffect(() => {
        if (streamTrigger === 0) return; // Don't run on initial mount, wait for trigger

        setStreamedThinkingItems([]); // Reset thinking items
        setIsLoading(true);

        const thinkingItems = getAIResponseItems();
        let currentIndex = 0;

        const thinkingInterval = setInterval(() => {
            if (currentIndex >= thinkingItems.length) {
                clearInterval(thinkingInterval);
                return;
            }
            setStreamedThinkingItems(prev => [...prev, thinkingItems[currentIndex]]);
            currentIndex++;
        }, 300); // Show each thinking item every 300ms

        return () => clearInterval(thinkingInterval);
    }, [streamTrigger]);

    // Simulate loading state and streaming response
    useEffect(() => {
        if (streamTrigger === 0) return; // Don't run on initial mount, wait for trigger

        const startTime = Date.now();
        const fullContent = getResponseContent();

        setStreamedContent({ sections: [] }); // Reset content
        setIsStreaming(true);

        // Wait 2 seconds before starting to stream
        const loadingTimer = setTimeout(() => {
            setIsLoading(false);

            // Calculate and set time taken immediately
            const endTime = Date.now();
            const duration = Math.round((endTime - startTime) / 1000);
            setTimeTaken(duration);

            // Flatten all content into a sequence
            const sequence = [];
            sequence.push({ type: 'title', content: fullContent.title });
            fullContent.sections.forEach(section => {
                sequence.push({ type: 'header', content: section.header });
                section.items.forEach(item => {
                    sequence.push({ type: 'item', content: item });
                });
            });

            let currentIndex = 0;
            let currentSection = null;
            const streamInterval = setInterval(() => {
                if (currentIndex >= sequence.length) {
                    // Streaming complete
                    clearInterval(streamInterval);
                    setIsStreaming(false);
                    setMessages(prev => [...prev, {
                        type: 'assistant',
                        content: 'Response content',
                        timeTaken: duration,
                        isComplete: true
                    }]);
                    return;
                }

                const current = sequence[currentIndex];

                if (current.type === 'title') {
                    setStreamedContent({ title: current.content, sections: [] });
                } else if (current.type === 'header') {
                    currentSection = { header: current.content, items: [] };
                    setStreamedContent(prev => ({
                        ...prev,
                        sections: [...prev.sections, currentSection]
                    }));
                } else if (current.type === 'item') {
                    setStreamedContent(prev => {
                        const newSections = [...prev.sections];
                        const lastSection = { ...newSections[newSections.length - 1] };
                        lastSection.items = [...lastSection.items, current.content];
                        newSections[newSections.length - 1] = lastSection;
                        return { ...prev, sections: newSections };
                    });
                }

                currentIndex++;
            }, 200); // Add new content every 200ms

            return () => clearInterval(streamInterval);
        }, 2000);

        return () => clearTimeout(loadingTimer);
    }, [streamTrigger]);

    const handleFollowUpSubmit = (query) => {
        if (query.trim()) {
            setMessages(prev => [...prev, { type: 'user', content: query }]);
            setFollowUpValue('');
            setIsStreaming(true); // Immediately show streaming icon
            setStreamTrigger(prev => prev + 1); // Trigger streaming
            // Add your follow-up logic here
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handleFollowUpSubmit(followUpValue);
        }
    };

    return (
        <>
            <style>{`
                @keyframes spin {
                    from {
                        transform: rotate(0deg);
                    }
                    to {
                        transform: rotate(360deg);
                    }
                }
                .Polaris-Page {
                    min-height: 100vh;
                    background: radial-gradient(115.53% 72.58% at 48.08% 50%, #FAFAFA 27.4%, #FAFAFA 54.33%, #F9F6FF 69.17%, #FFF 86.54%, #F0FAFF 98.08%), #F6F6F7;
                    padding: 24px 32px;
                    margin: 0;
                    display: flex;
                    flex-direction: column;
                }
            `}</style>
            <Page fullWidth>
                {/* Header with Share button */}
                <Box style={{ marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Text variant="headingLg" as="h1">
                        {messages[0]?.content}
                    </Text>
                    <button
                        style={{
                            padding: '8px 16px',
                            borderRadius: '8px',
                            border: '1px solid #C9CCCF',
                            background: '#FFF',
                            cursor: 'pointer',
                            fontSize: '14px',
                            fontWeight: 500
                        }}
                    >
                        Share
                    </button>
                </Box>

                {/* Conversation content */}
                <Box
                    style={{
                        display: 'flex',
                        width: '520px',
                        flexDirection: 'column',
                        alignItems: 'flex-start',
                        gap: '16px',
                        marginTop: '130px',
                        marginLeft: '218px',
                        paddingBottom: '150px'
                    }}
                >
                    {messages.map((message, index) => (
                        message.type === 'user' ? (
                            <Box
                                key={index}
                                style={{
                                    width: '100%',
                                    display: 'flex',
                                    justifyContent: 'flex-end'
                                }}
                            >
                                <Box
                                    style={{
                                        maxWidth: '70%',
                                        padding: '12px 16px',
                                        borderRadius: '12px 12px 4px 12px',
                                        border: '1px solid #C9CCCF',
                                        background: 'rgba(255, 255, 255, 0.40)'
                                    }}
                                >
                                    <Text variant="bodyMd" as="p">
                                        <span style={{
                                            color: '#202223',
                                            fontFeatureSettings: "'liga' off, 'clig' off",
                                            fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                            fontSize: '12px',
                                            fontStyle: 'normal',
                                            fontWeight: 400,
                                            lineHeight: '16px'
                                        }}>
                                            {message.content}
                                        </span>
                                    </Text>
                                </Box>
                            </Box>
                        ) : message.isComplete ? (
                            <Box
                                key={`response-${index}`}
                                style={{
                                    width: '100%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '8px'
                                }}
                            >
                                {/* Time taken box */}
                                {message.timeTaken && (
                                    <Box
                                        style={{
                                            width: '100%',
                                            paddingLeft: '20px'
                                        }}
                                    >
                                        <Text variant="bodySm" as="p" tone="subdued">
                                            Thought for {message.timeTaken} seconds
                                        </Text>
                                    </Box>
                                )}

                                {/* Response content box */}
                                <Box
                                    style={{
                                        width: '100%',
                                        display: 'flex',
                                        justifyContent: 'flex-start'
                                    }}
                                >
                                    <Box style={{ width: '100%' }}>
                                        <Box
                                            style={{
                                                padding: '16px 20px',
                                                borderRadius: '12px 12px 12px 0px',
                                                display: 'flex',
                                                flexDirection: 'column',
                                                gap: '16px',
                                                background: 'transparent'
                                            }}
                                        >
                                            {/* Response title */}
                                            <Text variant="headingMd" as="h3">
                                                {getResponseContent().title}
                                            </Text>

                                            {/* Response sections */}
                                            {getResponseContent().sections.map((section, sectionIndex) => (
                                                <Box key={sectionIndex} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                                    <Text variant="bodyMd" as="p" fontWeight="semibold">
                                                        {section.header}
                                                    </Text>
                                                    <Box style={{ display: 'flex', flexDirection: 'column', gap: '4px', paddingLeft: '4px' }}>
                                                        {section.items.map((item, itemIndex) => (
                                                            <Box
                                                                key={itemIndex}
                                                                style={{
                                                                    display: 'flex',
                                                                    gap: '8px',
                                                                    alignItems: 'flex-start'
                                                                }}
                                                            >
                                                                <Box style={{
                                                                    width: '4px',
                                                                    height: '4px',
                                                                    borderRadius: '50%',
                                                                    background: '#202223',
                                                                    marginTop: '8px',
                                                                    flexShrink: 0
                                                                }} />
                                                                <Text variant="bodySm" as="p">
                                                                    <span style={{
                                                                        color: '#202223',
                                                                        fontFeatureSettings: "'liga' off, 'clig' off",
                                                                        fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                                                        fontSize: '12px',
                                                                        fontStyle: 'normal',
                                                                        fontWeight: 400,
                                                                        lineHeight: '16px'
                                                                    }}>
                                                                        {item}
                                                                    </span>
                                                                </Text>
                                                            </Box>
                                                        ))}
                                                    </Box>
                                                </Box>
                                            ))}
                                        </Box>
                                    </Box>
                                </Box>

                                {/* Clipboard copy button box */}
                                <Box
                                    style={{
                                        width: '100%',
                                        display: 'flex',
                                        justifyContent: 'flex-start'
                                    }}
                                >
                                    <Box
                                        onClick={() => {
                                            const content = getResponseContent();
                                            let textToCopy = `${content.title}\n\n`;
                                            content.sections.forEach(section => {
                                                textToCopy += `${section.header}\n`;
                                                section.items.forEach(item => {
                                                    textToCopy += `• ${item}\n`;
                                                });
                                                textToCopy += '\n';
                                            });
                                            navigator.clipboard.writeText(textToCopy);
                                            // Optional: Show a toast notification here
                                        }}
                                        style={{
                                            padding: '8px 12px',
                                            borderRadius: '8px',
                                            cursor: 'pointer',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '8px',
                                            transition: 'opacity 0.2s ease'
                                        }}
                                        onMouseEnter={(e) => e.currentTarget.style.opacity = '0.7'}
                                        onMouseLeave={(e) => e.currentTarget.style.opacity = '1'}
                                    >
                                        <svg width="16" height="16" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                                            <path d="M13 3H7C5.89543 3 5 3.89543 5 5V13C5 14.1046 5.89543 15 7 15H13C14.1046 15 15 14.1046 15 13V5C15 3.89543 14.1046 3 13 3Z" stroke="#202223" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                            <path d="M15 7H16C17.1046 7 18 7.89543 18 9V15C18 16.1046 17.1046 17 16 17H10C8.89543 17 8 16.1046 8 15V14" stroke="#202223" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                        </svg>
                                        <Text variant="bodySm" as="span">
                                            Copy
                                        </Text>
                                    </Box>
                                </Box>

                                {/* Suggestions - only show for the last message */}
                                {index === messages.length - 1 && !isLoading && !isStreaming && (
                                    <Box
                                        style={{
                                            width: '100%',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            gap: '4px'
                                        }}
                                    >
                                        {getConversationSuggestions().map((suggestion, suggestionIndex) => (
                                        <Box
                                            key={suggestionIndex}
                                            onClick={() => {
                                                setFollowUpValue(suggestion);
                                                handleFollowUpSubmit(suggestion);
                                            }}
                                            style={{
                                                padding: '12px 16px',
                                                borderRadius: '8px',
                                                cursor: 'pointer',
                                                transition: 'opacity 0.2s ease'
                                            }}
                                            onMouseEnter={(e) => e.currentTarget.style.opacity = '0.8'}
                                            onMouseLeave={(e) => e.currentTarget.style.opacity = '1'}
                                        >
                                            <Box style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                <Box style={{ width: '20px', height: '20px', flexShrink: 0 }}>
                                                    <img
                                                        src="/public/suggestion.svg"
                                                        alt="Suggestion"
                                                        style={{ width: '100%', height: '100%', display: 'block' }}
                                                    />
                                                </Box>
                                                <Text variant="bodySm" as="p">
                                                    <span style={{ color: 'rgba(109, 113, 117, 0.8)' }}>
                                                        {suggestion}
                                                    </span>
                                                </Text>
                                            </Box>
                                        </Box>
                                        ))}
                                    </Box>
                                )}
                            </Box>
                        ) : null
                    ))}

                    {/* Loading state - showing what agent is thinking */}
                    {isLoading && (
                        <Box
                            style={{
                                width: '100%',
                                display: 'flex',
                                justifyContent: 'flex-start'
                            }}
                        >
                            <Box style={{ width: '100%' }}>
                                <Box
                                    style={{
                                        padding: '12px 16px',
                                        borderRadius: '12px 12px 12px 0px',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: '8px',
                                        background: 'transparent'
                                    }}
                                >
                                    {/* Top text */}
                                    <Text variant="bodyMd" as="p">
                                        <span style={{
                                            color: 'rgba(190, 190, 191, 1)',
                                            fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                            fontSize: '12px',
                                            fontStyle: 'normal',
                                            fontWeight: 400
                                        }}>
                                            Pondering, stand by...
                                        </span>
                                    </Text>

                                    {/* List of thinking items with 8px gap from above */}
                                    <Box style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                        {streamedThinkingItems.map((item, itemIndex) => (
                                            <Box
                                                key={itemIndex}
                                                style={{
                                                    display: 'flex',
                                                    gap: '7px',
                                                    alignItems: 'flex-start'
                                                }}
                                            >
                                                <Box style={{
                                                    width: '4px',
                                                    height: '4px',
                                                    borderRadius: '50%',
                                                    background: '#8C9196',
                                                    marginTop: '5px',
                                                    flexShrink: 0
                                                }} />
                                                <Text variant="bodySm" as="p">
                                                    <span style={{
                                                        color: '#8C9196',
                                                        fontFeatureSettings: "'liga' off, 'clig' off",
                                                        fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                                        fontSize: '10px',
                                                        fontStyle: 'normal',
                                                        fontWeight: 400,
                                                        lineHeight: '14px'
                                                    }}>
                                                        {item}
                                                    </span>
                                                </Text>
                                            </Box>
                                        ))}
                                    </Box>
                                </Box>
                            </Box>
                        </Box>
                    )}

                    {/* Streaming response */}
                    {isStreaming && streamedContent.sections.length > 0 && (
                        <Box
                            style={{
                                width: '100%',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '8px'
                            }}
                        >
                            {/* Time taken box */}
                            {timeTaken && (
                                <Box
                                    style={{
                                        width: '100%',
                                        paddingLeft: '20px'
                                    }}
                                >
                                    <Text variant="bodySm" as="p" tone="subdued">
                                        Thought for {timeTaken} seconds
                                    </Text>
                                </Box>
                            )}

                            {/* Response content box */}
                            <Box
                                style={{
                                    width: '100%',
                                    display: 'flex',
                                    justifyContent: 'flex-start'
                                }}
                            >
                                <Box style={{ width: '100%' }}>
                                    <Box
                                        style={{
                                            padding: '16px 20px',
                                            borderRadius: '12px 12px 12px 0px',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            gap: '16px',
                                            background: 'transparent'
                                        }}
                                    >
                                    {/* Response title */}
                                    {streamedContent.title && (
                                        <Text variant="headingMd" as="h3">
                                            {streamedContent.title}
                                        </Text>
                                    )}

                                    {/* Response sections */}
                                    {streamedContent.sections.map((section, sectionIndex) => (
                                        <Box key={sectionIndex} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                            <Text variant="bodyMd" as="p" fontWeight="semibold">
                                                {section.header}
                                            </Text>
                                            <Box style={{ display: 'flex', flexDirection: 'column', gap: '4px', paddingLeft: '4px' }}>
                                                {section.items.map((item, itemIndex) => (
                                                    <Box
                                                        key={itemIndex}
                                                        style={{
                                                            display: 'flex',
                                                            gap: '8px',
                                                            alignItems: 'flex-start'
                                                        }}
                                                    >
                                                        <Box style={{
                                                            width: '4px',
                                                            height: '4px',
                                                            borderRadius: '50%',
                                                            background: '#202223',
                                                            marginTop: '8px',
                                                            flexShrink: 0
                                                        }} />
                                                        <Text variant="bodySm" as="p">
                                                            <span style={{
                                                                color: '#202223',
                                                                fontFeatureSettings: "'liga' off, 'clig' off",
                                                                fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                                                fontSize: '12px',
                                                                fontStyle: 'normal',
                                                                fontWeight: 400,
                                                                lineHeight: '16px'
                                                            }}>
                                                                {item}
                                                            </span>
                                                        </Text>
                                                    </Box>
                                                ))}
                                            </Box>
                                        </Box>
                                    ))}
                                </Box>
                            </Box>
                            </Box>
                        </Box>
                    )}
                </Box>

                {/* Fixed follow-up input bar with background overlay */}
                <Box
                    style={{
                        position: 'fixed',
                        bottom: '0',
                        left: '300px',
                        right: '0',
                        paddingTop: '40px',
                        paddingBottom: '28px',
                        background: 'linear-gradient(to top, rgba(250, 250, 250, 1) 70%, rgba(250, 250, 250, 0) 100%)',
                        zIndex: 100
                    }}
                >
                    <Box
                        style={{
                            width: '520px',
                            marginLeft: '218px'
                        }}
                    >
                    <Box
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            padding: '8px 12px',
                            borderRadius: '12px',
                            border: '1px solid rgba(98, 0, 234, 0.67)',
                            background: '#FFF',
                            boxShadow: '0 0 5px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.15)'
                        }}
                    >
                        <input
                            type="text"
                            value={followUpValue}
                            onChange={(e) => setFollowUpValue(e.target.value)}
                            onKeyDown={handleKeyDown}
                            placeholder="Ask a follow up..."
                            style={{
                                flex: 1,
                                border: 'none',
                                outline: 'none',
                                fontSize: '14px',
                                padding: '8px',
                                background: 'transparent'
                            }}
                        />
                        <Box
                            onClick={() => handleFollowUpSubmit(followUpValue)}
                            style={{
                                padding: '8px',
                                borderRadius: '8px',
                                background: (isStreaming || followUpValue.trim()) ? 'rgba(109, 59, 239, 1)' : 'rgba(109, 59, 239, 0.2)',
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
                                    <svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                                        <path d="M10 3L10 17M10 3L5 8M10 3L15 8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                    </svg>
                                </Box>
                            )}
                        </Box>
                    </Box>
                    </Box>
                </Box>
        </Page>
        </>
    );
}

export default AgenticConversationPage;

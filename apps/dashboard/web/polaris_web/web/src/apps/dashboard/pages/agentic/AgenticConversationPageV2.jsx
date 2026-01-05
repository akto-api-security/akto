import { useState, useEffect } from 'react';
import { Page, Box, Text, Banner, Icon } from '@shopify/polaris';
import { ArrowLeftMinor } from '@shopify/polaris-icons';
import AgenticUserMessage from './components/AgenticUserMessage';
import AgenticResponseContent from './components/AgenticResponseContent';
import AgenticCopyButton from './components/AgenticCopyButton';
import AgenticSuggestionsList from './components/AgenticSuggestionsList';
import AgenticSearchInput from './components/AgenticSearchInput';
import { useAgenticChat } from './hooks/useAgenticChat';
import { generateConversationId } from './services/agenticService';

/**
 * AgenticConversationPageV2 - Fully AI SDK Powered
 *
 * Uses AI SDK's useChat hook for:
 * - Automatic message management and streaming
 * - Built-in state management
 * - Standard streaming protocol
 *
 * All UI components remain the same - only the data layer changed!
 */
function AgenticConversationPageV2({ initialQuery, existingConversationId, onBack }) {
    const [error, setError] = useState(null);
    const [suggestions, setSuggestions] = useState([]);

    // Generate conversation ID if not provided
    const [conversationId] = useState(() =>
        existingConversationId || generateConversationId()
    );

    // Use AI SDK - it handles ALL message state and streaming
    const {
        messages,
        input,
        handleInputChange,
        handleSubmit,
        isLoading,
        append,
        timeTaken,
    } = useAgenticChat({
        api: '/api/agentic/chat',
        conversationId,
        initialMessages: [],
        onFinish: (message) => {
            console.log('Message complete:', message);
            // Fetch suggestions after response completes
            fetchSuggestions();
        },
        onError: (err) => {
            console.error('Chat error:', err);
            setError(err.message);
        },
    });

    // Send initial query on mount
    useEffect(() => {
        if (initialQuery && !existingConversationId) {
            // Start conversation with initial query using AI SDK's append
            append({
                role: 'user',
                content: initialQuery,
            });
        }
        // TODO: Load existing conversation from backend/localStorage if existingConversationId is provided
    }, [initialQuery, existingConversationId, append]);

    // Fetch suggestions after a response completes
    const fetchSuggestions = async () => {
        try {
            // TODO: Replace with real endpoint
            const response = await fetch(`/api/agentic/conversations/${conversationId}/suggestions`);
            if (response.ok) {
                const data = await response.json();
                setSuggestions(data.suggestions || []);
            }
        } catch (err) {
            console.error('Error fetching suggestions:', err);
        }
    };

    // Handle suggestion click - use AI SDK's append
    const handleSuggestionClick = (suggestion) => {
        setSuggestions([]);
        append({
            role: 'user',
            content: suggestion,
        });
    };

    // Parse assistant message content
    const parseMessageContent = (message) => {
        if (message.role !== 'assistant') return null;

        try {
            // If content is JSON string, parse it
            if (typeof message.content === 'string' && message.content.startsWith('{')) {
                return JSON.parse(message.content);
            }
            // If already an object, return it
            if (typeof message.content === 'object') {
                return message.content;
            }
            // Plain text - wrap in expected format
            return {
                title: 'Response',
                sections: [{
                    header: 'Result',
                    items: [message.content]
                }]
            };
        } catch (err) {
            // Fallback for parsing errors
            return {
                title: 'Response',
                sections: [{
                    header: 'Result',
                    items: [message.content]
                }]
            };
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
                {/* Error Banner */}
                {error && (
                    <Box style={{ marginBottom: '16px' }}>
                        <Banner
                            title="Error"
                            tone="critical"
                            onDismiss={() => setError(null)}
                        >
                            <p>{error}</p>
                        </Banner>
                    </Box>
                )}

                {/* Header with Back button */}
                <Box style={{ marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Box style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        {onBack && (
                            <Box
                                onClick={onBack}
                                style={{
                                    cursor: 'pointer',
                                    padding: '8px',
                                    borderRadius: '8px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    transition: 'background 0.2s ease',
                                    background: 'transparent'
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.background = '#F6F6F7'}
                                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                            >
                                <Icon source={ArrowLeftMinor} />
                            </Box>
                        )}
                        <Text variant="headingLg" as="h1">
                            {messages[0]?.content || 'Agentic AI Conversation'}
                        </Text>
                    </Box>
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
                    {/* Render AI SDK messages directly */}
                    {messages.map((message, index) => (
                        message.role === 'user' ? (
                            <AgenticUserMessage
                                key={message.id || index}
                                content={message.content}
                            />
                        ) : (
                            <Box
                                key={message.id || index}
                                style={{
                                    width: '100%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '8px'
                                }}
                            >
                                <AgenticResponseContent
                                    content={parseMessageContent(message)}
                                    timeTaken={index === messages.length - 1 ? timeTaken : null}
                                />
                                <AgenticCopyButton content={parseMessageContent(message)} />

                                {/* Show suggestions for the last assistant message */}
                                {index === messages.length - 1 && !isLoading && suggestions.length > 0 && (
                                    <AgenticSuggestionsList
                                        suggestions={suggestions}
                                        onSuggestionClick={handleSuggestionClick}
                                    />
                                )}
                            </Box>
                        )
                    ))}

                    {/* Loading indicator */}
                    {isLoading && (
                        <Box style={{ padding: '16px' }}>
                            <Text variant="bodyMd" as="p" tone="subdued">
                                Thinking...
                            </Text>
                        </Box>
                    )}
                </Box>

                {/* Fixed follow-up input - fully AI SDK compatible */}
                <AgenticSearchInput
                    input={input}
                    handleInputChange={handleInputChange}
                    handleSubmit={handleSubmit}
                    isLoading={isLoading}
                    placeholder="Ask a follow up..."
                    isFixed={true}
                />
            </Page>
        </>
    );
}

export default AgenticConversationPageV2;

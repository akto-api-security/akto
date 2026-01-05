import { useState, useEffect } from 'react';
import { Page, Box, Text, Banner, VerticalStack, HorizontalStack, Button } from '@shopify/polaris';
import { ArrowLeftMinor } from '@shopify/polaris-icons';
import UserMessage from './components/UserMessage';
import ResponseContent from './components/ResponseContent';
import CopyButton from './components/CopyButton';
import SuggestionsList from './components/SuggestionsList';
import SearchInput from './components/SearchInput';
import { useAgenticChatV3 } from './hooks/useAgenticChatV3';
import { generateConversationId, getMockSuggestions } from './services/mockAIService';

/**
 * ConversationPage - Main conversation interface
 * Uses only Polaris components, no HTML tags
 * Pure mock implementation - no real API calls
 */
function ConversationPage({ initialQuery, existingConversationId, onBack }) {
    const [error, setError] = useState(null);
    const [suggestions, setSuggestions] = useState([]);

    // Generate conversation ID if not provided
    const [conversationId] = useState(() =>
        existingConversationId || generateConversationId()
    );

    // Use custom chat hook with mock streaming
    const {
        messages,
        input,
        handleInputChange,
        handleSubmit,
        isLoading,
        append,
        timeTaken,
    } = useAgenticChatV3({
        conversationId,
        onFinish: (message) => {
            console.log('Message complete:', message);
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
            append(initialQuery);
        }
    }, [initialQuery, existingConversationId, append]);

    // Fetch suggestions after a response completes
    const fetchSuggestions = async () => {
        try {
            const mockSuggestions = await getMockSuggestions();
            setSuggestions(mockSuggestions);
        } catch (err) {
            console.error('Error fetching suggestions:', err);
        }
    };

    // Handle suggestion click
    const handleSuggestionClick = (suggestion) => {
        setSuggestions([]);
        append(suggestion);
    };

    return (
        <Page fullWidth>
            {/* Error Banner */}
            {error && (
                <Box paddingBlockEnd="400">
                    <Banner
                        title="Error"
                        tone="critical"
                        onDismiss={() => setError(null)}
                    >
                        <Text as="p">{error}</Text>
                    </Banner>
                </Box>
            )}

            {/* Header with Back button */}
            <Box paddingBlockEnd="600">
                <HorizontalStack align="space-between" blockAlign="center">
                    <HorizontalStack gap="300" blockAlign="center">
                        {onBack && (
                            <Button
                                icon={ArrowLeftMinor}
                                onClick={onBack}
                                accessibilityLabel="Go back"
                            />
                        )}
                        <Text variant="headingLg" as="h1">
                            {messages[0]?.content || 'Agentic AI Conversation'}
                        </Text>
                    </HorizontalStack>
                    <Button>Share</Button>
                </HorizontalStack>
            </Box>

            {/* Conversation content */}
            <Box paddingBlockEnd="800">
                <Box maxWidth="720px" marginInline="auto">
                    <VerticalStack gap="400">
                        {/* Render messages */}
                        {messages.map((message, index) => (
                            message.role === 'user' ? (
                                <UserMessage
                                    key={message.id || index}
                                    content={message.content}
                                />
                            ) : (
                                <VerticalStack key={message.id || index} gap="200">
                                    <ResponseContent
                                        content={message.content}
                                        timeTaken={index === messages.length - 1 ? timeTaken : null}
                                    />
                                    <HorizontalStack gap="200">
                                        <CopyButton content={message.content} />
                                    </HorizontalStack>

                                    {/* Show suggestions for the last assistant message */}
                                    {index === messages.length - 1 && !isLoading && suggestions.length > 0 && (
                                        <SuggestionsList
                                            suggestions={suggestions}
                                            onSuggestionClick={handleSuggestionClick}
                                        />
                                    )}
                                </VerticalStack>
                            )
                        ))}

                        {/* Loading indicator */}
                        {isLoading && messages[messages.length - 1]?.role !== 'assistant' && (
                            <Box padding="400">
                                <Text variant="bodyMd" as="p" tone="subdued">
                                    Thinking...
                                </Text>
                            </Box>
                        )}
                    </VerticalStack>
                </Box>
            </Box>

            {/* Fixed follow-up input */}
            <SearchInput
                input={input}
                handleInputChange={handleInputChange}
                handleSubmit={handleSubmit}
                isLoading={isLoading}
                placeholder="Ask a follow up..."
                isFixed={true}
            />
        </Page>
    );
}

export default ConversationPage;

import { useState, useEffect, useRef } from 'react';
import { Page, Box, Button, Text, HorizontalStack, VerticalStack } from '@shopify/polaris';
import { ArrowLeftMinor } from '@shopify/polaris-icons';
import AgenticUserMessage from './components/AgenticUserMessage';
import AgenticThinkingBox from './components/AgenticThinkingBox';
import AgenticResponseContent from './components/AgenticResponseContent';
import AgenticCopyButton from './components/AgenticCopyButton';
import AgenticSuggestionsList from './components/AgenticSuggestionsList';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticHistoryModal from './components/AgenticHistoryModal';
import './AgenticConversationPage.css';
import {
    sendQuery,
    streamThinkingItems,
    streamResponse,
    getSuggestions,
    saveConversationToLocal
} from './services/agenticService';

function AgenticConversationPage({ initialQuery, existingConversationId, onBack, existingMessages = [] }) {
    // Conversation state
    const [conversationId, setConversationId] = useState(existingConversationId || null);
    const [messages, setMessages] = useState([]);

    // UI state
    const [isLoading, setIsLoading] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [followUpValue, setFollowUpValue] = useState('');
    const [showHistoryModal, setShowHistoryModal] = useState(false);

    // Current response state
    const [streamedThinkingItems, setStreamedThinkingItems] = useState([]);
    const [streamedContent, setStreamedContent] = useState('');
    const [currentTimeTaken, setCurrentTimeTaken] = useState(null);
    const [currentSuggestions, setCurrentSuggestions] = useState([]);

    // Error state
    const [error, setError] = useState(null);

    // Ref for search input
    const searchInputRef = useRef(null);

    // Initialize conversation on mount
    useEffect(() => {
        const initializeConversation = async () => {
            try {
                // If existingConversationId is provided, load the conversation
                if (existingConversationId && existingMessages.length > 0) {
                    let messages = [];
                    existingMessages.forEach((item) => {
                        messages.push({
                            _id: "user_" + item.prompt,
                            message: item.prompt,
                            role: "user"
                        })
                        messages.push({
                            _id: "system_" + item.response,
                            message: item.response,
                            role: "system",
                            isComplete: true
                        })
                    })
                    setMessages(messages);
                    return;
                }

                // Add initial user message
                const userMessage = {
                    id: 'e',
                    type: 'user',
                    content: initialQuery,
                    timestamp: new Date().toISOString()
                };
                setMessages([userMessage]);

                // Process the initial query
                // await processQuery(newConversationId, query);
            } catch (err) {
                setError('Failed to initialize conversation');
                console.error(err);
            }
        };

        initializeConversation();
    }, [initialQuery, existingConversationId]);

    // Save conversation to localStorage whenever messages change
    useEffect(() => {
        if (conversationId && messages.length > 0) {
            saveConversationToLocal(conversationId, messages);
        }
    }, [conversationId, messages]);

    // Auto-focus input on keypress
    useEffect(() => {
        const handleKeyDown = (e) => {
            // Ignore if user is already typing in an input/textarea
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
                return;
            }

            // Ignore special keys
            const ignoredKeys = ['Escape', 'Tab', 'Enter', 'Shift', 'Control', 'Alt', 'Meta', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];
            if (ignoredKeys.includes(e.key)) {
                return;
            }

            // Focus the input field
            if (searchInputRef.current) {
                searchInputRef.current.focus();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, []);

    // Process a query and handle streaming
    const processQuery = async (convId, query) => {
        try {
            setError(null);
            setIsLoading(true);
            setStreamedThinkingItems([]);
            setStreamedContent('');
            setCurrentTimeTaken(null);
            setCurrentSuggestions([]);

            // Send query to backend
            await sendQuery(convId, query);

            const startTime = Date.now();

            // Stream thinking items
            await streamThinkingItems(
                convId,
                (thinkingItem) => {
                    setStreamedThinkingItems(prev => [...prev, thinkingItem]);
                },
                () => {
                    // Thinking complete - but keep loading true until response starts
                },
                (err) => {
                    console.error('Error streaming thinking items:', err);
                    setError('Failed to process thinking');
                    setIsLoading(false);
                }
            );

            // Stream response content
            setIsStreaming(true);
            let isFirstChunk = true;
            let fullResponseContent = '';

            await streamResponse(
                convId,
                (chunk) => {
                    // On first chunk, stop loading and calculate time
                    if (isFirstChunk) {
                        isFirstChunk = false;
                        setIsLoading(false);
                        const duration = Math.round((Date.now() - startTime) / 1000);
                        setCurrentTimeTaken(duration);
                    }

                    // Handle streaming markdown chunk
                    if (chunk.type === 'markdown') {
                        setStreamedContent(prev => prev + chunk.content);
                        fullResponseContent += chunk.content;
                    }
                },
                async (responseData) => {
                    // Get suggestions
                    const suggestions = await getSuggestions(convId);
                    setCurrentSuggestions(suggestions);

                    // Stop streaming FIRST to prevent double display
                    setIsStreaming(false);

                    // Then add complete assistant message to history
                    const assistantMessage = {
                        id: 'a',
                        type: 'assistant',
                        thinkingItems: streamedThinkingItems,
                        content: fullResponseContent, // Changed from 'response' to 'content'
                        suggestions: suggestions,
                        timeTaken: responseData.timeTaken || currentTimeTaken,
                        timestamp: responseData.timestamp,
                        isComplete: true
                    };

                    setMessages(prev => [...prev, assistantMessage]);
                },
                (err) => {
                    console.error('Error streaming response:', err);
                    setError('Failed to get response');
                    setIsStreaming(false);
                }
            );
        } catch (err) {
            console.error('Error processing query:', err);
            setError('Failed to process your request');
            setIsLoading(false);
            setIsStreaming(false);
        }
    };

    const handleFollowUpSubmit = async (query) => {
        if (query.trim() && conversationId) {
            // Add user message immediately
            const userMessage = {
                id: 'u',
                type: 'user',
                content: query,
                timestamp: new Date().toISOString()
            };
            setMessages(prev => [...prev, userMessage]);
            setFollowUpValue('');

            // Process the query
            await processQuery(conversationId, query);
        }
    };

    const handleHistoryClick = (convId, title) => {
        // Navigate back to main page and load the conversation
        if (onBack) {
            onBack();
        }
        // The main page will handle loading the conversation
        window.location.href = `/dashboard/ask-ai?conversation=${convId}`;
    };

    return (
        <>
            <Page id="agentic-conversation-page" fullWidth>
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between" blockAlign="center" gap="3">
                        <HorizontalStack gap="3" blockAlign="center">
                            {onBack && (
                                <Button plain onClick={onBack} icon={ArrowLeftMinor} />
                            )}
                            <Text variant="headingLg" as="h1">
                                {messages[0]?.content || 'Agentic AI Conversation'}
                            </Text>
                        </HorizontalStack>
                        <Button
                            plain
                            onClick={() => setShowHistoryModal(true)}
                        >
                            <img src="/public/history.svg" alt="History" style={{ width: '20px', height: '20px' }} />
                        </Button>
                    </HorizontalStack>
                    <Box
                        paddingBlockStart="16"
                        paddingBlockEnd="19"
                        paddingInlineStart="27"
                        style={{ flex: 1, overflow: 'auto' }}
                    >
                        <VerticalStack gap="4" align="start">
                            {messages.map((message, index) => (
                                message.role === 'user' ? (
                                    <AgenticUserMessage key={message._id || index} content={message.message} />
                                ) : message.isComplete ? (
                                    <VerticalStack key={message.id || `response-${index}`} gap="2" align="start">
                                        <AgenticResponseContent
                                            content={message.message}
                                        />
                                        <AgenticCopyButton content={message.message} />
                                        {index === messages.length - 1 && !isLoading && !isStreaming && message.suggestions && (
                                            <AgenticSuggestionsList
                                                suggestions={message.suggestions}
                                                onSuggestionClick={(suggestion) => {
                                                    setFollowUpValue(suggestion);
                                                    handleFollowUpSubmit(suggestion);
                                                }}
                                            />
                                        )}
                                    </VerticalStack>
                                ) : null
                            ))}

                            {/* Loading state */}
                            {isLoading && (
                                <AgenticThinkingBox thinkingItems={streamedThinkingItems} />
                            )}

                            {/* Streaming response */}
                            {isStreaming && streamedContent && (
                                <AgenticResponseContent
                                    content={streamedContent}
                                    timeTaken={currentTimeTaken}
                                />
                            )}
                        </VerticalStack>
                    </Box>
                </VerticalStack>

                {/* Fixed follow-up input bar */}
                <AgenticSearchInput
                    ref={searchInputRef}
                    value={followUpValue}
                    onChange={setFollowUpValue}
                    onSubmit={() => handleFollowUpSubmit(followUpValue)}
                    placeholder="Ask a follow up..."
                    isStreaming={isStreaming}
                    isFixed={true}
                />

                {/* History Modal */}
                <AgenticHistoryModal
                    isOpen={showHistoryModal}
                    onClose={() => setShowHistoryModal(false)}
                    onHistoryClick={handleHistoryClick}
                />
            </Page>
        </>
    );
}

export default AgenticConversationPage;

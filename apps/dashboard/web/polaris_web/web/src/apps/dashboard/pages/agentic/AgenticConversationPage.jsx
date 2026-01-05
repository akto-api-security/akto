import { useState, useEffect, useRef } from 'react';
import { Page, Box, Text, Banner, Icon } from '@shopify/polaris';
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
    generateConversationId,
    generateMessageId,
    createConversation,
    sendQuery,
    streamThinkingItems,
    streamResponse,
    getSuggestions,
    saveConversationToLocal,
    loadConversationFromLocal
} from './services/agenticService';

function AgenticConversationPage({ initialQuery, existingConversationId, onBack }) {
    // Conversation state
    const [conversationId, setConversationId] = useState(existingConversationId || null);
    const [messages, setMessages] = useState([]);
    const [isLoadingHistory, setIsLoadingHistory] = useState(false);

    // UI state
    const [isLoading, setIsLoading] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [followUpValue, setFollowUpValue] = useState('');
    const [showHistoryModal, setShowHistoryModal] = useState(false);

    // Current response state
    const [streamedThinkingItems, setStreamedThinkingItems] = useState([]);
    const [streamedContent, setStreamedContent] = useState({ sections: [] });
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
                if (existingConversationId) {
                    setIsLoadingHistory(true);
                    const savedConversation = loadConversationFromLocal(existingConversationId);

                    if (savedConversation && savedConversation.messages) {
                        setMessages(savedConversation.messages);
                        setConversationId(existingConversationId);
                    } else {
                        setError('Conversation not found');
                    }
                    setIsLoadingHistory(false);
                    return;
                }

                // Otherwise, create new conversation
                const query = initialQuery || 'Generate an executive report on agentic risks this week';
                const newConversationId = generateConversationId();
                setConversationId(newConversationId);

                // Add initial user message
                const userMessage = {
                    id: generateMessageId(),
                    type: 'user',
                    content: query,
                    timestamp: new Date().toISOString()
                };
                setMessages([userMessage]);

                // Process the initial query
                await processQuery(newConversationId, query);
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
            setStreamedContent({ sections: [] });
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
            let fullResponseContent = { sections: [] };

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

                    // Handle streaming chunk
                    if (chunk.type === 'title') {
                        setStreamedContent(prev => ({ ...prev, title: chunk.content }));
                        fullResponseContent.title = chunk.content;
                    } else if (chunk.type === 'header') {
                        const newSection = { header: chunk.content, items: [] };
                        setStreamedContent(prev => ({
                            ...prev,
                            sections: [...prev.sections, newSection]
                        }));
                        fullResponseContent.sections.push(newSection);
                    } else if (chunk.type === 'item') {
                        setStreamedContent(prev => {
                            const newSections = [...prev.sections];
                            const lastSection = { ...newSections[newSections.length - 1] };
                            lastSection.items = [...lastSection.items, chunk.content];
                            newSections[newSections.length - 1] = lastSection;
                            return { ...prev, sections: newSections };
                        });
                        fullResponseContent.sections[fullResponseContent.sections.length - 1].items.push(chunk.content);
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
                        id: generateMessageId(),
                        type: 'assistant',
                        thinkingItems: streamedThinkingItems,
                        response: fullResponseContent,
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
                id: generateMessageId(),
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

                {/* Header with Back and History buttons */}
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
                    <Box
                        onClick={() => setShowHistoryModal(true)}
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
                        <img src="/public/history.svg" alt="History" style={{ width: '20px', height: '20px' }} />
                    </Box>
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
                            <AgenticUserMessage key={message.id || index} content={message.content} />
                        ) : message.isComplete ? (
                            <Box
                                key={message.id || `response-${index}`}
                                style={{
                                    width: '100%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '8px'
                                }}
                            >
                                <AgenticResponseContent
                                    content={message.response}
                                    timeTaken={message.timeTaken}
                                />
                                <AgenticCopyButton content={message.response} />
                                {index === messages.length - 1 && !isLoading && !isStreaming && message.suggestions && (
                                    <AgenticSuggestionsList
                                        suggestions={message.suggestions}
                                        onSuggestionClick={(suggestion) => {
                                            setFollowUpValue(suggestion);
                                            handleFollowUpSubmit(suggestion);
                                        }}
                                    />
                                )}
                            </Box>
                        ) : null
                    ))}

                    {/* Loading state */}
                    {isLoading && (
                        <AgenticThinkingBox thinkingItems={streamedThinkingItems} />
                    )}

                    {/* Streaming response */}
                    {isStreaming && streamedContent.sections.length > 0 && (
                        <AgenticResponseContent
                            content={streamedContent}
                            timeTaken={currentTimeTaken}
                        />
                    )}
                </Box>

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

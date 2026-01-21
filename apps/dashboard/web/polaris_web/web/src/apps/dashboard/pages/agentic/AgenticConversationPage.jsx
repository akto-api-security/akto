import { useState, useEffect, useRef } from 'react';
import { Page, Box, Button, Text, HorizontalStack, VerticalStack } from '@shopify/polaris';
import { ArrowLeftMinor } from '@shopify/polaris-icons';
import AgenticUserMessage from './components/AgenticUserMessage';
import AgenticThinkingBox from './components/AgenticThinkingBox';
import AgenticStreamingResponse from './components/AgenticStreamingResponse';
import AgenticCopyButton from './components/AgenticCopyButton';
import AgenticSuggestionsList from './components/AgenticSuggestionsList';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticHistoryModal from './components/AgenticHistoryModal';
import './AgenticConversationPage.css';
import { sendQuery, getConversationsList } from './services/agenticService';

function AgenticConversationPage({ initialQuery, existingConversationId, onBack, existingMessages = [], onLoadConversation, conversationType }) {
    // Conversation state
    const [conversationId, setConversationId] = useState(existingConversationId || null);
    const [messages, setMessages] = useState([]);
    const [completedStreamingMessages, setCompletedStreamingMessages] = useState(new Set());

    // UI state
    const [isLoading, setIsLoading] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [followUpValue, setFollowUpValue] = useState('');
    const [showHistoryModal, setShowHistoryModal] = useState(false);

    // History state
    const [historyItems, setHistoryItems] = useState([]);
    const [historySearchQuery, setHistorySearchQuery] = useState('');
    const [isHistoryLoading, setIsHistoryLoading] = useState(false);

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
                    if (existingMessages.length > 0) {
                        let messages = [];
                        const title = existingMessages[0].title;
                        existingMessages[0].messages.reverse().forEach((item) => {
                            messages.push({
                                _id: "user_" + item.prompt,
                                message: item.prompt,
                                role: "user",
                                title: title
                            })
                            messages.push({
                                _id: "system_" + item.response,
                                message: item.response,
                                role: "system",
                                isComplete: true,
                                isFromHistory: true,
                                title: title
                            })
                        })
                        setMessages(messages);
                    }
                    // Don't call processQuery for existing conversations
                    return;
                }

                // Only process new queries when there's an initialQuery and no existingConversationId
                if (initialQuery) {
                    // Add initial user message
                    const userMessage = {
                        _id: 'conversation_user_' + Date.now(),
                        role: 'user',
                        message: initialQuery
                    };
                    setMessages([userMessage]);

                    // Process the initial query
                    await processQuery(initialQuery, "", conversationType);
                }
            } catch (err) {
                setError('Failed to initialize conversation');
                console.error(err);
            }
        };

        initializeConversation();
    }, [initialQuery, existingConversationId]);


    // Fetch history when modal opens or search query changes
    useEffect(() => {
        const fetchHistory = async () => {
            if (!showHistoryModal) return;

            setIsHistoryLoading(true);
            try {
                const response = await getConversationsList(50, historySearchQuery);
                if (response && response.history) {
                    const formattedHistory = response.history.map(conv => {
                        // Get the conversation ID from the nested structure
                        const conversationId = conv._id?._id || conv._id;

                        // Use the title from the conversation
                        const title = conv.title || 'Untitled conversation';

                        // Use lastUpdatedAt if available and not 0, otherwise use current time
                        const lastUpdatedAt = conv.lastUpdatedAt && conv.lastUpdatedAt !== 0
                            ? conv.lastUpdatedAt
                            : Date.now();

                        return {
                            id: conversationId,
                            title: title,
                            lastUpdatedAt: lastUpdatedAt
                        };
                    });
                    console.log('Formatted history:', formattedHistory);
                    setHistoryItems(formattedHistory);
                }
            } catch (error) {
                console.error('Error fetching history:', error);
                setHistoryItems([]);
            } finally {
                setIsHistoryLoading(false);
            }
        };

        fetchHistory();
    }, [showHistoryModal, historySearchQuery]);

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
    const processQuery = async (query, convId, conversationType) => {
        try {
            setIsLoading(true);

            let res = await sendQuery(query, convId, conversationType);
            if(res && res.conversationId) {
                setConversationId(res.conversationId);
            }

            // Add AI response message to the conversation
            if(res && res.response) {
                const aiMessage = {
                    _id: "system_" + Date.now(),
                    message: res.response,
                    role: "system",
                    isComplete: true,
                    isFromHistory: false
                };
                setMessages(prev => [...prev, aiMessage]);
            }

            setIsLoading(false);

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
                _id: "user_" + Date.now(),
                message: query,
                role: 'user'
            };
            setMessages(prev => [...prev, userMessage]);
            setFollowUpValue('');

            // Process the query
            await processQuery(query, conversationId, conversationType);
        }
    };

    const handleHistoryClick = (convId, title) => {
        // Load the conversation without page reload
        if (onLoadConversation) {
            onLoadConversation(convId);
        }
    };

    const handleDeleteHistory = (conversationId) => {
        // Remove the conversation from the history list
        setHistoryItems(prev => prev.filter(item => item.id !== conversationId));
    };

    const handleStreamingComplete = (messageId) => {
        setCompletedStreamingMessages(prev => new Set([...prev, messageId]));
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
                                {messages[0]?.title || 'Agentic AI Conversation'}
                            </Text>
                        </HorizontalStack>
                        <Button
                            plain
                            onClick={() => setShowHistoryModal(true)}
                        >
                            <img src="/public/history.svg" alt="History" style={{ width: '20px', height: '20px' }} />
                        </Button>
                    </HorizontalStack>
                        <Box style={{ flex: 1, overflow: 'hidden', display: 'flex', justifyContent: 'center', maxWidth: '100%' }}>
                        <Box style={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', maxWidth: '500px' }}>
                            <Box style={{ flex: 1, overflowY: 'auto', paddingBottom: '120px' }}>
                                <Box paddingBlockStart="16" paddingBlockEnd="19">
                                    <VerticalStack gap="4" align="start">
                            {messages.map((message, index) => (
                                message.role === 'user' ? (
                                    <AgenticUserMessage key={message._id || index} content={message.message} />
                                ) : message.isComplete ? (
                                    <VerticalStack key={message.id || `response-${index}`} gap="2" align="start">
                                        <AgenticStreamingResponse
                                            content={message.message}
                                            onStreamingComplete={() => handleStreamingComplete(message._id)}
                                            skipStreaming={message.isFromHistory || false}
                                        />
                                        {completedStreamingMessages.has(message._id) && (
                                            <AgenticCopyButton content={message.message} />
                                        )}
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
                                <AgenticThinkingBox thinkingItems={[]} />
                            )}

                            {/* Streaming response */}
                            {/* {isStreaming && streamedContent && (
                                <AgenticResponseContent
                                    content={streamedContent}
                                    timeTaken={currentTimeTaken}
                                />
                            )} */}
                                    </VerticalStack>
                                </Box>
                            </Box>
                        </Box>
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
                    centerAlign={true}
                    inputWidth="600px"
                />

                {/* History Modal */}
                <AgenticHistoryModal
                    isOpen={showHistoryModal}
                    onClose={() => setShowHistoryModal(false)}
                    onHistoryClick={handleHistoryClick}
                    historyItems={historyItems}
                    searchQuery={historySearchQuery}
                    onSearchQueryChange={setHistorySearchQuery}
                    isLoading={isHistoryLoading}
                    onDelete={handleDeleteHistory}
                />
            </Page>
        </>
    );
}

export default AgenticConversationPage;

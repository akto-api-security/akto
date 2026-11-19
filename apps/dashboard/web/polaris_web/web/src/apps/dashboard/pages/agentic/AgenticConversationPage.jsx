import { useState, useEffect, useRef } from 'react';
import { Page, Box, Button, Text, HorizontalStack, VerticalStack, Banner } from '@shopify/polaris';
import { ArrowLeftMinor } from '@shopify/polaris-icons';
import AgenticUserMessage from './components/AgenticUserMessage';
import AgenticThinkingBox from './components/AgenticThinkingBox';
import AgenticStreamingResponse from './components/AgenticStreamingResponse';
import AgenticCopyButton from './components/AgenticCopyButton';
import AgenticSuggestionsList from './components/AgenticSuggestionsList';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticHistoryModal from './components/AgenticHistoryModal';
import './AgenticConversationPage.css';
import { sendQuery, getConversationById } from './services/agenticService';
import SpinnerCentered from '../../components/progress/SpinnerCentered';
import { usePermissions, NO_PERMISSION_REASON } from '@/util/permissions';

// Maps stored turns to chat messages; positional ids keep React keys unique for repeated prompts
function toChatMessages(conversation) {
    const title = conversation.title;
    const conversationKey = conversation._id?._id || conversation._id;
    const chatMessages = [];
    (conversation.messages || []).forEach((turn, index) => {
        chatMessages.push({
            _id: `${conversationKey}_${index}_user`,
            message: turn.prompt,
            role: "user",
            title: title
        });
        chatMessages.push({
            _id: `${conversationKey}_${index}_system`,
            message: turn.response,
            role: "system",
            isComplete: true,
            isFromHistory: true,
            title: title
        });
    });
    return chatMessages;
}

function AgenticConversationPage({ initialQuery, existingConversationId, onBack, onLoadConversation, onConversationCreated, conversationType, metadata }) {
    const { canCall } = usePermissions();
    const canChat = canCall('api/chatAndStore');
    // Conversation state
    const [conversationId, setConversationId] = useState(existingConversationId || null);
    const [messages, setMessages] = useState([]);
    const [conversationTitle, setConversationTitle] = useState(null);
    const [completedStreamingMessages, setCompletedStreamingMessages] = useState(new Set());
    // Conversation on screen; a ref so the URL update for a just-created conversation doesn't reload it
    const shownConversationIdRef = useRef(null);
    const startedQueryRef = useRef(null);

    // UI state
    const [isLoading, setIsLoading] = useState(false);
    const [isConversationLoading, setIsConversationLoading] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [followUpValue, setFollowUpValue] = useState('');
    const [showHistoryModal, setShowHistoryModal] = useState(false);

    // Error state
    const [error, setError] = useState(null);

    // Ref for search input
    const searchInputRef = useRef(null);

    // Load the selected conversation whenever the selection changes
    useEffect(() => {
        if (!existingConversationId || existingConversationId === shownConversationIdRef.current) {
            return;
        }
        let cancelled = false;
        shownConversationIdRef.current = existingConversationId;
        setConversationId(existingConversationId);
        setMessages([]);
        setConversationTitle(null);
        setCompletedStreamingMessages(new Set());
        setError(null);
        setIsLoading(false); // a pending answer belongs to the previous conversation
        setIsConversationLoading(true);

        getConversationById(existingConversationId)
            .then((conversation) => {
                if (cancelled) return;
                if (!conversation) {
                    setError('This conversation could not be found. It may have been deleted.');
                    return;
                }
                setMessages(toChatMessages(conversation));
            })
            .catch((err) => {
                if (cancelled) return;
                console.error(err);
                setError('Failed to load this conversation');
            })
            .finally(() => {
                if (!cancelled) setIsConversationLoading(false);
            });

        // Ignore this response if another conversation is selected before it arrives
        return () => { cancelled = true; };
    }, [existingConversationId]);

    // Start a new conversation from the query asked on the home page
    useEffect(() => {
        if (!initialQuery || existingConversationId || startedQueryRef.current === initialQuery) {
            return;
        }
        startedQueryRef.current = initialQuery;
        const userMessage = {
            _id: 'conversation_user_' + Date.now(),
            role: 'user',
            message: initialQuery
        };
        setMessages([userMessage]);
        processQuery(initialQuery, "", conversationType);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [initialQuery, existingConversationId]);


    // Auto-focus input on keypress
    useEffect(() => {
        const handleKeyDown = (e) => {
            // Ignore if user is already typing in an input/textarea
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
                return;
            }

            // Ignore if any modifier keys are being held (keyboard shortcuts)
            if (e.metaKey || e.ctrlKey || e.altKey || e.shiftKey) {
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
    const processQuery = async (query, convId, conversationType, queryMetadata) => {
        // Conversation this answer belongs to; null for a new conversation without an id yet
        const askedIn = shownConversationIdRef.current;
        try {
            setIsLoading(true);
            setError(null);

            let res = await sendQuery(query, convId, conversationType, queryMetadata || metadata);
            if (shownConversationIdRef.current !== askedIn) {
                // Another conversation was opened meanwhile; this answer is saved to its own conversation
                return;
            }
            if (res && res.title && !convId) {
                setConversationTitle(res.title);
            }
            if(res && res.conversationId) {
                setConversationId(res.conversationId);
                if (!convId) {
                    // Mark as shown before the URL changes so the load effect doesn't refetch it
                    shownConversationIdRef.current = res.conversationId;
                    if (onConversationCreated) onConversationCreated(res.conversationId);
                }
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
            if (shownConversationIdRef.current === askedIn) {
                setError('Failed to process your request');
                setIsLoading(false);
                setIsStreaming(false);
            }
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
                                {conversationTitle || messages[0]?.title || 'Agentic AI Conversation'}
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
                        <Box style={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', maxWidth: '800px' }}>
                            <Box style={{ flex: 1, overflowY: 'auto', paddingBottom: '120px' }}>
                                <Box paddingBlockStart="16" paddingBlockEnd="19">
                                    <VerticalStack gap="4" align="start">
                            {messages.map((message, index) => (
                                message.role === 'user' ? (
                                    <AgenticUserMessage key={message._id || index} content={message.message} />
                                ) : message.isComplete ? (
                                    <VerticalStack key={message._id || `response-${index}`} gap="2" align="start">
                                        <AgenticStreamingResponse
                                            content={message.message}
                                            onStreamingComplete={() => handleStreamingComplete(message._id)}
                                            skipStreaming={message.isFromHistory || false}
                                        />
                                        {completedStreamingMessages.has(message._id) && (
                                            <AgenticCopyButton content={message.message} />
                                        )}
                                        {index === messages.length - 1 && !isLoading && !isStreaming && message.suggestions && canChat && (
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
                            {isConversationLoading && <SpinnerCentered />}
                            {error && !isConversationLoading && (
                                <Banner status="critical">{error}</Banner>
                            )}
                            {isLoading && (
                                <AgenticThinkingBox />
                            )}
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
                    placeholder={canChat ? "Ask a follow up..." : NO_PERMISSION_REASON}
                    disabled={!canChat}
                    isStreaming={isStreaming}
                    isFixed={true}
                    centerAlign={true}
                    inputWidth="800px"
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

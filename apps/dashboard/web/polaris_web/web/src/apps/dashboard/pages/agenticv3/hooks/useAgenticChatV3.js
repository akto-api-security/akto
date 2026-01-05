import { useState, useCallback, useRef } from 'react';
import { streamMockResponse } from '../services/mockAIService';

/**
 * useAgenticChatV3 - Pure mock implementation
 *
 * Simulates AI chat with streaming responses without any real API calls
 * Manages all message state, streaming simulation, and timing
 */
export const useAgenticChatV3 = ({ conversationId, onFinish, onError }) => {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [timeTaken, setTimeTaken] = useState(null);
    const startTimeRef = useRef(null);
    const streamingMessageRef = useRef('');

    /**
     * Handle input change
     */
    const handleInputChange = useCallback((value) => {
        if (typeof value === 'string') {
            setInput(value);
        } else if (value?.target) {
            setInput(value.target.value);
        }
    }, []);

    /**
     * Send a message and simulate streaming response
     */
    const sendMessage = useCallback(async (messageContent) => {
        if (!messageContent.trim()) return;

        // Start timing
        startTimeRef.current = Date.now();
        setTimeTaken(null);
        setIsLoading(true);

        // Add user message
        const userMessage = {
            id: `msg_${Date.now()}`,
            role: 'user',
            content: messageContent,
            timestamp: new Date().toISOString()
        };

        setMessages(prev => [...prev, userMessage]);

        // Create assistant message placeholder
        const assistantMessageId = `msg_${Date.now() + 1}`;
        streamingMessageRef.current = '';

        setMessages(prev => [...prev, {
            id: assistantMessageId,
            role: 'assistant',
            content: '',
            timestamp: new Date().toISOString(),
            isStreaming: true
        }]);

        // Simulate streaming
        try {
            await streamMockResponse(
                messageContent,
                // onChunk
                (chunk) => {
                    streamingMessageRef.current += chunk;

                    setMessages(prev => {
                        const newMessages = [...prev];
                        const lastIndex = newMessages.length - 1;

                        if (newMessages[lastIndex]?.id === assistantMessageId) {
                            newMessages[lastIndex] = {
                                ...newMessages[lastIndex],
                                content: streamingMessageRef.current,
                                isStreaming: true
                            };
                        }

                        return newMessages;
                    });
                },
                // onComplete
                (finalResponse) => {
                    // Calculate time taken
                    const duration = Math.round((Date.now() - startTimeRef.current) / 1000);
                    setTimeTaken(duration);

                    // Update final message
                    setMessages(prev => {
                        const newMessages = [...prev];
                        const lastIndex = newMessages.length - 1;

                        if (newMessages[lastIndex]?.id === assistantMessageId) {
                            newMessages[lastIndex] = {
                                ...newMessages[lastIndex],
                                content: finalResponse,
                                isStreaming: false
                            };
                        }

                        return newMessages;
                    });

                    setIsLoading(false);
                    streamingMessageRef.current = '';

                    if (onFinish) {
                        onFinish(finalResponse);
                    }
                },
                // onError
                (error) => {
                    console.error('Streaming error:', error);
                    setIsLoading(false);

                    if (onError) {
                        onError(error);
                    }
                }
            );
        } catch (error) {
            console.error('Send message error:', error);
            setIsLoading(false);

            if (onError) {
                onError(error);
            }
        }
    }, [onFinish, onError]);

    /**
     * Handle form submit
     */
    const handleSubmit = useCallback(async (e) => {
        if (e && typeof e.preventDefault === 'function') {
            e.preventDefault();
        }

        if (!input.trim()) return;

        const messageToSend = input;
        setInput(''); // Clear input immediately

        await sendMessage(messageToSend);
    }, [input, sendMessage]);

    /**
     * Append a message (for initial queries)
     */
    const append = useCallback(async (message) => {
        const content = typeof message === 'string' ? message : message.content;
        await sendMessage(content);
    }, [sendMessage]);

    /**
     * Stop generation (no-op for mock, but included for API compatibility)
     */
    const stop = useCallback(() => {
        setIsLoading(false);
    }, []);

    /**
     * Reload/regenerate last message
     */
    const reload = useCallback(async () => {
        if (messages.length < 2) return;

        // Remove last assistant message
        const messagesWithoutLast = messages.slice(0, -1);
        setMessages(messagesWithoutLast);

        // Get last user message
        const lastUserMessage = messagesWithoutLast[messagesWithoutLast.length - 1];

        if (lastUserMessage?.role === 'user') {
            await sendMessage(lastUserMessage.content);
        }
    }, [messages, sendMessage]);

    return {
        messages,
        input,
        handleInputChange,
        handleSubmit,
        isLoading,
        append,
        stop,
        reload,
        setInput,
        setMessages,
        timeTaken
    };
};

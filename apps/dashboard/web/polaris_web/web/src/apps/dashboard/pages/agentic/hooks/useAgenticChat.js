import { useState, useCallback, useRef } from 'react';
import { useChat } from '@ai-sdk/react';

/**
 * useAgenticChat - AI SDK Wrapper
 *
 * Wraps AI SDK's useChat with a compatibility layer to support
 * the traditional input/handleInputChange/handleSubmit pattern.
 *
 * Usage:
 * const { messages, input, handleInputChange, handleSubmit, isLoading } = useAgenticChat({
 *   api: '/api/agentic/chat',
 *   conversationId: 'conv_123',
 *   initialMessages: [],
 *   onFinish: (message) => console.log('Done:', message)
 * });
 */
export function useAgenticChat({
    api = '/api/agentic/chat',
    conversationId: initialConversationId,
    initialMessages = [],
    onResponse,
    onFinish,
    onError,
}) {
    const [conversationId, setConversationId] = useState(initialConversationId);
    const [timeTaken, setTimeTaken] = useState(null);
    const [input, setInput] = useState('');
    const startTimeRef = useRef(null);

    // Use AI SDK's useChat with the new API
    const chat = useChat({
        api,
        messages: initialMessages,
        body: {
            conversationId,
        },
        onResponse: (response) => {
            // Extract conversation ID from response headers
            if (response.headers) {
                const newConvId = response.headers.get('X-Conversation-Id');
                if (newConvId && newConvId !== conversationId) {
                    setConversationId(newConvId);
                }
            }

            onResponse?.(response);
        },
        onFinish: (message) => {
            // Calculate response time
            if (startTimeRef.current) {
                const duration = Math.round((Date.now() - startTimeRef.current) / 1000);
                setTimeTaken(duration);
                startTimeRef.current = null;
            }

            onFinish?.(message);
        },
        onError: (error) => {
            startTimeRef.current = null;
            onError?.(error);
        },
    });

    // Compatibility layer: handleInputChange for the old pattern
    const handleInputChange = useCallback((e) => {
        const value = typeof e === 'string' ? e : e.target?.value || '';
        setInput(value);
    }, []);

    // Compatibility layer: handleSubmit for the old pattern
    const handleSubmit = useCallback(
        async (e, options) => {
            if (e && typeof e.preventDefault === 'function') {
                e.preventDefault();
            }

            if (!input.trim()) {
                return;
            }

            // Start timing
            startTimeRef.current = Date.now();
            setTimeTaken(null);

            // Use new SDK's sendMessage API
            const messageToSend = input;
            setInput(''); // Clear input immediately

            try {
                await chat.sendMessage({
                    role: 'user',
                    content: messageToSend,
                }, options);
            } catch (error) {
                console.error('Error sending message:', error);
                setInput(messageToSend); // Restore input on error
                throw error;
            }
        },
        [input, chat]
    );

    // Compatibility layer: append function
    const append = useCallback(
        async (message) => {
            // Start timing
            startTimeRef.current = Date.now();
            setTimeTaken(null);

            try {
                await chat.sendMessage(message);
            } catch (error) {
                console.error('Error appending message:', error);
                throw error;
            }
        },
        [chat]
    );

    // Check if loading
    const isLoading = chat.status === 'streaming' || chat.status === 'waiting';

    return {
        // Compatibility layer - old API
        messages: chat.messages || [],
        input,
        handleInputChange,
        handleSubmit,
        isLoading,
        error: chat.error,

        // AI SDK methods
        reload: chat.regenerate || (() => {}),
        stop: chat.stop || (() => {}),
        append,
        setMessages: chat.setMessages || (() => {}),
        setInput,

        // Custom state
        conversationId,
        setConversationId,
        timeTaken,
    };
}

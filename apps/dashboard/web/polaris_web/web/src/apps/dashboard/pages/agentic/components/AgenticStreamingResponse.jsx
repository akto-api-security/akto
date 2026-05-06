import { useState, useEffect, useRef } from 'react';
import { Box, Text, VerticalStack } from '@shopify/polaris';
import MarkdownViewer from '../../../components/shared/MarkdownViewer';

function AgenticStreamingResponse({ content, timeTaken, onStreamingComplete, skipStreaming = false }) {
    const [displayedContent, setDisplayedContent] = useState('');
    const hasCalledComplete = useRef(false);

    useEffect(() => {
        if (!content) return;

        // If skipStreaming is true, show content immediately
        if (skipStreaming) {
            setDisplayedContent(content);
            if (onStreamingComplete && !hasCalledComplete.current) {
                hasCalledComplete.current = true;
                onStreamingComplete();
            }
            return;
        }

        // Reset state when content changes
        setDisplayedContent('');
        hasCalledComplete.current = false;

        // Split content into words for streaming
        const words = content.split(' ');
        let currentIndex = 0;

        const streamInterval = setInterval(() => {
            if (currentIndex < words.length) {
                const indexToUse = currentIndex;
                setDisplayedContent(prev => prev + (prev ? ' ' : '') + words[indexToUse]);
                currentIndex++;
            } else {
                clearInterval(streamInterval);
                if (onStreamingComplete && !hasCalledComplete.current) {
                    hasCalledComplete.current = true;
                    onStreamingComplete();
                }
            }
        }, 50); // 50ms delay between words (adjust for faster/slower streaming)

        return () => clearInterval(streamInterval);
    }, [content, skipStreaming]);

    return (
        <VerticalStack gap="2" align="start">
            {/* Time taken */}
            {timeTaken && (
                <Box paddingInlineStart="5">
                    <Text variant="bodySm" as="p" tone="subdued">
                        Thought for {timeTaken} seconds
                    </Text>
                </Box>
            )}

            {/* Content */}
            <Box
                borderRadius='3'
                borderRadiusEndStart='0'
            >
                {/* Render markdown in real-time during streaming */}
                <MarkdownViewer markdown={displayedContent} />
            </Box>
        </VerticalStack>
    );
}

export default AgenticStreamingResponse;

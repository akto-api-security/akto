import { useState, useEffect, useRef } from 'react';
import { Box, Text, VerticalStack } from '@shopify/polaris';
import MarkdownViewer from '../../../components/shared/MarkdownViewer';

function AgenticStreamingResponse({ content, timeTaken, onStreamingComplete, skipStreaming = false }) {
    const [displayedContent, setDisplayedContent] = useState('');
    const [isStreamingComplete, setIsStreamingComplete] = useState(false);
    const hasCalledComplete = useRef(false);

    useEffect(() => {
        if (!content) return;

        // If skipStreaming is true, show content immediately
        if (skipStreaming) {
            setDisplayedContent(content);
            setIsStreamingComplete(true);
            if (onStreamingComplete && !hasCalledComplete.current) {
                hasCalledComplete.current = true;
                onStreamingComplete();
            }
            return;
        }

        // Reset state when content changes
        setDisplayedContent('');
        setIsStreamingComplete(false);
        hasCalledComplete.current = false;

        // Split content into words for streaming
        const words = content.split(' ');
        let currentIndex = 0;

        const streamInterval = setInterval(() => {
            if (currentIndex < words.length) {
                setDisplayedContent(prev => {
                    const newContent = prev + (prev ? ' ' : '') + words[currentIndex];
                    return newContent;
                });
                currentIndex++;
            } else {
                clearInterval(streamInterval);
                setIsStreamingComplete(true);
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
                {isStreamingComplete ? (
                    // Once streaming is complete, show in MarkdownViewer
                    <MarkdownViewer markdown={content} />
                ) : (
                    // While streaming, show plain text with similar styling
                    <Box padding="4">
                        <Text as="p" variant="bodyMd">
                            {displayedContent}
                        </Text>
                    </Box>
                )}
            </Box>
        </VerticalStack>
    );
}

export default AgenticStreamingResponse;

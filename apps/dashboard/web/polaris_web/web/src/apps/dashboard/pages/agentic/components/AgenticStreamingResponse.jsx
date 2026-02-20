import { useState, useEffect, useRef } from 'react';
import { Box, Button, Collapsible, Text, VerticalStack } from '@shopify/polaris';
import MarkdownViewer from '../../../components/shared/MarkdownViewer';

function AgenticStreamingResponse({ content, timeTaken, thinkingBlocks, onStreamingComplete, skipStreaming = false }) {
    const [displayedContent, setDisplayedContent] = useState('');
    const [thoughtsOpen, setThoughtsOpen] = useState(false);
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

    const hasThoughts = thinkingBlocks && thinkingBlocks.length > 0;

    return (
        <VerticalStack gap="2" align="start">
            {/* Chain of Thoughts — collapsible, collapsed by default */}
            {hasThoughts && (
                <Box>
                    <Button
                        plain
                        disclosure={thoughtsOpen ? 'up' : 'down'}
                        onClick={() => setThoughtsOpen(prev => !prev)}
                        ariaControls="chain-of-thoughts"
                    >
                        <Text variant="bodySm" tone="subdued">
                            Chain of Thoughts ({thinkingBlocks.length} steps)
                        </Text>
                    </Button>
                    <Collapsible
                        open={thoughtsOpen}
                        id="chain-of-thoughts"
                        transition={{ duration: '200ms', timingFunction: 'ease-in-out' }}
                    >
                        <Box
                            padding="3"
                            paddingInlineStart="4"
                            paddingInlineEnd="4"
                            background="bg-transparent-active-experimental"
                            borderRadius="3"
                            borderRadiusEndStart="0"
                        >
                            <VerticalStack gap="3">
                                {thinkingBlocks.map((block, index) => (
                                    <Box key={index}>
                                        <Text variant="bodySm" as="p" tone="subdued">
                                            Step {index + 1}
                                        </Text>
                                        <MarkdownViewer markdown={block} />
                                    </Box>
                                ))}
                            </VerticalStack>
                        </Box>
                    </Collapsible>
                </Box>
            )}

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

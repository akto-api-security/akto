import React, { useState, useEffect, useRef } from 'react';
import { Box, VerticalStack } from '@shopify/polaris';
import AiAnalysisCard from './components/AiAnalysisCard';
import AgenticSearchInput from '../../agentic/components/AgenticSearchInput';
import AgenticStreamingResponse from '../../agentic/components/AgenticStreamingResponse';
import AgenticUserMessage from '../../agentic/components/AgenticUserMessage';
import AgenticThinkingBox from '../../agentic/components/AgenticThinkingBox';
import "./style.css";

function AskAktoSection({ aiSummary, aiSummaryLoading, aiMessages, aiLoading, onGenerateAiOverview, onSendFollowUp }) {
    const [followUpValue, setFollowUpValue] = useState('');
    const chatScrollRef = useRef(null);
    const hasRequestedOverview = useRef(false);

    useEffect(() => {
        // Reset when switching to a new test result (aiSummary goes from truthy back to null)
        hasRequestedOverview.current = false;
    }, [onGenerateAiOverview]);

    useEffect(() => {
        if (onGenerateAiOverview && !aiSummary && !aiSummaryLoading && !hasRequestedOverview.current) {
            hasRequestedOverview.current = true;
            onGenerateAiOverview();
        }
    }, [aiSummary, aiSummaryLoading]);

    useEffect(() => {
        const el = chatScrollRef.current;
        if (!el) return;
        const observer = new MutationObserver(() => {
            el.scrollTop = el.scrollHeight;
        });
        observer.observe(el, { childList: true, subtree: true, characterData: true });
        return () => observer.disconnect();
    }, []);

    const handleSubmit = (value) => {
        if (value && value.trim()) {
            onSendFollowUp(value.trim());
            setFollowUpValue('');
        }
    };

    return (
        <Box padding="4">
            <AiAnalysisCard
                summary={aiSummary}
                isLoading={aiSummaryLoading}
                scrollRef={chatScrollRef}
                footer={
                    <AgenticSearchInput
                        value={followUpValue}
                        onChange={setFollowUpValue}
                        onSubmit={handleSubmit}
                        placeholder="Ask a follow up..."
                        isStreaming={aiLoading}
                        isFixed={false}
                        inputWidth="100%"
                        containerStyle={{ display: 'block' }}
                    />
                }
            >
                {aiMessages.length > 0 && (
                    <Box>
                        <VerticalStack gap="3">
                            {aiMessages.map((msg, idx) => (
                                msg.role === 'user' ? (
                                    <AgenticUserMessage key={msg._id || idx} content={msg.message} />
                                ) : (
                                    <AgenticStreamingResponse
                                        key={msg._id || idx}
                                        content={msg.message}
                                        skipStreaming={msg.isFromHistory || false}
                                    />
                                )
                            ))}
                            {aiLoading && <AgenticThinkingBox />}
                        </VerticalStack>
                    </Box>
                )}
            </AiAnalysisCard>
        </Box>
    );
}

export default AskAktoSection;

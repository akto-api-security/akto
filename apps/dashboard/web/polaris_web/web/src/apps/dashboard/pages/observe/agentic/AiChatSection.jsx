import React, { useState, useEffect, useRef, useCallback } from "react";
import { Box, HorizontalStack, VerticalStack, Text, Button } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
import "../../../components/layouts/style.css";
import AgenticSearchInput from "../../agentic/components/AgenticSearchInput";
import AgenticStreamingResponse from "../../agentic/components/AgenticStreamingResponse";
import AgenticUserMessage from "../../agentic/components/AgenticUserMessage";
import AgenticThinkingBox from "../../agentic/components/AgenticThinkingBox";
import { sendQuery } from "../../agentic/services/agenticService";

export default function AiChatSection({
    placeholder,
    resetKey,
    conversationType = "ASK_AKTO",
    chatMetadata,
}) {
    const [messages, setMessages] = useState([]);
    const [conversationId, setConversationId] = useState(null);
    const [inputValue, setInputValue] = useState("");
    const [userCollapsed, setUserCollapsed] = useState(false);
    const [aiLoading, setAiLoading] = useState(false);
    const bottomRef = useRef(null);

    const hasContent = inputValue.length > 0 || messages.length > 0;
    const expanded = hasContent && !userCollapsed;

    useEffect(() => {
        setMessages([]);
        setConversationId(null);
        setInputValue("");
        setUserCollapsed(false);
        setAiLoading(false);
    }, [resetKey]);

    useEffect(() => {
        if (messages.length) bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, aiLoading]);

    useEffect(() => {
        if (inputValue.length > 0) setUserCollapsed(false);
    }, [inputValue]);

    const handleSubmit = useCallback(async (val) => {
        const text = (val ?? inputValue).trim();
        if (!text || aiLoading) return;

        const userMsg = { _id: `user_${Date.now()}`, role: "user", message: text };
        setMessages((prev) => [...prev, userMsg]);
        setInputValue("");
        setUserCollapsed(false);
        setAiLoading(true);

        try {
            const response = await sendQuery(text, conversationId, conversationType, chatMetadata);
            if (response?.conversationId && !conversationId) {
                setConversationId(response.conversationId);
            }
            if (response?.response) {
                setMessages((prev) => [
                    ...prev,
                    {
                        _id: `system_${Date.now()}`,
                        role: "system",
                        message: response.response,
                        isFromHistory: false,
                    },
                ]);
            }
        } catch {
            setMessages((prev) => [
                ...prev,
                {
                    _id: `system_${Date.now()}`,
                    role: "system",
                    message: "Unable to get a response. Please try again.",
                    isFromHistory: true,
                },
            ]);
        } finally {
            setAiLoading(false);
        }
    }, [inputValue, aiLoading, conversationId, conversationType, chatMetadata]);

    return (
        <Box
            background="bg"
            borderColor="border"
            borderBlockStartWidth="1"
            className={expanded ? "agentic-chat agentic-chat--expanded" : "agentic-chat"}
            style={expanded ? { marginInlineStart: "var(--p-space-2)", marginInlineEnd: "var(--p-space-2)", marginBlockEnd: "var(--p-space-2)" } : { marginInlineStart: "var(--p-space-2)", marginInlineEnd: "var(--p-space-2)" }}
        >
            {expanded && (
                <Box
                    paddingBlockStart="2"
                    paddingBlockEnd="2"
                    paddingInlineStart="3"
                    paddingInlineEnd="3"
                    borderColor="border-subdued"
                    borderBlockEndWidth="1"
                    className="agentic-chat-header"
                >
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingXs">Ask Akto</Text>
                        <Button
                            icon={ChevronDownMinor}
                            size="slim"
                            onClick={() => setUserCollapsed(true)}
                        />
                    </HorizontalStack>
                </Box>
            )}

            {expanded && messages.length > 0 && (
                <Box
                    paddingBlockStart="3"
                    paddingBlockEnd="3"
                    paddingInlineStart="4"
                    paddingInlineEnd="4"
                    overflowY="scroll"
                    className="agentic-chat-messages"
                >
                    <VerticalStack gap="2">
                        {messages.map((msg, i) => (
                            msg.role === "user" ? (
                                <AgenticUserMessage key={msg._id || i} content={msg.message} />
                            ) : (
                                <AgenticStreamingResponse
                                    key={msg._id || i}
                                    content={msg.message}
                                    skipStreaming={msg.isFromHistory || false}
                                />
                            )
                        ))}
                        {aiLoading && <AgenticThinkingBox />}
                        <Box ref={bottomRef} />
                    </VerticalStack>
                </Box>
            )}

            {expanded && messages.length === 0 && !aiLoading && (
                <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="2" paddingBlockEnd="2" className="agentic-chat-empty">
                    <Text variant="bodySm" color="subdued">Press Enter to ask…</Text>
                </Box>
            )}

            <Box
                paddingBlockStart="3"
                paddingBlockEnd="3"
                paddingInlineStart="4"
                paddingInlineEnd="4"
                className="agentic-chat-input"
            >
                <AgenticSearchInput
                    placeholder={placeholder || "Ask anything related to your endpoints..."}
                    isFixed={false}
                    inputWidth="100%"
                    containerStyle={{ display: "block" }}
                    value={inputValue}
                    onChange={setInputValue}
                    onSubmit={handleSubmit}
                    isStreaming={aiLoading}
                />
            </Box>
        </Box>
    );
}

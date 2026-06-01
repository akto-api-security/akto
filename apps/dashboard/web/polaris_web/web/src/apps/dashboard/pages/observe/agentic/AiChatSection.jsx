import React, { useState, useEffect, useRef, useCallback } from "react";
import { HorizontalStack, Text, Button } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
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
        <div style={{
            display: "flex",
            flexDirection: "column",
            borderTop: "1px solid #E1E3E5",
            background: "white",
            flexShrink: expanded ? undefined : 0,
            flex: expanded ? 1 : undefined,
            minHeight: expanded ? 0 : undefined,
        }}>
            {expanded && (
                <div style={{ padding: "8px 12px", borderBottom: "1px solid #F1F2F3", flexShrink: 0 }}>
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingXs">Ask Akto</Text>
                        <Button
                            icon={ChevronDownMinor}
                            size="slim"
                            onClick={() => setUserCollapsed(true)}
                        />
                    </HorizontalStack>
                </div>
            )}

            {expanded && messages.length > 0 && (
                <div style={{
                    flex: 1,
                    minHeight: 0,
                    overflowY: "auto",
                    padding: "12px 16px",
                    display: "flex",
                    flexDirection: "column",
                    gap: 8,
                }}>
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
                    <div ref={bottomRef} />
                </div>
            )}

            {expanded && messages.length === 0 && !aiLoading && (
                <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <span style={{ fontSize: 12, color: "#C4C9D0" }}>Press Enter to ask…</span>
                </div>
            )}

            <div style={{ padding: "10px 16px 12px", flexShrink: 0 }}>
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
            </div>
        </div>
    );
}

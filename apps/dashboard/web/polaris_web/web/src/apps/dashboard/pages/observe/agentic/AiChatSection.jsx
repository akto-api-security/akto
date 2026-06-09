import React, { useState, useEffect, useRef, useCallback } from "react";
import { Box, HorizontalStack, VerticalStack, Text, Button, Divider } from "@shopify/polaris";
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
    const messagesRef = useRef(null);
    const [resizeHeight, setResizeHeight] = useState(null);
    const dragRef = useRef({ isDragging: false, startY: 0, startHeight: 0 });
    const sectionRef = useRef(null);

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
        if (messages.length && messagesRef.current) {
            messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
        }
    }, [messages, aiLoading]);

    useEffect(() => {
        if (inputValue.length > 0) setUserCollapsed(false);
    }, [inputValue]);

    useEffect(() => {
        if (!expanded) setResizeHeight(null);
    }, [expanded]);

    const handleDragMove = useCallback((e) => {
        if (!dragRef.current.isDragging) return;
        const dy = dragRef.current.startY - e.clientY;
        const newH = Math.max(60, dragRef.current.startHeight + dy);
        setResizeHeight(newH);
    }, []);

    const handleDragEnd = useCallback(() => {
        dragRef.current.isDragging = false;
        document.removeEventListener('mousemove', handleDragMove);
        document.removeEventListener('mouseup', handleDragEnd);
    }, [handleDragMove]);

    const handleDragStart = useCallback((e) => {
        e.preventDefault();
        if (!sectionRef.current) return;
        const startHeight = sectionRef.current.offsetHeight;
        dragRef.current = { isDragging: true, startY: e.clientY, startHeight };
        document.addEventListener('mousemove', handleDragMove);
        document.addEventListener('mouseup', handleDragEnd);
    }, [handleDragMove, handleDragEnd]);

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
        <>
            {expanded && <Box paddingBlockStart="2" paddingBlockEnd="2"><Divider /></Box>}
            <Box
                ref={sectionRef}
                className={"agentic-chat agentic-chat-section" + (expanded && !resizeHeight ? " agentic-chat--expanded" : "")}
                style={resizeHeight ? { height: resizeHeight + "px" } : undefined}
            >
                <Box
                    background="bg"
                    borderColor="border"
                    paddingInlineStart="4"
                    paddingInlineEnd="4"
                    className="agentic-chat agentic-chat-fill"
                >
                    {expanded && (
                        <Box
                            paddingBlockStart="2"
                            paddingBlockEnd="2"
                            paddingInlineStart="1"
                            paddingInlineEnd="2"
                            borderColor="border-subdued"
                            borderBlockEndWidth="1"
                            className="agentic-chat-header agentic-chat-drag-handle"
                            onMouseDown={handleDragStart}
                        >
                            <HorizontalStack align="space-between" blockAlign="center">
                                <Text variant="headingXs">Ask Akto</Text>
                                <Button
                                    icon={ChevronDownMinor}
                                    size="slim"
                                    onClick={() => setUserCollapsed(prev => !prev)}
                                />
                            </HorizontalStack>
                        </Box>
                    )}

                    {expanded && messages.length > 0 && (
                        <div ref={messagesRef} style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: "12px 0" }}>
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
                            </VerticalStack>
                        </div>
                    )}

                    {expanded && messages.length === 0 && !aiLoading && (
                        <Box paddingBlockStart="2" paddingBlockEnd="2" className="agentic-chat-empty">
                            <Text variant="bodySm" color="subdued">Press Enter to ask…</Text>
                        </Box>
                    )}

                    <Box
                        paddingBlockStart="3"
                        paddingBlockEnd="3"
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
            </Box>
        </>
    );
}

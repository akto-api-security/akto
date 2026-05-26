import React, { useState, useEffect, useRef, useCallback } from "react";
import { HorizontalStack, Text, Button } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
import AgenticSearchInput from "../../agentic/components/AgenticSearchInput";
import { MOCK_RESPONSE } from "./agenticDummyData";

export default function AiChatSection({ placeholder, resetKey }) {
    const [messages,      setMessages]     = useState([]);
    const [inputValue,    setInputValue]   = useState("");
    const [userCollapsed, setUserCollapsed] = useState(false);
    const bottomRef = useRef(null);

    const hasContent = inputValue.length > 0 || messages.length > 0;
    const expanded   = hasContent && !userCollapsed;

    useEffect(() => {
        setMessages([]);
        setInputValue("");
        setUserCollapsed(false);
    }, [resetKey]);

    useEffect(() => {
        if (messages.length) bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages]);

    useEffect(() => {
        if (inputValue.length > 0) setUserCollapsed(false);
    }, [inputValue]);

    const handleSubmit = useCallback((val) => {
        const text = (val ?? inputValue).trim();
        if (!text) return;
        setMessages(prev => [
            ...prev,
            { role: "user",      content: text          },
            { role: "assistant", content: MOCK_RESPONSE },
        ]);
        setInputValue("");
        setUserCollapsed(false);
    }, [inputValue]);

    return (
        <div style={{
            display: "flex",
            flexDirection: "column",
            borderTop: "1px solid #E1E3E5",
            background: "white",
            flexShrink: expanded ? undefined : 0,
            flex:       expanded ? 1        : undefined,
            minHeight:  expanded ? 0        : undefined,
        }}>
            {/* Header bar with collapse button — only when expanded */}
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

            {/* Message history */}
            {expanded && messages.length > 0 && (
                <div style={{
                    flex: 1,
                    minHeight: 0,
                    overflowY: "auto",
                    padding: "12px 16px",
                    display: "flex",
                    flexDirection: "column",
                    gap: 4,
                }}>
                    {messages.map((msg, i) =>
                        msg.role === "user" ? (
                            <div key={i} style={{ display: "flex", justifyContent: "flex-end", marginBottom: 2 }}>
                                <div style={{
                                    maxWidth: "72%",
                                    padding: "7px 12px",
                                    borderRadius: "12px 12px 2px 12px",
                                    background: "#F1F2F3",
                                    fontSize: 13,
                                    color: "#202223",
                                    lineHeight: 1.5,
                                }}>
                                    {msg.content}
                                </div>
                            </div>
                        ) : (
                            <div key={i} style={{
                                fontSize: 13,
                                color: "#202223",
                                lineHeight: 1.6,
                                marginBottom: 8,
                            }}>
                                {msg.content}
                            </div>
                        )
                    )}
                    <div ref={bottomRef} />
                </div>
            )}

            {/* Hint when expanded but nothing sent yet */}
            {expanded && messages.length === 0 && (
                <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <span style={{ fontSize: 12, color: "#C4C9D0" }}>Press Enter to ask…</span>
                </div>
            )}

            {/* Input */}
            <div style={{ padding: "10px 16px 12px", flexShrink: 0 }}>
                <AgenticSearchInput
                    placeholder={placeholder || "Ask anything related to your endpoints..."}
                    isFixed={false}
                    inputWidth="100%"
                    containerStyle={{ display: "block" }}
                    value={inputValue}
                    onChange={setInputValue}
                    onSubmit={handleSubmit}
                />
            </div>
        </div>
    );
}

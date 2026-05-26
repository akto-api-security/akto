import React, { useState, useEffect, useRef, useCallback } from "react";
import AgenticSearchInput from "../../agentic/components/AgenticSearchInput";

const MOCK_RESPONSE =
    "I've analysed the request context. Based on the endpoint traffic and skill invocation patterns, I can see some anomalous behavior. The prompt appears to violate the configured security policy — this type of request would be flagged as a critical violation.";

/**
 * AiChatSection — shared split-screen AI chat footer for all flyouts.
 *
 * Behaviour:
 *  • Collapsed (default): pinned at bottom, acts as a normal input bar.
 *  • Expanded: as soon as the user starts typing, the section grows to flex:1,
 *    splitting the flyout 50/50 — content above, AI chat below.
 *
 * Props:
 *   placeholder  – input placeholder text
 *   resetKey     – change to reset messages + input (e.g. pass agent?.endpoint)
 */
export default function AiChatSection({ placeholder, resetKey }) {
    const [messages,    setMessages]    = useState([]);
    const [inputValue,  setInputValue]  = useState("");
    const bottomRef = useRef(null);

    const expanded = inputValue.length > 0 || messages.length > 0;

    // Reset when the context changes (different device / agent / mcp)
    useEffect(() => {
        setMessages([]);
        setInputValue("");
    }, [resetKey]);

    // Auto-scroll messages
    useEffect(() => {
        if (messages.length) bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages]);

    const handleSubmit = useCallback((val) => {
        const text = (val ?? inputValue).trim();
        if (!text) return;
        setMessages(prev => [
            ...prev,
            { role: "user",      content: text          },
            { role: "assistant", content: MOCK_RESPONSE },
        ]);
        setInputValue("");
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
            {/* Message history — only rendered once there are messages */}
            {messages.length > 0 && (
                <div style={{
                    flex: 1,
                    minHeight: 0,
                    overflowY: "auto",
                    padding: "12px 16px",
                    display: "flex",
                    flexDirection: "column",
                    gap: 10,
                }}>
                    {messages.map((msg, i) =>
                        msg.role === "user" ? (
                            <div key={i} style={{ display: "flex", justifyContent: "flex-end" }}>
                                <div style={{
                                    maxWidth: "72%",
                                    padding: "8px 12px",
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
                            <div key={i} style={{ fontSize: 13, color: "#202223", lineHeight: 1.65 }}>
                                {msg.content}
                            </div>
                        )
                    )}
                    <div ref={bottomRef} />
                </div>
            )}

            {/* Empty-state hint when expanded but no messages yet */}
            {expanded && messages.length === 0 && (
                <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <span style={{ fontSize: 12, color: "#C4C9D0" }}>Press Enter to ask…</span>
                </div>
            )}

            {/* Input bar */}
            <div style={{ padding: "12px 16px", flexShrink: 0 }}>
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

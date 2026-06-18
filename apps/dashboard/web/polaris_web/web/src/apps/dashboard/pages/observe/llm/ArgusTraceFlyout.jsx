import { useEffect, useMemo, useState } from "react";
import { Box, Divider, HorizontalGrid, HorizontalStack, Scrollable, Text, VerticalStack } from "@shopify/polaris";
import AgenticFlyoutShell from "../agentic/AgenticFlyoutShell";
import FlyoutBreadcrumb from "../agentic/FlyoutBreadcrumb";
import AiChatSection from "../agentic/AiChatSection";
import { buildAgenticObserveChatMetadata } from "../agentic/agenticObserveApi";
import ConversationHistory from "../../testing/TestRunResultPage/components/ConversationHistory";
import { formatCost, formatCompact, formatDurationMs, parsePromptText, parseResponseText, truncate } from "./constants";
import api from "./api";


function formatTs(ts) {
    if (!ts) return "-";
    const ms = ts > 1e10 ? ts : ts * 1000;
    return new Date(ms).toLocaleString("en-US", {
        month: "numeric", day: "numeric", year: "2-digit",
        hour: "numeric", minute: "2-digit", hour12: true,
    });
}

export default function ArgusTraceFlyout({ trace, onClose }) {
    const [conversations, setConversations] = useState([]);

    useEffect(() => {
        if (!trace?.traceId) { setConversations([]); return; }
        let cancelled = false;

        // Seed immediately from the trace-level row so something shows while spans load
        const seed = [];
        if (trace._promptText)   seed.push({ role: "user",      message: trace._promptText,   customLabel: "User prompt",        creationTimestamp: trace.latestTimestamp });
        if (trace._responseText) seed.push({ role: "assistant", message: trace._responseText, customLabel: "AI agent response",  creationTimestamp: trace.latestTimestamp });
        setConversations(seed);

        // Fetch span-level rows — these carry the actual responsePayload
        api.fetchTraceDetail(trace.traceId).then(spans => {
            if (cancelled || !spans?.length) return;
            const msgs = [];
            spans.forEach(span => {
                const prompt   = parsePromptText(span.queryPayload)      || span._promptText   || "";
                const response = parseResponseText(span.responsePayload) || span._responseText || "";
                // Span 2+ in a tool-use trace re-includes the original question in queryPayload.
                // Only push the user message when it differs from the last one seen.
                const lastUserMsg = [...msgs].reverse().find(m => m.role === "user");
                if (prompt && prompt !== lastUserMsg?.message) {
                    msgs.push({ role: "user",      message: prompt,   customLabel: "User prompt",       creationTimestamp: span.timestamp });
                }
                if (response) msgs.push({ role: "assistant", message: response, customLabel: "AI agent response", creationTimestamp: span.timestamp });
            });
            if (msgs.length) setConversations(msgs);
        });

        return () => { cancelled = true; };
    }, [trace?.traceId]);

    const chatMetadata = useMemo(() => {
        if (!trace) return null;
        return buildAgenticObserveChatMetadata("session", {
            sessionId: trace.traceId,
            serviceId: trace.serviceId,
            userName:  trace.userName,
            model:     trace._model,
        });
    }, [trace?.traceId]);

    if (!trace) return null;

    const totalTokens = (Number(trace._inputTokens) || 0) + (Number(trace._outputTokens) || 0);

    const stats = [
        { label: "Duration",      value: formatDurationMs(trace.durationMs) },
        { label: "Total tokens",  value: formatCompact(totalTokens) },
        { label: "Est. cost",     value: formatCost(trace._inputTokens || 0, trace._outputTokens || 0) },
        { label: "Tokens in/out", value: `${(trace._inputTokens || 0).toLocaleString("en-US")} / ${(trace._outputTokens || 0).toLocaleString("en-US")}` },
    ];

    const metaItems = [
        { label: "Time",        value: formatTs(trace.latestTimestamp) },
        { label: "Application", value: trace.serviceId || "-" },
    ];

    return (
        <AgenticFlyoutShell
            show={!!trace}
            width={800}
            header={
                <FlyoutBreadcrumb
                    items={[{ label: trace._promptText ? truncate(trace._promptText, 40) : "Trace" }]}
                    onClose={onClose}
                />
            }
            footer={
                <AiChatSection
                    placeholder="Ask anything about this trace..."
                    resetKey={trace?.traceId}
                    conversationType="AGENTIC_OBSERVE"
                    chatMetadata={chatMetadata}
                />
            }
        >
            <Scrollable style={{ flex: 1 }}>
                <Box padding="4">
                    <VerticalStack gap="5">
                        {/* Stats */}
                        <HorizontalGrid columns={4} gap="3">
                            {stats.map(s => (
                                <VerticalStack gap="1" key={s.label}>
                                    <Text variant="heading2xl" as="p">{s.value}</Text>
                                    <Text variant="bodySm" color="subdued">{s.label}</Text>
                                </VerticalStack>
                            ))}
                        </HorizontalGrid>

                        <Divider />

                        {/* Time + Application below stats */}
                        <HorizontalStack gap="10">
                            {metaItems.map(m => (
                                <VerticalStack gap="1" key={m.label}>
                                    <Text variant="bodySm" color="subdued">{m.label}</Text>
                                    <Text variant="bodySm" fontWeight="medium">{m.value}</Text>
                                </VerticalStack>
                            ))}
                        </HorizontalStack>

                        {/* Flow graph */}
                        {/* <TraceFlowGraph trace={trace} /> */}

                        {/* Conversation */}
                        <ConversationHistory conversations={conversations} />
                    </VerticalStack>
                </Box>
            </Scrollable>
        </AgenticFlyoutShell>
    );
}

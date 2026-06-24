import { useEffect, useMemo, useState } from "react";
import { Box, Divider, HorizontalGrid, HorizontalStack, Scrollable, Text, VerticalStack } from "@shopify/polaris";
import InfoTooltipIcon from "@/apps/dashboard/components/shared/InfoTooltipIcon";
import AgenticFlyoutShell from "../agentic/AgenticFlyoutShell";
import FlyoutBreadcrumb from "../agentic/FlyoutBreadcrumb";
import AiChatSection from "../agentic/AiChatSection";
import { buildAgenticObserveChatMetadata } from "../agentic/agenticObserveApi";
import ConversationHistory from "../../testing/TestRunResultPage/components/ConversationHistory";
import { formatCompact, parsePromptText, parseResponseText, truncate, TOKEN_ESTIMATE_TOOLTIP } from "./constants";
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
        const traceKey = trace?.traceId || trace?.id;
        if (!traceKey) { setConversations([]); return; }
        let cancelled = false;

        // Seed from the row's own payloads — works even when traceId is absent
        const promptText   = trace._promptText   || parsePromptText(trace.queryPayload)      || "";
        const responseText = trace._responseText || parseResponseText(trace.responsePayload) || "";
        const ts = trace.latestTimestamp || trace.timestamp;
        const seed = [];
        if (promptText)   seed.push({ role: "user",      message: promptText,   customLabel: "User prompt",       creationTimestamp: ts });
        if (responseText) seed.push({ role: "assistant", message: responseText, customLabel: "AI agent response", creationTimestamp: ts });
        setConversations(seed);

        // If a real traceId exists, fetch span-level rows for richer detail
        if (trace.traceId) {
            api.fetchTraceDetail(trace.traceId).then(spans => {
                if (cancelled || !spans?.length) return;
                const msgs = [];
                spans.forEach(span => {
                    const prompt   = parsePromptText(span.queryPayload)      || span._promptText   || "";
                    const response = parseResponseText(span.responsePayload) || span._responseText || "";
                    const lastUserMsg = [...msgs].reverse().find(m => m.role === "user");
                    if (prompt && prompt !== lastUserMsg?.message) {
                        msgs.push({ role: "user",      message: prompt,   customLabel: "User prompt",       creationTimestamp: span.timestamp });
                    }
                    if (response) msgs.push({ role: "assistant", message: response, customLabel: "AI agent response", creationTimestamp: span.timestamp });
                });
                if (msgs.length) setConversations(msgs);
            });
        }

        return () => { cancelled = true; };
    }, [trace]);

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
        { label: "Total tokens",  value: formatCompact(totalTokens),                                                                                         tooltip: TOKEN_ESTIMATE_TOOLTIP },
        { label: "Tokens in/out", value: `${(trace._inputTokens || 0).toLocaleString("en-US")} / ${(trace._outputTokens || 0).toLocaleString("en-US")}`, tooltip: TOKEN_ESTIMATE_TOOLTIP },
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
                                    <HorizontalStack gap="1" blockAlign="center">
                                        <Text variant="bodySm" color="subdued">{s.label}</Text>
                                        <InfoTooltipIcon content={s.tooltip} />
                                    </HorizontalStack>
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

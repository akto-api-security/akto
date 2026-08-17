import { useEffect, useMemo, useState } from "react";
import { Badge, Box, Divider, HorizontalGrid, HorizontalStack, Scrollable, Text, VerticalStack } from "@shopify/polaris";
import InfoTooltipIcon from "@/apps/dashboard/components/shared/InfoTooltipIcon";
import AgenticFlyoutShell from "../agentic/AgenticFlyoutShell";
import FlyoutBreadcrumb from "../agentic/FlyoutBreadcrumb";
import AiChatSection from "../agentic/AiChatSection";
import { buildAgenticObserveChatMetadata } from "../agentic/agenticObserveApi";
import ChatMessage from "../../testing/TestRunResultPage/components/ChatMessage";
import { formatCompact, parsePromptText, parseResponseText, truncate, TOKEN_ESTIMATE_TOOLTIP } from "./constants";
import api from "./api";
import { GuardrailBanner, hasGuardrailVerdict, verdictStatus, verdictTone } from "./GuardrailVerdict";


function toSeconds(ts) {
    if (!ts) return 0;
    return ts > 1e10 ? Math.floor(ts / 1000) : ts;
}

const promptWord = (n) => (n === 1 ? "prompt" : "prompts");

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
    const [spanRows, setSpanRows] = useState([]);

    useEffect(() => {
        const traceKey = trace?.traceId || trace?.id;
        if (!traceKey) { setConversations([]); setSpanRows([]); return; }
        let cancelled = false;

        // Seed from the row's own payloads — works even when traceId is absent
        const promptText   = trace._promptText   || parsePromptText(trace.queryPayload)      || "";
        const responseText = trace._responseText || parseResponseText(trace.responsePayload) || "";
        const ts = trace.latestTimestamp || trace.timestamp;
        const seed = [];
        if (promptText)   seed.push({ role: "user",      message: promptText,   customLabel: "User prompt",       creationTimestamp: ts, span: trace });
        if (responseText) seed.push({ role: "assistant", message: responseText, customLabel: "AI agent response", creationTimestamp: ts });
        setConversations(seed);
        setSpanRows([]);

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
                        msgs.push({ role: "user",      message: prompt,   customLabel: "User prompt",       creationTimestamp: span.timestamp, span });
                    }
                    if (response) msgs.push({ role: "assistant", message: response, customLabel: "AI agent response", creationTimestamp: span.timestamp });
                });
                if (msgs.length) setConversations(msgs);
                setSpanRows(spans);
            });
        }

        return () => { cancelled = true; };
    }, [trace]);

    // Guardrail results saved on this trace's spans. Uses the row itself when the trace has no
    // separate span rows, so a trace with one span still shows its result.
    const evaluatedSpans = useMemo(() => {
        const rows = spanRows.length ? spanRows : (trace ? [trace] : []);
        return rows.filter(hasGuardrailVerdict);
    }, [spanRows, trace]);
    const violatingSpans = useMemo(
        () => evaluatedSpans.filter(s => s.guardrailViolated),
        [evaluatedSpans],
    );
    // Show the trace badge as critical only when a prompt was actually blocked. If every hit was a
    // warn or alert the traffic went through, so it should read as caution.
    const summaryStatus = useMemo(
        () => (violatingSpans.some(s => verdictStatus(s) === "critical") ? "critical" : "warning"),
        [violatingSpans],
    );

    // One box per exchange: a user prompt and the responses after it. Keeps the guardrail result
    // next to its own turn instead of splitting the prompt from the response.
    const turns = useMemo(() => {
        const out = [];
        conversations.forEach((msg) => {
            if (msg.role === "user" || out.length === 0) {
                out.push({ span: msg.span, messages: [msg] });
            } else {
                out[out.length - 1].messages.push(msg);
            }
        });
        return out;
    }, [conversations]);

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
        { label: "Session ID",  value: trace.sessionIdentifier || "-" },
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

                        {/* Guardrail results saved on this trace */}
                        {evaluatedSpans.length > 0 && (
                            <HorizontalStack gap="2" blockAlign="center" wrap>
                                <Text variant="bodySm" color="subdued" fontWeight="semibold">GUARDRAIL</Text>
                                {violatingSpans.length === 0 ? (
                                    <Badge status="success" size="small">
                                        {`All ${evaluatedSpans.length} ${promptWord(evaluatedSpans.length)} passed`}
                                    </Badge>
                                ) : (
                                    <Badge status={summaryStatus} size="small">
                                        {`${violatingSpans.length} of ${evaluatedSpans.length} ${promptWord(evaluatedSpans.length)} hit a policy`}
                                    </Badge>
                                )}
                            </HorizontalStack>
                        )}

                        {/* Flow graph */}
                        {/* <TraceFlowGraph trace={trace} /> */}

                        {/* Conversation: one box per exchange, with a small banner on top when
                            that exchange hit a guardrail. */}
                        <VerticalStack gap="4">
                            {turns.map((turn, i) => {
                                const tone = verdictTone(turn.span);
                                return (
                                    <Box
                                        key={turn.span?.spanId ? `${turn.span.spanId}-${i}` : `turn-${i}`}
                                        borderWidth="1"
                                        borderRadius="2"
                                        borderColor={tone ? tone.border : "border-subdued"}
                                        background="bg"
                                    >
                                        {tone && (
                                            <Box background={tone.bg} padding="2" borderRadius="2">
                                                <GuardrailBanner span={turn.span} />
                                            </Box>
                                        )}
                                        <Box padding="3">
                                            <VerticalStack gap="3">
                                                {turn.messages.map((msg, j) => (
                                                    <ChatMessage
                                                        key={`m-${i}-${j}`}
                                                        type={msg.role === "user" ? "request" : "response"}
                                                        content={msg.message}
                                                        timestamp={toSeconds(msg.creationTimestamp)}
                                                        customLabel={msg.customLabel}
                                                        isCode={false}
                                                        toolsMetadata={{}}
                                                    />
                                                ))}
                                            </VerticalStack>
                                        </Box>
                                    </Box>
                                );
                            })}
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </Scrollable>
        </AgenticFlyoutShell>
    );
}

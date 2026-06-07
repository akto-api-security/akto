import { useEffect, useState } from "react";
import { Badge, Box, Button, HorizontalStack, Scrollable, Spinner, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import api from "./api";
import { enrichRow } from "./utils";
import PromptDetailModal from "./PromptDetailModal";
import ChatMessage from "../../testing/TestRunResultPage/components/ChatMessage";

export default function SpansPanel({ traceId }) {
    const [spans, setSpans] = useState([]);
    const [loading, setLoading] = useState(false);
    const [expandedSpan, setExpandedSpan] = useState(null);

    useEffect(() => {
        if (!traceId) { setSpans([]); return; }
        let cancelled = false;
        setLoading(true);
        setExpandedSpan(null);
        api.fetchTraceDetail(traceId)
            .then(rows => { if (!cancelled) setSpans((rows || []).map(enrichRow)); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [traceId]);

    if (loading) {
        return (
            <Box padding="6">
                <HorizontalStack align="center"><Spinner size="small" /></HorizontalStack>
            </Box>
        );
    }

    if (spans.length === 0) {
        return (
            <Box padding="6">
                <Text tone="subdued" variant="bodySm">No spans for this message.</Text>
            </Box>
        );
    }

    return (
        <>
            <Scrollable style={{ flex: 1, minHeight: 0 }}>
                <Box padding="4">
                    <VerticalStack gap="0">
                        {spans.map((span, idx) => (
                            <SpanFlowRow
                                key={span.id || idx}
                                span={span}
                                index={idx}
                                isLast={idx === spans.length - 1}
                                onExpand={() => setExpandedSpan(span)}
                            />
                        ))}
                    </VerticalStack>
                </Box>
            </Scrollable>
            <PromptDetailModal prompt={expandedSpan} onClose={() => setExpandedSpan(null)} />
        </>
    );
}

export function SpanFlowRow({ span, index, isLast, onExpand }) {
    const tokens = (span._inputTokens || 0) + (span._outputTokens || 0);
    const spanTimestamp = Math.floor((span.timestamp || 0) / 1000);

    return (
        <div style={{ display: "flex", gap: 0, marginBottom: isLast ? 0 : 24 }}>
            {/* Connector column */}
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", width: 32, flexShrink: 0 }}>
                <div style={{
                    width: 20, height: 20, borderRadius: "50%",
                    background: "var(--p-color-bg-fill-info)",
                    display: "flex", alignItems: "center", justifyContent: "center",
                    flexShrink: 0, marginTop: 14,
                }}>
                    <Text variant="bodySm" fontWeight="semibold" tone="text-inverse">
                        {index + 1}
                    </Text>
                </div>
                {!isLast && (
                    <div style={{
                        width: 2, flex: 1, minHeight: 16,
                        background: "var(--p-color-border)",
                        marginTop: 4, marginBottom: 4,
                    }} />
                )}
            </div>

            {/* Span card */}
            <Box style={{ flex: 1, minWidth: 0 }}>
                <VerticalStack gap="2">
                    {/* Span meta header */}
                    <HorizontalStack align="space-between" blockAlign="center">
                        <HorizontalStack gap="2" blockAlign="center">
                            <Badge tone="info">{"Span " + (index + 1)}</Badge>
                            {span.serviceId && <Badge>{span.serviceId}</Badge>}
                            {span._model && <Badge tone="success">{span._model}</Badge>}
                        </HorizontalStack>
                        <HorizontalStack gap="2" blockAlign="center">
                            {tokens > 0 &&
                                <Text variant="bodySm" tone="subdued">{tokens + " tok"}</Text>}
                            <Text variant="bodySm" tone="subdued">
                                {func.prettifyEpoch(spanTimestamp)}
                            </Text>
                            <Button plain monochrome onClick={onExpand}>
                                View full detail →
                            </Button>
                        </HorizontalStack>
                    </HorizontalStack>

                    {/* Input rendered as ChatMessage request */}
                    {span._promptText && (
                        <ChatMessage
                            type="request"
                            content={span._promptText}
                            timestamp={spanTimestamp}
                            customLabel={"Span " + (index + 1) + " Input"}
                            isCode={false}
                            toolsMetadata={{}}
                        />
                    )}

                    {/* Output rendered as ChatMessage response */}
                    {span._responseText && (
                        <ChatMessage
                            type="response"
                            content={span._responseText}
                            timestamp={spanTimestamp}
                            customLabel={"Span " + (index + 1) + " Output"}
                            isCode={false}
                            toolsMetadata={{}}
                        />
                    )}
                </VerticalStack>
            </Box>
        </div>
    );
}

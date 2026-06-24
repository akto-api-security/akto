import { useEffect, useMemo, useState } from "react";
import { Badge, Box, Button, Divider, HorizontalStack, Link, Scrollable, Spinner, Text, VerticalStack } from "@shopify/polaris";
import { CancelMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import api from "./api";
import { enrichRow } from "./utils";
import { classifySpan, durationColor, formatDurationMs, SPAN_TYPE_LABEL, truncate } from "./constants";
import { ModelChip } from "./LLMCellRenderers";
import SpanBar from "./SpanBar";
import AgenticFlyoutShell from "../agentic/AgenticFlyoutShell";

// ─── small reusable pieces ────────────────────────────────────────────────────

function FlyoutMetaItem({ label, children }) {
    return (
        <VerticalStack gap="0">
            <Text variant="bodySm" color="subdued">{label}</Text>
            <Box paddingBlockStart="05">{children}</Box>
        </VerticalStack>
    );
}

function WaterfallRow({ name, offset, width, durationMs, color }) {
    return (
        <HorizontalStack gap="3" blockAlign="center" wrap={false}>
            <Box minWidth="120px" maxWidth="120px">
                <Text variant="bodySm" color="subdued" truncate>{name}</Text>
            </Box>
            <Box width="100%"><SpanBar offset={offset} width={width} color={color} /></Box>
            <Box minWidth="56px">
                <Text variant="bodySm" color="subdued" alignment="end">{formatDurationMs(durationMs)}</Text>
            </Box>
        </HorizontalStack>
    );
}

function MessagePreview({ span }) {
    const text = span._responseText || span._promptText || "";
    if (!text) return null;
    const ts = Math.floor((span.timestamp || 0) / 1000);
    const kind = classifySpan(span);
    return (
        <Box background="bg-subdued" borderRadius="2" borderWidth="1" borderColor="border-subdued" padding="2">
            <VerticalStack gap="1">
                <HorizontalStack gap="2" blockAlign="center">
                    <Badge>{SPAN_TYPE_LABEL[kind] || "Span"}</Badge>
                    {span._model && <Badge tone="success">{span._model}</Badge>}
                    <Box width="100%" />
                    <Text variant="bodySm" color="subdued">{func.prettifyEpoch(ts)}</Text>
                </HorizontalStack>
                <Text variant="bodySm">{truncate(text, 240)}</Text>
            </VerticalStack>
        </Box>
    );
}

// ─── flyout ───────────────────────────────────────────────────────────────────

export default function TraceFlyout({ trace, onClose, onOpenSession, onViewMessages }) {
    const [spans, setSpans] = useState([]);
    const [loading, setLoading] = useState(false);

    const traceId = trace?.traceId;

    useEffect(() => {
        if (!traceId) { setSpans([]); return; }
        let cancelled = false;
        setLoading(true);
        api.fetchTraceDetail(traceId)
            .then(rows => { if (!cancelled) setSpans((rows || []).map(enrichRow)); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [traceId]);

    // Derive a waterfall from span timestamps relative to the trace window.
    const waterfall = useMemo(() => {
        if (!spans.length) return [];
        const times = spans.map(s => Number(s.timestamp) || 0).filter(Boolean);
        const start = Math.min(...times);
        const end = Math.max(...times);
        const total = end - start || 1;
        const rows = spans.map((s, i) => {
            const t = Number(s.timestamp) || start;
            const next = spans[i + 1] ? (Number(spans[i + 1].timestamp) || end) : end;
            const dur = Math.max(0, next - t);
            return {
                name: truncate(s._promptText || s.spanId || ("span " + (i + 1)), 40),
                offset: (t - start) / total,
                width: dur / total,
                durationMs: dur,
            };
        });
        // Colour bars on a duration ramp relative to the slowest span in this trace.
        const maxDur = Math.max(1, ...rows.map(r => r.durationMs));
        return rows.map(r => ({ ...r, color: durationColor(r.durationMs, maxDur) }));
    }, [spans]);

    if (!trace) return null;

    const header = (
        <Box padding="4" borderBlockEndWidth="1" borderColor="border-subdued">
            <HorizontalStack align="space-between" blockAlign="start" wrap={false}>
                <VerticalStack gap="0">
                    <Text variant="headingMd">{truncate(trace._promptText || trace.traceId, 48)}</Text>
                    <Text variant="bodySm" color="subdued">{trace.traceId}</Text>
                </VerticalStack>
                <Button icon={CancelMinor} plain onClick={onClose} accessibilityLabel="Close" />
            </HorizontalStack>
        </Box>
    );

    const footer = (
        <Box padding="4" borderBlockStartWidth="1" borderColor="border-subdued">
            <Button fullWidth onClick={() => onViewMessages?.(trace.traceId)}>View all messages</Button>
        </Box>
    );

    return (
        <AgenticFlyoutShell show={!!trace} width={680} header={header} footer={footer}>
            <Scrollable style={{ flex: 1, minHeight: 0 }}>
                <Box padding="4">
                    <VerticalStack gap="5">
                        {/* Meta grid */}
                        <HorizontalStack gap="6" align="start">
                            <FlyoutMetaItem label="Application">
                                <Text variant="bodySm">{trace.serviceId || "-"}</Text>
                            </FlyoutMetaItem>
                            <FlyoutMetaItem label="Session">
                                {trace.sessionIdentifier ? (
                                    <Link removeUnderline monochrome onClick={() => onOpenSession?.(trace.sessionIdentifier)}>
                                        <Text variant="bodySm">{truncate(trace.sessionIdentifier, 20)}</Text>
                                    </Link>
                                ) : <Text variant="bodySm" color="subdued">-</Text>}
                            </FlyoutMetaItem>
                            <FlyoutMetaItem label="Model"><ModelChip model={trace._model} /></FlyoutMetaItem>
                        </HorizontalStack>
                        <HorizontalStack gap="6" align="start">
                            <FlyoutMetaItem label="User"><Text variant="bodySm">{trace.userName || "-"}</Text></FlyoutMetaItem>
                            <FlyoutMetaItem label="Duration">
                                <Text variant="bodySm">{formatDurationMs(trace.durationMs)}</Text>
                            </FlyoutMetaItem>
                            <FlyoutMetaItem label="Tokens in / out">
                                <Text variant="bodySm">{(trace._inputTokens || 0).toLocaleString("en-US")} / {(trace._outputTokens || 0).toLocaleString("en-US")}</Text>
                            </FlyoutMetaItem>
                        </HorizontalStack>

                        <Divider />

                        {/* Span waterfall */}
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Span waterfall <Text as="span" variant="bodySm" color="subdued">({spans.length} spans)</Text></Text>
                            {loading ? (
                                <Box padding="4"><HorizontalStack align="center"><Spinner size="small" /></HorizontalStack></Box>
                            ) : (
                                <VerticalStack gap="1">
                                    {waterfall.map((w, i) => (
                                        <WaterfallRow key={i} name={w.name} offset={w.offset} width={w.width} durationMs={w.durationMs} color={w.color} />
                                    ))}
                                </VerticalStack>
                            )}
                        </VerticalStack>

                        <Divider />

                        {/* Message previews */}
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Messages</Text>
                            {spans.slice(0, 4).map((s, i) => <MessagePreview key={s.id || i} span={s} />)}
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </Scrollable>
        </AgenticFlyoutShell>
    );
}

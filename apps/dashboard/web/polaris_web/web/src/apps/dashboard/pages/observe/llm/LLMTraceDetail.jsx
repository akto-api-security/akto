import { useEffect, useMemo, useState } from "react";
import { Box, Divider, HorizontalStack, Scrollable, Spinner, Text, VerticalStack } from "@shopify/polaris";
import DetailGrid from "../agentic/DetailGrid";
import SpanSection from "./LLMSpanSection";
import api from "./api";
import { enrichRow } from "./utils";
import { formatDurationMs, truncate, TOKEN_ESTIMATE_TOOLTIP } from "./constants";

// ─── Waterfall ────────────────────────────────────────────────────────────────

const WATERFALL_COLORS = { green: "#50B83C", yellow: "#EEC200", red: "#D72C0D" };

function spanBarColor(durationMs, totalDuration) {
    if (!totalDuration || !durationMs) return WATERFALL_COLORS.green;
    const pct = durationMs / totalDuration;
    if (pct < 0.2) return WATERFALL_COLORS.green;
    if (pct < 0.5) return WATERFALL_COLORS.yellow;
    return WATERFALL_COLORS.red;
}

function spanWaterfallLabel(span, index) {
    if (span.responsePayload) {
        try {
            const resp = JSON.parse(span.responsePayload);
            const toolCalls = resp.choices?.[0]?.message?.tool_calls;
            if (toolCalls?.length) {
                const names = toolCalls.map(tc => tc.function?.name).filter(Boolean);
                if (names.length) return names.join(", ");
            }
        } catch (_) {}
    }
    return span._promptText || `Span ${index + 1}`;
}

// Derives durationMs for each span when the field is absent: uses gap between
// consecutive span timestamps (same approach as the original TraceFlyout on master).
// The last span never has a "next" timestamp, so it gets the average of the others.
function resolveSpanDurations(spans) {
    const hasDurations = spans.some(s => (s.durationMs || 0) > 0);
    if (hasDurations) return spans;

    const times = spans.map(s => Number(s.timestamp) || 0);
    const validTimes = times.filter(Boolean);
    if (!validTimes.length) return spans;

    const resolved = spans.map((s, i) => {
        const t    = times[i] || validTimes[0];
        const next = times[i + 1] || 0;
        return { ...s, durationMs: next > t ? next - t : 0 };
    });

    // Last span has no successor → give it the average of all non-zero durations.
    const nonZero = resolved.slice(0, -1).map(s => s.durationMs).filter(d => d > 0);
    if (nonZero.length && resolved[resolved.length - 1].durationMs === 0) {
        const avg = Math.round(nonZero.reduce((a, b) => a + b, 0) / nonZero.length);
        resolved[resolved.length - 1] = { ...resolved[resolved.length - 1], durationMs: avg };
    }
    return resolved;
}

function WaterfallGraph({ spans }) {
    if (!spans?.length) return null;

    const resolved = resolveSpanDurations(spans);
    const totalDuration = resolved.reduce((sum, s) => sum + (s.durationMs || 0), 0);
    if (!totalDuration) return null;

    let cumulative = 0;

    return (
        <VerticalStack gap="2">
            <HorizontalStack gap="1" blockAlign="baseline">
                <Text variant="headingSm" fontWeight="semibold">Span waterfall</Text>
                <Text variant="bodySm" color="subdued">({resolved.length} {resolved.length === 1 ? "span" : "spans"})</Text>
            </HorizontalStack>
            <Box borderWidth="1" borderColor="border" borderRadius="2" overflowX="hidden" overflowY="hidden">
                {resolved.map((span, i) => {
                    const leftPct     = (cumulative / totalDuration) * 100;
                    const widthPct    = Math.max((span.durationMs / totalDuration) * 100, 1.5);
                    cumulative       += (span.durationMs || 0);
                    const color       = spanBarColor(span.durationMs, totalDuration);
                    const hasDuration = span.durationMs > 0;
                    const isLast      = i === resolved.length - 1;
                    return (
                        <Box key={span.spanId || i}>
                            <Box paddingBlockStart="2" paddingBlockEnd="2" paddingInlineStart="3" paddingInlineEnd="3">
                                <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                                    <Box width="180px" minWidth="0">
                                        <Text variant="bodySm" color="subdued" truncate>
                                            {truncate(spanWaterfallLabel(span, i), 32)}
                                        </Text>
                                    </Box>
                                    {/* flex:1 + position:absolute bar — inexpressible via Box props */}
                                    <div style={{ flex: 1, position: "relative", height: 12, background: "#F3F4F6", borderRadius: 4 }}>
                                        {hasDuration && (
                                            <div style={{
                                                position: "absolute", left: `${leftPct}%`, width: `${widthPct}%`,
                                                minWidth: 6, height: "100%", background: color, borderRadius: 4,
                                            }} />
                                        )}
                                    </div>
                                    <Box width="52px">
                                        <Text variant="bodySm" color="subdued" alignment="end">
                                            {hasDuration ? formatDurationMs(span.durationMs) : "-"}
                                        </Text>
                                    </Box>
                                </HorizontalStack>
                            </Box>
                            {!isLast && <Divider />}
                        </Box>
                    );
                })}
            </Box>
        </VerticalStack>
    );
}

// ─── TraceDetailView ──────────────────────────────────────────────────────────

export default function TraceDetailView({ trace, currDateRange, initialSpans }) {
    const [spans, setSpans]     = useState(initialSpans || []);
    const [loading, setLoading] = useState(false);

    // Compute the search window once. Prefer the parent's date range (same window
    // the user selected) so searchPrompts returns all spans in scope — a ±24h
    // derivation from latestTimestamp can miss spans near the range edges.
    const searchWindow = useMemo(() => {
        if (currDateRange) {
            return {
                startTime: Math.floor(Date.parse(currDateRange.period.since) / 1000),
                endTime:   Math.floor(Date.parse(currDateRange.period.until) / 1000),
            };
        }
        const fromTs = trace?.latestTimestamp
            ? (trace.latestTimestamp > 1e10 ? Math.floor(trace.latestTimestamp / 1000) : Number(trace.latestTimestamp))
            : Math.floor(Date.now() / 1000);
        return { startTime: fromTs - 86400, endTime: fromTs + 3600 };
    }, [currDateRange, trace?.latestTimestamp]);

    useEffect(() => {
        const traceId   = trace?.traceId;
        const sessionId = trace?.sessionIdentifier;
        if (!traceId && !sessionId) { setSpans([]); return; }
        // Spans pre-fetched by parent (session fallback) — nothing to do.
        if (!traceId && sessionId && initialSpans?.length) return;
        let cancelled = false;
        setLoading(true);

        const loadSpans = async () => {
            try {
                if (traceId) {
                    // Primary: dedicated trace-detail endpoint
                    const rows = await api.fetchTraceDetail(traceId);
                    if (!cancelled && rows.length > 0) { setSpans(rows.map(enrichRow)); return; }
                    // Fallback: searchPrompts scoped to traceId with the full selected date range
                    const result = await api.searchPrompts({
                        startTime: searchWindow.startTime,
                        endTime:   searchWindow.endTime,
                        traceId,
                        limit:     100,
                    });
                    if (!cancelled) setSpans(result.value || []);
                } else {
                    // No traceId — old records: load flat spans scoped to sessionIdentifier
                    const result = await api.searchPrompts({
                        startTime: searchWindow.startTime,
                        endTime:   searchWindow.endTime,
                        sessionId,
                        limit:     100,
                    });
                    if (!cancelled) setSpans(result.value || []);
                }
            } catch (_) {
                if (!cancelled) setSpans([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        };
        loadSpans();
        return () => { cancelled = true; };
    }, [trace?.traceId, trace?.sessionIdentifier, searchWindow, initialSpans]);

    const metaItems = [
        { label: "Application",     value: trace.serviceId },
        { label: "Model",           value: trace._model },
        { label: "User",            value: trace.userName },
        { label: "Duration",        value: formatDurationMs(trace.durationMs) },
        { label: "Tokens in / out", value: `${(trace._inputTokens || 0).toLocaleString("en-US")} / ${(trace._outputTokens || 0).toLocaleString("en-US")}`, tooltip: TOKEN_ESTIMATE_TOOLTIP },
    ];

    return (
        <Scrollable style={{ flex: 1 }}>
            <Box padding="3">
                <VerticalStack gap="5">
                    <DetailGrid items={metaItems} columns={3} />
                    <Divider />

                    {!loading && spans.length > 0 && (
                        <>
                            <WaterfallGraph spans={spans} />
                            <Divider />
                        </>
                    )}

                    {loading ? (
                        <HorizontalStack align="center"><Spinner size="small" /></HorizontalStack>
                    ) : spans.length ? (
                        <VerticalStack gap="3">
                            {spans.map((span, i) => (
                                <SpanSection key={span.spanId || i} span={span} index={i} />
                            ))}
                        </VerticalStack>
                    ) : (
                        <Box padding="8">
                            <VerticalStack gap="1" inlineAlign="center">
                                <Text variant="bodySm" color="subdued">No spans available for this trace.</Text>
                            </VerticalStack>
                        </Box>
                    )}
                </VerticalStack>
            </Box>
        </Scrollable>
    );
}

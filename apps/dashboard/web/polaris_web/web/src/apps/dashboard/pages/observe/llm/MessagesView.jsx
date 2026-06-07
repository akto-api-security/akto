import { useCallback, useEffect, useState } from "react";
import { Badge, Box, HorizontalStack, Scrollable, Spinner, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import api from "./api";
import { enrichRow } from "./utils";
import { truncate } from "./constants";
import CardList from "./CardList";
import PromptDetailModal from "./PromptDetailModal";
import { SpanFlowRow } from "./SpansPanel";
import LLMFilterBar from "./LLMFilterBar";

export default function MessagesView({ currDateRange }) {
    const [messages, setMessages] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selected, setSelected] = useState(null);

    const [spans, setSpans] = useState([]);
    const [spansLoading, setSpansLoading] = useState(false);
    const [expandedSpan, setExpandedSpan] = useState(null);

    const [sessionInput, setSessionInput] = useState("");    // raw input (instant display)
    const [sessionSearch, setSessionSearch] = useState(""); // debounced → drives fetch
    const [enumFilters, setEnumFilters] = useState({ userName: "", deviceId: "", serviceId: "" });

    const load = useCallback(async () => {
        const since = Math.floor(Date.parse(currDateRange.period.since) / 1000);
        const until = Math.floor(Date.parse(currDateRange.period.until) / 1000);
        setLoading(true);
        setSelected(null);
        setSpans([]);
        try {
            const rows = await api.fetchMessages(since, until, {
                sessionId: sessionSearch.trim(),
                userName:  enumFilters.userName,
                deviceId:  enumFilters.deviceId,
                serviceId: enumFilters.serviceId,
            });
            setMessages((rows || []).map(enrichRow));
        } finally {
            setLoading(false);
        }
    }, [currDateRange, sessionSearch, enumFilters]);

    useEffect(() => { load(); }, [load]);

    useEffect(() => {
        if (!selected) { setSpans([]); return; }
        let cancelled = false;
        setSpansLoading(true);
        setExpandedSpan(null);
        api.fetchTraceDetail(selected.traceId)
            .then(rows => { if (!cancelled) setSpans((rows || []).map(enrichRow)); })
            .finally(() => { if (!cancelled) setSpansLoading(false); });
        return () => { cancelled = true; };
    }, [selected]);

    const renderMessageCard = (m) => (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between" blockAlign="center">
                <HorizontalStack gap="2" blockAlign="center">
                    {m.serviceId ? <Badge tone="new">{m.serviceId}</Badge> : null}
                    {m.userName
                        ? <Text variant="bodySm" fontWeight="medium">{m.userName}</Text>
                        : null}
                </HorizontalStack>
                <Text variant="bodySm" tone="subdued">
                    {func.prettifyEpoch(Math.floor((m.latestTimestamp || 0) / 1000))}
                </Text>
            </HorizontalStack>
            <Text variant="bodySm">{truncate(m._promptText || "", 80)}</Text>
            <HorizontalStack gap="3">
                <Text variant="bodySm" tone="subdued">
                    {m.spanCount + " span" + (m.spanCount !== 1 ? "s" : "")}
                </Text>
                {(m.totalTokens > 0) &&
                    <Text variant="bodySm" tone="subdued">{m.totalTokens + " tok"}</Text>}
            </HorizontalStack>
        </VerticalStack>
    );

    return (
        <HorizontalStack wrap={false} blockAlign="stretch" style={{ height: "calc(100vh - 220px)", minHeight: 0 }}>

            {/* ── Left: trace list ── */}
            <Box
                borderInlineEndWidth="025"
                borderColor="border"
                minWidth="360px"
                maxWidth="360px"
                style={{ display: "flex", flexDirection: "column" }}
            >
                <LLMFilterBar
                    currDateRange={currDateRange}
                    showSessionSearch
                    sessionValue={sessionInput}
                    onSessionChange={setSessionInput}
                    onSessionCommit={setSessionSearch}
                    filters={enumFilters}
                    onFiltersChange={setEnumFilters}
                    count={messages.length}
                    title="Messages"
                />
                <CardList
                    items={messages}
                    loading={loading}
                    emptyText="No messages for this time range."
                    selectedKey={selected?.traceId}
                    getKey={(m) => m.traceId}
                    onSelect={setSelected}
                    renderCard={renderMessageCard}
                />
            </Box>

            {/* ── Right: span flow ── */}
            <Box minWidth="0" width="100%" style={{ display: "flex", flexDirection: "column" }}>
                {!selected ? (
                    <Box padding="8">
                        <Text tone="subdued" variant="bodyMd">Select a message to view its spans</Text>
                    </Box>
                ) : (
                    <>
                        {/* Trace header */}
                        <Box padding="4" borderBlockEndWidth="025" borderColor="border">
                            <VerticalStack gap="1">
                                <Text variant="headingSm">{truncate(selected._promptText || "", 120)}</Text>
                                <HorizontalStack gap="3">
                                    {selected.userName &&
                                        <Text variant="bodySm" tone="subdued">{selected.userName}</Text>}
                                    {selected.serviceId &&
                                        <Badge tone="new">{selected.serviceId}</Badge>}
                                    <Badge>{selected.spanCount + " span" + (selected.spanCount !== 1 ? "s" : "")}</Badge>
                                    {(selected.totalTokens > 0) &&
                                        <Badge tone="info">{selected.totalTokens + " tokens"}</Badge>}
                                    <Text variant="bodySm" tone="subdued">
                                        {func.prettifyEpoch(Math.floor((selected.latestTimestamp || 0) / 1000))}
                                    </Text>
                                </HorizontalStack>
                            </VerticalStack>
                        </Box>

                        {/* Span flow */}
                        {spansLoading ? (
                            <Box padding="6">
                                <HorizontalStack align="center"><Spinner size="small" /></HorizontalStack>
                            </Box>
                        ) : spans.length === 0 ? (
                            <Box padding="6">
                                <Text tone="subdued" variant="bodySm">No spans for this message.</Text>
                            </Box>
                        ) : (
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
                        )}
                    </>
                )}
            </Box>

            <PromptDetailModal prompt={expandedSpan} onClose={() => setExpandedSpan(null)} />
        </HorizontalStack>
    );
}

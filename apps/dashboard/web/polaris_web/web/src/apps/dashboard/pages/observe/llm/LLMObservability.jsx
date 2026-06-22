import { useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { Box, Card, Divider, HorizontalGrid, HorizontalStack, Text } from "@shopify/polaris";
import { produce } from "immer";

import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import AgenticStatsCard from "../agentic/AgenticStatsCard";
import AgenticTopListCard from "../agentic/AgenticTopListCard";
import func from "@/util/func";
import values from "@/util/values";
import PersistStore from "@/apps/main/PersistStore";
import "../../../components/layouts/style.css";

import api from "./api";
import { buildSparkline, buildWeightedSparkline, buildSparklineLabels, enrichRow } from "./utils";
import { formatCompact, truncate } from "./constants";
import { ARGUS_TRACE_COL_DEFS } from "./columns";
import SessionsView from "./SessionsView";
import SessionFlyout from "./SessionFlyout";
import ArgusTraceFlyout from "./ArgusTraceFlyout";
import MessagesView from "./MessagesView";

const SERVICE_COLORS = ["#9642FC", "#4285F4", "#10A37F", "#EAB308", "#F97316", "#DC2626"];

// Normalize latestTimestamp (could be ms or s) → epoch seconds.
function toEpochSec(ts) {
    if (!ts) return 0;
    return ts > 1e10 ? Math.floor(ts / 1000) : ts;
}

export default function LLMObservability() {
    const dashboardCategory = PersistStore(state => state.dashboardCategory) || "API Security";
    const isArgus = dashboardCategory === "Agentic Security";

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[5]
    );
    const [selectedSession, setSelectedSession] = useState(null);
    const [selectedTrace, setSelectedTrace]     = useState(null);
    const [sessions, setSessions] = useState([]);
    const [traces, setTraces]     = useState([]);

    const epochs = useMemo(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        let cancelled = false;
        if (!isArgus) {
            api.fetchSessions(epochs.since, epochs.until, {})
                .then(rows => { if (!cancelled) setSessions((rows || []).map(enrichRow)); });
        }
        return () => { cancelled = true; };
    }, [epochs, isArgus]);

    const onArgusRowsFetched = useCallback((rows) => setTraces(rows), []);

    const openSession = useCallback((row) => {
        setSelectedSession(row);
    }, []);

    // ─── Atlas graph data (sessions) ─────────────────────────────────────────

    const sessionSpark = useMemo(
        () => buildSparkline(sessions, r => toEpochSec(r.latestTimestamp)),
        [sessions]
    );

    const tokenSpark = useMemo(
        () => buildWeightedSparkline(
            sessions,
            r => toEpochSec(r.latestTimestamp),
            r => (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0)
        ),
        [sessions]
    );

    const sparklineLabels = useMemo(
        () => buildSparklineLabels(epochs.since, epochs.until),
        [epochs]
    );

    const sessionBreakdown = useMemo(() => {
        const byModel = {};
        sessions.forEach(r => {
            const m = r._model || "Unknown";
            byModel[m] = (byModel[m] || 0) + 1;
        });
        return Object.entries(byModel)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([label, count], i) => ({ label, count, color: SERVICE_COLORS[i] || "#D1D5DB" }));
    }, [sessions]);

    const totalInputTokens = useMemo(
        () => sessions.reduce((s, r) => s + (Number(r._inputTokens) || 0), 0),
        [sessions]
    );

    const totalOutputTokens = useMemo(
        () => sessions.reduce((s, r) => s + (Number(r._outputTokens) || 0), 0),
        [sessions]
    );

    const totalTokens = totalInputTokens + totalOutputTokens;

    const topUserRows = useMemo(() => {
        const byUser = {};
        sessions.forEach(r => {
            const user = r.userName || "Unknown";
            if (!byUser[user]) byUser[user] = { tokens: 0, os: r.os || "linux" };
            byUser[user].tokens += (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0);
        });
        return Object.entries(byUser)
            .sort((a, b) => b[1].tokens - a[1].tokens)
            .slice(0, 5)
            .map(([userName, { tokens, os }]) => ({
                id: userName,
                name: userName,
                type: "OS",
                assetTagValue: os,
                renderValue: () => (
                    <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
                        <Box minHeight="28px">
                            <Text variant="bodyMd" alignment="end">{formatCompact(tokens)}</Text>
                        </Box>
                    </HorizontalStack>
                ),
            }));
    }, [sessions]);

    const topModelRows = useMemo(() => {
        const byModel = {};
        sessions.forEach(r => {
            const m = r._model;
            if (!m) return;
            if (!byModel[m]) byModel[m] = { count: 0 };
            byModel[m].count++;
        });
        return Object.entries(byModel)
            .sort((a, b) => b[1].count - a[1].count)
            .slice(0, 3)
            .map(([model, { count }]) => ({
                id: model,
                name: model,
                type: "LLM",
                assetTagValue: model,
                renderValue: () => (
                    <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
                        <Box minHeight="28px">
                            <Text variant="bodyMd" alignment="end">{count}</Text>
                        </Box>
                    </HorizontalStack>
                ),
            }));
    }, [sessions]);

    // ─── Argus graph data (traces) ────────────────────────────────────────────

    const argusTraceSpark = useMemo(
        () => buildSparkline(traces, r => toEpochSec(r.latestTimestamp)),
        [traces]
    );

    const argusTokenSpark = useMemo(
        () => buildWeightedSparkline(
            traces,
            r => toEpochSec(r.latestTimestamp),
            r => (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0)
        ),
        [traces]
    );

    const argusTraceBreakdown = useMemo(() => {
        const byApp = {};
        traces.forEach(r => {
            const app = r.serviceId || "Unknown";
            byApp[app] = (byApp[app] || 0) + 1;
        });
        return Object.entries(byApp)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([label, count], i) => ({ label, count, color: SERVICE_COLORS[i] || "#D1D5DB" }));
    }, [traces]);

    const argusInputTokens  = useMemo(() => traces.reduce((s, r) => s + (Number(r._inputTokens)  || 0), 0), [traces]);
    const argusOutputTokens = useMemo(() => traces.reduce((s, r) => s + (Number(r._outputTokens) || 0), 0), [traces]);
    const argusTotalTokens  = argusInputTokens + argusOutputTokens;

    const argusTopAppByCost = useMemo(() => {
        const byApp = {};
        traces.forEach(r => {
            const app = r.serviceId || "Unknown";
            if (!byApp[app]) byApp[app] = { input: 0, output: 0 };
            byApp[app].input  += Number(r._inputTokens)  || 0;
            byApp[app].output += Number(r._outputTokens) || 0;
        });
        return Object.entries(byApp)
            .map(([app, { input, output }]) => ({ app, cost: (input * 15 + output * 75) / 1e6 }))
            .sort((a, b) => b.cost - a.cost)
            .slice(0, 5)
            .map(({ app, cost }) => ({
                id: app,
                name: app,
                type: "Application",
                assetTagValue: app,
                renderValue: () => (
                    <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
                        <Box minHeight="28px">
                            <Text variant="bodyMd" alignment="end">{cost < 0.001 ? "< $0.001" : `$${cost.toFixed(cost < 0.01 ? 4 : 2)}`}</Text>
                        </Box>
                    </HorizontalStack>
                ),
            }));
    }, [traces]);

    const argusTopTraceByTokens = useMemo(() => {
        return [...traces]
            .sort((a, b) => ((Number(b._inputTokens) || 0) + (Number(b._outputTokens) || 0)) - ((Number(a._inputTokens) || 0) + (Number(a._outputTokens) || 0)))
            .slice(0, 5)
            .map((r, i) => {
                const tokens = (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0);
                return {
                    id: r.traceId || i,
                    name: truncate(r._promptText || r.traceId || `Trace ${i + 1}`, 40),
                    type: "LLM",
                    assetTagValue: r._model,
                    onClick: () => setSelectedTrace(r),
                    renderValue: () => (
                        <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
                            <Box minHeight="28px">
                                <Text variant="bodyMd" alignment="end">{formatCompact(tokens)}</Text>
                            </Box>
                        </HorizontalStack>
                    ),
                };
            });
    }, [traces, setSelectedTrace]);

    const topCards = useMemo(() => isArgus ? (
        <HorizontalGrid key="top-row-argus" columns={3} gap="4">
            <Card padding="0">
                <Box className="agentic-stats-card-fill">
                    <Box className="agentic-stats-card-item">
                        <AgenticStatsCard
                            title="Total traces"
                            total={traces.length}
                            sparklineCounts={argusTraceSpark}
                            sparklineColor="#9642FC"
                            sparklineLabels={sparklineLabels}
                            breakdown={argusTraceBreakdown}
                            noCard
                        />
                    </Box>
                    <Divider />
                    <Box className="agentic-stats-card-item">
                        <AgenticStatsCard
                            title="Total tokens"
                            total={formatCompact(argusTotalTokens)}
                            sparklineCounts={argusTokenSpark}
                            sparklineColor="#4285F4"
                            sparklineLabels={sparklineLabels}
                            breakdown={[
                                { label: `In: ${formatCompact(argusInputTokens)}`,  count: argusInputTokens,  color: "#4285F4" },
                                { label: `Out: ${formatCompact(argusOutputTokens)}`, count: argusOutputTokens, color: "#10A37F" },
                            ]}
                            noCard
                        />
                    </Box>
                </Box>
            </Card>
            <AgenticTopListCard
                title="Top application by cost"
                columns={[{ label: "Application" }, { label: "Cost" }]}
                rows={argusTopAppByCost}
                emptyStateText="No application data in this range."
            />
            <AgenticTopListCard
                title="Top traces by token usage"
                columns={[{ label: "Trace" }, { label: "Tokens" }]}
                rows={argusTopTraceByTokens}
                emptyStateText="No trace data in this range."
            />
        </HorizontalGrid>
    ) : (
        <HorizontalGrid key="top-row" columns={3} gap="4">
            <Card padding="0">
                <Box className="agentic-stats-card-fill">
                    <Box className="agentic-stats-card-item">
                        <AgenticStatsCard
                            title="Total sessions"
                            total={sessions.length}
                            sparklineCounts={sessionSpark}
                            sparklineColor="#9642FC"
                            sparklineLabels={sparklineLabels}
                            breakdown={sessionBreakdown}
                            noCard
                        />
                    </Box>
                    <Divider />
                    <Box className="agentic-stats-card-item">
                        <AgenticStatsCard
                            title="Total tokens"
                            total={formatCompact(totalTokens)}
                            sparklineCounts={tokenSpark}
                            sparklineColor="#4285F4"
                            sparklineLabels={sparklineLabels}
                            breakdown={[
                                { label: `In: ${formatCompact(totalInputTokens)}`, count: totalInputTokens, color: "#4285F4" },
                                { label: `Out: ${formatCompact(totalOutputTokens)}`, count: totalOutputTokens, color: "#10A37F" },
                            ]}
                            noCard
                        />
                    </Box>
                </Box>
            </Card>
            <AgenticTopListCard
                title="Top Users by tokens"
                columns={[{ label: "User" }, { label: "Tokens" }]}
                rows={topUserRows}
                emptyStateText="No user data in this range."
            />
            <AgenticTopListCard
                title="Top Models by sessions"
                columns={[{ label: "Model" }, { label: "Sessions" }]}
                rows={topModelRows}
                emptyStateText="No model data in this range."
            />
        </HorizontalGrid>
    ), [isArgus, traces.length, argusTraceSpark, argusTraceBreakdown, argusTokenSpark, sparklineLabels, argusTotalTokens, argusInputTokens, argusOutputTokens, argusTopAppByCost, argusTopTraceByTokens, sessions.length, sessionSpark, sessionBreakdown, totalTokens, totalInputTokens, totalOutputTokens, tokenSpark, topUserRows, topModelRows]);

    return (
        <>
            <PageWithMultipleCards
                title="Traces"
                isFirstPage
                primaryAction={
                    <DateRangeFilter
                        initialDispatch={currDateRange}
                        dispatch={(dateObj) =>
                            dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
                        }
                    />
                }
                components={[
                    topCards,
                    isArgus ? (
                        <MessagesView key="traces-table" currDateRange={currDateRange} columnDefs={ARGUS_TRACE_COL_DEFS} onRowClicked={p => p.data && setSelectedTrace(p.data)} onRowsFetched={onArgusRowsFetched} />
                    ) : (
                        <SessionsView key="sessions-table" currDateRange={currDateRange} onOpenSession={openSession} />
                    ),
                ]}
            />
            {isArgus ? (
                <ArgusTraceFlyout
                    trace={selectedTrace}
                    onClose={() => setSelectedTrace(null)}
                />
            ) : (
                <SessionFlyout
                    session={selectedSession}
                    currDateRange={currDateRange}
                    onClose={() => setSelectedSession(null)}
                />
            )}
        </>
    );
}

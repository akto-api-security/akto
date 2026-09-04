import { useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { Box, Card, Divider, HorizontalGrid, HorizontalStack, Modal, Text } from "@shopify/polaris";
import { produce } from "immer";

import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import AgenticStatsCard from "../agentic/AgenticStatsCard";
import AgenticTopListCard from "../agentic/AgenticTopListCard";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import func from "@/util/func";
import values from "@/util/values";
import PersistStore from "@/apps/main/PersistStore";
import "../../../components/layouts/style.css";

import api from "./api";
import { formatSparklineLabels } from "./utils";
import { formatCompact, truncate, TOKEN_ESTIMATE_TOOLTIP } from "./constants";
import { ARGUS_TRACE_COL_DEFS } from "./columns";
import SessionsView from "./SessionsView";
import SessionFlyout from "./SessionFlyout";
import ArgusTraceFlyout from "./ArgusTraceFlyout";
import MessagesView from "./MessagesView";
import { fetchGuardrailPolicyNamesCached } from "../../guardrails/topicGuardrailUtils";
import { CATEGORY_ENDPOINT_SECURITY, CATEGORY_AGENTIC_SECURITY } from "../../../../main/labelHelper";

const SERVICE_COLORS = ["#9642FC", "#4285F4", "#10A37F", "#EAB308", "#F97316", "#DC2626"];


export default function LLMObservability() {
    const dashboardCategory = PersistStore(state => state.dashboardCategory) || "API Security";
    const setDashboardCategory = PersistStore(state => state.setDashboardCategory) || "API Security";
    const isArgus = dashboardCategory === CATEGORY_AGENTIC_SECURITY

    // Read username/topic/subTopic from URL query params once on mount.
    const [urlFilters] = useState(() => {
        const params = new URLSearchParams(window.location.search);
        const username = params.get("username");
        const topic    = params.get("topic");
        const subTopic = params.get("subTopic");
        const f = {};
        if (username) f.userName = [username];
        if (topic)    f.topic    = [topic];
        if (subTopic) f.subTopic = [subTopic];
        if(Object.keys(f).length > 0){
            setDashboardCategory(CATEGORY_ENDPOINT_SECURITY)
            return f
        }else{
            return null;
        }
            
    });

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[5]
    );
    const [selectedSession, setSelectedSession] = useState(null);
    const [selectedTrace, setSelectedTrace]     = useState(null);
    // Prompt content is admin-only; admins confirm per row that opening it is recorded in audit data.
    const isAdmin = func.isUserAdmin();
    const [pendingReveal, setPendingReveal] = useState(null);
    const [argusStats, setArgusStats] = useState(null);
    // Aggregated stats from the dedicated endpoint (accurate, not 500-capped)
    const [sessionStats, setSessionStats] = useState(null);
    const [loading, setLoading] = useState(true);

    // Read traceId once on mount — deep-links a real-invocation trace (e.g. an insight CTA)
    // straight into the Argus trace flyout. ArgusTraceFlyout only renders in the isArgus branch,
    // so opening one from anywhere else must force the category over, same technique urlFilters
    // above uses for the Atlas/username-topic-subTopic case, just pointed the other way.
    const [initialTraceId] = useState(() => new URLSearchParams(window.location.search).get("traceId"));
    useEffect(() => {
        if (!initialTraceId) return;
        setDashboardCategory(CATEGORY_AGENTIC_SECURITY);
        if (!func.isUserAdmin()) return;
        setPendingReveal({ open: () => setSelectedTrace({ traceId: initialTraceId }), traceId: initialTraceId });
    }, [initialTraceId, setDashboardCategory]);

    useEffect(() => {
        fetchGuardrailPolicyNamesCached();
    }, []);

    const epochs = useMemo(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        if (!isArgus) {
            api.fetchSessionStats(epochs.since, epochs.until)
                .then(stats => {
                    if (cancelled) return;
                    setSessionStats(stats);
                    setLoading(false);
                })
                .catch(() => { if (!cancelled) setLoading(false); });
        } else {
            api.fetchArgusStats(epochs.since, epochs.until)
                .then(stats => {
                    if (cancelled) return;
                    setArgusStats(stats);
                    setLoading(false);
                })
                .catch(() => { if (!cancelled) setLoading(false); });
        }
        return () => { cancelled = true; };
    }, [epochs, isArgus]);

    const openSession = useCallback((row) => {
        if (!isAdmin) return;
        setPendingReveal({ open: () => setSelectedSession(row), sessionId: row?.sessionIdentifier });
    }, [isAdmin]);

    const openTrace = useCallback((row) => {
        if (!isAdmin) return;
        setPendingReveal({ open: () => setSelectedTrace(row), traceId: row?.traceId, sessionId: row?.sessionIdentifier });
    }, [isAdmin]);

    // Open only once the audit entry is written, so content is never shown unlogged.
    const confirmReveal = useCallback(() => {
        if (!pendingReveal) return;
        api.logPromptContentAccess({ sessionId: pendingReveal.sessionId, traceId: pendingReveal.traceId })
            .then(() => {
                pendingReveal.open();
                setPendingReveal(null);
            })
            .catch(() => {
                func.setToast(true, true, "Could not record this access in audit data. Prompt content was not shown.");
                setPendingReveal(null);
            });
    }, [pendingReveal]);

    // ─── Atlas graph data (sessions) ─────────────────────────────────────────

    const sessionSpark       = useMemo(() => sessionStats?.sessionSpark  || [0], [sessionStats]);
    const sessionSparkTs     = useMemo(() => sessionStats?.sessionSparkTs || [],  [sessionStats]);
    const sessionSparkLabels = useMemo(() => formatSparklineLabels(sessionSparkTs), [sessionSparkTs]);

    const tokenSpark       = useMemo(() => sessionStats?.tokenSpark || [0], [sessionStats]);
    const tokenSparkLabels = sessionSparkLabels;

    const argusTraceSparkLabels = useMemo(
        () => formatSparklineLabels(argusStats?.traceSparkTs || []),
        [argusStats]
    );

    // Breakdown by top users (by session count)
    const sessionBreakdown = useMemo(() => {
        const breakdown = sessionStats?.userBreakdown || [];
        const entries = breakdown.map(({ label, count }, i) => ({
            label,
            count: Number(count),
            color: SERVICE_COLORS[i] || "#D1D5DB",
        }));
        const shown = entries.reduce((sum, e) => sum + e.count, 0);
        const rest  = (sessionStats?.totalSessions || 0) - shown;
        if (rest > 0) entries.push({ label: "Others", count: rest, color: "#D1D5DB" });
        return entries;
    }, [sessionStats]);

    const totalTokens = (sessionStats?.totalInputTokens || 0) + (sessionStats?.totalOutputTokens || 0);

    // Top users by token usage — from aggregated backend stats.
    const topUserRows = useMemo(() => {
        const topUsers = sessionStats?.topUsers || [];
        return topUsers
            .sort((a, b) => (b.totalTokens || 0) - (a.totalTokens || 0))
            .slice(0, 5).map(({ userName, totalTokens: tokens }) => ({
            id: userName,
            name: userName,
            type: "OS",
            assetTagValue: "",
            renderValue: () => (
                <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
                    <Box minHeight="28px">
                        <Text variant="bodyMd" alignment="end">{formatCompact(tokens)}</Text>
                    </Box>
                </HorizontalStack>
            ),
        }));
    }, [sessionStats]);

    // Top models by session count — from aggregated backend stats.
    const topModelRows = useMemo(() => (sessionStats?.topModels || []).map(({ model, count }) => ({
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
    })), [sessionStats]);

    // ─── Argus graph data (from fetchArgusStats — accurate, not table-page-capped) ──

    const argusTraceSpark    = useMemo(() => argusStats?.traceSpark  || [0], [argusStats]);
    const argusTokenSpark    = useMemo(() => argusStats?.tokenSpark  || [0], [argusStats]);
    const argusInputTokens   = useMemo(() => argusStats?.totalInputTokens  || 0, [argusStats]);
    const argusOutputTokens  = useMemo(() => argusStats?.totalOutputTokens || 0, [argusStats]);
    const argusTotalTokens   = argusInputTokens + argusOutputTokens;

    const argusTraceBreakdown = useMemo(
        () => (argusStats?.appBreakdown || []).map(({ label, count }, i) => ({
            label, count, color: SERVICE_COLORS[i] || "#D1D5DB",
        })),
        [argusStats]
    );

    const argusTopAppByInputTokens = useMemo(() => {
        return (argusStats?.topApps || [])
            .map(({ serviceId, inputTokens: inp }) => ({ app: serviceId || "Unknown", inp: inp || 0 }))
            .sort((a, b) => b.inp - a.inp)
            .slice(0, 5)
            .map(({ app, inp }) => ({
                id: app,
                name: app,
                type: "Application",
                assetTagValue: app,
                renderValue: () => (
                    <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
                        <Box minHeight="28px">
                            <Text variant="bodyMd" alignment="end">{formatCompact(inp)}</Text>
                        </Box>
                    </HorizontalStack>
                ),
            }));
    }, [argusStats]);

    const argusTopTraceByTokens = useMemo(() => {
        return (argusStats?.topTraces || []).map((r, i) => {
            const tokens = (Number(r._inputTokens) || 0) + (Number(r._outputTokens) || 0);
            return {
                id: r.traceId || i,
                name: truncate((isAdmin ? r._promptText : "") || r.traceId || `Trace ${i + 1}`, 40),
                type: "LLM",
                assetTagValue: r._model,
                onClick: () => openTrace(r),
                renderValue: () => (
                    <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
                        <Box minHeight="28px">
                            <Text variant="bodyMd" alignment="end">{formatCompact(tokens)}</Text>
                        </Box>
                    </HorizontalStack>
                ),
            };
        });
    }, [argusStats, openTrace, isAdmin]);

    const totalDisplaySessions = sessionStats?.totalSessions || 0;

    const topCards = useMemo(() => isArgus ? (
        <HorizontalGrid key="top-row-argus" columns={3} gap="4">
            <Card padding="0">
                <Box className="agentic-stats-card-fill">
                    <Box className="agentic-stats-card-item">
                        <AgenticStatsCard
                            title="Total traces"
                            total={argusStats?.totalSpans || 0}
                            sparklineCounts={argusTraceSpark}
                            sparklineColor="#9642FC"
                            sparklineLabels={argusTraceSparkLabels}
                            breakdown={argusTraceBreakdown}
                            noCard
                        />
                    </Box>
                    <Divider />
                    <Box className="agentic-stats-card-item">
                        <AgenticStatsCard
                            title="Total tokens"
                            titleTooltip={TOKEN_ESTIMATE_TOOLTIP}
                            total={formatCompact(argusTotalTokens)}
                            sparklineCounts={argusTokenSpark}
                            sparklineColor="#4285F4"
                            sparklineLabels={argusTraceSparkLabels}
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
                title="Top application by input tokens"
                columns={[{ label: "Application" }, { label: "Tokens" }]}
                rows={argusTopAppByInputTokens}
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
                            total={totalDisplaySessions}
                            sparklineCounts={sessionSpark}
                            sparklineColor="#9642FC"
                            sparklineLabels={sessionSparkLabels}
                            breakdown={sessionBreakdown}
                            noCard
                        />
                    </Box>
                    <Divider />
                    <Box className="agentic-stats-card-item">
                        <AgenticStatsCard
                            title="Total tokens"
                            titleTooltip={TOKEN_ESTIMATE_TOOLTIP}
                            total={formatCompact(totalTokens)}
                            sparklineCounts={tokenSpark}
                            sparklineColor="#4285F4"
                            sparklineLabels={tokenSparkLabels}
                            breakdown={[
                                { label: `In: ${formatCompact(sessionStats?.totalInputTokens || 0)}`, count: sessionStats?.totalInputTokens || 0, color: "#4285F4" },
                                { label: `Out: ${formatCompact(sessionStats?.totalOutputTokens || 0)}`, count: sessionStats?.totalOutputTokens || 0, color: "#10A37F" },
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
    ), [isArgus, argusStats, argusTraceSpark, argusTraceBreakdown, argusTokenSpark, argusTraceSparkLabels, argusTotalTokens, argusInputTokens, argusOutputTokens, argusTopAppByInputTokens, argusTopTraceByTokens, totalDisplaySessions, sessionSpark, sessionSparkLabels, sessionBreakdown, totalTokens,sessionStats, tokenSpark, tokenSparkLabels, topUserRows, topModelRows]);

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
                    loading ? (
                        <Box key="top-cards-loading" padding="5">
                            <SpinnerCentered height="200px" />
                        </Box>
                    ) : topCards,
                    isArgus ? (
                        <MessagesView key="traces-table" currDateRange={currDateRange} columnDefs={ARGUS_TRACE_COL_DEFS} onRowClicked={p => p.data && openTrace(p.data)} />
                    ) : (
                        <SessionsView key="sessions-table" currDateRange={currDateRange} onOpenSession={openSession} initialFilters={urlFilters} />
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
            <Modal
                open={!!pendingReveal}
                onClose={() => setPendingReveal(null)}
                title="Show prompt content?"
                primaryAction={{ content: "Show content", onAction: confirmReveal }}
                secondaryActions={[{ content: "Cancel", onAction: () => setPendingReveal(null) }]}
            >
                <Modal.Section>
                    <Text variant="bodyMd">
                        Prompts and responses can contain sensitive data. Your email, IP address and the time of
                        this access will be recorded in audit data.
                    </Text>
                </Modal.Section>
            </Modal>
        </>
    );
}

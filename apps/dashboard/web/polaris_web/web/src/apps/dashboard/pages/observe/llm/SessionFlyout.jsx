import { useCallback, useEffect, useMemo, useState } from "react";
import { Box, Divider, HorizontalGrid, HorizontalStack, Scrollable, Tabs, Text, VerticalStack } from "@shopify/polaris";
import InfoTooltipIcon from "@/apps/dashboard/components/shared/InfoTooltipIcon";
import AgenticFlyoutShell from "../agentic/AgenticFlyoutShell";
import FlyoutBreadcrumb from "../agentic/FlyoutBreadcrumb";
import AssetTopologyGraph from "../agentic/AssetTopologyGraph";
import DetailGrid from "../agentic/DetailGrid";
import AiChatSection from "../agentic/AiChatSection";
import { buildAgenticObserveChatMetadata } from "../agentic/agenticObserveApi";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import TraceDetailView from "./LLMTraceDetail";
import api from "./api";
import { enrichRow } from "./utils";
import { getTraceColumnDefs } from "./columns";
import { formatCompact, formatDurationMs, truncate, TOKEN_ESTIMATE_TOOLTIP } from "./constants";
import func from "@/util/func";

const TAB_OVERVIEW = 0;
const TAB_TRACES   = 1;
const TABS = [
    { id: "overview", content: "Overview", panelID: "panel-overview" },
    { id: "traces",   content: "Traces",   panelID: "panel-traces"   },
];

// ─── Session flow graph ───────────────────────────────────────────────────────

function SessionFlowGraph({ session }) {
    const nodes = useMemo(() => [
        { id: "user",  type: "topoNode", draggable: false, position: { x: 40,  y: 50 }, data: { component: { category: "external", type: "User",        label: session.userName  || "Unknown" } } },
        { id: "agent", type: "topoNode", draggable: false, position: { x: 240, y: 50 }, data: { component: { category: "agent",    type: "Application", label: session.serviceId || "Unknown" } } },
        { id: "model", type: "topoNode", draggable: false, position: { x: 440, y: 50 }, data: { component: { category: "ai-model", type: "LLM",         label: session._model    || "Unknown" } } },
    ], [session.userName, session.serviceId, session._model]);

    const edges = useMemo(() => [
        { id: "e-user-agent",  source: "user",  target: "agent", type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } },
        { id: "e-agent-model", source: "agent", target: "model", type: "smoothstep", style: { stroke: "#ec4899", strokeWidth: 1.5 } },
    ], []);

    return <AssetTopologyGraph nodes={nodes} edges={edges} />;
}

// ─── Overview ─────────────────────────────────────────────────────────────────

function OverviewContent({ session, traceCount }) {
    const totalTokens = (Number(session._inputTokens) || 0) + (Number(session._outputTokens) || 0);

    const stats = [
        { label: "Traces",       value: traceCount },
        { label: "Total tokens", value: formatCompact(totalTokens), tooltip: TOKEN_ESTIMATE_TOOLTIP },
        { label: "Duration",     value: formatDurationMs(session.durationMs) },
    ];

    const detailItems = [
        { label: "User",        value: session.userName },
        { label: "Application", value: session.serviceId },
        { label: "Session ID",  value: truncate(session.sessionIdentifier, 36) },
        { label: "Models",      value: session._models?.length ? session._models.join(", ") : undefined },
        { label: "Endpoint ID", value: session.deviceId, href: session.deviceId ? `/dashboard/observe/inventory/${session.deviceId}` : undefined },
        { label: "Topics queried",value: session.topicHierarchy ? Object.keys(session.topicHierarchy).map((x) => func.toSentenceCase(x)).join(", ") : undefined },
    ];

    return (
        <Box padding="4">
            <VerticalStack gap="5">
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

                {session._description && (
                    <>
                        <Divider />
                        <Text variant="bodyMd">{session._description}</Text>
                    </>
                )}

                <SessionFlowGraph session={session} />

                <DetailGrid
                    heading="Session Details"
                    items={detailItems}
                    columns={3}
                />
            </VerticalStack>
        </Box>
    );
}

// ─── Session traces content ───────────────────────────────────────────────────
// Pure presenter — data is fetched once at SessionFlyout level and passed down.
// If records exist → shows a trace table (clicking a row opens TraceDetailView).
// If empty (old records without traceId) → renders TraceDetailView directly,
// which loads spans via searchPrompts scoped to the sessionIdentifier.

const TRACE_COL_DEFS = getTraceColumnDefs({ showSession: false });

function SessionTracesContent({ rows, spanRows, loading, hasMessages, session, currDateRange, onTraceClick }) {
    const handleRowClick = useCallback(p => p.data && onTraceClick?.(p.data), [onTraceClick]);

    if (loading || hasMessages === null) return <SpinnerCentered height="200px" />;

    if (!hasMessages) return <TraceDetailView trace={session} currDateRange={currDateRange} initialSpans={spanRows} />;

    return (
        <AgGridTable
            rowData={rows}
            columnDefs={TRACE_COL_DEFS}
            defaultColDef={{ resizable: true, sortable: true, filter: false }}
            searchPlaceholder="Search traces..."
            rowHeight={44}
            headerHeight={40}
            rowSelection="single"
            pagination
            paginationPageSize={20}
            paginationPageSizeSelector={[20, 50, 100]}
            noOuterBorder
            fillHeight
            domLayout="normal"
            animateRows
            suppressCellFocus
            getRowStyle={() => ({ cursor: "pointer" })}
            onRowClicked={handleRowClick}
            sideBar={{ toolPanels: ["columns", "filters"] }}
        />
    );
}

// ─── SessionFlyout ────────────────────────────────────────────────────────────

export default function SessionFlyout({ session, currDateRange, onClose }) {
    const [activeTab, setActiveTab]     = useState(TAB_OVERVIEW);
    const [topNav, setTopNav]           = useState(null);
    const [traceRows, setTraceRows]       = useState([]);
    const [spanRows, setSpanRows]         = useState([]);
    const [traceLoading, setTraceLoading] = useState(false);
    const [hasMessages, setHasMessages]   = useState(null);

    useEffect(() => {
        setActiveTab(TAB_OVERVIEW);
        setTopNav(null);
        setTraceRows([]);
        setSpanRows([]);
        setHasMessages(null);
    }, [session?.sessionIdentifier]);

    useEffect(() => {
        if (!session?.sessionIdentifier || !currDateRange) return;
        let cancelled = false;
        setTraceLoading(true);
        const since = Math.floor(Date.parse(currDateRange.period.since) / 1000);
        const until = Math.floor(Date.parse(currDateRange.period.until) / 1000);
        api.fetchMessages(since, until, { sessionId: session.sessionIdentifier })
            .then(data => {
                if (cancelled) return;
                const traces = (data || []).map(enrichRow);
                if (traces.length > 0) {
                    setTraceRows(traces);
                    setHasMessages(true);
                    setTraceLoading(false);
                } else {
                    // Old records with no traceId — load spans directly so both the
                    // Overview count and the Traces tab have data without a second fetch.
                    return api.searchPrompts({ startTime: since, endTime: until, sessionId: session.sessionIdentifier, limit: 100 })
                        .then(result => {
                            if (!cancelled) {
                                setSpanRows(result.value || []);
                                setHasMessages(false);
                                setTraceLoading(false);
                            }
                        });
                }
            })
            .catch(() => { if (!cancelled) { setHasMessages(false); setTraceLoading(false); } });
        return () => { cancelled = true; };
    }, [session?.sessionIdentifier, currDateRange]);

    const chatMetadata = useMemo(() => {
        if (!session) return null;
        return buildAgenticObserveChatMetadata("session", {
            sessionId: session.sessionIdentifier,
            serviceId: session.serviceId,
            userName:  session.userName,
            model:     session._model,
        });
    }, [session?.sessionIdentifier]);

    if (!session) return null;

    const sessionLabel = session._promptText ? truncate(session._promptText, 32) : "Session";
    const openTrace    = (trace) => setTopNav({ label: trace._promptText ? truncate(trace._promptText, 28) : "Trace detail", trace });
    // Accurate count once fetch resolves; fall back to stored agg value while loading.
    const traceCount = hasMessages === true  ? traceRows.length
                     : hasMessages === false ? spanRows.length
                     : (session.messageCount || 0);

    function renderContent() {
        if (topNav) return <TraceDetailView trace={topNav.trace} currDateRange={currDateRange} />;
        switch (activeTab) {
            case TAB_OVERVIEW: return (
                <Scrollable style={{ flex: 1 }}>
                    <OverviewContent session={session} traceCount={traceCount} />
                </Scrollable>
            );
            case TAB_TRACES: return (
                <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                    <SessionTracesContent
                        rows={traceRows}
                        spanRows={spanRows}
                        loading={traceLoading}
                        hasMessages={hasMessages}
                        session={session}
                        currDateRange={currDateRange}
                        onTraceClick={openTrace}
                    />
                </div>
            );
            default: return null;
        }
    }

    return (
        <AgenticFlyoutShell
            show={!!session}
            width={800}
            header={
                <>
                    <FlyoutBreadcrumb
                        items={topNav
                            ? [{ label: sessionLabel, onClick: () => setTopNav(null) }, { label: topNav.label }]
                            : [{ label: sessionLabel }]
                        }
                        onClose={onClose}
                    />
                    {!topNav && (
                        <>
                            <Box paddingInlineStart="1" paddingInlineEnd="1">
                                <Tabs tabs={TABS} selected={activeTab} onSelect={setActiveTab} />
                            </Box>
                            <Divider />
                        </>
                    )}
                </>
            }
            footer={
                <AiChatSection
                    placeholder="Ask anything about this session..."
                    resetKey={session?.sessionIdentifier}
                    conversationType="AGENTIC_OBSERVE"
                    chatMetadata={chatMetadata}
                />
            }
        >
            {renderContent()}
        </AgenticFlyoutShell>
    );
}

import { useCallback, useEffect, useMemo, useState } from "react";
import { Box, Divider, HorizontalGrid, Scrollable, Tabs, Text, VerticalStack } from "@shopify/polaris";
import AgenticFlyoutShell from "../agentic/AgenticFlyoutShell";
import FlyoutBreadcrumb from "../agentic/FlyoutBreadcrumb";
import AssetTopologyGraph from "../agentic/AssetTopologyGraph";
import DetailGrid from "../agentic/DetailGrid";
import AiChatSection from "../agentic/AiChatSection";
import { buildAgenticObserveChatMetadata } from "../agentic/agenticObserveApi";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import TraceDetailView from "./LLMTraceDetail";
import api from "./api";
import { enrichRow } from "./utils";
import { getTraceColumnDefs } from "./columns";
import { formatCost, formatCompact, formatDurationMs, truncate } from "./constants";

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

function OverviewContent({ session }) {
    const totalTokens = (Number(session._inputTokens) || 0) + (Number(session._outputTokens) || 0);

    const stats = [
        { label: "Traces",       value: session.messageCount || 0 },
        { label: "Total tokens", value: formatCompact(totalTokens) },
        { label: "Duration",     value: formatDurationMs(session.durationMs) },
        { label: "Est. cost",    value: formatCost(session._inputTokens || 0, session._outputTokens || 0) },
    ];

    const detailItems = [
        { label: "User",        value: session.userName },
        { label: "Application", value: session.serviceId },
        { label: "Session ID",  value: truncate(session.sessionIdentifier, 36) },
        { label: "Models",      value: session._models?.length ? session._models.join(", ") : undefined },
        { label: "Endpoint ID", value: session.endpointId, href: session.endpointId ? `/dashboard/observe/inventory/${session.endpointId}` : undefined },
    ];

    return (
        <Box padding="4">
            <VerticalStack gap="5">
                <HorizontalGrid columns={4} gap="3">
                    {stats.map(s => (
                        <VerticalStack gap="1" key={s.label}>
                            <Text variant="heading2xl" as="p">{s.value}</Text>
                            <Text variant="bodySm" color="subdued">{s.label}</Text>
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

// ─── Session traces table ─────────────────────────────────────────────────────

const TRACE_COL_DEFS = getTraceColumnDefs({ showSession: false });

function SessionTracesTable({ sessionId, currDateRange, onTraceClick }) {
    const [rows, setRows] = useState([]);

    useEffect(() => {
        if (!sessionId) return;
        let cancelled = false;
        const since = Math.floor(Date.parse(currDateRange.period.since) / 1000);
        const until = Math.floor(Date.parse(currDateRange.period.until) / 1000);
        api.fetchMessages(since, until, { sessionId })
            .then(data => { if (!cancelled) setRows((data || []).map(enrichRow)); });
        return () => { cancelled = true; };
    }, [sessionId, currDateRange]);

    const handleRowClick = useCallback(p => p.data && onTraceClick?.(p.data), [onTraceClick]);

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
    const [activeTab, setActiveTab] = useState(TAB_OVERVIEW);
    const [topNav, setTopNav]       = useState(null);

    useEffect(() => {
        setActiveTab(TAB_OVERVIEW);
        setTopNav(null);
    }, [session?.sessionIdentifier]);

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

    function renderContent() {
        if (topNav) return <TraceDetailView trace={topNav.trace} currDateRange={currDateRange} />;
        switch (activeTab) {
            case TAB_OVERVIEW: return (
                <Scrollable style={{ flex: 1 }}>
                    <OverviewContent session={session} />
                </Scrollable>
            );
            case TAB_TRACES: return (
                <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                    <SessionTracesTable sessionId={session.sessionIdentifier} currDateRange={currDateRange} onTraceClick={openTrace} />
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

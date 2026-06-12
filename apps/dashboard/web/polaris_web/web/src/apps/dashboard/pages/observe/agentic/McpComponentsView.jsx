import React, { useState, useEffect, useCallback, useRef } from "react";
import { Box, Text, Badge, Divider, ActionList, Button, Spinner, VerticalStack, HorizontalStack } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { ParamNameCellRenderer, ParamTypeCellRenderer, ParamDescCellRenderer, SeverityBadge, RiskPill } from "./AgenticCellRenderers";
import ComponentRiskAnalysisBadges from "../components/ComponentRiskAnalysisBadges";
import agenticObserveApi from "./agenticObserveApi";
import observeApi from "../api";
import SampleDataList from "../../../components/shared/SampleDataList";

// ── Shared detail panel helpers ───────────────────────────────────────────────

const SCHEMA_COL_DEFS = [
    { field: "name", headerName: "Name",        flex: 1,   minWidth: 140, cellRenderer: ParamNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "type", headerName: "Type",        width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ParamTypeCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "desc", headerName: "Description", flex: 2,   minWidth: 160, cellRenderer: ParamDescCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

const TRAFFIC_LOADING = <Box padding="4"><Spinner accessibilityLabel="Loading traffic" size="small" /></Box>;
const TRAFFIC_EMPTY  = <Box padding="8"><VerticalStack gap="1" inlineAlign="center"><Text variant="bodySm" fontWeight="semibold">No captured traffic</Text><Text variant="bodySm" color="subdued">No request/response samples recorded for this component.</Text></VerticalStack></Box>;

// Renders captured samples as paginated request + response cards — reuses the legacy ApiDetails
// "Values" tab component (SampleDataList) so padding, pagination and cards match the old flyout.
function TrafficView({ traffic, loading }) {
    if (loading) return TRAFFIC_LOADING;
    if (!traffic?.length) return TRAFFIC_EMPTY;
    const sampleData = traffic.map((s) => ({ message: typeof s === "string" ? s : JSON.stringify(s) }));
    return (
        <div style={{ flex: 1, minHeight: 0, overflowY: "auto", overflowX: "hidden", padding: "16px" }}>
            <SampleDataList
                sampleData={sampleData}
                heading="Sample values"
                minHeight="35vh"
                vertical={true}
                isAPISampleData={true}
            />
        </div>
    );
}

// Shared hook: lazily fetch sample traffic for an endpoint (url + method + collection).
function useEndpointTraffic(item, active) {
    const [traffic, setTraffic] = useState(null);
    const [loading, setLoading] = useState(false);
    const fetched = useRef(false);
    useEffect(() => {
        if (!active || fetched.current) return;
        if (!item?.url || !item?.apiCollectionId) { setTraffic([]); return; }
        fetched.current = true;
        setLoading(true);
        observeApi.fetchSampleData(item.url, item.apiCollectionId, item.method || "GET")
            .then(res => {
                const samples = (res?.sampleDataList || []).flatMap(s => s.samples || []);
                setTraffic(samples);
            })
            .catch(() => setTraffic([]))
            .finally(() => setLoading(false));
    }, [active, item?.url, item?.apiCollectionId, item?.method]);
    return { traffic, loading };
}

export function ToolDetailPanel({ tool, onBack }) {
    const { traffic, loading } = useEndpointTraffic(tool, true);
    return (
        <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
            <TrafficView traffic={traffic} loading={loading} />
        </div>
    );
}

function ResourcePromptDetailPanel({ item }) {
    const { traffic, loading } = useEndpointTraffic(item, true);
    return (
        <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
            <Box paddingInlineStart="3" paddingInlineEnd="3" paddingBlockStart="3" paddingBlockEnd="2">
                <Text variant="headingSm" as="h3" fontWeight="semibold">{item.name}</Text>
            </Box>
            <Divider />
            <TrafficView traffic={traffic} loading={loading} />
        </div>
    );
}

export function SkillDetailPanel({ skill }) {
    const { traffic, loading } = useEndpointTraffic(skill, true);
    return (
        <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
            <Box paddingInlineStart="3" paddingInlineEnd="3" paddingBlockStart="3" paddingBlockEnd="2">
                <Text variant="headingSm" as="h3" fontWeight="semibold">{skill.name}</Text>
            </Box>
            <Divider />
            <TrafficView traffic={traffic} loading={loading} />
        </div>
    );
}

// ── Cell renderers ────────────────────────────────────────────────────────────

const MCP_ITEM_BADGE_STATUS = { Tool: "info", Resource: "success", Prompt: "new", Server: "attention", Skill: "warning" };

function McpItemTypeCellRenderer({ value }) {
    if (!value) return null;
    return <Badge status={MCP_ITEM_BADGE_STATUS[value]}>{value}</Badge>;
}

export function ViolationCountCellRenderer({ value }) {
    if (!value) return <Text variant="bodyMd" color="subdued">-</Text>;
    return <SeverityBadge severity="critical">{value}</SeverityBadge>;
}

function McpRiskScoreCellRenderer({ data }) {
    if (!data) return null;
    const score = data.riskScore;
    if (!score) return <Text variant="bodyMd" color="subdued">-</Text>;
    return <RiskPill score={score} />;
}

function McpRiskCellRenderer({ data }) {
    if (!data) return null;
    const cra = {
        isComponentMalicious: data.isMalicious || false,
        hasPrivilegedAccess: data.hasPrivilegedAccess || false,
        evidence: data.riskDescription || "",
    };
    const hasViolations = data._type === "Tool" && (data.violationCount || 0) > 0;
    const hasRisk = cra.isComponentMalicious || cra.hasPrivilegedAccess;
    if (!hasRisk && !hasViolations) return <Text variant="bodyMd" color="subdued">-</Text>;
    return (
        <HorizontalStack gap="2" blockAlign="center">
            {hasRisk && <ComponentRiskAnalysisBadges componentRiskAnalysis={cra} />}
            {hasViolations && <SeverityBadge severity="medium">{data.violationCount}</SeverityBadge>}
        </HorizontalStack>
    );
}

// ── Column definitions ────────────────────────────────────────────────────────

const COMBINED_MCP_COL_DEFS = [
    {
        field: "name",
        headerName: "Name",
        flex: 1.5,
        minWidth: 160,
        filter: "agTextColumnFilter",
        cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#202223" },
    },
    {
        field: "_type",
        headerName: "Type",
        width: 100,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: McpItemTypeCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        field: "riskScore",
        headerName: "Risk Score",
        width: 110,
        filter: false,
        sort: "desc",
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: McpRiskScoreCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: p => p.data?.riskScore || 0,
    },
    {
        headerName: "Risk",
        width: 160,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: McpRiskCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: p => (p.data?.isMalicious ? 4 : 0) + (p.data?.hasPrivilegedAccess ? 2 : 0) + (p.data?._type === "Tool" && p.data?.violationCount > 0 ? 1 : 0),
    },
];


// ── Picker dropdown — switch between tools/resources/prompts from breadcrumb ──

function McpPickerDropdown({ allRows, selected, onSelect }) {
    const [open, setOpen] = useState(false);
    const containerRef = useRef(null);

    useEffect(() => {
        if (!open) return;
        const close = (e) => {
            if (containerRef.current && !containerRef.current.contains(e.target)) {
                setOpen(false);
            }
        };
        const timer = setTimeout(() => {
            document.addEventListener("click", close);
        }, 0);
        return () => {
            clearTimeout(timer);
            document.removeEventListener("click", close);
        };
    }, [open]);

    if (!allRows || allRows.length <= 1) {
        return <Text variant="bodySm" fontWeight="semibold">{selected?.name}</Text>;
    }
    return (
        <Box ref={containerRef} style={{ position: "relative", display: "inline-block" }}>
            <Button plain disclosure onClick={() => setOpen(s => !s)}>
                {selected?.name}
            </Button>
            {open && (
                <Box
                    style={{
                        position: "absolute",
                        top: "100%",
                        left: 0,
                        zIndex: 1001,
                        background: "white",
                        border: "1px solid #E1E3E5",
                        borderRadius: 8,
                        boxShadow: "0 4px 12px rgba(0,0,0,0.12)",
                        minWidth: 200,
                        maxHeight: 300,
                        overflowY: "auto",
                    }}
                    padding="1"
                >
                    <ActionList
                        items={allRows.map(r => ({
                            content: r.name,
                            active: r.name === selected?.name,
                            onAction: () => { onSelect(r); setOpen(false); },
                        }))}
                    />
                </Box>
            )}
        </Box>
    );
}

// ── Main view ─────────────────────────────────────────────────────────────────

export default function McpComponentsView({ asset, onNavChange }) {
    const [selectedItem, setSelectedItem] = useState(null);
    const [allRows, setAllRows] = useState([]);

    useEffect(() => {
        const collectionIds = asset?.collectionIds;
        if (!collectionIds?.length) { setAllRows([]); return; }
        let cancelled = false;
        (async () => {
            try {
                const results = await Promise.all(collectionIds.map(id => agenticObserveApi.fetchMcpComponentsData(id)));
                if (cancelled) return;
                // Merge across all collections, dedupe by name+type
                const seen = new Set();
                const tools = [], resources = [], prompts = [], skills = [];
                const toolViolations = {};
                results.forEach(data => {
                    Object.assign(toolViolations, data.toolViolations || {});
                    (data.tools     || []).forEach(t  => { const k = `tool:${t.name}`;     if (!seen.has(k)) { seen.add(k); tools.push(t); } });
                    (data.resources || []).forEach(r  => { const k = `resource:${r.name}`; if (!seen.has(k)) { seen.add(k); resources.push(r); } });
                    (data.prompts   || []).forEach(p  => { const k = `prompt:${p.name}`;   if (!seen.has(k)) { seen.add(k); prompts.push(p); } });
                    (data.skills    || []).forEach(s  => { const k = `skill:${s.name}`;    if (!seen.has(k)) { seen.add(k); skills.push(s); } });
                });
                setAllRows([
                    ...tools.map(t     => ({ ...t, _type: "Tool",     violationCount: toolViolations[t.name] || 0 })),
                    ...resources.map(r => ({ ...r, _type: "Resource" })),
                    ...prompts.map(p   => ({ ...p, _type: "Prompt" })),
                    ...skills.map(s    => ({ ...s, _type: "Skill" })),
                ]);
            } catch {
                if (!cancelled) setAllRows([]);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.collectionIds]);

    const selectItemRef = useRef(null);
    const selectItem = useCallback((row) => {
        setSelectedItem({ item: row, type: row._type.toLowerCase() });
    }, []);
    selectItemRef.current = selectItem;

    // Re-publish nav whenever selectedItem or allRows changes so the picker JSX is always fresh
    useEffect(() => {
        if (!selectedItem) return;
        onNavChange(
            [{ label: asset.name, onClick: () => { setSelectedItem(null); onNavChange(null); } }],
            <McpPickerDropdown allRows={allRows} selected={selectedItem.item} onSelect={(r) => selectItemRef.current?.(r)} />
        );
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedItem, allRows, asset.name, onNavChange]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        selectItem(e.data);
    }, [selectItem]);

    if (selectedItem?.type === "skill") return <SkillDetailPanel key={selectedItem.item.name} skill={selectedItem.item} />;
    if (selectedItem?.type === "tool")  return <ToolDetailPanel key={selectedItem.item.name} tool={selectedItem.item} />;
    if (selectedItem)                   return <ResourcePromptDetailPanel key={selectedItem.item.name} item={selectedItem.item} />;

    return allRows.length === 0 ? (
        <Box padding="4"><Text variant="bodySm" color="subdued">No tools, resources, prompts or skills found.</Text></Box>
    ) : (
        <AgGridTable
            rowData={allRows}
            columnDefs={COMBINED_MCP_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={handleRowClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search tools, resources, prompts..."
            pagination={false}
            sideBar
            domLayout="normal"
        />
    );
}

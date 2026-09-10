import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Box, Text, Badge, HorizontalStack, VerticalStack, Divider, Spinner } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { TypeBadge, RiskPill, SeverityBadge } from "./AgenticCellRenderers";
import { ToolDetailPanel, SkillDetailPanel } from "./McpComponentsView";
import PluginComponentsView from "./PluginComponentsView";
import ComponentRiskAnalysisBadges from "../components/ComponentRiskAnalysisBadges";
import agenticObserveApi, { openViolationInGuardrailViolations } from "./agenticObserveApi";
import { buildMcpComponentsFromStis } from "./agenticPageBuilders";
import api from "../api";
import func from "@/util/func";

// ── Cell renderers ────────────────────────────────────────────────────────────

function AgentComponentNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <Box width="100%" overflowX="hidden">
                <Text variant="bodySm" truncate>{data.name}</Text>
            </Box>
            {data.isNew && <Badge>New</Badge>}
        </HorizontalStack>
    );
}

function AgentComponentTypeCellRenderer({ value }) {
    if (!value) return null;
    if (value === "Config") return <Badge status="attention">Config</Badge>;
    return <TypeBadge type={value} />;
}

function AgentComponentViolationsCellRenderer({ data }) {
    if (!data?.violations) return <Text variant="bodySm" color="subdued">-</Text>;
    // Skill rows: violations is a plain number from fetchSkillsFlyoutData
    if (typeof data.violations === "number") {
        if (!data.violations) return <Text variant="bodySm" color="subdued">-</Text>;
        return <SeverityBadge severity="critical">{data.violations}</SeverityBadge>;
    }
    // Config / other rows: violations is an object { critical, high, medium, low }
    const parts = ["critical", "high", "medium", "low"].filter(k => data.violations[k] > 0);
    if (!parts.length) return <Text variant="bodySm" color="subdued">-</Text>;
    return (
        <HorizontalStack gap="1" blockAlign="center">
            {parts.map(k => <SeverityBadge key={k} severity={k}>{data.violations[k]}</SeverityBadge>)}
        </HorizontalStack>
    );
}

// ── Column definitions ────────────────────────────────────────────────────────

function ToolRiskCellRenderer({ data }) {
    if (!data) return null;
    const cra = {
        isComponentMalicious: data.isMalicious || false,
        hasPrivilegedAccess: data.hasPrivilegedAccess || false,
        evidence: data.riskDescription || "",
    };
    if (!cra.isComponentMalicious && !cra.hasPrivilegedAccess) return <Text variant="bodySm" color="subdued">-</Text>;
    return <ComponentRiskAnalysisBadges componentRiskAnalysis={cra} />;
}

function ToolRiskScoreCellRenderer({ data }) {
    if (!data) return null;
    const score = data.riskScore;
    if (!score) return <Text variant="bodyMd" color="subdued">-</Text>;
    return <RiskPill score={score} />;
}

const TOOLS_COL_DEFS = [
    { field: "name", headerName: "Tool", flex: 1, minWidth: 160, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#202223" } },
    { field: "riskScore", headerName: "Risk Score", width: 110, sort: "desc", suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolRiskScoreCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => p.data?.riskScore || 0 },
    { headerName: "Risk", width: 160, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolRiskCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => (p.data?.isMalicious ? 2 : 0) + (p.data?.hasPrivilegedAccess ? 1 : 0) },
];

const COMBINED_AGENT_COL_DEFS = [
    {
        field: "name",
        headerName: "Component Name",
        flex: 2,
        minWidth: 200,
        filter: "agTextColumnFilter",
        cellRenderer: AgentComponentNameCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        field: "_type",
        headerName: "Type",
        width: 130,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: AgentComponentTypeCellRenderer,
        cellClass: (p) => ({ "AI Agent": "agentic-type-AGENT", "MCP Server": "agentic-type-MCP", "LLM": "agentic-type-LLM", "Skill": "agentic-type-SKILL", "Plugin": "agentic-type-PLUGIN", "Tool": "agentic-type-TOOL" })[p.value] || "agentic-type-DEFAULT",
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        headerName: "Violations",
        width: 160,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: AgentComponentViolationsCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: p => {
            const v = p.data?.violations;
            if (!v) return 0;
            if (typeof v === "number") return v;
            return (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0);
        },
    },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ── MCP tools drill-down ──────────────────────────────────────────────────────
// Exported — PluginComponentsView reuses this unchanged for a plugin's own bundled MCP servers
// (it only needs selectedMcp.collectionIds/name, nothing agent-specific).

export function AgentMcpToolsView({ asset, selectedMcp, goToList, onNavChange, setSelectedTool, setView }) {
    const [mcpTools, setMcpTools] = useState([]);

    useEffect(() => {
        const collectionIds = selectedMcp?.collectionIds;
        if (!collectionIds?.length) { setMcpTools([]); return; }
        let cancelled = false;
        (async () => {
            try {
                // Batched — see AgenticAssetFlyout.jsx's AI Agent effect for why this matters.
                const bundleMap = await agenticObserveApi.fetchCollectionStiBundlesBatch(collectionIds);
                if (cancelled) return;
                const seen = new Set();
                const merged = [];
                bundleMap.forEach(b => {
                    const data = buildMcpComponentsFromStis(b.stiEndpoints, b.apiInfoList, b.id, b.auditRows);
                    (data.tools || []).forEach(t => {
                        if (!seen.has(t.name)) { seen.add(t.name); merged.push(t); }
                    });
                });
                setMcpTools(merged);
            } catch {
                if (!cancelled) setMcpTools([]);
            }
        })();
        return () => { cancelled = true; };
    }, [selectedMcp?.name, selectedMcp?.collectionIds]);

    return (
        <Box className="agentic-flex-fill">
            {selectedMcp?.description && (
                <>
                    <Box paddingInlineStart="3" paddingInlineEnd="3" paddingBlockStart="3" paddingBlockEnd="3">
                        <Text variant="bodySm">{selectedMcp.description}</Text>
                    </Box>
                    <Divider />
                </>
            )}
            {mcpTools.length === 0 ? (
                <Box padding="4"><Text variant="bodySm" color="subdued">No tools found.</Text></Box>
            ) : (
                <AgGridTable
                    rowData={mcpTools}
                    columnDefs={TOOLS_COL_DEFS}
                    defaultColDef={GRID_DEFAULT_COL}
                    onRowClicked={(e) => {
                        if (!e.data) return;
                        setSelectedTool(e.data);
                        setView("tool-detail");
                        onNavChange?.([
                            { label: asset.name, onClick: goToList },
                            {
                                label: selectedMcp.name,
                                onClick: () => {
                                    setView("mcp-tools");
                                    setSelectedTool(null);
                                    onNavChange?.([{ label: asset.name, onClick: goToList }, { label: selectedMcp.name }]);
                                },
                            },
                            { label: e.data.name },
                        ]);
                    }}
                    getRowStyle={() => ({ cursor: "pointer" })}
                    fillHeight
                    noOuterBorder
                    searchPlaceholder="Search tools..."
                    pagination={false}
                    sideBar={false}
                    domLayout="normal"
                />
            )}
        </Box>
    );
}

// ── Plugin detail drill-down ──────────────────────────────────────────────────
// Same in-place render as SkillDetailPanel above — the Components list row only carries the
// plugin's name, so its version/scope/status/marketplace are fetched lazily on selection instead
// of being embedded in every row of the (otherwise cheap, no-re-derivation) components list.

// pluginGroupKey is the compound "pluginName|ownerAgent" identity (AgenticObserveAction's
// classifyAllGroups), not just the plugin's display name — the same plugin name installed under
// two different agents (e.g. "figma" on both claude and copilot) are genuinely different installs.
function usePluginDetail(pluginGroupKey) {
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(true);
    useEffect(() => {
        if (!pluginGroupKey) { setDetail(null); setLoading(false); return; }
        let cancelled = false;
        setLoading(true);
        api.fetchAgenticAssetDetail({ groupKey: pluginGroupKey, rowType: "plugin" })
            .then((found) => { if (!cancelled) setDetail(found); })
            .catch(() => { if (!cancelled) setDetail(null); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [pluginGroupKey]);
    return { detail, loading };
}

function PluginDetailPanel({ plugin, onNavChange }) {
    const pluginGroupKey = plugin?.rawName || plugin?.name;
    const { detail, loading } = usePluginDetail(pluginGroupKey);

    if (loading) {
        return <Box padding="8"><Spinner accessibilityLabel="Loading plugin" size="small" /></Box>;
    }

    return (
        <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
            <Box paddingInlineStart="3" paddingInlineEnd="3" paddingBlockStart="3" paddingBlockEnd="2">
                <Text variant="headingSm" as="h3" fontWeight="semibold">{plugin?.name}</Text>
            </Box>
            <Divider />
            <PluginComponentsView
                asset={{
                    id: pluginGroupKey,
                    name: plugin?.name,
                    collectionIds: detail?.collectionIds || [],
                    pluginMcpServers: detail?.pluginMcpServers || [],
                    pluginMcpServerCollectionIds: detail?.pluginMcpServerCollectionIds || {},
                    pluginSkills: detail?.pluginSkills || [],
                }}
                onNavChange={onNavChange}
            />
        </div>
    );
}

// ── Config violations drill-down ──────────────────────────────────────────────
// Mirrors the MCP tools drill-down: a breadcrumb sub-view listing the individual config
// violation events; clicking a row opens that event in threat-activity.

const SEVERITY_ORDER = { low: 1, medium: 2, high: 3, critical: 4 };

function ConfigViolTitleCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Box width="100%" overflowX="hidden">
            <Text variant="bodySm" fontWeight="semibold" truncate>{data.title}</Text>
        </Box>
    );
}

function ConfigViolSeverityCellRenderer({ data }) {
    if (!data) return null;
    return <SeverityBadge severity={data.severity} />;
}

const CONFIG_VIOL_COL_DEFS = [
    { field: "time", headerName: "Time", width: 160, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.timeEpoch || 0) - (nodeB?.data?.timeEpoch || 0) },
    { field: "title", headerName: "Violation", flex: 1, minWidth: 200, cellRenderer: ConfigViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "severity", headerName: "Severity", width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ConfigViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, comparator: (a, b) => (SEVERITY_ORDER[a] || 0) - (SEVERITY_ORDER[b] || 0) },
];

function ConfigViolationsView({ configRows = [] }) {
    const rows = useMemo(
        () => configRows.map((r) => ({ ...r, time: r.timeEpoch ? func.formatChatTimestamp(r.timeEpoch) : "" })),
        [configRows],
    );

    if (!rows.length) {
        return <Box padding="4"><Text variant="bodySm" color="subdued">No config violations found.</Text></Box>;
    }

    return (
        <Box className="agentic-flex-fill">
            <AgGridTable
                rowData={rows}
                columnDefs={CONFIG_VIOL_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                onRowClicked={(e) => { if (e.data) openViolationInGuardrailViolations(e.data); }}
                getRowStyle={() => ({ cursor: "pointer" })}
                fillHeight
                noOuterBorder
                searchPlaceholder="Search config violations..."
                pagination={false}
                sideBar={false}
                domLayout="normal"
            />
        </Box>
    );
}

// ── Main view ─────────────────────────────────────────────────────────────────

export default function AgentComponentsView({ asset, onNavChange, onNavigateToAsset, configViolations = null, configRows = [] }) {
    const [view,          setView]          = useState("list");
    const [selectedMcp,   setSelectedMcp]   = useState(null);
    const [selectedTool,  setSelectedTool]  = useState(null);
    const [selectedSkill, setSelectedSkill] = useState(null);
    const [selectedPlugin, setSelectedPlugin] = useState(null);

    // Server-side paginated — merges skills, built-in tools, and connected MCP servers into one
    // batched query instead of the old per-collection-id N+1 (see AgenticObserveAction.
    // fetchAgenticComponentsPage). asset.collectionIds/mcpServers/mcpServerCollectionIds are all
    // already known client-side (part of the asset row itself), so no extra fetch is needed to
    // build the request.
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        return api.fetchAgenticComponentsPage({
            apiCollectionIds: [...(asset.collectionIds || []), ...(asset.pluginCollectionIds || [])],
            mcpServerNames: asset.mcpServers || [],
            mcpServerCollectionIds: asset.mcpServerCollectionIds || {},
            pluginNames: asset.pluginNames || [],
            skip,
            limit: limit || 20,
            sortKey,
            sortOrder: sortOrder ? -sortOrder : -1,
            queryValue: searchString || undefined,
        }).then((res) => ({
            value: res.components || [],
            total: res.total || 0,
        }));
    }, [asset.id, asset.collectionIds, asset.pluginCollectionIds, asset.mcpServers, asset.mcpServerCollectionIds, asset.pluginNames]);

    const goToList = useCallback(() => {
        setView("list"); setSelectedMcp(null); setSelectedTool(null); setSelectedSkill(null); setSelectedPlugin(null);
        onNavChange?.(null);
    }, [onNavChange]);

    // Config row: shown for Claude agents that have claude-config violations (url /claude/config/*)
    // attributed to their devices. Clicking drills into a breadcrumb sub-view listing the individual
    // config violations; each of those rows opens in threat-activity.
    const configRow = useMemo(() => {
        const isClaudeAgent = asset?.assetTagValue?.toLowerCase() === "claude";
        if (!isClaudeAgent || !configViolations || configViolations.total === 0) return null;
        return {
            id: "__config__",
            name: "Claude Settings",
            _type: "Config",
            violations: configViolations,
        };
    }, [asset?.assetTagValue, configViolations]);

    const handleListRowClick = useCallback((e) => {
        if (!e.data || e.data._nonClickable) return;
        if (e.data._type === "Config") {
            setView("config-detail");
            onNavChange?.([
                { label: asset.name, onClick: goToList },
                { label: e.data.name },
            ]);
            return;
        }
        if (e.data._type === "MCP Server") {
            setSelectedMcp(e.data);
            setView("mcp-tools");
            onNavChange?.([
                { label: asset.name, onClick: goToList },
                { label: e.data.name },
            ]);
        } else if (e.data._type === "Tool") {
            setSelectedTool(e.data);
            setView("tool-detail");
            onNavChange?.([
                { label: asset.name, onClick: goToList },
                { label: e.data.name },
            ]);
        } else if (e.data._type === "Skill") {
            // Show the skill's captured traffic inline (same as the MCP-server skill drill-down)
            setSelectedSkill(e.data);
            setView("skill-detail");
            onNavChange?.([
                { label: asset.name, onClick: goToList },
                { label: e.data.name },
            ]);
        } else if (e.data._type === "Plugin") {
            // Show the plugin's own metadata inline (same idiom as the skill drill-down above)
            setSelectedPlugin(e.data);
            setView("plugin-detail");
            onNavChange?.([
                { label: asset.name, onClick: goToList },
                { label: e.data.name },
            ]);
        }
    }, [setSelectedMcp, setView, onNavChange, goToList, asset.name]);

    if (view === "tool-detail" && selectedTool) {
        return <ToolDetailPanel tool={selectedTool} onBack={() => {
            setSelectedTool(null);
            if (selectedMcp) {
                setView("mcp-tools");
                onNavChange?.([
                    { label: asset.name, onClick: goToList },
                    { label: selectedMcp?.name },
                ]);
            } else {
                goToList();
            }
        }} />;
    }

    if (view === "mcp-tools" && selectedMcp) {
        return (
            <AgentMcpToolsView
                asset={asset}
                selectedMcp={selectedMcp}
                goToList={goToList}
                onNavChange={onNavChange}
                setSelectedTool={setSelectedTool}
                setView={setView}
            />
        );
    }

    if (view === "skill-detail" && selectedSkill) {
        return <SkillDetailPanel skill={selectedSkill} collectionIds={asset?.collectionIds} />;
    }

    if (view === "plugin-detail" && selectedPlugin) {
        return <PluginDetailPanel plugin={selectedPlugin} onNavChange={onNavChange} />;
    }

    if (view === "config-detail") {
        return <ConfigViolationsView configRows={configRows} />;
    }

    return (
        <AgGridTable
            key={asset.id}
            columnDefs={COMBINED_AGENT_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onServerFetch={onServerFetch}
            serverSideRowModel
            getRowId={(params) => `${params.data._type}:${params.data.name}`}
            pinnedTopRowData={configRow ? [configRow] : undefined}
            onRowClicked={handleListRowClick}
            getRowStyle={({ data }) => ({ cursor: data?._nonClickable ? "default" : "pointer" })}
            noOuterBorder
            searchPlaceholder="Search components..."
            paginationPageSize={20}
            sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            domLayout="normal"
        />
    );
}

// ── LLM config (only shown for LLM type assets) ───────────────────────────────

export function LlmConfigSection({ asset }) {
    const config = useMemo(() => [
        { label: "Model",     value: asset.name },
        { label: "Devices",   value: `${asset.deviceCount || 0} device${asset.deviceCount !== 1 ? "s" : ""}` },
        { label: "Last seen", value: asset.lastSeen || "-" },
    ], [asset]);

    return (
        <Box padding="4">
            <VerticalStack gap="3">
                <Text variant="headingSm">Configuration</Text>
                {config.map(c => (
                    <HorizontalStack key={c.label} gap="4" blockAlign="center">
                        <Box minWidth="140px">
                            <Text variant="bodySm" color="subdued">{c.label}</Text>
                        </Box>
                        <Text variant="bodySm" fontWeight="semibold">{c.value}</Text>
                    </HorizontalStack>
                ))}
            </VerticalStack>
        </Box>
    );
}

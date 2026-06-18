import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Box, Text, Badge, HorizontalStack, VerticalStack } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { TypeBadge, RiskPill, SeverityBadge } from "./AgenticCellRenderers";
import { ToolDetailPanel, SkillDetailPanel } from "./McpComponentsView";
import ComponentRiskAnalysisBadges from "../components/ComponentRiskAnalysisBadges";
import agenticObserveApi, { openViolationInThreatActivity } from "./agenticObserveApi";
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
        cellClass: (p) => ({ "AI Agent": "agentic-type-AGENT", "MCP Server": "agentic-type-MCP", "LLM": "agentic-type-LLM", "Skill": "agentic-type-SKILL" })[p.value] || "agentic-type-DEFAULT",
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

function AgentMcpToolsView({ asset, selectedMcp, agenticFlatData, goToList, onNavChange, setSelectedTool, setView }) {
    const [mcpTools, setMcpTools] = useState([]);

    useEffect(() => {
        const flat = agenticFlatData.find((a) => a.name === selectedMcp.name || a.id === selectedMcp.name);
        const collectionIds = flat?.collectionIds;
        if (!collectionIds?.length) { setMcpTools([]); return; }
        let cancelled = false;
        (async () => {
            try {
                const results = await Promise.all(collectionIds.map(id => agenticObserveApi.fetchMcpComponentsData(id)));
                if (cancelled) return;
                const seen = new Set();
                const merged = [];
                results.forEach(data => {
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
    }, [selectedMcp?.name, agenticFlatData]);

    return (
        <Box className="agentic-flex-fill">
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
                onRowClicked={(e) => { if (e.data) openViolationInThreatActivity(e.data); }}
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

export default function AgentComponentsView({ asset, onNavChange, onNavigateToAsset, agenticFlatData = [], configViolations = null, configRows = [] }) {
    const [view,          setView]          = useState("list");
    const [selectedMcp,   setSelectedMcp]   = useState(null);
    const [selectedTool,  setSelectedTool]  = useState(null);
    const [selectedSkill, setSelectedSkill] = useState(null);
    const [skills,        setSkills]        = useState([]);

    const connectedMcps = useMemo(() => {
        if (!asset.mcpServers?.length) return [];
        const seen = new Set();
        return asset.mcpServers
            .filter((mcpName) => {
                const key = String(mcpName).toLowerCase();
                if (seen.has(key)) return false;
                seen.add(key);
                return true;
            })
            .map((mcpName) => ({ id: mcpName, name: mcpName, endpoint: mcpName, toolCount: 0 }));
    }, [asset.mcpServers]);

    useEffect(() => {
        const collectionIds = asset?.collectionIds;
        if (!collectionIds?.length) { setSkills([]); return; }
        let cancelled = false;
        (async () => {
            try {
                const results = await Promise.all(collectionIds.map(id => agenticObserveApi.fetchSkillsFlyoutData(id)));
                if (cancelled) return;
                const seen = new Set();
                const merged = [];
                results.forEach(data => {
                    (data.skills || []).forEach(s => {
                        if (!seen.has(s.name)) { seen.add(s.name); merged.push(s); }
                    });
                });
                setSkills(merged);
            } catch {
                if (!cancelled) setSkills([]);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.collectionIds]);

    const goToList = useCallback(() => {
        setView("list"); setSelectedMcp(null); setSelectedTool(null); setSelectedSkill(null);
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

    const allComponents = useMemo(() => [
        ...(configRow ? [configRow] : []),
        ...connectedMcps.map(m => ({ ...m, _type: "MCP Server" })),
        ...skills.map(s => ({ ...s, _type: "Skill" })),
    ], [configRow, connectedMcps, skills]);

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
        } else if (e.data._type === "Skill") {
            // Show the skill's captured traffic inline (same as the MCP-server skill drill-down)
            setSelectedSkill(e.data);
            setView("skill-detail");
            onNavChange?.([
                { label: asset.name, onClick: goToList },
                { label: e.data.name },
            ]);
        }
    }, [setSelectedMcp, setView, onNavChange, goToList, asset.name]);

    if (view === "tool-detail" && selectedTool) {
        return <ToolDetailPanel tool={selectedTool} onBack={() => {
            setView("mcp-tools"); setSelectedTool(null);
            onNavChange?.([
                { label: asset.name, onClick: goToList },
                { label: selectedMcp?.name },
            ]);
        }} />;
    }

    if (view === "mcp-tools" && selectedMcp) {
        return (
            <AgentMcpToolsView
                asset={asset}
                selectedMcp={selectedMcp}
                agenticFlatData={agenticFlatData}
                goToList={goToList}
                onNavChange={onNavChange}
                setSelectedTool={setSelectedTool}
                setView={setView}
            />
        );
    }

    if (view === "skill-detail" && selectedSkill) {
        return <SkillDetailPanel skill={selectedSkill} />;
    }

    if (view === "config-detail") {
        return <ConfigViolationsView configRows={configRows} />;
    }

    if (allComponents.length === 0) {
        return <Box padding="4"><Text variant="bodySm" color="subdued">No components found for this agent.</Text></Box>;
    }

    return (
        <AgGridTable
            rowData={allComponents}
            columnDefs={COMBINED_AGENT_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={handleListRowClick}
            getRowStyle={({ data }) => ({ cursor: data?._nonClickable ? "default" : "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search components..."
            pagination
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

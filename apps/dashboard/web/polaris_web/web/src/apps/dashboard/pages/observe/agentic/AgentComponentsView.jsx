import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Box, Text, Badge, HorizontalStack, VerticalStack } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { RiskPill, TypeBadge } from "./AgenticCellRenderers";
import { ToolDetailPanel, SkillDetailPanel, ViolationCountCellRenderer } from "./McpComponentsView";
import agenticObserveApi from "./agenticObserveApi";

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
    return <TypeBadge type={value} />;
}

function DeviceRiskCellRenderer({ value }) {
    if (value == null) return <Text variant="bodyMd" color="subdued">-</Text>;
    return <RiskPill score={value} />;
}

// ── Column definitions ────────────────────────────────────────────────────────

const TOOLS_COL_DEFS = [
    { field: "name",      headerName: "Tool",   flex: 1,   minWidth: 160, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#202223" } },
    { field: "riskLevel", headerName: "Risk",   width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ({ value }) => value ? <Text variant="bodyMd" color="subdued">{value}</Text> : null, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "params",    headerName: "Params", width: 80,  suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => p.data?.params?.length ?? 0 },
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
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        field: "riskScore",
        headerName: "Risk Score",
        width: 100,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: DeviceRiskCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        headerName: "Violations",
        width: 110,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: ViolationCountCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: p => p.data?._type === "Skill" ? (p.data.violations || 0) : 0,
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
                />
            )}
        </Box>
    );
}

// ── Main view ─────────────────────────────────────────────────────────────────

export default function AgentComponentsView({ asset, onNavChange, onNavigateToAsset, agenticFlatData = [] }) {
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

    const allComponents = useMemo(() => [
        ...connectedMcps.map(m => ({ ...m, _type: "MCP Server" })),
        ...skills.map(s => ({ ...s, _type: "Skill" })),
    ], [connectedMcps, skills]);

    const handleListRowClick = useCallback((e) => {
        if (!e.data) return;
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

    if (allComponents.length === 0) {
        return <Box padding="4"><Text variant="bodySm" color="subdued">No components found for this agent.</Text></Box>;
    }

    return (
        <AgGridTable
            rowData={allComponents}
            columnDefs={COMBINED_AGENT_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={handleListRowClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search components..."
            pagination
            paginationPageSize={20}
            sideBar={false}
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

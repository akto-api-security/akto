import React, { useState, useCallback } from "react";
import { Box, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { TypeBadge } from "./AgenticCellRenderers";
import { SkillDetailPanel, ToolDetailPanel } from "./McpComponentsView";
import { AgentMcpToolsView } from "./AgentComponentsView";
import api from "../api";

function buildAgentMarkdown(sampleMessage, agentName) {
    try {
        const parsed = JSON.parse(sampleMessage);
        const bodyStr = parsed?.request?.body || parsed?.requestPayload || "{}";
        const body = JSON.parse(bodyStr);
        if (!body.agent_name) return null;
        if (agentName && body.agent_name.toLowerCase() !== agentName.toLowerCase()) return null;
        return body.agent_content || "";
    } catch (_) {
        return null;
    }
}

export async function fetchAgentMarkdownFromCollections(collectionIds, agentName) {
    for (const collectionId of (collectionIds || [])) {
        const infoResp = await api.fetchApiInfosForCollection(collectionId);
        const infos = infoResp?.apiInfoList || [];
        for (const info of infos) {
            const url = String(info?.id?.url || "");
            if (!url.toLowerCase().includes("/agents/")) continue;
            const method = info?.id?.method || "POST";
            const pathOnly = url.replace(/^https?:\/\/[^/]+/, "");
            for (const candidateUrl of new Set([url, pathOnly])) {
                const resp = await api.fetchSampleData(candidateUrl, collectionId, method);
                const samples = (resp?.sampleDataList || []).flatMap((s) => s.samples || []);
                for (const sample of samples) {
                    const md = buildAgentMarkdown(sample, agentName);
                    if (md !== null) return { markdown: md, collectionId };
                }
            }
        }
    }
    return { markdown: null, collectionId: null };
}

// ── Cell renderers ────────────────────────────────────────────────────────────

function PluginComponentNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Box width="100%" overflowX="hidden">
            <Text variant="bodySm" truncate>{data.name}</Text>
        </Box>
    );
}

function PluginComponentTypeCellRenderer({ value }) {
    if (!value) return null;
    return <TypeBadge type={value} />;
}

// ── Column definitions ────────────────────────────────────────────────────────
// Skills, MCP servers, and sub-agents — a plugin doesn't bundle Tools/Config/other plugins directly
// (those only show up once you drill into one of its MCP servers, same as AgentComponentsView).

const PLUGIN_COMPONENT_COL_DEFS = [
    {
        field: "name",
        headerName: "Component Name",
        flex: 2,
        minWidth: 200,
        filter: "agTextColumnFilter",
        cellRenderer: PluginComponentNameCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        field: "_type",
        headerName: "Type",
        width: 130,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: PluginComponentTypeCellRenderer,
        cellClass: (p) => ({ "MCP Server": "agentic-type-MCP", "Skill": "agentic-type-SKILL", "Agent": "agentic-type-AGENT" })[p.value] || "agentic-type-DEFAULT",
        cellStyle: { display: "flex", alignItems: "center" },
    },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ── Main view ─────────────────────────────────────────────────────────────────
// Plugins are discovery-only: no traffic/violations of their own. Metadata (status/version/scope/
// marketplace) lives in OverviewTab now — this view is just the bundled components table, same
// list/drill-down idiom AgentComponentsView already uses for an AI Agent's MCP servers/skills.
export default function PluginComponentsView({ asset, onNavChange }) {
    const [view, setView] = useState("list");
    const [selectedMcp, setSelectedMcp] = useState(null);
    const [selectedTool, setSelectedTool] = useState(null);
    const [selectedSkill, setSelectedSkill] = useState(null);
    const [selectedAgent, setSelectedAgent] = useState(null);

    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        return api.fetchAgenticComponentsPage({
            apiCollectionIds: asset.collectionIds || [],
            mcpServerNames: asset.pluginMcpServers || [],
            mcpServerCollectionIds: asset.pluginMcpServerCollectionIds || {},
            skip,
            limit: limit || 20,
            sortKey,
            sortOrder: sortOrder ? -sortOrder : -1,
            queryValue: searchString || undefined,
        }).then((res) => ({
            value: res.components || [],
            total: res.total || 0,
        }));
    }, [asset.id, asset.collectionIds, asset.pluginMcpServers, asset.pluginMcpServerCollectionIds]);

    const goToList = useCallback(() => {
        setView("list"); setSelectedMcp(null); setSelectedTool(null); setSelectedSkill(null); setSelectedAgent(null);
        onNavChange?.(null);
    }, [onNavChange]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        if (e.data._type === "MCP Server") {
            setSelectedMcp(e.data);
            setView("mcp-tools");
            onNavChange?.([{ label: asset.name, onClick: goToList }, { label: e.data.name }]);
        } else if (e.data._type === "Skill") {
            setSelectedSkill(e.data);
            setView("skill-detail");
            onNavChange?.([{ label: asset.name, onClick: goToList }, { label: e.data.name }]);
        } else if (e.data._type === "Agent") {
            setSelectedAgent(e.data);
            setView("agent-detail");
            onNavChange?.([{ label: asset.name, onClick: goToList }, { label: e.data.name }]);
        }
    }, [onNavChange, goToList, asset.name]);

    if (view === "tool-detail" && selectedTool) {
        return <ToolDetailPanel tool={selectedTool} onBack={() => {
            setSelectedTool(null);
            setView("mcp-tools");
            onNavChange?.([{ label: asset.name, onClick: goToList }, { label: selectedMcp?.name }]);
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
        // hideOwningPlugin: this skill row only ever appears here because THIS plugin bundles it —
        // showing "X uses this skill" would just repeat the asset you're already looking at.
        return <SkillDetailPanel skill={selectedSkill} collectionIds={asset?.collectionIds} hideOwningPlugin />;
    }

    if (view === "agent-detail" && selectedAgent) {
        return (
            <SkillDetailPanel
                skill={selectedAgent}
                collectionIds={asset?.collectionIds}
                hideOwningPlugin
                entityLabel="agent"
                fetchMarkdown={fetchAgentMarkdownFromCollections}
            />
        );
    }

    return (
        <Box className="agentic-flex-fill">
            <AgGridTable
                key={asset.id}
                columnDefs={PLUGIN_COMPONENT_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                onServerFetch={onServerFetch}
                serverSideRowModel
                getRowId={(params) => `${params.data._type}:${params.data.name}`}
                onRowClicked={handleRowClick}
                getRowStyle={() => ({ cursor: "pointer" })}
                noOuterBorder
                searchPlaceholder="Search components..."
                paginationPageSize={20}
                sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
                domLayout="normal"
            />
        </Box>
    );
}

import React, { useState, useMemo, useCallback, useEffect } from "react";
import ReactFlow, { Handle, Position, Background, Controls } from "react-flow-renderer";
import { Tabs, Box, HorizontalStack, HorizontalGrid, VerticalStack, Text, Divider, Card, LegacyCard, Icon, Avatar, Badge, Link, Popover, ActionList, Button } from "@shopify/polaris";
import { AutomationMajor, MagicMajor, CustomersMinor, ChevronRightMinor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import SampleDataComponent from "../../../components/shared/SampleDataComponent";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AiChatSection from "./AiChatSection";
import { ParamNameCellRenderer, ParamTypeCellRenderer, ParamDescCellRenderer } from "./agenticCellRenderers";
import { TYPE_STYLES, SEVERITY_COLORS, getRiskColor, getRiskLabel } from "./agenticStyles";
import { generateToolSample, generateResourceSample, generatePromptSample, generateSkillSample } from "./agenticSampleHelpers";
import agenticObserveApi, { buildAgenticObserveChatMetadata } from "./agenticObserveApi";
import observeApi from "../api";
import "../../../components/layouts/style.css";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function TypeBadge({ type }) {
    if (!type) return null;
    const s = TYPE_STYLES[type] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    return (
        <span style={{
            display: "inline-flex", alignItems: "center",
            padding: "1px 7px", borderRadius: 12,
            fontSize: 11, fontWeight: 500, lineHeight: "18px",
            background: s.bg, color: s.color, border: `1px solid ${s.border}`,
            whiteSpace: "nowrap",
        }}>{type}</span>
    );
}

// ─── Topology graph ───────────────────────────────────────────────────────────

function topoColors(category) {
    switch (category) {
        case "external": return { borderColor: "#3b82f6", backgroundColor: "#eff6ff" };
        case "agent":    return { borderColor: "#f97316", backgroundColor: "#fff7ed" };
        case "mcp":      return { borderColor: "#4cbebb", backgroundColor: "#ecfdf5" };
        case "ai-model": return { borderColor: "#ec4899", backgroundColor: "#fdf2f8" };
        case "skill":    return { borderColor: "#7C3AED", backgroundColor: "#F3E8FF" };
        default:         return { borderColor: "#6b7280", backgroundColor: "#f9fafb" };
    }
}

function topoIcon(category) {
    switch (category) {
        case "external": return CustomersMinor;
        case "agent":    return AutomationMajor;
        case "mcp":      return MCPIcon;
        case "ai-model": return MagicMajor;
        case "skill":    return AutomationMajor;
        default:         return CustomersMinor;
    }
}

function TopoNode({ data }) {
    const { component } = data;
    const colors = topoColors(component.category);
    const IconComponent = topoIcon(component.category);
    const isDevice = component.category === "external";

    return (
        <>
            {!isDevice && <Handle type="target" position={Position.Left} />}
            <Card padding={0}>
                {/* Dynamic border/background from data — Box doesn't support computed border-color */}
                <div style={{ border: `1px solid ${colors.borderColor}`, borderRadius: "8px", backgroundColor: colors.backgroundColor }}>
                    <Box padding={3}>
                        <VerticalStack gap={1}>
                            <Box width="150px">
                                <Text color="subdued" variant="bodySm">{component.type}</Text>
                            </Box>
                            <HorizontalStack gap={1} blockAlign="center">
                                {typeof IconComponent === "string"
                                    ? <Avatar source={IconComponent} size="extraSmall" />
                                    : <Icon source={IconComponent} />
                                }
                                <Box width={component.category === "ai-model" ? "140px" : "110px"}>
                                    <Text variant="bodySm" color="base">{component.label}</Text>
                                </Box>
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                </div>
            </Card>
            <Handle type="source" position={Position.Right} id="b" />
        </>
    );
}

const TOPO_NODE_TYPES = { topoNode: TopoNode };

const GRAPH_HEIGHT = 280;
const NODE_H       = 84;

/** Linked MCP/LLM rows for an AI agent (tree children when present, else flat asset data). */
function getAgentLinkedComponents(asset, agenticTreeData = [], agenticFlatData = []) {
    const fromTree = agenticTreeData.filter((r) => r.path?.length === 2 && r.path[0] === asset.id);
    if (fromTree.length) return fromTree;

    const seen = new Set();
    const linked = [];
    (asset.mcpServers || []).forEach((name) => {
        const key = String(name).toLowerCase();
        if (seen.has(key)) return;
        seen.add(key);
        const flat = agenticFlatData.find((a) => a.name === name || a.id === name);
        linked.push({
            name: flat?.name || name,
            type: flat?.type || "MCP Server",
        });
    });
    return linked;
}

function AssetTopologyGraph({ asset, assetDevices = {}, agenticTreeData = [], agenticFlatData = [] }) {
    const { nodes, edges } = useMemo(() => {
        const devices = assetDevices[asset.id] || [];

        if (asset.type === "AI Agent") {
            const children = getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData);
            const mcps = children.filter(c => c.type === "MCP Server");
            const llms = children.filter(c => c.type === "LLM");
            const rightCount = mcps.length + llms.length;
            const maxRows  = Math.max(devices.length, rightCount, 1);
            const totalH   = maxRows * NODE_H;
            const agentY   = (totalH - 44) / 2;
            const devOffset = Math.max(0, (rightCount - devices.length) * NODE_H / 2);

            return {
                nodes: [
                    { id: "agent", type: "topoNode", draggable: false, position: { x: 270, y: agentY }, data: { component: { category: "agent",    type: "AI Agent",    label: asset.name } } },
                    ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: 40,  y: devOffset + i * NODE_H          }, data: { component: { category: "external",  type: "User",        label: d.username || d.endpoint } } })),
                    ...mcps.map((m, i)    => ({ id: `mcp-${i}`, type: "topoNode", draggable: false, position: { x: 500, y: i * NODE_H                       }, data: { component: { category: "mcp",       type: "MCP Server",  label: m.name     } } })),
                    ...llms.map((l, i)    => ({ id: `llm-${i}`, type: "topoNode", draggable: false, position: { x: 500, y: (mcps.length + i) * NODE_H       }, data: { component: { category: "ai-model",  type: "LLM",         label: l.name     } } })),
                ],
                edges: [
                    ...devices.map((_, i) => ({ id: `e-d${i}-a`,  source: `dev-${i}`, target: "agent",   type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } })),
                    ...mcps.map((_, i)    => ({ id: `e-a-m${i}`,  source: "agent",    target: `mcp-${i}`, type: "smoothstep", style: { stroke: "#4cbebb", strokeWidth: 1.5 } })),
                    ...llms.map((_, i)    => ({ id: `e-a-l${i}`,  source: "agent",    target: `llm-${i}`, type: "smoothstep", style: { stroke: "#ec4899", strokeWidth: 1.5 } })),
                ],
            };
        }

        if (asset.type === "Skill") {
            // Devices (left, sources) → Skill (right, target)
            const totalH  = Math.max(devices.length, 1) * NODE_H;
            const skillY  = (totalH - 44) / 2;
            return {
                nodes: [
                    ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: 40,  y: i * NODE_H }, data: { component: { category: "external", type: "User", label: d.username || d.endpoint } } })),
                    { id: "skill", type: "topoNode", draggable: false, position: { x: 380, y: skillY }, data: { component: { category: "skill", type: "Skill", label: asset.name } } },
                ],
                edges: devices.map((_, i) => ({ id: `e-d${i}-s`, source: `dev-${i}`, target: "skill", type: "smoothstep", style: { stroke: "#7E22CE", strokeWidth: 1.5 } })),
            };
        }

        // MCP Server & LLM: Devices (left) → Asset (right)
        const totalH  = Math.max(devices.length, 1) * NODE_H;
        const assetY  = (totalH - 44) / 2;
        const cat     = asset.type === "MCP Server" ? "mcp" : "ai-model";
        const edgeCol = asset.type === "MCP Server" ? "#4cbebb" : "#ec4899";
        return {
            nodes: [
                { id: "asset", type: "topoNode", draggable: false, position: { x: 310, y: assetY }, data: { component: { category: cat, type: asset.type, label: asset.name } } },
                ...devices.map((d, i) => ({ id: `dev-${i}`, type: "topoNode", draggable: false, position: { x: 40, y: i * NODE_H }, data: { component: { category: "external", type: "User", label: d.username || d.endpoint } } })),
            ],
            edges: devices.map((_, i) => ({ id: `e-d${i}-a`, source: `dev-${i}`, target: "asset", type: "smoothstep", style: { stroke: edgeCol, strokeWidth: 1.5 } })),
        };
    }, [asset, assetDevices, agenticTreeData, agenticFlatData]);

    // Fixed-size container — ReactFlow's fitView + zoom handles overflow
    return (
        <div style={{ height: GRAPH_HEIGHT, borderRadius: 8, border: "1px solid #E1E5E9", overflow: "hidden", background: "#F8FAFC" }}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={TOPO_NODE_TYPES}
                fitView
                fitViewOptions={{ padding: 0.2 }}
                onInit={api => api.fitView({ padding: 0.2 })}
                minZoom={0.2}
                maxZoom={2}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable={false}
                zoomOnScroll
                zoomOnPinch
                panOnDrag
                preventScrolling={false}
            >
                <Background color="#E1E5E9" gap={16} />
                <Controls showInteractive={false} />
            </ReactFlow>
        </div>
    );
}

function computeAssetRiskFactors(asset) {
    const factors = [];
    if ((asset.violations?.critical || 0) > 0) {
        factors.push({ severity: "critical", title: `${asset.violations.critical} Critical Violation${asset.violations.critical > 1 ? "s" : ""}`, description: "Active critical policy violations indicate potential data exfiltration or unauthorized system access." });
    }
    if ((asset.violations?.high || 0) > 0) {
        factors.push({ severity: "high", title: `${asset.violations.high} High-Severity Violation${asset.violations.high > 1 ? "s" : ""}`, description: "High-severity violations require investigation and may indicate significant policy breaches." });
    }
    if (asset.type === "MCP Server" && (asset.name.includes("razorpay") || asset.name.includes("quickbooks") || asset.name.includes("sap"))) {
        factors.push({ severity: "critical", title: "Financial System Integration", description: "This MCP server grants AI agents direct read/write access to payment and financial systems." });
    }
    if (asset.type === "MCP Server" && (asset.name.includes("kubernetes") || asset.name.includes("aws"))) {
        factors.push({ severity: "high", title: "Cloud Infrastructure Control", description: "This MCP server allows agents to provision or destroy cloud resources." });
    }
    if (asset.type === "MCP Server" && (asset.name.includes("postgres") || asset.name.includes("databricks"))) {
        factors.push({ severity: "high", title: "Direct Database Access", description: "This MCP server enables agents to run arbitrary queries against production databases." });
    }
    if ((asset.mcpServers || []).length >= 3) {
        factors.push({ severity: "medium", title: `High Integration Complexity (${asset.mcpServers.length} MCP servers)`, description: `${asset.mcpServers.length} external system integrations expand the blast radius of any compromised agent session.` });
    }
    if (factors.length === 0) {
        factors.push({ severity: "low", title: "Standard Risk Profile", description: "No elevated risk factors detected. Score reflects baseline activity levels." });
    }
    return factors;
}

// ─── Cell renderers ───────────────────────────────────────────────────────────
// Exception: AG Grid cell renderers use inline styles (Polaris tokens don't reach into the grid sandbox)

function ToolNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 600, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{data.name}</span>
        </div>
    );
}

function ToolRiskCellRenderer({ value }) {
    if (!value) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    const COLORS = { critical: { bg: "#FEE2E2", text: "#DC2626" }, high: { bg: "#FED3D1", text: "#9A3412" }, medium: { bg: "#FFD79D", text: "#92400E" }, low: { bg: "#E4E5E7", text: "#374151" } };
    const c = COLORS[value] || COLORS.low;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", height: 22, padding: "0 8px", borderRadius: 12, fontSize: 11, fontWeight: 600, lineHeight: "22px", background: c.bg, color: c.text, textTransform: "capitalize", whiteSpace: "nowrap" }}>{value}</span>
        </div>
    );
}

function ToolParamsCellRenderer({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#6D7175" }}>{data.params?.length || 0}</span></div>;
}

function ViolSeverityCellRenderer({ data }) {
    if (!data) return null;
    const s = SEVERITY_COLORS[data.severity] || SEVERITY_COLORS.low;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600, lineHeight: "16px", background: s.bg, color: s.text, textTransform: "capitalize", whiteSpace: "nowrap" }}>{data.severity}</span>
        </div>
    );
}

function ViolTitleCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 12, fontWeight: 600, color: "#202223", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{data.title}</span>
        </div>
    );
}

function OsIcon({ os }) {
    if (os === "mac")     return <img src="/public/os-mac.svg"     width={15} height={15} alt="macOS"   style={{ flexShrink: 0 }} />;
    if (os === "windows") return <img src="/public/os-windows.svg" width={15} height={15} alt="Windows" style={{ flexShrink: 0 }} />;
    return                       <img src="/public/os-linux.svg"   width={15} height={15} alt="Linux"   style={{ flexShrink: 0 }} />;
}

function DeviceNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <OsIcon os={data.os} />
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 500 }}>{data.endpoint}</span>
        </div>
    );
}

function DeviceRiskCellRenderer({ value }) {
    if (value == null) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    const { bg, color } = getRiskColor(value);
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", padding: "2px 8px", borderRadius: 12, fontSize: 11, fontWeight: 600, lineHeight: "16px", background: bg, color }}>{value.toFixed(1)}</span>
        </div>
    );
}

function ConnectedMcpCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 500 }}>{data.name}</span>
            <TypeBadge type="MCP Server" />
        </div>
    );
}

function SkillNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{data.name}</span>
            {data.isNew && (
                <span style={{ flexShrink: 0, fontSize: 11, fontWeight: 500, padding: "2px 8px", borderRadius: 12, background: "#F1F2F3", color: "#6D7175", border: "1px solid #E1E3E5", lineHeight: "16px", display: "inline-flex", alignItems: "center" }}>New</span>
            )}
        </div>
    );
}

// ─── Column definitions ───────────────────────────────────────────────────────

const TOOLS_COL_DEFS = [
    { field: "name",      headerName: "Tool",      flex: 1,   minWidth: 160, cellRenderer: ToolNameCellRenderer,   cellStyle: { display: "flex", alignItems: "center" } },
    { field: "riskLevel", headerName: "Risk",      width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolRiskCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "params",    headerName: "Params",    width: 80,  suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolParamsCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => p.data?.params?.length ?? 0 },
];

const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",      width: 130, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
    { field: "severity", headerName: "Severity",  width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "title",    headerName: "Violation", flex: 1, minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const DEVICES_COL_DEFS = [
    { field: "username",  headerName: "User",      flex: 1,   minWidth: 120, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#202223" }, valueFormatter: p => p.value || "—" },
    { field: "endpoint",  headerName: "Device ID", flex: 1.5, minWidth: 180, cellRenderer: DeviceNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "riskScore", headerName: "Risk",      width: 80, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: DeviceRiskCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "lastSeen",  headerName: "Last Seen", width: 130, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, valueFormatter: p => p.value || "—" },
];

const CONNECTED_MCP_COL_DEFS = [
    { field: "name",     headerName: "MCP Server", flex: 1, minWidth: 160, cellRenderer: ConnectedMcpCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "toolCount", headerName: "Tools",     width: 80, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#202223" } },
];

const SKILLS_COL_DEFS = [
    { field: "name",       headerName: "Skill",      flex: 1,   minWidth: 160, cellRenderer: SkillNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "violations", headerName: "Violations", width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
];

const SCHEMA_COL_DEFS = [
    { field: "name", headerName: "Name",        flex: 1,   minWidth: 140, cellRenderer: ParamNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "type", headerName: "Type",        width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ParamTypeCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "desc", headerName: "Description", flex: 2,   minWidth: 160, cellRenderer: ParamDescCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Overview tab ─────────────────────────────────────────────────────────────

const SEV_ORDER = { critical: 0, high: 1, medium: 2, low: 3 };

function getAssetNarrative(asset, factors) {
    const score = asset.riskScore?.toFixed(1);
    const label = getRiskLabel(asset.riskScore);
    const parts = [];

    if ((asset.violations?.critical || 0) > 0)
        parts.push(`${asset.violations.critical} critical violation${asset.violations.critical > 1 ? "s" : ""} indicate unauthorized data transmission or credential exposure`);
    if ((asset.violations?.high || 0) > 0)
        parts.push(`${asset.violations.high} high-severity violation${asset.violations.high > 1 ? "s" : ""} require immediate investigation`);
    if (asset.type === "MCP Server" && (asset.name.includes("razorpay") || asset.name.includes("quickbooks") || asset.name.includes("sap")))
        parts.push("direct financial system access grants agents write privileges to payment APIs without per-call authorisation");
    if (asset.type === "MCP Server" && (asset.name.includes("kubernetes") || asset.name.includes("aws")))
        parts.push("cloud infrastructure access enables agents to provision or destroy resources autonomously");
    if (asset.type === "MCP Server" && (asset.name.includes("postgres") || asset.name.includes("databricks")))
        parts.push("direct database access enables arbitrary query execution against production data");
    if ((asset.skillCount || 0) > 80)
        parts.push(`${asset.skillCount} exposed skills significantly expand the attack surface`);

    if (parts.length === 0)
        return `${asset.name} shows a standard activity profile with no elevated signals. Score reflects baseline activity patterns.`;

    return `${asset.name} carries a ${label} score of ${score}/5.0 because ${parts.join(", and ")}. ${(asset.violations?.critical || 0) > 0 ? "Immediate action is recommended." : "Monitor closely and review permissions."}`;
}

function OverviewTab({ asset, onTabChange, assetDevices = {}, agenticTreeData = [], agenticFlatData = [] }) {
    const totalV = useMemo(() =>
        (asset.violations?.critical || 0) + (asset.violations?.high || 0) + (asset.violations?.medium || 0) + (asset.violations?.low || 0),
        [asset.violations]
    );

    const rawFactors = useMemo(() => computeAssetRiskFactors(asset), [asset]);
    const factors    = useMemo(() => [...rawFactors].sort((a, b) => (SEV_ORDER[a.severity] ?? 99) - (SEV_ORDER[b.severity] ?? 99)), [rawFactors]);
    const narrative  = useMemo(() => getAssetNarrative(asset, rawFactors), [asset, rawFactors]);

    const stats = useMemo(() => {
        const devices = assetDevices[asset.id] || [];
        const devCount = devices.length;
        const children = asset.type === "AI Agent"
            ? getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData)
            : [];
        const mcpCount = children.filter((c) => c.type === "MCP Server").length;

        if (asset.type === "AI Agent") return [
            { label: devCount  === 1 ? "Device"     : "Devices",     value: devCount },
            { label: mcpCount  === 1 ? "MCP Server" : "MCP Servers", value: mcpCount },
            { label: (asset.skillCount || 0) === 1 ? "Skill" : "Skills", value: asset.skillCount || 0 },
            { label: totalV    === 1 ? "Violation"  : "Violations",  value: totalV },
        ];
        if (asset.type === "MCP Server") return [
            { label: devCount  === 1 ? "Device"     : "Devices",     value: devCount },
            { label: (asset.toolCount || 0) === 1 ? "Tool" : "Tools", value: asset.toolCount || 0 },
            { label: totalV    === 1 ? "Violation"  : "Violations",  value: totalV },
        ];
        if (asset.type === "Skill") return [
            { label: devCount === 1 ? "Device" : "Devices",    value: devCount },
            { label: totalV   === 1 ? "Violation" : "Violations", value: totalV },
        ];
        // LLM
        return [
            { label: devCount  === 1 ? "Device"     : "Devices",     value: devCount },
            { label: totalV    === 1 ? "Violation"  : "Violations",  value: totalV },
        ];
    }, [asset, totalV, assetDevices, agenticTreeData, agenticFlatData]);

    return (
        <Box padding="4">
            <VerticalStack gap="5">
                {/* Stats — same 4-column pattern as DeviceFlyout */}
                <HorizontalGrid columns={stats.length} gap="3">
                    {stats.map(s => (
                        <VerticalStack gap="1" key={s.label}>
                            <Text variant="heading2xl" as="p">{s.value}</Text>
                            <Text variant="bodySm" color="subdued">{s.label}</Text>
                        </VerticalStack>
                    ))}
                </HorizontalGrid>

                {/* Connection topology */}
                <AssetTopologyGraph asset={asset} assetDevices={assetDevices} agenticTreeData={agenticTreeData} agenticFlatData={agenticFlatData} />

                {/* Asset Details */}
                <VerticalStack gap="2">
                    <Text variant="headingXs" color="subdued">Asset Details</Text>
                    <HorizontalStack gap="4" blockAlign="center">
                        <Box minWidth="140px"><Text variant="bodySm" color="subdued">AI Interactions</Text></Box>
                        <Text variant="bodySm" fontWeight="semibold">
                            {asset.aiInteractions != null ? Number(asset.aiInteractions).toLocaleString("en-US") : "—"}
                        </Text>
                    </HorizontalStack>
                    <HorizontalStack gap="4" blockAlign="center">
                        <Box minWidth="140px"><Text variant="bodySm" color="subdued">Last Traffic Seen</Text></Box>
                        <Text variant="bodySm" fontWeight="semibold">{asset.lastSeen || "—"}</Text>
                    </HorizontalStack>
                    <HorizontalStack gap="4" blockAlign="center">
                        <Box minWidth="140px"><Text variant="bodySm" color="subdued">Group</Text></Box>
                        <Text variant="bodySm" fontWeight="semibold">{asset.groups?.[0]?.name || "—"}</Text>
                    </HorizontalStack>
                </VerticalStack>

                {/* Risk narrative + clickable risk factors — mirrors DeviceFlyout's OverviewTab */}
                <VerticalStack gap="3">
                    <Text variant="headingXs" color="subdued">Risk Analysis</Text>
                    <Text variant="bodySm">{narrative}</Text>
                    <VerticalStack gap="0">
                        {factors.map((f, i) => {
                            const badgeStatus = f.severity === "critical" ? "critical" : f.severity === "high" ? "warning" : f.severity === "medium" ? "attention" : "info";
                            const targetTab = f.title.toLowerCase().includes("violation") ? 2 : 1;
                            return (
                                <React.Fragment key={i}>
                                    {i > 0 && <Divider />}
                                    {/* full-width interactive row — no Polaris equivalent for computed hover background */}
                                    <div
                                        onClick={() => onTabChange?.(targetTab)}
                                        style={{ padding: "12px 8px", display: "flex", alignItems: "flex-start", gap: 12, cursor: "pointer", borderRadius: 6, transition: "background 0.15s" }}
                                        onMouseEnter={e => e.currentTarget.style.background = "#F6F6F7"}
                                        onMouseLeave={e => e.currentTarget.style.background = "transparent"}
                                    >
                                        <div style={{ flexShrink: 0, paddingTop: 1, width: 72 }}>
                                            <Badge status={badgeStatus}>{f.severity.charAt(0).toUpperCase() + f.severity.slice(1)}</Badge>
                                        </div>
                                        <div style={{ flex: 1, minWidth: 0 }}>
                                            <VerticalStack gap="0">
                                                <Text variant="bodySm" fontWeight="semibold">{f.title}</Text>
                                                <Text variant="bodySm" color="subdued">{f.description}</Text>
                                            </VerticalStack>
                                        </div>
                                        <div style={{ flexShrink: 0, paddingTop: 2 }}>
                                            <Icon source={ChevronRightMinor} color="subdued" />
                                        </div>
                                    </div>
                                </React.Fragment>
                            );
                        })}
                    </VerticalStack>
                </VerticalStack>
            </VerticalStack>
        </Box>
    );
}

// ─── Agentic Components tab ───────────────────────────────────────────────────

// Extra cell renderers for Resources / Prompts lists
function ResourceUriCellRenderer({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#8C9196", fontFamily: "ui-monospace, 'Cascadia Mono', Consolas, monospace" }}>{data.uri}</span></div>;
}

function PromptDescCellRenderer({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%", overflow: "hidden" }}><span style={{ fontSize: 12, color: "#6D7175", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{data.description}</span></div>;
}

const RESOURCES_COL_DEFS = [
    { field: "name", headerName: "Name", flex: 1, minWidth: 120, cellStyle: { display: "flex", alignItems: "center", fontSize: 13, fontWeight: 600, color: "#202223" } },
    { field: "uri",  headerName: "URI",  flex: 1, minWidth: 160, cellRenderer: ResourceUriCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const PROMPTS_COL_DEFS = [
    { field: "name",        headerName: "Session Title", flex: 1, minWidth: 140, cellStyle: { display: "flex", alignItems: "center", fontSize: 13, fontWeight: 600, color: "#202223" } },
    { field: "description", headerName: "Prompt",        flex: 2, minWidth: 200, cellRenderer: PromptDescCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

// ── Shared detail panels ──────────────────────────────────────────────────────

function SamplePair({ sampleData }) {
    return (
        <div style={{ flex: 1, overflowY: "auto" }}>
            <Box padding="4">
                <VerticalStack gap="4">
                    <LegacyCard>
                        <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                    </LegacyCard>
                    <LegacyCard>
                        <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                    </LegacyCard>
                </VerticalStack>
            </Box>
        </div>
    );
}

// Detail panels — no FlyoutBreadcrumb; top-level nav is managed via onNavChange in parent

function ToolDetailPanel({ tool, parentLabel, onBack, onNavChange, extraCrumbs }) {
    const [tab, setTab] = useState(0);
    const sampleData    = useMemo(() => generateToolSample(tool), [tool.id]);
    const detailTabs    = [{ id: "value", content: "Value" }, { id: "schema", content: "Schema" }];

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <Box paddingInlineStart="1" paddingInlineEnd="1">
                <Tabs tabs={detailTabs} selected={tab} onSelect={setTab} />
            </Box>
            <Divider />
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                {tab === 0 && <SamplePair sampleData={sampleData} />}
                {tab === 1 && (
                    tool.params?.length > 0
                        ? <AgGridTable rowData={tool.params} columnDefs={SCHEMA_COL_DEFS} defaultColDef={GRID_DEFAULT_COL} fillHeight noOuterBorder pagination={false} sideBar={false} />
                        : <Box padding="4"><Text variant="bodySm" color="subdued">No parameters.</Text></Box>
                )}
            </div>
        </div>
    );
}

function SkillDetailPanel({ skill }) {
    return <SamplePair sampleData={generateSkillSample(skill)} />;
}

function ResourcePromptDetailPanel({ item, type }) {
    const sampleData = useMemo(() =>
        type === "resource" ? generateResourceSample(item) : generatePromptSample(item),
        [item, type]
    );
    return <SamplePair sampleData={sampleData} />;
}

// ── Component picker dropdown — shown in breadcrumb when drilling into a detail ─

function McpPickerDropdown({ allRows, selected, onSelect }) {
    const [open, setOpen] = useState(false);
    if (!allRows || allRows.length <= 1) {
        return <Text variant="bodySm" fontWeight="semibold">{selected?.name}</Text>;
    }
    return (
        <Popover
            active={open}
            onClose={() => setOpen(false)}
            preferredAlignment="left"
            activator={
                <Button plain disclosure onClick={() => setOpen(s => !s)}>
                    {selected?.name}
                </Button>
            }
        >
            <Popover.Pane>
                <ActionList
                    items={allRows.map(r => ({
                        content: r.name,
                        active: r.name === selected?.name,
                        onAction: () => { onSelect(r); setOpen(false); },
                    }))}
                />
            </Popover.Pane>
        </Popover>
    );
}

// ── MCP Server: single combined table (Tools + Resources + Prompts) → detail ──

function McpItemTypeCellRenderer({ value }) {
    if (!value) return null;
    const COLORS = {
        "Tool":     { bg: "#EFF6FF", color: "#1D4ED8", border: "#BFDBFE" },
        "Resource": { bg: "#F0FDF4", color: "#166534", border: "#BBF7D0" },
        "Prompt":   { bg: "#FDF4FF", color: "#7C3AED", border: "#E9D5FF" },
    };
    const s = COLORS[value] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", height: 20, padding: "0 7px", borderRadius: 12, fontSize: 11, fontWeight: 500, background: s.bg, color: s.color, border: `1px solid ${s.border}`, whiteSpace: "nowrap" }}>
                {value}
            </span>
        </div>
    );
}

function ViolationCountCellRenderer({ value }) {
    if (!value) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", minWidth: 22, height: 22, padding: "0 6px", borderRadius: 11, fontSize: 11, fontWeight: 700, background: "#DF2909", color: "#FFFBFB" }}>
                {value}
            </span>
        </div>
    );
}

const COMBINED_MCP_COL_DEFS = [
    {
        field: "name",
        headerName: "Name",
        flex: 1.5,
        minWidth: 160,
        filter: "agTextColumnFilter",
        cellStyle: { display: "flex", alignItems: "center", fontSize: 13, fontWeight: 600, color: "#202223" },
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
        headerName: "Violations",
        width: 110,
        filter: false,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: ViolationCountCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: p => {
            if (!p.data) return 0;
            return p.data._type === "Tool" ? (p.data.violationCount || 0) : 0;
        },
    },
];

function McpComponentsView({ asset, onNavChange }) {
    const [selectedItem, setSelectedItem] = useState(null);
    const [allRows, setAllRows] = useState([]);

    useEffect(() => {
        const collectionId = asset?.collectionIds?.[0];
        if (!collectionId) {
            setAllRows([]);
            return;
        }
        let cancelled = false;
        (async () => {
            try {
                const data = await agenticObserveApi.fetchMcpFlyoutData(collectionId);
                if (cancelled) return;
                const toolViolations = data.toolViolations || {};
                setAllRows([
                    ...(data.tools || []).map((t) => ({ ...t, _type: "Tool", violationCount: toolViolations[t.name] || 0 })),
                    ...(data.resources || []).map((r) => ({ ...r, _type: "Resource" })),
                    ...(data.prompts || []).map((p) => ({ ...p, _type: "Prompt" })),
                ]);
            } catch {
                if (!cancelled) setAllRows([]);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.collectionIds]);

    const handleBack = useCallback(() => {
        setSelectedItem(null);
        onNavChange(null);
    }, [onNavChange]);

    // selectItem sets state AND updates the breadcrumb with a picker dropdown
    const selectItem = useCallback((row) => {
        setSelectedItem({ item: row, type: row._type.toLowerCase() });
        onNavChange(
            [{ label: asset.name, onClick: () => { setSelectedItem(null); onNavChange(null); } }],
            <McpPickerDropdown allRows={allRows} selected={row} onSelect={selectItem} />
        );
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [asset.name, onNavChange, allRows]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        selectItem(e.data);
    }, [selectItem]);

    if (selectedItem?.type === "tool") {
        return <ToolDetailPanel tool={selectedItem.item} onBack={handleBack} />;
    }
    if (selectedItem) {
        return <ResourcePromptDetailPanel item={selectedItem.item} type={selectedItem.type} />;
    }

    return allRows.length === 0 ? (
        <Box padding="4"><Text variant="bodySm" color="subdued">No tools, resources or prompts found.</Text></Box>
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
            sideBar={false}
        />
    );
}

// ── AI Agent: single combined table (MCPs + Skills) → detail ─────────────────

function AgentComponentNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 500, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </span>
            {data.isNew && (
                <span style={{ flexShrink: 0, fontSize: 11, fontWeight: 500, padding: "1px 7px", borderRadius: 12, background: "#F1F2F3", color: "#6D7175", border: "1px solid #E1E3E5", lineHeight: "18px", display: "inline-flex", alignItems: "center" }}>New</span>
            )}
        </div>
    );
}

function AgentComponentTypeCellRenderer({ value }) {
    if (!value) return null;
    const s = TYPE_STYLES[value] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", height: 20, padding: "0 7px", borderRadius: 12, fontSize: 11, fontWeight: 500, background: s.bg, color: s.color, border: `1px solid ${s.border}`, whiteSpace: "nowrap" }}>
                {value}
            </span>
        </div>
    );
}

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
        valueGetter: p => {
            if (!p.data) return 0;
            if (p.data._type === "Skill") return p.data.violations || 0;
            return 0;
        },
    },
];

function AgentMcpToolsView({ asset, selectedMcp, agenticFlatData, goToList, onNavChange, setSelectedTool, setView }) {
    const [mcpTools, setMcpTools] = useState([]);

    useEffect(() => {
        const flat = agenticFlatData.find((a) => a.name === selectedMcp.name || a.id === selectedMcp.name);
        const collectionId = flat?.collectionIds?.[0];
        if (!collectionId) {
            setMcpTools([]);
            return;
        }
        let cancelled = false;
        (async () => {
            try {
                const data = await agenticObserveApi.fetchMcpFlyoutData(collectionId);
                if (!cancelled) setMcpTools(data.tools || []);
            } catch {
                if (!cancelled) setMcpTools([]);
            }
        })();
        return () => { cancelled = true; };
    }, [selectedMcp?.name, agenticFlatData]);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
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
        </div>
    );
}

function AgentComponentsView({ asset, onNavChange, onNavigateToAsset, agenticFlatData = [] }) {
    const [view,          setView]          = useState("list");
    const [selectedMcp,   setSelectedMcp]   = useState(null);
    const [selectedTool,  setSelectedTool]  = useState(null);
    const [selectedSkill, setSelectedSkill] = useState(null);
    const [skills, setSkills] = useState([]);

    const connectedMcps = useMemo(() => {
        if (!asset.mcpServers?.length) return [];
        const seen = new Set();
        return asset.mcpServers.filter((mcpName) => {
            const key = String(mcpName).toLowerCase();
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        }).map((mcpName) => ({
            id: mcpName,
            name: mcpName,
            endpoint: mcpName,
            toolCount: 0,
        }));
    }, [asset.mcpServers]);

    useEffect(() => {
        const collectionId = asset?.collectionIds?.[0];
        if (!collectionId) {
            setSkills([]);
            return;
        }
        let cancelled = false;
        (async () => {
            try {
                const data = await agenticObserveApi.fetchSkillsFlyoutData(collectionId);
                if (!cancelled) setSkills(data.skills || []);
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

    // These hooks must be declared before any conditional returns (Rules of Hooks)
    const allComponents = useMemo(() => [
        ...connectedMcps.map(m => ({ ...m, _type: "MCP Server" })),
        ...skills.map(s => ({ ...s, _type: "Skill" })),
    ], [connectedMcps, skills]);

    const handleListRowClick = useCallback((e) => {
        if (!e.data) return;
        if (e.data._type === "MCP Server") {
            // Navigate to that MCP's own flyout
            const mcpAsset = agenticFlatData.find((a) => a.name === e.data.name || a.id === e.data.name)
                || { ...e.data, id: e.data.name, type: "MCP Server" };
            onNavigateToAsset(mcpAsset);
        } else if (e.data._type === "Skill") {
            const skillId = e.data.rawName
                ? `skill-${e.data.rawName}`
                : `skill-${e.data.name.toLowerCase().replace(/ /g, "-")}`;
            const skillAsset = agenticFlatData.find((a) => a.name === e.data.name || a.id === skillId)
                || { id: skillId, name: e.data.name, type: "Skill", riskScore: e.data.riskScore || 0, violations: { critical: 0, high: 0, medium: e.data.violations > 0 ? 1 : 0, low: 0 }, deviceCount: 1, lastSeen: "Recently" };
            onNavigateToAsset?.(skillAsset);
        }
    }, [onNavigateToAsset, agenticFlatData]);

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

// ── LLM config ────────────────────────────────────────────────────────────────

function LlmConfigSection({ asset }) {
    const config = useMemo(() => [
        { label: "Model",          value: asset.name },
        { label: "Provider",       value: asset.name.includes("gpt") || asset.name.includes("GPT") ? "OpenAI" : asset.name.includes("claude") || asset.name.includes("Claude") ? "Anthropic" : asset.name.includes("gemini") ? "Google" : "Local" },
        { label: "Context window", value: asset.name.includes("gpt-4o") || asset.name.includes("GPT-4o") ? "128K tokens" : asset.name.includes("claude") ? "200K tokens" : "8K tokens" },
        { label: "Devices",        value: `${asset.deviceCount} device${asset.deviceCount !== 1 ? "s" : ""}` },
        { label: "Last seen",      value: asset.lastSeen },
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

function AgenticComponentsTab({ asset, onNavChange, onNavigateToAsset, agenticFlatData = [] }) {
    if (asset.type === "MCP Server") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    if (asset.type === "AI Agent")   return <AgentComponentsView asset={asset} onNavChange={onNavChange} onNavigateToAsset={onNavigateToAsset} agenticFlatData={agenticFlatData} />;
    if (asset.type === "LLM")        return <LlmConfigSection asset={asset} />;

    return <SamplePair sampleData={generateSkillSample(asset)} />;
}

// ─── Violations tab ───────────────────────────────────────────────────────────

function ViolationsTab({ asset }) {
    const [violations, setViolations] = useState([]);

    useEffect(() => {
        if (!asset?.id) {
            setViolations([]);
            return;
        }
        let cancelled = false;
        (async () => {
            try {
                const rows = await agenticObserveApi.fetchAgenticViolations({
                    apiCollectionIds: asset.collectionIds,
                    assetId: asset.collectionIds?.length ? undefined : asset.id,
                });
                if (!cancelled) setViolations(rows);
            } catch {
                if (!cancelled) setViolations([]);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id]);

    if (violations.length === 0) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No violations</Text>
                    <Text variant="bodySm" color="subdued">This asset is operating within policy.</Text>
                </VerticalStack>
            </Box>
        );
    }

    return (
        <AgGridTable
            rowData={violations}
            columnDefs={VIOLATIONS_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={() => window.open("/dashboard/guardrails/activity", "_blank")}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search violations..."
            pagination
            paginationPageSize={20}
            sideBar={false}
        />
    );
}

// ─── Devices tab ──────────────────────────────────────────────────────────────

function DevicesTab({ asset, assetDevices = {} }) {
    const devices = useMemo(() => assetDevices[asset.id] || [], [asset.id, assetDevices]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        const deviceId = e.data.deviceId || e.data.endpoint;
        window.open(`/dashboard/observe/endpoints?device=${encodeURIComponent(deviceId)}`, "_blank");
    }, []);

    if (devices.length === 0) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No devices found</Text>
                    <Text variant="bodySm" color="subdued">This asset hasn't been observed on any device.</Text>
                </VerticalStack>
            </Box>
        );
    }

    return (
        <AgGridTable
            rowData={devices}
            columnDefs={DEVICES_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={handleRowClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search devices..."
            pagination
            paginationPageSize={20}
            sideBar={false}
        />
    );
}

// ─── Main AgenticAssetFlyout ──────────────────────────────────────────────────

export default function AgenticAssetFlyout({
    asset,
    show,
    onClose,
    onNavigateToAsset,
    agenticTreeData = [],
    agenticFlatData = [],
    assetDevices = {},
}) {
    const [selectedTab, setSelectedTab] = useState(0);
    // topNav: null = show tabs; array of {label, onClick?} = show drill breadcrumb
    const [topNav, setTopNav]           = useState(null);
    // topNavPicker: optional ReactNode rendered as FlyoutBreadcrumb children (dropdown on last crumb)
    const [topNavPicker, setTopNavPicker] = useState(null);
    const [mcpComponentCount, setMcpComponentCount] = useState(0);
    const [mcpFlyoutData, setMcpFlyoutData] = useState(null);

    const lockScroll   = useCallback(() => { document.body.style.overflow = "hidden"; }, []);
    const unlockScroll = useCallback(() => { document.body.style.overflow = "";       }, []);

    useEffect(() => { if (!show) document.body.style.overflow = ""; }, [show]);
    useEffect(() => { setSelectedTab(0); setTopNav(null); setTopNavPicker(null); }, [asset?.id]);

    useEffect(() => {
        const collectionId = asset?.collectionIds?.[0];
        if (!collectionId || asset?.type !== "MCP Server") {
            setMcpComponentCount(0);
            setMcpFlyoutData(null);
            return;
        }
        let cancelled = false;
        (async () => {
            try {
                const data = await agenticObserveApi.fetchMcpFlyoutData(collectionId);
                if (cancelled) return;
                setMcpComponentCount(
                    (data.tools?.length || 0) + (data.resources?.length || 0) + (data.prompts?.length || 0)
                );
                setMcpFlyoutData(data);
            } catch {
                if (!cancelled) { setMcpComponentCount(0); setMcpFlyoutData(null); }
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.type, asset?.collectionIds]);

    // Minimal identity only — the MCP agent fetches endpoints/components/violations on demand
    // via akto_agentic_asset_details using these collectionIds (see agentic_observe system prompt).
    const chatMetadata = useMemo(() => {
        if (!asset) return null;
        return buildAgenticObserveChatMetadata("asset", {
            assetName: asset.name,
            assetType: asset.type,
            collectionIds: asset.collectionIds || [],
            assetTagValue: asset.assetTagValue,
        });
    }, [asset]);

    const handleTabSelect = useCallback((tab) => {
        setSelectedTab(tab);
        setTopNav(null);
        setTopNavPicker(null);
    }, []);

    // onNavChange(items, picker?) — called by child components when drilling into detail
    const handleNavChange = useCallback((items, picker = null) => {
        setTopNav(items);
        setTopNavPicker(picker || null);
    }, []);

    const tabs = useMemo(() => {
        if (!asset) return [];
        const totalV    = (asset.violations?.critical || 0) + (asset.violations?.high || 0) + (asset.violations?.medium || 0) + (asset.violations?.low || 0);
        const devCount  = (assetDevices[asset.id] || []).length;
        let componentCount = 0;
        if (asset.type === "AI Agent") {
            const children = getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData);
            componentCount = children.length + (asset.skillCount || 0);
        } else if (asset.type === "MCP Server") {
            componentCount = mcpComponentCount;
        }
        return [
            { id: "overview",   content: "Overview" },
            { id: "components", content: componentCount > 0 ? `Components (${componentCount})` : "Components" },
            { id: "violations", content: `Violations (${totalV})` },
            { id: "devices",    content: `Devices (${devCount})` },
        ];
    }, [asset, assetDevices, agenticTreeData, agenticFlatData, mcpComponentCount]);

    if (!asset) return null;

    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{ width: 720 }}>
            {/* onMouseEnter/onMouseLeave not available on Box; flyout positioning requires flex+height CSS not supported by Box */}
            <div
                className="innerFlyLayout"
                onMouseEnter={lockScroll}
                onMouseLeave={unlockScroll}
                style={{
                    width: 720, top: "3.5rem",
                    height: "calc(100vh - 3.5rem)",
                    overflowY: "hidden",
                    display: "flex", flexDirection: "column",
                    background: "white",
                    borderLeft: "1px solid #E1E3E5",
                    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                }}
            >
                {/* Merge drill path into the single header breadcrumb — picker as children for dropdown */}
                <FlyoutBreadcrumb
                    items={topNav
                        ? [{ label: asset.name, badge: asset.riskScore, onClick: topNav[0]?.onClick }, ...topNav.slice(1)]
                        : [{ label: asset.name, badge: asset.riskScore }]
                    }
                    onClose={onClose}
                >
                    {topNavPicker && (
                        <>
                            <Text variant="bodySm" color="subdued">/</Text>
                            {topNavPicker}
                        </>
                    )}
                </FlyoutBreadcrumb>

                {!topNav && (
                    <>
                        <Box paddingInlineStart="1" paddingInlineEnd="1">
                            <Tabs tabs={tabs} selected={selectedTab} onSelect={handleTabSelect} />
                        </Box>
                        <Divider />
                    </>
                )}

                {/* flex:1 + minHeight:0 required for AG Grid tabs to fill remaining space — Box props insufficient */}
                <div style={{ flex: 1, minHeight: 0, overflowY: selectedTab === 0 ? "auto" : "hidden", display: "flex", flexDirection: "column" }}>
                    {selectedTab === 0 && (
                        <OverviewTab
                            asset={asset}
                            onTabChange={handleTabSelect}
                            assetDevices={assetDevices}
                            agenticTreeData={agenticTreeData}
                            agenticFlatData={agenticFlatData}
                        />
                    )}
                    {selectedTab === 1 && (
                        <AgenticComponentsTab
                            asset={asset}
                            onNavChange={handleNavChange}
                            onNavigateToAsset={onNavigateToAsset}
                            agenticFlatData={agenticFlatData}
                        />
                    )}
                    {selectedTab === 2 && <ViolationsTab asset={asset} />}
                    {selectedTab === 3 && <DevicesTab asset={asset} assetDevices={assetDevices} />}
                </div>

                <AiChatSection
                    placeholder="Ask anything about this agentic asset..."
                    resetKey={asset?.id}
                    conversationType="AGENTIC_OBSERVE"
                    chatMetadata={chatMetadata}
                />
            </div>
        </div>
    );
}

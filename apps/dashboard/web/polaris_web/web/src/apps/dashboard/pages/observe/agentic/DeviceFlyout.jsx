import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Tabs, Icon, Avatar, Card, Box, VerticalStack, HorizontalStack, HorizontalGrid, Text, Badge, Divider } from "@shopify/polaris";
import { CustomersMinor, AutomationMajor, MagicMajor, ChevronRightMinor } from "@shopify/polaris-icons";
import ReactFlow, { Handle, Position, Background, Controls } from "react-flow-renderer";
import MCPIcon from "@/assets/MCP_Icon.svg";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AiChatSection from "./AiChatSection";
import { TYPE_STYLES, SEVERITY_COLORS, getRiskColor, getRiskLabel } from "./agenticStyles";
import agenticObserveApi, { buildAgenticObserveChatMetadata } from "./agenticObserveApi";
import { formatDisplayName } from "./mcpClientHelper";
import "../../../components/layouts/style.css";

// ─── Shared helpers ───────────────────────────────────────────────────────────

function TypeBadge({ type }) {
    if (!type) return null;
    const s = TYPE_STYLES[type] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    // TYPE_STYLES colours are outside the Polaris Badge status set — custom span justified
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

// ─── Risk factor computation ───────────────────────────────────────────────────

function computeRiskFactors(device, agents) {
    const factors = [];
    if (device.violations?.critical > 0) {
        factors.push({
            severity: "critical",
            title: `${device.violations.critical} Critical Violation${device.violations.critical > 1 ? "s" : ""}`,
            description: "Active critical policy violations signal potential data exfiltration or unauthorized system access.",
        });
    }
    if (device.violations?.high > 0) {
        factors.push({
            severity: "high",
            title: `${device.violations.high} High-Severity Violation${device.violations.high > 1 ? "s" : ""}`,
            description: "High-severity violations indicate significant policy breaches that require investigation.",
        });
    }
    if (device.hasPersonalAccount) {
        factors.push({
            severity: "high",
            title: "Personal Account Access Detected",
            description: "AI agents are using personal (non-corporate) accounts, creating shadow IT and data exfiltration risks outside DLP policies.",
        });
    }
    const financialMcps = agents.filter(a =>
        a.type === "MCP Server" && (a.endpoint.includes("razorpay") || a.endpoint.includes("quickbooks") || a.endpoint.includes("sap") || a.endpoint.includes("stripe"))
    );
    if (financialMcps.length > 0) {
        factors.push({
            severity: "critical",
            title: "Financial System Integration",
            description: `${financialMcps.map(a => a.endpoint).join(", ")} grants agents direct read/write access to payment and financial systems.`,
        });
    }
    const cloudMcps = agents.filter(a =>
        a.type === "MCP Server" && (a.endpoint.includes("aws") || a.endpoint.includes("k8s") || a.endpoint.includes("kubernetes"))
    );
    if (cloudMcps.length > 0) {
        factors.push({
            severity: "high",
            title: "Cloud Infrastructure Control",
            description: `${cloudMcps.map(a => a.endpoint).join(", ")} allows agents to provision or destroy cloud resources.`,
        });
    }
    const dbMcps = agents.filter(a =>
        a.type === "MCP Server" && (a.endpoint.includes("postgres") || a.endpoint.includes("mysql") || a.endpoint.includes("databricks"))
    );
    if (dbMcps.length > 0) {
        factors.push({
            severity: "high",
            title: "Direct Database Access",
            description: `${dbMcps.map(a => a.endpoint).join(", ")} enables agents to run arbitrary queries against production databases.`,
        });
    }
    const mcpCount = agents.filter(a => a.type === "MCP Server").length;
    if (mcpCount >= 3) {
        factors.push({
            severity: "medium",
            title: `High Integration Complexity (${mcpCount} MCP servers)`,
            description: `${mcpCount} external system integrations expand the blast radius of any compromised agent session.`,
        });
    }
    if (factors.length === 0) {
        factors.push({
            severity: "low",
            title: "Standard Risk Profile",
            description: "No elevated risk factors detected. Score reflects baseline activity levels.",
        });
    }
    return factors;
}

// ─── Cell renderers ───────────────────────────────────────────────────────────
// Exception: AG Grid cell renderers use inline styles (Polaris tokens don't reach into the grid sandbox)

function AgentNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 500 }}>{data.endpoint}</span>
            <TypeBadge type={data.type} />
        </div>
    );
}

function AgentRiskCellRenderer({ value }) {
    if (value == null) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    const { bg, color } = getRiskColor(value);
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", padding: "2px 8px", borderRadius: 12, fontSize: 11, fontWeight: 600, lineHeight: "16px", background: bg, color }}>
                {value.toFixed(1)}
            </span>
        </div>
    );
}

function AgentViolationsCellRenderer({ value }) {
    if (!value) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    const parts = ["critical", "high", "medium", "low"]
        .map(k => ({ k, c: value[k], ...SEVERITY_COLORS[k] }))
        .filter(p => p.c > 0);
    if (!parts.length) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 3 }}>
            {parts.map(p => (
                <span key={p.k} style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", minWidth: 20, height: 20, padding: "0 5px", borderRadius: 10, fontSize: 10, fontWeight: 700, background: p.bg, color: p.text }}>{p.c}</span>
            ))}
        </div>
    );
}

function AgentSkillsCellRenderer({ data }) {
    if (!data) return null;
    return data.skillCount
        ? <span style={{ fontSize: 12, color: "#202223", fontWeight: 500 }}>{data.skillCount}</span>
        : <span style={{ color: "#C4C7CB" }}>—</span>;
}

function ViolSeverityCellRenderer({ data }) {
    if (!data) return null;
    const s = SEVERITY_COLORS[data.severity] || SEVERITY_COLORS.low;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{
                display: "inline-flex", alignItems: "center",
                padding: "2px 8px", borderRadius: 20,
                fontSize: 11, fontWeight: 600, lineHeight: "16px",
                background: s.bg, color: s.text,
                textTransform: "capitalize", flexShrink: 0, whiteSpace: "nowrap",
            }}>
                {data.severity}
            </span>
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

function ViolAgentCellRenderer({ data }) {
    if (!data) return null;
    const displayAgent = data.agent ? formatDisplayName(data.agent) : null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 5, overflow: "hidden" }}>
            <TypeBadge type={data.agentType} />
            {displayAgent && <span style={{ fontSize: 12, color: "#6D7175", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{displayAgent}</span>}
        </div>
    );
}

// ─── Column definitions ───────────────────────────────────────────────────────

function buildAgentsColDefs(agentRiskData) {
    return [
    { field: "endpoint", headerName: "Agentic Asset", flex: 1, minWidth: 160, cellRenderer: AgentNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    {
        field: "riskScore", headerName: "Risk", width: 80,
        sort: "desc",
        suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
        cellRenderer: AgentRiskCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: (p) => {
            if (!p.data) return null;
            return agentRiskData[p.data.path?.join("/")]?.riskScore ?? null;
        },
    },
    {
        field: "violations", headerName: "Violations", width: 130,
        suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
        cellRenderer: AgentViolationsCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: (p) => {
            if (!p.data) return null;
            return agentRiskData[p.data.path?.join("/")]?.violations ?? null;
        },
    },
    { field: "skillCount", headerName: "Skills", width: 80, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: AgentSkillsCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];
}

const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",               width: 120, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
    { field: "severity", headerName: "Severity",           width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "title",    headerName: "Violation",          flex: 1, minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "agent",    headerName: "Agentic Component",  width: 200, cellRenderer: ViolAgentCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Risk narrative ───────────────────────────────────────────────────────────

function getRiskNarrative(device, agents, factors) {
    const critV = device.violations?.critical || 0;
    const highV = device.violations?.high || 0;
    const totalSkills = agents.reduce((s, a) => s + (a.skillCount || 0), 0);
    const mcpNames = agents.filter(a => a.type === "MCP Server").map(a => a.endpoint);
    const parts = [];

    if (critV > 0)
        parts.push(`${critV} active critical violation${critV > 1 ? "s" : ""} indicate unauthorized data transmission or credential exposure`);
    if (highV > 0)
        parts.push(`${highV} high-severity violation${highV > 1 ? "s" : ""} signal significant policy breaches requiring immediate review`);
    if (device.hasPersonalAccount)
        parts.push("AI agents are operating under personal (non-corporate) accounts outside DLP policy coverage");
    if (mcpNames.some(n => n.includes("razorpay") || n.includes("quickbooks") || n.includes("sap") || n.includes("stripe")))
        parts.push("financial system integrations grant agents direct write access to payment APIs without per-call authorization");
    if (mcpNames.some(n => n.includes("aws") || n.includes("k8s") || n.includes("kubernetes")))
        parts.push("cloud infrastructure MCPs allow agents to provision or destroy resources autonomously");
    if (mcpNames.some(n => n.includes("postgres") || n.includes("databricks") || n.includes("mysql")))
        parts.push("direct database access MCPs enable arbitrary query execution against production data");
    if (totalSkills > 100)
        parts.push(`${totalSkills.toLocaleString()} exposed skills significantly expand the attack surface`);

    const displayName = device.username && device.username !== "-" ? device.username : device.endpoint;

    if (parts.length === 0)
        return `${displayName} shows a standard activity profile with no elevated signals. Score reflects baseline AI agent usage patterns.`;

    const score = device.riskScore?.toFixed(1);
    const label = getRiskLabel(device.riskScore);
    return `${displayName} carries a ${label} score of ${score}/5.0 because ${parts.join(", and ")}. ${critV > 0 || device.hasPersonalAccount ? "Immediate action is recommended." : "Monitor closely and review agent permissions."}`;
}

// ─── Topology graph ───────────────────────────────────────────────────────────

function topoColors(category) {
    switch (category) {
        case "external": return { borderColor: "#3b82f6", backgroundColor: "#eff6ff" };
        case "agent":    return { borderColor: "#f97316", backgroundColor: "#fff7ed" };
        case "mcp":      return { borderColor: "#4cbebb", backgroundColor: "#ecfdf5" };
        case "ai-model": return { borderColor: "#ec4899", backgroundColor: "#fdf2f8" };
        default:         return { borderColor: "#6b7280", backgroundColor: "#f9fafb" };
    }
}

function topoIcon(category) {
    switch (category) {
        case "external": return CustomersMinor;
        case "agent":    return AutomationMajor;
        case "mcp":      return MCPIcon;
        case "ai-model": return MagicMajor;
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
                {/* Dynamic border/background colours from data — Box doesn't support computed border-color */}
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

function TopologyGraph({ device, agents }) {
    const { nodes, edges } = useMemo(() => {
        const aiAgents   = agents.filter(a => a.type === "AI Agent");
        const mcpServers = agents.filter(a => a.type === "MCP Server");
        const llms       = agents.filter(a => a.type === "LLM");
        const hasAgents  = aiAgents.length > 0;

        const NODE_H = 82;
        const COL1_X = 60, COL2_X = 310, COL3_X = 560;

        const col2Count = hasAgents ? aiAgents.length : 0;
        const col3Count = mcpServers.length + llms.length;
        const maxRows   = Math.max(col2Count, col3Count, 1);
        const totalH    = maxRows * NODE_H;
        const devY      = (totalH - 70) / 2;

        const deviceLabel = device.username && device.username !== "-" ? device.username : device.endpoint;
        const ns = [{
            id: "device", type: "topoNode", draggable: false,
            position: { x: COL1_X, y: devY },
            data: { component: { category: "external", type: "User", label: deviceLabel } },
        }];

        if (hasAgents) {
            const offset = Math.max(0, (col3Count - col2Count) * NODE_H / 2);
            aiAgents.forEach((a, i) => ns.push({
                id: `agent-${i}`, type: "topoNode", draggable: false,
                position: { x: COL2_X, y: offset + i * NODE_H },
                data: { component: { category: "agent", type: "AI Agent", label: a.endpoint } },
            }));
        }

        const resX = hasAgents ? COL3_X : COL2_X;
        mcpServers.forEach((a, i) => ns.push({
            id: `mcp-${i}`, type: "topoNode", draggable: false,
            position: { x: resX, y: i * NODE_H },
            data: { component: { category: "mcp", type: "MCP Server", label: a.endpoint } },
        }));
        llms.forEach((a, i) => ns.push({
            id: `llm-${i}`, type: "topoNode", draggable: false,
            position: { x: resX, y: (mcpServers.length + i) * NODE_H },
            data: { component: { category: "ai-model", type: "LLM", label: a.endpoint } },
        }));

        const es = [];
        if (hasAgents) {
            aiAgents.forEach((_, ai) => {
                es.push({ id: `e-d-a${ai}`, source: "device", target: `agent-${ai}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } });
            });
            const pivot = Math.floor(aiAgents.length / 2);
            mcpServers.forEach((_, mi) => es.push({ id: `e-pivot-m${mi}`, source: `agent-${pivot}`, target: `mcp-${mi}`, sourceHandle: "b", type: "smoothstep", style: { stroke: "#4cbebb", strokeWidth: 1.5 } }));
            llms.forEach((_, li) => es.push({ id: `e-pivot-l${li}`, source: `agent-${pivot}`, target: `llm-${li}`, sourceHandle: "b", type: "smoothstep", style: { stroke: "#ec4899", strokeWidth: 1.5 } }));
        } else {
            mcpServers.forEach((_, i) => es.push({ id: `e-d-m${i}`, source: "device", target: `mcp-${i}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } }));
            llms.forEach((_, i) => es.push({ id: `e-d-l${i}`, source: "device", target: `llm-${i}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } }));
        }

        return { nodes: ns, edges: es };
    }, [agents, device.endpoint]);

    // Fixed-size container — ReactFlow's fitView + zoom handles overflow
    return (
        <div style={{ height: 280, borderRadius: 8, border: "1px solid #e1e5e9", overflow: "hidden", background: "#f8fafc" }}>
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
                <Background color="#e1e5e9" gap={16} />
                <Controls showInteractive={false} />
            </ReactFlow>
        </div>
    );
}

// ─── Overview tab ─────────────────────────────────────────────────────────────

const SEV_ORDER = { critical: 0, high: 1, medium: 2, low: 3 };

function OverviewTab({ device, agents, onTabChange }) {
    const { aiCount, mcpCount, llmCount, totalV } = useMemo(() => ({
        aiCount:  agents.filter(a => a.type === "AI Agent").length,
        mcpCount: agents.filter(a => a.type === "MCP Server").length,
        llmCount: agents.filter(a => a.type === "LLM").length,
        totalV:   (device.violations?.critical || 0) + (device.violations?.high || 0) + (device.violations?.medium || 0) + (device.violations?.low || 0),
    }), [agents, device.violations]);

    const osLabel = useMemo(() => {
        if (device.os === "mac") return "macOS";
        if (device.os === "windows") return "Windows";
        if (device.os === "linux") return "Linux";
        return "Unknown OS";
    }, [device.os]);

    const rawFactors = useMemo(() => computeRiskFactors(device, agents), [device, agents]);
    const factors    = useMemo(() => [...rawFactors].sort((a, b) => (SEV_ORDER[a.severity] ?? 99) - (SEV_ORDER[b.severity] ?? 99)), [rawFactors]);
    const narrative  = useMemo(() => getRiskNarrative(device, agents, rawFactors), [device, agents, rawFactors]);

    const stats = useMemo(() => [
        { label: aiCount  === 1 ? "AI Agent"   : "AI Agents",   value: aiCount  },
        { label: mcpCount === 1 ? "MCP Server" : "MCP Servers", value: mcpCount },
        { label: llmCount === 1 ? "LLM"        : "LLMs",        value: llmCount },
        { label: totalV   === 1 ? "Violation"  : "Violations",  value: totalV   },
    ], [aiCount, mcpCount, llmCount, totalV]);

    const safeVal = (v) => (v && v !== "-" ? v : null);
    const deviceDetails = useMemo(() => [
        { label: "User",      value: safeVal(device.username) },
        { label: "OS",        value: osLabel },
        { label: "Group",     value: safeVal(device.group) },
        { label: "Role",      value: safeVal(device.role) },
        { label: "Last Seen", value: safeVal(device.lastTraffic) },
        device.hasPersonalAccount
            ? { label: "Account", value: "Personal account", isWarning: true }
            : { label: "Account", value: "Corporate" },
    ], [device, osLabel]);

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

                <TopologyGraph device={device} agents={agents} />

                <VerticalStack gap="3">
                    <Text variant="headingXs" color="subdued">Risk Analysis</Text>
                    <Text variant="bodySm">{narrative}</Text>
                    <VerticalStack gap="0">
                        {factors.map((f, i) => {
                            const badgeStatus = f.severity === "critical" ? "critical" : f.severity === "high" ? "warning" : f.severity === "medium" ? "attention" : "info";
                            const targetTab   = (f.severity === "critical" || f.severity === "high") && f.title.toLowerCase().includes("violation") ? 2 : f.title.toLowerCase().includes("integration") || f.title.toLowerCase().includes("database") || f.title.toLowerCase().includes("cloud") ? 1 : 2;
                            return (
                                <React.Fragment key={i}>
                                    {i > 0 && <Divider />}
                                    {/* clickable row — no Polaris equivalent for a full-width interactive area with hover state */}
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

                <VerticalStack gap="3">
                    <Text variant="headingXs" color="subdued">Device Details</Text>
                    <HorizontalGrid columns={3} gap="3">
                        {deviceDetails.map(d => (
                            <VerticalStack gap="1" key={d.label}>
                                <Text variant="bodySm" color="subdued">{d.label}</Text>
                                <Text variant="bodySm" fontWeight="semibold" color={d.isWarning ? "warning" : "base"}>
                                    {d.value || "-"}
                                </Text>
                            </VerticalStack>
                        ))}
                    </HorizontalGrid>
                </VerticalStack>
            </VerticalStack>
        </Box>
    );
}

// ─── Agentic Assets tab ───────────────────────────────────────────────────────

function isAgentNavigable(data) {
    if (!data) return false;
    return !!data.type; // all typed assets are navigable
}

function AgenticsTab({ agents, onAgentClick, agentRiskData = {} }) {
    const agentsColDefs = useMemo(() => buildAgentsColDefs(agentRiskData), [agentRiskData]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        if (!isAgentNavigable(e.data)) return;
        const assetId = e.data.rawServiceName || e.data.endpoint;
        const params = new URLSearchParams({ asset: assetId, type: e.data.type });
        window.open(`/dashboard/observe/agentic-assets?${params}`, "_blank");
    }, []);

    return (
        <AgGridTable
            rowData={agents}
            columnDefs={agentsColDefs}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={handleRowClick}
            getRowStyle={({ data }) => isAgentNavigable(data) ? { cursor: "pointer" } : { cursor: "default" }}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search assets..."
            pagination={false}
            sideBar={false}
        />
    );
}

// ─── Violations tab ───────────────────────────────────────────────────────────

function ViolationsTab({ device }) {
    const [violations, setViolations] = useState([]);

    useEffect(() => {
        if (!device?.path?.[0]) {
            setViolations([]);
            return;
        }
        let cancelled = false;
        (async () => {
            try {
                const rows = await agenticObserveApi.fetchAgenticViolations({ deviceId: device.path[0] });
                if (!cancelled) setViolations(rows);
            } catch {
                if (!cancelled) setViolations([]);
            }
        })();
        return () => { cancelled = true; };
    }, [device?.path?.[0]]);

    const handleViolationClick = useCallback((e) => {
        if (!e.data) return;
        window.open("/dashboard/guardrails/policies", "_blank");
    }, []);

    if (violations.length === 0) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No violations</Text>
                    <Text variant="bodySm" color="subdued">This device is operating within policy.</Text>
                </VerticalStack>
            </Box>
        );
    }

    return (
        <AgGridTable
            rowData={violations}
            columnDefs={VIOLATIONS_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={handleViolationClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search violations..."
            pagination={false}
            sideBar={false}
        />
    );
}

// ─── Main DeviceFlyout ────────────────────────────────────────────────────────

export default function DeviceFlyout({ device, agents, show, onClose, onAgentClick, agentRiskData = {} }) {
    const [selectedTab, setSelectedTab] = useState(0);

    // Minimal identity only — the MCP agent resolves this device's collections and fetches
    // its endpoints/components/violations on demand via akto_agentic_asset_details (deviceId).
    const chatMetadata = useMemo(() => buildAgenticObserveChatMetadata("device", {
        deviceEndpoint: device?.endpoint,
        deviceId: device?.path?.[0],
    }), [device]);

    const lockScroll   = useCallback(() => { document.body.style.overflow = "hidden"; }, []);
    const unlockScroll = useCallback(() => { document.body.style.overflow = "";       }, []);

    useEffect(() => { if (!show) document.body.style.overflow = ""; }, [show]);

    const tabs = useMemo(() => {
        if (!device) return [];
        const totalV = (device.violations?.critical || 0) + (device.violations?.high || 0) + (device.violations?.medium || 0) + (device.violations?.low || 0);
        return [
            { id: "overview",   content: "Overview" },
            { id: "assets",     content: `Agentic Assets (${(agents || []).length})` },
            { id: "violations", content: `Violations (${totalV})` },
        ];
    }, [device, agents]);

    if (!device) return null;

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
                <FlyoutBreadcrumb
                    items={[{ label: device.username && device.username !== "-" ? device.username : device.endpoint, badge: device.riskScore }]}
                    onClose={onClose}
                />

                <Box paddingInlineStart="1" paddingInlineEnd="1">
                    <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
                </Box>
                <Divider />

                {/* flex:1 + minHeight:0 required for AG Grid tabs to fill remaining space — Box props insufficient */}
                <div style={{ flex: 1, minHeight: 0, overflowY: selectedTab === 0 ? "auto" : "hidden", display: "flex", flexDirection: "column" }}>
                    {selectedTab === 0 && <OverviewTab device={device} agents={agents || []} onTabChange={setSelectedTab} />}
                    {selectedTab === 1 && <AgenticsTab agents={agents || []} onAgentClick={onAgentClick} agentRiskData={agentRiskData} />}
                    {selectedTab === 2 && <ViolationsTab device={device} agents={agents} />}
                </div>

                <AiChatSection
                    placeholder="Ask anything about this device..."
                    resetKey={device?.endpoint}
                    conversationType="AGENTIC_OBSERVE"
                    chatMetadata={chatMetadata}
                />
            </div>
        </div>
    );
}

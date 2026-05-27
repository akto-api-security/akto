import React, { useState, useMemo, useCallback, useRef, useEffect } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { Tabs, Icon, Avatar, Card, Box, VerticalStack, HorizontalStack, Text } from "@shopify/polaris";
import { CustomersMinor, AutomationMajor, MagicMajor } from "@shopify/polaris-icons";
import ReactFlow, { Handle, Position, Background } from "react-flow-renderer";
import MCPIcon from "@/assets/MCP_Icon.svg";
import AiChatSection from "./AiChatSection";
import { AGENT_RISK_DATA, VIOLATION_TEMPLATES, REL_TIMES, generateViolations } from "./agenticDummyData";
import "../../../components/layouts/style.css";

// ─── Theme ────────────────────────────────────────────────────────────────────

const gridTheme = themeQuartz.withParams({
    accentColor: "#9642FC",
    borderColor: "#E1E3E5",
    borderRadius: 4,
    browserColorScheme: "light",
    cellTextColor: "#202223",
    columnBorder: false,
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    fontSize: 13,
    foregroundColor: "#202223",
    headerFontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    headerRowBorder: true,
    headerTextColor: "#6D7175",
    rowBorder: true,
    spacing: 8,
    wrapperBorder: false,
    headerFontSize: 12,
    headerFontWeight: 500,
    checkboxBorderRadius: 4,
});

// ─── Shared helpers ───────────────────────────────────────────────────────────

const SEV = {
    critical: { dot: "#DC2626", bg: "#FEE2E2", color: "#DC2626", badgeBg: "#DF2909", badgeText: "#FFFBFB" },
    high:     { dot: "#F97316", bg: "#FFEDD5", color: "#EA580C", badgeBg: "#FED3D1", badgeText: "#202223" },
    medium:   { dot: "#EAB308", bg: "#FEF9C3", color: "#CA8A04", badgeBg: "#FFD79D", badgeText: "#202223" },
    low:      { dot: "#9CA3AF", bg: "#F1F2F3", color: "#6D7175", badgeBg: "#E4E5E7", badgeText: "#202223" },
};

function getRiskColors(score) {
    if (score >= 4.5) return { bg: "#FEE2E2", color: "#DC2626" };
    if (score >= 4.0) return { bg: "#FFEDD5", color: "#EA580C" };
    if (score >= 3.5) return { bg: "#FEF9C3", color: "#CA8A04" };
    return { bg: "#F0FDF4", color: "#16A34A" };
}
function getRiskLabel(score) {
    if (score >= 4.5) return "Critical Risk";
    if (score >= 4.0) return "High Risk";
    if (score >= 3.5) return "Medium Risk";
    return "Low Risk";
}

function RiskBadge({ score }) {
    if (score == null) return null;
    const { bg, color } = getRiskColors(score);
    return (
        <span style={{
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            padding: "2px 8px", borderRadius: 12,
            fontSize: 12, fontWeight: 600,
            background: bg, color, flexShrink: 0,
        }}>{score.toFixed(1)}</span>
    );
}

const TYPE_STYLES = {
    "AI Agent":   { bg: "#EFF6FF", color: "#1D4ED8", border: "#BFDBFE" },
    "MCP Server": { bg: "#FFFBEB", color: "#92400E", border: "#FDE68A" },
    "LLM":        { bg: "#F0FDF4", color: "#166534", border: "#BBF7D0" },
};

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
    const totalSkills = agents.reduce((s, a) => s + (a.skillCount || 0), 0);
    if (totalSkills > 100) {
        factors.push({
            severity: "medium",
            title: `Broad Skill Surface (${totalSkills.toLocaleString()} skills)`,
            description: `${totalSkills.toLocaleString()} skills exposed across agents. Each skill is a potential attack vector.`,
        });
    } else if (totalSkills > 20) {
        factors.push({
            severity: "low",
            title: `Elevated Skill Count (${totalSkills} skills)`,
            description: `${totalSkills} skills accessible. Monitor for anomalous invocation patterns.`,
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

// ─── Cell renderers for AG Grid ───────────────────────────────────────────────

function AgentNameCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 500 }}>{data.endpoint}</span>
            <TypeBadge type={data.type} />
        </div>
    );
}

function AgentRiskCell({ value }) {
    if (value == null) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    const { bg, color } = getRiskColors(value);
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", padding: "2px 8px", borderRadius: 12, fontSize: 11, fontWeight: 600, lineHeight: "16px", background: bg, color }}>
                {value.toFixed(1)}
            </span>
        </div>
    );
}

function AgentViolationsCell({ value }) {
    if (!value) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    const parts = [
        { k: "critical", c: value.critical, bg: "#DF2909", txt: "#FFFBFB" },
        { k: "high",     c: value.high,     bg: "#FED3D1", txt: "#202223" },
        { k: "medium",   c: value.medium,   bg: "#FFD79D", txt: "#202223" },
        { k: "low",      c: value.low,      bg: "#E4E5E7", txt: "#202223" },
    ].filter(p => p.c > 0);
    if (!parts.length) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 3 }}>
            {parts.map(p => (
                <span key={p.k} style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", minWidth: 20, height: 20, padding: "0 5px", borderRadius: 10, fontSize: 10, fontWeight: 700, background: p.bg, color: p.txt }}>{p.c}</span>
            ))}
        </div>
    );
}

function AgentSkillsCell({ data }) {
    if (!data) return null;
    return data.skillCount
        ? <span style={{ fontSize: 12, color: "#202223", fontWeight: 500 }}>{data.skillCount}</span>
        : <span style={{ color: "#C4C7CB" }}>—</span>;
}

function ViolSeverityCell({ data }) {
    if (!data) return null;
    const s = SEV[data.severity] || SEV.low;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{
                display: "inline-flex", alignItems: "center",
                padding: "2px 8px", borderRadius: 20,
                fontSize: 11, fontWeight: 600, lineHeight: "16px",
                background: s.badgeBg, color: s.badgeText,
                textTransform: "capitalize", flexShrink: 0,
                whiteSpace: "nowrap",
            }}>
                {data.severity}
            </span>
        </div>
    );
}

function ViolTitleCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 12, fontWeight: 600, color: "#202223", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{data.title}</span>
        </div>
    );
}

function ViolAgentCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 5, overflow: "hidden" }}>
            <TypeBadge type={data.agentType} />
            <span style={{ fontSize: 12, color: "#6D7175", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{data.agent}</span>
        </div>
    );
}

const AGENTS_COL_DEFS = [
    { field: "endpoint", headerName: "Agentic Asset", flex: 1, minWidth: 160, cellRenderer: AgentNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    {
        field: "riskScore", headerName: "Risk", width: 80,
        sort: "desc",
        suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
        cellRenderer: AgentRiskCell,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: (p) => {
            if (!p.data) return null;
            return AGENT_RISK_DATA[p.data.path?.join("/")]?.riskScore ?? null;
        },
    },
    {
        field: "violations", headerName: "Violations", width: 130,
        suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
        cellRenderer: AgentViolationsCell,
        cellStyle: { display: "flex", alignItems: "center" },
        valueGetter: (p) => {
            if (!p.data) return null;
            return AGENT_RISK_DATA[p.data.path?.join("/")]?.violations ?? null;
        },
    },
    { field: "skillCount", headerName: "Skills", width: 80, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: AgentSkillsCell, cellStyle: { display: "flex", alignItems: "center" } },
];

const VIOLATIONS_COL_DEFS = [
    { field: "severity", headerName: "Severity", width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "title",    headerName: "Violation", flex: 1, minWidth: 200, cellRenderer: ViolTitleCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "agent",    headerName: "Agent",     width: 200, cellRenderer: ViolAgentCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "time",     headerName: "Time",      width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center" } },
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

    if (parts.length === 0)
        return `${device.endpoint} shows a standard activity profile with no elevated signals. Score reflects baseline AI agent usage patterns.`;

    const score = device.riskScore?.toFixed(1);
    const label = getRiskLabel(device.riskScore);
    return `${device.endpoint} carries a ${label} score of ${score}/5.0 because ${parts.join(", and ")}. ${critV > 0 || device.hasPersonalAccount ? "Immediate action is recommended." : "Monitor closely and review agent permissions."}`;
}

// ─── Topology graph ───────────────────────────────────────────────────────────
// Reuses exact same AgentNode pattern as AgentDiscoveryGraphWithDummyData.jsx

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
    const { nodes, edges, graphHeight } = useMemo(() => {
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

        const ns = [{
            id: "device", type: "topoNode", draggable: false,
            position: { x: COL1_X, y: devY },
            data: { component: { category: "external", type: "Device", label: device.endpoint } },
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
            // Device → each AI Agent
            aiAgents.forEach((_, ai) => {
                es.push({ id: `e-d-a${ai}`, source: "device", target: `agent-${ai}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } });
            });
            // Only the center AI Agent connects to resources (avoids dense all-to-all crossing edges)
            const pivot = Math.floor(aiAgents.length / 2);
            mcpServers.forEach((_, mi) => es.push({ id: `e-pivot-m${mi}`, source: `agent-${pivot}`, target: `mcp-${mi}`, sourceHandle: "b", type: "smoothstep", style: { stroke: "#4cbebb", strokeWidth: 1.5 } }));
            llms.forEach((_, li) => es.push({ id: `e-pivot-l${li}`, source: `agent-${pivot}`, target: `llm-${li}`, sourceHandle: "b", type: "smoothstep", style: { stroke: "#ec4899", strokeWidth: 1.5 } }));
        } else {
            mcpServers.forEach((_, i) => es.push({ id: `e-d-m${i}`, source: "device", target: `mcp-${i}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } }));
            llms.forEach((_, i) => es.push({ id: `e-d-l${i}`, source: "device", target: `llm-${i}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } }));
        }

        return { nodes: ns, edges: es, graphHeight: Math.max(totalH + 40, 220) };
    }, [agents, device.endpoint]);

    return (
        <div style={{ height: graphHeight, borderRadius: 8, border: "1px solid #e1e5e9", overflow: "hidden", background: "#f8fafc" }}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={TOPO_NODE_TYPES}
                fitView
                fitViewOptions={{ padding: 0.2 }}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable={false}
                zoomOnScroll={false}
                zoomOnPinch={false}
                panOnDrag={false}
                panOnScroll={false}
                preventScrolling={false}
            >
                <Background color="#e1e5e9" gap={16} />
            </ReactFlow>
        </div>
    );
}

// ─── Overview tab ─────────────────────────────────────────────────────────────

function OverviewTab({ device, agents }) {
    const aiCount  = agents.filter(a => a.type === "AI Agent").length;
    const mcpCount = agents.filter(a => a.type === "MCP Server").length;
    const llmCount = agents.filter(a => a.type === "LLM").length;
    const totalV   = (device.violations?.critical || 0) + (device.violations?.high || 0) + (device.violations?.medium || 0) + (device.violations?.low || 0);
    const osLabel  = device.os === "mac" ? "macOS" : device.os === "windows" ? "Windows" : device.os === "linux" ? "Linux" : "Unknown OS";

    const factors   = computeRiskFactors(device, agents);
    const narrative = getRiskNarrative(device, agents, factors);

    const deviceDetails = [
        { label: "User",      value: device.username },
        { label: "OS",        value: osLabel },
        { label: "Group",     value: device.group },
        { label: "Role",      value: device.role },
        { label: "Last Seen", value: device.lastTraffic },
        device.hasPersonalAccount
            ? { label: "Account", value: "Personal account", isWarning: true }
            : { label: "Account", value: "Corporate" },
    ];

    return (
        <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 20 }}>

            {/* 1. Numbers */}
            <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10 }}>
                {[
                    { label: aiCount  === 1 ? "AI Agent"   : "AI Agents",   value: aiCount  },
                    { label: mcpCount === 1 ? "MCP Server" : "MCP Servers", value: mcpCount },
                    { label: llmCount === 1 ? "LLM"        : "LLMs",        value: llmCount },
                    { label: totalV   === 1 ? "Violation"  : "Violations",  value: totalV   },
                ].map(s => (
                    <div key={s.label}>
                        <Text variant="heading2xl" as="p">{s.value}</Text>
                        <div style={{ marginTop: 2 }}>
                            <Text variant="bodySm" color="subdued">{s.label}</Text>
                        </div>
                    </div>
                ))}
            </div>

            {/* 2. Topology graph */}
            <TopologyGraph device={device} agents={agents} />

            {/* 3. Why this risk score */}
            <div>
                <div style={{ marginBottom: 8 }}>
                    <Text variant="headingXs" color="subdued">Risk Analysis</Text>
                </div>
                <div style={{ marginBottom: 12 }}>
                    <Text variant="bodySm" color="base">{narrative}</Text>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    {factors.map((f, i) => {
                        const s = SEV[f.severity] || SEV.low;
                        return (
                            <div key={i} style={{
                                display: "flex", gap: 12,
                                padding: "10px 14px",
                                borderRadius: 6,
                                border: "1px solid #E1E3E5",
                                borderLeft: `3px solid ${s.dot}`,
                                background: "#FAFBFB",
                            }}>
                                <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                                    <Text variant="bodySm" fontWeight="semibold">{f.title}</Text>
                                    <Text variant="bodySm" color="subdued">{f.description}</Text>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* 4. Device details — minimal 3-col grid */}
            <div>
                <div style={{ marginBottom: 10 }}>
                    <Text variant="headingXs" color="subdued">Device Details</Text>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "10px 24px" }}>
                    {deviceDetails.map(d => (
                        <div key={d.label}>
                            <Text variant="bodySm" color="subdued">{d.label}</Text>
                            <div style={{ marginTop: 2 }}>
                                <Text variant="bodySm" fontWeight="semibold" color={d.isWarning ? "warning" : "base"}>
                                    {d.value || "—"}
                                </Text>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

// ─── Agentic Assets tab ───────────────────────────────────────────────────────

function isAgentNavigable(data) {
    if (!data) return false;
    return data.type === "MCP Server" || (data.type === "AI Agent" && data.skillCount > 0);
}

function AgenticsTab({ agents, onAgentClick }) {
    const gridRef = useRef(null);
    const enriched = useMemo(() =>
        agents.map(a => ({ ...a, _riskData: AGENT_RISK_DATA[a.path?.join("/")] })),
        [agents]
    );

    const handleRowClick = useCallback((e) => {
        if (!e.data || !onAgentClick) return;
        if (isAgentNavigable(e.data)) onAgentClick(e.data);
    }, [onAgentClick]);

    return (
        <div style={{ position: "relative", flex: 1, minHeight: 0, height: "100%" }}>
            <AgGridReact
                ref={gridRef}
                theme={gridTheme}
                rowData={enriched}
                columnDefs={AGENTS_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                rowHeight={44}
                headerHeight={40}
                suppressCellFocus
                animateRows
                onRowClicked={handleRowClick}
                getRowStyle={({ data }) => isAgentNavigable(data) ? { cursor: "pointer" } : { cursor: "default" }}
            />
        </div>
    );
}

// ─── Violations tab ───────────────────────────────────────────────────────────

function ViolationsTab({ device, agents }) {
    const violations = useMemo(() => generateViolations(device, agents), [device, agents]);
    const gridRef = useRef(null);

    if (violations.length === 0) {
        return (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, paddingTop: 48 }}>
                <span style={{ fontSize: 32 }}>✓</span>
                <span style={{ fontSize: 14, fontWeight: 600, color: "#16A34A" }}>No violations</span>
                <span style={{ fontSize: 12, color: "#6D7175" }}>This device is operating within policy.</span>
            </div>
        );
    }

    return (
        <div style={{ position: "relative", flex: 1, minHeight: 0, height: "100%" }}>
            <AgGridReact
                ref={gridRef}
                theme={gridTheme}
                rowData={violations}
                columnDefs={VIOLATIONS_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                rowHeight={44}
                headerHeight={40}
                suppressCellFocus
                animateRows
            />
        </div>
    );
}

// ─── Main DeviceFlyout ────────────────────────────────────────────────────────

export default function DeviceFlyout({ device, agents, show, onClose, onAgentClick }) {
    const [selectedTab, setSelectedTab] = useState(0);

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

    const { bg: riskBg, color: riskColor } = getRiskColors(device.riskScore);

    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{ width: 720 }}>
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
                {/* Breadcrumb header */}
                <div style={{
                    display: "flex", alignItems: "center", justifyContent: "space-between",
                    padding: "12px 16px", borderBottom: "1px solid #E1E3E5", flexShrink: 0,
                }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13 }}>
                        <span style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{device.endpoint}</span>
                        <span style={{ display: "inline-flex", alignItems: "center", padding: "2px 8px", borderRadius: 10, fontSize: 12, fontWeight: 600, background: riskBg, color: riskColor }}>{device.riskScore?.toFixed(1)}</span>
                    </div>
                    <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#6D7175", fontSize: 18, lineHeight: 1, padding: "2px 4px" }}>×</button>
                </div>

                {/* Tabs */}
                <div style={{ borderBottom: "1px solid #E1E3E5", padding: "0 4px", flexShrink: 0 }}>
                    <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
                </div>

                {/* Content */}
                <div style={{ flex: 1, minHeight: 0, overflowY: selectedTab === 0 ? "auto" : "hidden", display: "flex", flexDirection: "column" }}>
                    {selectedTab === 0 && <OverviewTab device={device} agents={agents || []} />}
                    {selectedTab === 1 && <AgenticsTab agents={agents || []} onAgentClick={onAgentClick} />}
                    {selectedTab === 2 && <ViolationsTab device={device} agents={agents || []} />}
                </div>

                {/* Ask Akto — expands to half-screen as user types */}
                <AiChatSection
                    placeholder="Ask anything about this device..."
                    resetKey={device?.endpoint}
                />
            </div>
        </div>
    );
}

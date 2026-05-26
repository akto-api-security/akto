import React, { useState, useMemo, useCallback, useRef } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { Tabs, Icon, Avatar, Card, Box, VerticalStack, HorizontalStack, Text } from "@shopify/polaris";
import { CustomersMinor, AutomationMajor, MagicMajor } from "@shopify/polaris-icons";
import ReactFlow, { Handle, Position, Background } from "react-flow-renderer";
import MCPIcon from "@/assets/MCP_Icon.svg";
import AgenticSearchInput from "../../agentic/components/AgenticSearchInput";
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

// ─── Per-agent risk lookup (exported for DeviceEndpoints valueGetters) ────────

export const AGENT_RISK_DATA = {
    "NYC-JDOE-MAC01/cursor-cli":          { riskScore: 1.2, violations: null },
    "NYC-JDOE-MAC01/cursor-code":         { riskScore: 3.8, violations: { critical:0, high:1, medium:0, low:0 } },
    "NYC-JDOE-MAC01/mcp-akto":            { riskScore: 4.1, violations: { critical:1, high:0, medium:0, low:0 } },
    "NYC-JDOE-MAC01/chatgpt":             { riskScore: 2.5, violations: null },
    "NYC-JDOE-MAC01/razorpay-stdio":      { riskScore: 4.5, violations: { critical:1, high:1, medium:0, low:0 } },
    "NYC-JDOE-MAC01/gemini":              { riskScore: 1.8, violations: null },
    "NYC-JDOE-WIN11/copilot365-jdoe":     { riskScore: 2.1, violations: null },
    "NYC-JDOE-WIN11/mcp-github-jdoe":     { riskScore: 3.2, violations: null },
    "NYC-JDOE-WIN11/gpt4-jdoe":           { riskScore: 1.9, violations: null },
    "BER-TSMITH-MAC02/vscode":            { riskScore: 2.3, violations: { critical:0, high:1, medium:0, low:0 } },
    "BER-TSMITH-MAC02/github-copilot":    { riskScore: 1.9, violations: null },
    "BER-TSMITH-MAC02/mcp-github":        { riskScore: 3.2, violations: { critical:1, high:0, medium:1, low:0 } },
    "SF-MWILSON-WIN10/copilot365":        { riskScore: 2.1, violations: null },
    "SF-MWILSON-WIN10/teams-bot":         { riskScore: 1.8, violations: null },
    "SF-MWILSON-WIN10/mcp-sharepoint":    { riskScore: 3.5, violations: { critical:2, high:2, medium:0, low:0 } },
    "SF-MWILSON-WIN10/gpt4":              { riskScore: 2.0, violations: null },
    "SF-MWILSON-MAC01/claude-mwilson":    { riskScore: 1.5, violations: null },
    "SF-MWILSON-MAC01/mcp-notion-mw":     { riskScore: 2.1, violations: null },
    "BER-DWILSON-MAC02/claude-desktop":   { riskScore: 2.8, violations: null },
    "BER-DWILSON-MAC02/mcp-slack":        { riskScore: 2.4, violations: null },
    "BER-DWILSON-MAC02/mcp-jira":         { riskScore: 2.0, violations: { critical:0, high:1, medium:0, low:0 } },
    "BER-DWILSON-MAC02/claude-3":         { riskScore: 1.6, violations: null },
    "SF-STAYLOR-MAC01/cursor-ai":         { riskScore: 3.1, violations: { critical:0, high:0, medium:0, low:3 } },
    "SF-STAYLOR-MAC01/mcp-k8s":           { riskScore: 4.2, violations: { critical:0, high:0, medium:0, low:2 } },
    "SF-STAYLOR-MAC01/mcp-aws":           { riskScore: 4.3, violations: null },
    "SF-STAYLOR-MAC01/gpt4-mini":         { riskScore: 1.7, violations: null },
    "SF-LTHOMAS-MAC02/playwright-ai":     { riskScore: 2.8, violations: { critical:1, high:2, medium:1, low:0 } },
    "SF-LTHOMAS-MAC02/gemini-pro":        { riskScore: 1.6, violations: null },
    "SF-RCLARK-MAC01/cursor-ai2":         { riskScore: 2.5, violations: { critical:0, high:1, medium:0, low:0 } },
    "SF-RCLARK-MAC01/mcp-xcode":          { riskScore: 2.9, violations: null },
    "SF-RCLARK-MAC01/claude-haiku":       { riskScore: 1.4, violations: null },
    "SF-JLEWIS-WIN10/copilot-data":       { riskScore: 2.4, violations: null },
    "SF-JLEWIS-WIN10/mcp-databricks":     { riskScore: 4.0, violations: { critical:4, high:1, medium:0, low:0 } },
    "SF-JLEWIS-WIN10/gpt4-data":          { riskScore: 2.1, violations: null },
    "SF-JLEWIS-LIN01/jupyter-jlewis":     { riskScore: 2.2, violations: null },
    "SF-JLEWIS-LIN01/ollama-jlewis":      { riskScore: 1.3, violations: null },
    "SF-JLEWIS-LIN01/mcp-pg-jlewis":      { riskScore: 3.4, violations: { critical:0, high:1, medium:0, low:0 } },
    "SF-JLEWIS-MAC01/cursor-jlewis":      { riskScore: 1.9, violations: null },
    "SF-JLEWIS-MAC01/claude-jlewis":      { riskScore: 1.4, violations: null },
    "SF-WHALL-WIN10/copilot365-pm":       { riskScore: 2.0, violations: null },
    "SF-WHALL-WIN10/mcp-notion":          { riskScore: 2.2, violations: { critical:0, high:0, medium:1, low:0 } },
    "SF-PYOUNG-WIN10/copilot365-fin":     { riskScore: 2.3, violations: null },
    "SF-PYOUNG-WIN10/mcp-sap":            { riskScore: 3.9, violations: { critical:0, high:0, medium:1, low:1 } },
    "SF-CKING-MAC01/claude-fin":          { riskScore: 2.8, violations: null },
    "SF-CKING-MAC01/mcp-quickbooks":      { riskScore: 4.1, violations: { critical:0, high:0, medium:1, low:1 } },
    "NYC-JANDERSON-MAC01/cursor-vp":      { riskScore: 2.5, violations: null },
    "NYC-JANDERSON-MAC01/mcp-linear":     { riskScore: 2.1, violations: { critical:0, high:0, medium:1, low:0 } },
    "NYC-JANDERSON-MAC01/gpt4-vp":        { riskScore: 1.9, violations: null },
    "LON-RJOHNSON-WIN11/copilot-win":     { riskScore: 2.2, violations: null },
    "LON-RJOHNSON-WIN11/mcp-crm":         { riskScore: 3.0, violations: { critical:0, high:1, medium:2, low:4 } },
    "TKY-AMATSUDA-LIN01/jupyter-ai":      { riskScore: 1.8, violations: null },
    "TKY-AMATSUDA-LIN01/ollama":          { riskScore: 1.2, violations: null },
    "TKY-AMATSUDA-LIN01/mcp-postgres":    { riskScore: 2.8, violations: { critical:0, high:0, medium:1, low:1 } },
};

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
            title: `Broad Tool Surface (${totalSkills.toLocaleString()} tools)`,
            description: `${totalSkills.toLocaleString()} tools exposed across agents. Each tool is a potential attack vector.`,
        });
    } else if (totalSkills > 20) {
        factors.push({
            severity: "low",
            title: `Elevated Skill Count (${totalSkills} tools)`,
            description: `${totalSkills} tools accessible. Monitor for anomalous invocation patterns.`,
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

// ─── Dummy violations generator ───────────────────────────────────────────────

const VIOLATION_TEMPLATES = {
    critical: [
        { title: "Unauthorized API Key Exposure", description: "Agent transmitted a plaintext API key in request payload to an external service granting write access to production infrastructure." },
        { title: "PII Sent to External LLM", description: "Customer PII (email, phone) was included in a prompt sent to a third-party LLM without redaction or consent." },
        { title: "Financial Transaction Without Authorization", description: "Agent invoked payment API to initiate a transaction without explicit user confirmation." },
        { title: "Credential Exfiltration Attempt", description: "Agent attempted to read system credential store and forward contents to an external endpoint." },
    ],
    high: [
        { title: "Excessive Database Query Scope", description: "Agent executed a full-table scan on `users` (1.2M rows) without WHERE clause, violating least privilege." },
        { title: "Shadow IT: Unauthorized SaaS Auth", description: "Personal account credentials used to authenticate an agent session. Corporate data may have been uploaded externally." },
        { title: "Prompt Injection Detected", description: "A prompt injection payload was detected in tool call arguments. Agent may have been manipulated by adversarial content." },
        { title: "Overprivileged Tool Invocation", description: "Agent invoked `admin_delete_user` outside its stated purpose. No business justification was logged." },
    ],
    medium: [
        { title: "Unusual Invocation Pattern", description: "Agent invoked the same tool 47 times within 2 minutes, consistent with automated enumeration or data scraping." },
        { title: "Missing Rate Limit Compliance", description: "Agent continued making API calls after receiving HTTP 429 responses, bypassing rate limiting." },
    ],
    low: [
        { title: "Deprecated Tool Version Used", description: "Agent is calling a deprecated tool version. Upgrade for improved security controls." },
        { title: "Verbose Logging of Sensitive Fields", description: "Debug logs include response payloads containing authentication tokens." },
    ],
};

const REL_TIMES = ["2m ago","17m ago","1h ago","3h ago","6h ago","12h ago","1d ago","2d ago"];

function generateViolations(device, agents) {
    const rows = [];
    let t = 0;
    const add = (sev, idx) => {
        const tpls = VIOLATION_TEMPLATES[sev] || VIOLATION_TEMPLATES.low;
        const tpl = tpls[idx % tpls.length];
        const ag = agents[(idx * 2) % Math.max(agents.length, 1)];
        rows.push({ id: rows.length, severity: sev, title: tpl.title, description: tpl.description, agent: ag?.endpoint || "Unknown", agentType: ag?.type || "AI Agent", time: REL_TIMES[t++ % REL_TIMES.length] });
    };
    for (let i = 0; i < (device.violations?.critical || 0); i++) add("critical", i);
    for (let i = 0; i < (device.violations?.high    || 0); i++) add("high",     i);
    for (let i = 0; i < (device.violations?.medium  || 0); i++) add("medium",   i);
    for (let i = 0; i < (device.violations?.low     || 0); i++) add("low",      i);
    return rows;
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
        parts.push(`${totalSkills.toLocaleString()} exposed tool endpoints significantly expand the attack surface`);

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

    const osLabel = device.os === "mac" ? "macOS" : device.os === "windows" ? "Windows" : device.os === "linux" ? "Linux" : "Unknown OS";

    const factors   = computeRiskFactors(device, agents);
    const narrative = getRiskNarrative(device, agents, factors);

    return (
        <div style={{ padding: 16, display: "flex", flexDirection: "column", gap: 16 }}>
            {/* Topology graph */}
            <TopologyGraph device={device} agents={agents} />

            {/* Risk narrative */}
            <p style={{ margin: 0, fontSize: 13, color: "#202223", lineHeight: 1.6 }}>{narrative}</p>

            {/* Quick stats */}
            <div style={{ display: "flex", borderBottom: "1px solid #E1E3E5", paddingBottom: 16 }}>
                {[
                    { label: "AI Agents",   value: aiCount,  isViolation: false },
                    { label: "MCP Servers", value: mcpCount, isViolation: false },
                    { label: "LLMs",        value: llmCount, isViolation: false },
                    { label: "Violations",  value: totalV,   isViolation: true  },
                ].map((s, i) => (
                    <div key={s.label} style={{ flex: 1, paddingLeft: i > 0 ? 16 : 0, borderLeft: i > 0 ? "1px solid #E1E3E5" : "none", marginLeft: i > 0 ? 16 : 0 }}>
                        <div style={{ fontSize: 22, fontWeight: 700, color: s.isViolation && s.value > 0 ? "#DC2626" : "#202223", lineHeight: 1 }}>{s.value}</div>
                        <div style={{ fontSize: 12, color: "#6D7175", marginTop: 4 }}>{s.label}</div>
                    </div>
                ))}
            </div>

            {/* Device context */}
            <div>
                <div style={{ fontSize: 11, fontWeight: 600, color: "#8C9196", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 12 }}>Device Context</div>
                <div style={{ display: "grid", gridTemplateColumns: "90px 1fr", gap: "10px 0", alignItems: "start" }}>
                    {[
                        ["User",     device.username],
                        ["OS",       osLabel],
                        ["Group",    device.group],
                        ["Role",     device.role],
                        ["Last Seen",device.lastTraffic],
                    ].map(([k, v]) => (
                        <React.Fragment key={k}>
                            <span style={{ fontSize: 12, color: "#8C9196", fontWeight: 500, paddingTop: 2 }}>{k}</span>
                            <span style={{ fontSize: 12, color: "#202223", fontWeight: 500 }}>{v || "—"}</span>
                        </React.Fragment>
                    ))}
                    {device.hasPersonalAccount && (
                        <>
                            <span style={{ fontSize: 12, color: "#8C9196", fontWeight: 500, paddingTop: 2 }}>Account</span>
                            <span style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 12, color: "#B45309", fontWeight: 500, background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 6, padding: "2px 8px", width: "fit-content" }}>
                                Personal account detected
                            </span>
                        </>
                    )}
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

                {/* Ask Akto footer */}
                <div style={{ borderTop: "1px solid #E1E3E5", padding: "12px 16px", flexShrink: 0, background: "white" }}>
                    <AgenticSearchInput
                        placeholder="Ask anything about this device..."
                        isFixed={false}
                        inputWidth="100%"
                        containerStyle={{ display: "block" }}
                    />
                </div>
            </div>
        </div>
    );
}

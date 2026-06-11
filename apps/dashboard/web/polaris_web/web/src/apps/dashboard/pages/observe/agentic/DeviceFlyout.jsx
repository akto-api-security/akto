import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Tabs, Box, VerticalStack, HorizontalStack, HorizontalGrid, Text, Divider, Badge } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AgenticFlyoutShell from "./AgenticFlyoutShell";
import AiChatSection from "./AiChatSection";
import { TypeBadge, SeverityBadge, RiskPill } from "./AgenticCellRenderers";

import AssetTopologyGraph from "./AssetTopologyGraph";
import { RiskFactorRow } from "./RiskFactorRow";
import DetailGrid from "./DetailGrid";
import { buildAgenticObserveChatMetadata, fetchAgenticViolations, openViolationInThreatActivity, deviceServiceKey } from "./agenticObserveApi";
import func from "@/util/func";
import "../../../components/layouts/style.css";

// ─── Risk factor computation ───────────────────────────────────────────────────

function computeRiskFactors(device, agents) {
    const factors = [];
    const sevLabels = { critical: "Critical", high: "High", medium: "Medium", low: "Low" };

    for (const [sev, label] of Object.entries(sevLabels)) {
        const count = device.violations?.[sev] || 0;
        if (count > 0) {
            factors.push({
                severity: sev,
                title: `${count} ${label} Violation${count > 1 ? "s" : ""}`,
                description: `Contains ${label.toLowerCase()} violations`,
                type: "violation",
            });
        }
    }

    if (device.hasPersonalAccount) {
        factors.push({
            severity: "high",
            title: "Personal Account",
            description: "Contains personal account",
            type: "personal_account",
        });
    }

    const maliciousSkills = agents.filter(a => a.isMalicious);
    if (maliciousSkills.length > 0) {
        const names = maliciousSkills.map(a => a.endpoint).join(", ");
        factors.push({
            severity: "critical",
            title: `${maliciousSkills.length} Malicious Skill${maliciousSkills.length > 1 ? "s" : ""}`,
            description: `Contains malicious skill: ${names}`,
            type: "malicious_skill",
        });
    }

    return factors;
}

// ─── Cell renderers ───────────────────────────────────────────────────────────
// Exception: AG Grid cell renderers use inline styles (Polaris tokens don't reach into the grid sandbox)

function AgentNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <Text variant="bodySm" fontWeight="medium">{data.endpoint}</Text>
            <TypeBadge type={data.type} />
        </HorizontalStack>
    );
}

function AgentRiskCellRenderer({ value }) {
    if (value == null) return <Text variant="bodySm" color="subdued">-</Text>;
    return <RiskPill score={value} />;
}

function AgentViolationsCellRenderer({ value }) {
    const dash = <Text variant="bodySm" color="subdued">-</Text>;
    if (!value) return dash;
    const parts = ["critical", "high", "medium", "low"].filter(k => value[k] > 0);
    if (!parts.length) return dash;
    return (
        <HorizontalStack gap="1" blockAlign="center">
            {parts.map(k => <SeverityBadge key={k} severity={k}>{value[k]}</SeverityBadge>)}
        </HorizontalStack>
    );
}

function AgentSkillsCellRenderer({ data }) {
    if (!data) return null;
    return data.skillCount
        ? <Text variant="bodySm" fontWeight="medium">{data.skillCount}</Text>
        : <Text variant="bodySm" color="subdued">-</Text>;
}

function ViolSeverityCellRenderer({ data }) {
    if (!data) return null;
    return <SeverityBadge severity={data.severity} />;
}

function ViolTitleCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Box width="100%" overflowX="hidden">
            <Text variant="bodySm" fontWeight="semibold" truncate>{data.title}</Text>
        </Box>
    );
}

function ViolStatusCellRenderer({ data }) {
    if (!data?.status) return null;
    const s = data.status.toUpperCase();
    const statusMap = { ACTIVE: "success", UNDER_REVIEW: "warning", IGNORED: "subdued", TRAINING: "info" };
    return <Badge status={statusMap[s] || "default"} size="small">{func.toSentenceCase(s)}</Badge>;
}

// ─── Column definitions ───────────────────────────────────────────────────────

function buildAgentsColDefs(agentRiskData) {
    return [
    { field: "endpoint", headerName: "Agentic Asset", flex: 1, minWidth: 160, cellRenderer: AgentNameCellRenderer, cellClass: (p) => ({ "AI Agent": "agentic-type-AGENT", "MCP Server": "agentic-type-MCP", "LLM": "agentic-type-LLM", "Skill": "agentic-type-SKILL" })[p.data?.type] || "", cellStyle: { display: "flex", alignItems: "center" } },
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
        comparator: (a, b) => {
            const sum = (v) => v ? (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0) : 0;
            return sum(a) - sum(b);
        },
    },
    { field: "skillCount", headerName: "Skills", width: 80, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: AgentSkillsCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];
}

const SEVERITY_ORDER = { low: 1, medium: 2, high: 3, critical: 4 };

const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",      width: 120, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.timeEpoch || 0) - (nodeB?.data?.timeEpoch || 0) },
    { field: "title",    headerName: "Violation", flex: 1, minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "severity", headerName: "Severity",  width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, comparator: (a, b) => (SEVERITY_ORDER[a] || 0) - (SEVERITY_ORDER[b] || 0) },
    { field: "actor",    headerName: "Actor",     width: 130, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, valueFormatter: p => p.value || "-", cellStyle: { display: "flex", alignItems: "center", fontSize: 12 } },
    { field: "status",   headerName: "Status",    width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolStatusCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Topology graph ───────────────────────────────────────────────────────────

function TopologyGraph({ device, agents }) {
    const { nodes, edges } = useMemo(() => {
        const aiAgents   = agents.filter(a => a.type === "AI Agent");
        const mcpServers = agents.filter(a => a.type === "MCP Server");
        const llms       = agents.filter(a => a.type === "LLM");
        const hasAgents  = aiAgents.length > 0;

        const NODE_H = 100;
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

    return <AssetTopologyGraph nodes={nodes} edges={edges} />;
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

                <VerticalStack gap="2">
                    <Text variant="headingXs" color="subdued">Risk Analysis</Text>
                    <VerticalStack gap="0">
                        {factors.map((f, i) => {
                            let handleClick;
                            if (f.type === "violation") {
                                handleClick = () => onTabChange?.(2);
                            } else if (f.type === "personal_account") {
                                // Open endpoints page with this specific device's flyout pre-opened
                                const deviceId = device.path?.[0] || device.deviceId;
                                handleClick = deviceId
                                    ? () => window.open(`/dashboard/observe/endpoints?device=${encodeURIComponent(deviceId)}`, "_blank")
                                    : undefined;
                            } else if (f.type === "malicious_skill") {
                                const maliciousAgents = agents.filter(a => a.isMalicious);
                                const firstSkill = maliciousAgents[0];
                                handleClick = firstSkill
                                    ? () => window.open(`/dashboard/observe/agentic-assets?asset=${encodeURIComponent(firstSkill.rawServiceName || firstSkill.endpoint)}`, "_blank")
                                    : () => window.open("/dashboard/observe/agentic-assets", "_blank");
                            } else {
                                handleClick = undefined;
                            }
                            return (
                                <React.Fragment key={i}>
                                    {i > 0 && <Divider />}
                                    <RiskFactorRow factor={f} onClick={handleClick} />
                                </React.Fragment>
                            );
                        })}
                    </VerticalStack>
                </VerticalStack>

                <DetailGrid heading="Device Details" items={deviceDetails} columns={3} />
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
            pagination
            paginationPageSize={20}
            paginationPageSizeSelector={[20, 50, 100]}
            sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            domLayout="normal" />
    );
}

// ─── Violations tab ───────────────────────────────────────────────────────────

function ViolationsTab({ hostNames = [] }) {
    const [violations, setViolations] = useState([]);

    useEffect(() => {
        if (!hostNames.length) { setViolations([]); return; }
        let cancelled = false;
        const hostSet = new Set(hostNames);
        const looseHostSet = new Set(hostNames.map(h => deviceServiceKey(h)).filter(Boolean));
        fetchAgenticViolations({})
            .then((rows) => {
                if (cancelled) return;
                const filtered = rows.filter(r => hostSet.has(r.host) || looseHostSet.has(deviceServiceKey(r.host)));
                setViolations(filtered.map((r) => ({
                    ...r,
                    time: r.timeEpoch ? func.formatChatTimestamp(r.timeEpoch) : "",
                })));
            })
            .catch(() => {
                if (!cancelled) setViolations([]);
            });
        return () => { cancelled = true; };
    }, [hostNames.join(",")]);

    const handleViolationClick = useCallback((e) => {
        if (!e.data) return;
        openViolationInThreatActivity(e.data);
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
            pagination
            paginationPageSize={20}
            paginationPageSizeSelector={[20, 50, 100]}
            sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            domLayout="normal" />
    );
}

// ─── Main DeviceFlyout ────────────────────────────────────────────────────────

export default function DeviceFlyout({ device, agents, show, onClose, onAgentClick, agentRiskData = {}, deviceHostNames = [] }) {
    const [selectedTab, setSelectedTab] = useState(0);

    // Minimal identity only — the MCP agent resolves this device's collections and fetches
    // its endpoints/components/violations on demand via akto_agentic_asset_details (deviceId).
    const chatMetadata = useMemo(() => buildAgenticObserveChatMetadata("device", {
        deviceEndpoint: device?.endpoint,
        deviceId: device?.path?.[0],
    }), [device]);

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
        <AgenticFlyoutShell
            show={show}
            width={800}
            header={
                <>
                    <FlyoutBreadcrumb
                        items={[{ label: device.username && device.username !== "-" ? device.username : device.endpoint, badge: device.riskScore }]}
                        onClose={onClose}
                    />
                    <Box paddingInlineStart="1" paddingInlineEnd="1">
                        <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
                    </Box>
                    <Divider />
                </>
            }
            footer={
                <AiChatSection
                    placeholder="Ask anything about this device..."
                    resetKey={device?.endpoint}
                    conversationType="AGENTIC_OBSERVE"
                    chatMetadata={chatMetadata}
                />
            }
        >
            <Box padding="2" style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "flex", flexDirection: "column" }}>
                {selectedTab === 0 && <OverviewTab device={device} agents={agents || []} onTabChange={setSelectedTab} />}
                {selectedTab === 1 && <AgenticsTab agents={agents || []} onAgentClick={onAgentClick} agentRiskData={agentRiskData} />}
                {selectedTab === 2 && <ViolationsTab hostNames={deviceHostNames} />}
            </Box>
        </AgenticFlyoutShell>
    );
}

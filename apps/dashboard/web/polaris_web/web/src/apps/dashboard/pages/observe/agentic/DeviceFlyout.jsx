import React, { useState, useMemo, useCallback, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Tabs, Box, VerticalStack, HorizontalStack, HorizontalGrid, Text, Divider, Link, Tooltip, Badge } from "@shopify/polaris";
import { buildTopicGuardrailPrefill, GUARDRAIL_POLICIES_PATH } from "../../guardrails/topicGuardrailUtils";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AgenticFlyoutShell from "./AgenticFlyoutShell";
import AiChatSection from "./AiChatSection";
import { TypeBadge, SeverityBadge, RiskPill } from "./AgenticCellRenderers";

import AssetTopologyGraph from "./AssetTopologyGraph";
import { RiskFactorRow } from "./RiskFactorRow";
import DetailGrid from "./DetailGrid";
import { buildAgenticObserveChatMetadata, fetchAgenticViolations, openViolationInThreatActivity, deviceServiceKey, isClaudeConfigHost } from "./agenticObserveApi";
import { extractServiceName, extractEndpointId } from "./constants";
import func from "@/util/func";
import settingsApi from "../../settings/api";
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

    const misconfiguredSkills = agents.filter(a => a.isMisconfigured);
    if (misconfiguredSkills.length > 0) {
        const names = misconfiguredSkills.map(a => a.endpoint).join(", ");
        factors.push({
            severity: "high",
            title: `${misconfiguredSkills.length} Misconfigured Skill${misconfiguredSkills.length > 1 ? "s" : ""}`,
            description: `Contains misconfigured skill: ${names}`,
            type: "misconfigured_config",
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
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Topology graph ───────────────────────────────────────────────────────────

// Build col3 items (MCPs, LLMs, Skills) linked to a given AI Agent using collections data.
// The agent's collectionIds tell us which collections it owns; service names on those
// collections (excluding the agent's own service segment) are its linked MCP/LLM servers.
function buildAgentCol3Items(agent, collections, agentIdx) {
    const agentIdSet = new Set((agent.collectionIds || []).map(Number));
    const deviceId = agent.path?.[0];
    const agentServiceKey = agent.rawServiceName?.toLowerCase();
    const seen = new Set();
    const items = [];

    collections.forEach((c) => {
        if (!agentIdSet.has(Number(c.id))) return;
        const hostName = c.hostName || c.displayName || c.name;
        if (!hostName) return;
        if (extractEndpointId(hostName) !== deviceId) return;
        const svc = extractServiceName(hostName);
        if (!svc || svc.toLowerCase() === agentServiceKey) return;
        if (seen.has(svc)) return;
        seen.add(svc);
        const tags = c.envType || [];
        const isLlm = tags.some(t => t.keyName === "gen-ai" || t.keyName === "llm");
        const cat = isLlm ? "ai-model" : "mcp";
        const type = isLlm ? "LLM" : "MCP Server";
        const edgeColor = isLlm ? "#ec4899" : "#4cbebb";
        items.push({ id: `c3-${agentIdx}-${seen.size}`, cat, type, label: svc, agentIdx, edgeColor });
    });

    // Also add skills from the agent's skillNames
    (agent.skillNames || []).forEach((name, si) => {
        items.push({ id: `skl-${agentIdx}-${si}`, cat: "skill", type: "Skill", label: name, agentIdx, edgeColor: "#7C3AED" });
    });

    return items;
}

function TopologyGraph({ device, agents, collections = [] }) {
    const { nodes, edges } = useMemo(() => {
        const aiAgents = agents.filter(a => a.type === "AI Agent");
        const hasAgents = aiAgents.length > 0;

        const NODE_H = 84;
        const COL1_X = 40, COL2_X = 230, COL3_X = 420;

        const deviceLabel = device.username && device.username !== "-" ? device.username : device.endpoint;
        const ns = [];
        const es = [];

        if (hasAgents) {
            // Build col3 items for all agents, tagged with agentIdx
            const col3Items = aiAgents.flatMap((a, ai) => buildAgentCol3Items(a, collections, ai));
            const maxRows = Math.max(aiAgents.length, col3Items.length, 1);
            const totalH  = maxRows * NODE_H;
            const devY    = (totalH - 44) / 2;
            const agentOffset = Math.max(0, (col3Items.length - aiAgents.length) * NODE_H / 2);

            ns.push({ id: "device", type: "topoNode", draggable: false, position: { x: COL1_X, y: devY }, data: { component: { category: "external", type: "User", label: deviceLabel } } });
            aiAgents.forEach((a, i) => {
                ns.push({ id: `agent-${i}`, type: "topoNode", draggable: false, position: { x: COL2_X, y: agentOffset + i * NODE_H }, data: { component: { category: "agent", type: "AI Agent", label: a.endpoint } } });
                es.push({ id: `e-d-a${i}`, source: "device", target: `agent-${i}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } });
            });
            col3Items.forEach((item, i) => {
                ns.push({ id: item.id, type: "topoNode", draggable: false, position: { x: COL3_X, y: i * NODE_H }, data: { component: { category: item.cat, type: item.type, label: item.label } } });
                es.push({ id: `e-a${item.agentIdx}-${item.id}`, source: `agent-${item.agentIdx}`, target: item.id, type: "smoothstep", style: { stroke: item.edgeColor, strokeWidth: 1.5 } });
            });
        } else {
            // No AI Agents — show device → direct service children (MCP/LLM)
            const direct = agents.filter(a => a.type === "MCP Server" || a.type === "LLM");
            const maxRows = Math.max(direct.length, 1);
            const devY = (maxRows * NODE_H - 44) / 2;
            ns.push({ id: "device", type: "topoNode", draggable: false, position: { x: COL1_X, y: devY }, data: { component: { category: "external", type: "User", label: deviceLabel } } });
            direct.forEach((a, i) => {
                const cat = a.type === "LLM" ? "ai-model" : "mcp";
                const color = a.type === "LLM" ? "#ec4899" : "#9ca3af";
                ns.push({ id: `svc-${i}`, type: "topoNode", draggable: false, position: { x: COL2_X, y: i * NODE_H }, data: { component: { category: cat, type: a.type, label: a.endpoint } } });
                es.push({ id: `e-d-s${i}`, source: "device", target: `svc-${i}`, type: "smoothstep", style: { stroke: color, strokeWidth: 1.5 } });
            });
        }

        return { nodes: ns, edges: es };
    }, [agents, device.endpoint, device.username, collections]);

    return <AssetTopologyGraph nodes={nodes} edges={edges} />;
}

// ─── User analysis section ─────────────────────────────────────────────────────

// Domain names only (no subtopics), as badges on one line capped at 5 with a
// "+N" tooltip badge for the rest.
const MAX_INLINE_TOPICS = 5;

function TopicsLine({ topics }) {
    const labels = topics.map(t => func.toSentenceCase(t));
    const shown = labels.slice(0, MAX_INLINE_TOPICS);
    const overflow = labels.slice(MAX_INLINE_TOPICS);

    return (
        <HorizontalStack gap="2" wrap>
            {shown.map(label => <Badge key={label}>{label}</Badge>)}
            {overflow.length > 0 && (
                <Tooltip content={overflow.join(", ")}>
                    <Badge>{`+${overflow.length}`}</Badge>
                </Tooltip>
            )}
        </HorizontalStack>
    );
}

function UserAnalysisSection({ username, startTimestamp, endTimestamp }) {
    const [analysis, setAnalysis] = useState(null);
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();

    const handleCreateGuardrail = useCallback(() => {
        const prefill = buildTopicGuardrailPrefill(analysis?.topicHierarchy || {});
        navigate(GUARDRAIL_POLICIES_PATH, { state: { topicGuardrailPrefill: prefill } });
    }, [analysis, navigate]);

    useEffect(() => {
        if (!username) { setLoading(false); return; }
        let cancelled = false;
        setLoading(true);
        settingsApi.getUserAnalysis(username)
            .then(data => {
                if (cancelled) return;
                setAnalysis(data || null);
                setLoading(false);
            })
            .catch(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [username]);

    const topicEntries = useMemo(() => {
        const h = analysis?.topicHierarchy;
        if (!h || typeof h !== "object") return [];
        return Object.entries(h).sort((a, b) => {
            const sumA = Object.values(a[1] || {}).reduce((s, v) => s + v, 0);
            const sumB = Object.values(b[1] || {}).reduce((s, v) => s + v, 0);
            return sumB - sumA;
        });
    }, [analysis]);

    if (!username) return null;
    if (loading) return (
        <Box paddingBlockStart="2">
            <Text variant="bodySm" color="subdued">Loading user analysis...</Text>
        </Box>
    );
    if (!analysis) return null;

    const inputTokens = analysis.totalInputTokens || 0;
    const outputTokens = analysis.totalOutputTokens || 0;

    return (
        <VerticalStack gap="3">
            <Text variant="headingXs" color="subdued">User Analysis</Text>

            {analysis.aiSummary && (
                <Text variant="bodySm">{analysis.aiSummary}</Text>
            )}

            <HorizontalGrid columns={2} gap="3">
                <VerticalStack gap="1">
                    <Text variant="headingMd" as="p">{inputTokens.toLocaleString("en-US")}</Text>
                    <Text variant="bodySm" color="subdued">Input tokens</Text>
                </VerticalStack>
                <VerticalStack gap="1">
                    <Text variant="headingMd" as="p">{outputTokens.toLocaleString("en-US")}</Text>
                    <Text variant="bodySm" color="subdued">Output tokens</Text>
                </VerticalStack>
            </HorizontalGrid>

            {topicEntries.length > 0 && (
                <VerticalStack gap="2" inlineAlign="start">
                    <Divider />
                    <Text variant="headingXs" color="subdued">Topics Queried</Text>
                    <TopicsLine topics={topicEntries.map(([topic]) => topic)} />
                    <Tooltip content="Create a new blocking guardrail policy, pre-filled with these topics as denied topics">
                        <Link onClick={handleCreateGuardrail}>Create guardrail</Link>
                    </Tooltip>
                </VerticalStack>
            )}
        </VerticalStack>
    );
}

// ─── Overview tab ─────────────────────────────────────────────────────────────

const SEV_ORDER = { critical: 0, high: 1, medium: 2, low: 3 };

function OverviewTab({ device, agents, collections, onTabChange, startTimestamp, endTimestamp }) {
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

                <TopologyGraph device={device} agents={agents} collections={collections} />

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

                <UserAnalysisSection
                    username={safeVal(device.username)}
                    startTimestamp={startTimestamp}
                    endTimestamp={endTimestamp}
                />
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

function ViolationsTab({ hostNames = [], deviceId, startTimestamp, endTimestamp }) {
    const [violations, setViolations] = useState([]);

    useEffect(() => { setViolations([]); }, [hostNames, deviceId]);

    useEffect(() => {
        const claudeDeviceIds = new Set(
            hostNames
                .filter(h => { const parts = h.split("."); return parts[parts.length - 1]?.toLowerCase() === "claude"; })
                .map(h => h.split(".")[0])
                .filter(Boolean)
        );
        if (deviceId) claudeDeviceIds.add(deviceId);

        if (!hostNames.length && !claudeDeviceIds.size) { setViolations([]); return; }

        let cancelled = false;
        const hostSet = new Set(hostNames);
        const looseHostSet = new Set(hostNames.map(h => deviceServiceKey(h)).filter(Boolean));
        fetchAgenticViolations({ startTimestamp, endTimestamp })
            .then((rows) => {
                if (cancelled) return;
                const filtered = rows.filter(r =>
                    hostSet.has(r.host) ||
                    looseHostSet.has(deviceServiceKey(r.host)) ||
                    (isClaudeConfigHost(r.host) && claudeDeviceIds.has(r.host.split(".")[0]))
                );
                setViolations(filtered.map((r) => ({
                    ...r,
                    time: r.timeEpoch ? func.formatChatTimestamp(r.timeEpoch) : "",
                })));
            })
            .catch(() => {
                if (!cancelled) setViolations([]);
            });
        return () => { cancelled = true; };
    }, [hostNames, deviceId, startTimestamp, endTimestamp]);

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

export default function DeviceFlyout({ device, agents, show, onClose, onAgentClick, agentRiskData = {}, deviceHostNames = [], collections = [], startTimestamp, endTimestamp }) {
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
                {selectedTab === 0 && <OverviewTab device={device} agents={agents || []} collections={collections} onTabChange={setSelectedTab} startTimestamp={startTimestamp} endTimestamp={endTimestamp} />}
                {selectedTab === 1 && <AgenticsTab agents={agents || []} onAgentClick={onAgentClick} agentRiskData={agentRiskData} />}
                {selectedTab === 2 && <ViolationsTab hostNames={deviceHostNames} deviceId={device?.path?.[0] || device?.deviceId} startTimestamp={startTimestamp} endTimestamp={endTimestamp} />}
            </Box>
        </AgenticFlyoutShell>
    );
}

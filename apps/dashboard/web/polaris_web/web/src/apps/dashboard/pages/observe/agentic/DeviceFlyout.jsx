import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Tabs, Box, VerticalStack, HorizontalStack, HorizontalGrid, Text, Divider } from "@shopify/polaris";
import TopicsGuardrailList from "../../guardrails/components/TopicsGuardrailList";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AgenticFlyoutShell from "./AgenticFlyoutShell";
import AiChatSection from "./AiChatSection";
import { TypeBadge, SeverityBadge, RiskPill } from "./AgenticCellRenderers";

import AssetTopologyGraph from "./AssetTopologyGraph";
import { RiskFactorRow } from "./RiskFactorRow";
import DetailGrid from "./DetailGrid";
import agenticObserveApi, { buildAgenticObserveChatMetadata, fetchAgenticViolationsPage, openViolationInThreatActivity, deviceServiceKey } from "./agenticObserveApi";
import { buildMcpComponentsFromStis } from "./agenticPageBuilders";
import api from "../api";
import { extractEndpointId } from "./constants";
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

// Reads riskScore/violations straight off each row — buildDeviceChildren (Java) already computes
// them there, so no separate agentRiskData side-map/lookup is needed (it used to just re-derive
// the exact same values from the same rows).
function buildAgentsColDefs() {
    return [
    { field: "endpoint", headerName: "Agentic Asset", flex: 1, minWidth: 160, cellRenderer: AgentNameCellRenderer, cellClass: (p) => ({ "AI Agent": "agentic-type-AGENT", "MCP Server": "agentic-type-MCP", "LLM": "agentic-type-LLM", "Skill": "agentic-type-SKILL" })[p.data?.type] || "", cellStyle: { display: "flex", alignItems: "center" } },
    {
        field: "riskScore", headerName: "Risk", width: 80,
        sort: "desc",
        suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
        cellRenderer: AgentRiskCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        field: "violations", headerName: "Violations", width: 130,
        suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
        cellRenderer: AgentViolationsCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
    { field: "skillCount", headerName: "Skills", width: 80, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: AgentSkillsCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];
}

// Only "time" (-> detectedAt) and "severity" have a server-side sort mapping (see
// ViolationsTab's onServerFetch) — "title" isn't a real backend field (it's filterId).
const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",      width: 120, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
    { field: "title",    headerName: "Violation", flex: 1, minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, sortable: false },
    { field: "severity", headerName: "Severity",  width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Topology graph ───────────────────────────────────────────────────────────

// Build col3 items (MCPs, Skills, Plugins) linked to a given AI Agent — from the batch detail
// fetch (mcpServers/skillCount/pluginNames, keyed by agent.groupKey), the same fields
// AssetTopologyGraph.jsx's own asset-flyout graph uses. No detail (fetch still pending, or this
// agent has no groupKey) means no col3Items — nothing rather than a wrong guess.
function buildAgentCol3Items(detail, agentIdx, builtinTools = []) {
    const items = [];
    if (detail) {
        const llmNames = new Set(detail.llmServers || []);
        (detail.mcpServers || []).forEach((name, i) => {
            const isLlm = llmNames.has(name);
            const collectionId = detail.mcpServerCollectionIds?.[name]?.[0];
            items.push(isLlm
                ? { id: `c3-${agentIdx}-${i}`, cat: "ai-model", type: "LLM", label: name, agentIdx, edgeColor: "#ec4899" }
                : { id: `c3-${agentIdx}-${i}`, cat: "mcp", type: "MCP Server", label: name, agentIdx, edgeColor: "#4cbebb", collectionId });
        });
        if (detail.skillCount > 0) {
            items.push({ id: `skl-${agentIdx}`, cat: "skill", type: "Skill", label: detail.skillCount === 1 ? "1 Skill" : `${detail.skillCount} Skills`, agentIdx, edgeColor: "#7C3AED" });
        }
        // pluginNames are compound "pluginName|ownerKey" keys (see AgenticObserveAction's
        // classifyAllGroups) — only the bare name in front of "|" is ever shown.
        (detail.pluginNames || []).forEach((key, i) => {
            items.push({ id: `plg-${agentIdx}-${i}`, cat: "plugin", type: "Plugin", label: key.split("|")[0], agentIdx, edgeColor: "#4F46E5" });
        });
    }

    const seenTools = new Set();
    (builtinTools || []).forEach((tool, ti) => {
        const name = tool?.name;
        if (!name || seenTools.has(name)) return;
        seenTools.add(name);
        items.push({ id: `inline-tool-${agentIdx}-${ti}`, cat: "tool", type: "Tool", label: name, agentIdx, edgeColor: "#D97706" });
    });

    return items;
}

const TOPO_ROW_H = 76;      // one component row
const TOPO_BLOCK_GAP = 28;  // gap between two agents' blocks
const TOPO_NODE_H = 64;     // rendered node height, for vertical centering

const TOOL_CAP = 4;

// One row per component, plus a row per tool hanging off an MCP — so nothing ever shares a row.
function buildAgentRows(items, mcpTools) {
    const rows = [];
    items.forEach((item) => {
        rows.push({ item });
        const tools = item.cat === "mcp" && item.collectionId ? (mcpTools[item.collectionId] || []) : [];
        const shown = tools.slice(0, TOOL_CAP);
        const labels = tools.length > shown.length ? [...shown, `+${tools.length - shown.length} more`] : shown;
        labels.forEach((label, i) => rows.push({ item, tool: { id: `${item.id}-tool-${i}`, label } }));
    });
    return rows;
}

function TopologyGraph({ device, agents, agentDetails = new Map(), agentTools = {}, mcpTools = {} }) {
    const { nodes, edges } = useMemo(() => {
        const aiAgents = agents.filter(a => a.type === "AI Agent");
        const hasAgents = aiAgents.length > 0;

        const COL1_X = 40, COL2_X = 250, COL3_X = 470, COL4_X = 690;
        const centerIn = (top, blockH) => top + (blockH - TOPO_NODE_H) / 2;

        const deviceLabel = device.username && device.username !== "-" ? device.username : device.endpoint;
        const ns = [];
        const es = [];

        if (hasAgents) {
            // Each agent owns a vertical block sized to its own components, so a component always
            // sits directly across from the agent it belongs to. Laying every agent's components
            // out as one flat list instead (the old approach) left them lined up against
            // whichever agent happened to share that row.
            let cursor = 0;
            const blocks = aiAgents.map((a, i) => {
                const items = buildAgentCol3Items(agentDetails.get(a.groupKey), i, agentTools[i] || []);
                const rows = buildAgentRows(items, mcpTools);
                const blockH = Math.max(1, rows.length) * TOPO_ROW_H;
                const block = { idx: i, label: a.endpoint, rows, top: cursor, blockH };
                cursor += blockH + TOPO_BLOCK_GAP;
                return block;
            });
            const contentH = Math.max(cursor - TOPO_BLOCK_GAP, TOPO_ROW_H);

            ns.push({ id: "device", type: "topoNode", draggable: false, position: { x: COL1_X, y: centerIn(0, contentH) }, data: { component: { category: "external", type: "User", label: deviceLabel } } });
            blocks.forEach((b) => {
                ns.push({ id: `agent-${b.idx}`, type: "topoNode", draggable: false, position: { x: COL2_X, y: centerIn(b.top, b.blockH) }, data: { component: { category: "agent", type: "AI Agent", label: b.label } } });
                es.push({ id: `e-d-a${b.idx}`, source: "device", target: `agent-${b.idx}`, type: "smoothstep", style: { stroke: "#9ca3af", strokeWidth: 1.5 } });
                b.rows.forEach((row, j) => {
                    const y = b.top + j * TOPO_ROW_H;
                    if (row.tool) {
                        ns.push({ id: row.tool.id, type: "topoNode", draggable: false, position: { x: COL4_X, y }, data: { component: { category: "tool", type: "Tool", label: row.tool.label } } });
                        es.push({ id: `e-${row.tool.id}`, source: row.item.id, target: row.tool.id, type: "smoothstep", style: { stroke: "#D97706", strokeWidth: 1.5 } });
                        return;
                    }
                    const item = row.item;
                    ns.push({ id: item.id, type: "topoNode", draggable: false, position: { x: COL3_X, y }, data: { component: { category: item.cat, type: item.type, label: item.label, collectionId: item.collectionId } } });
                    es.push({ id: `e-a${b.idx}-${item.id}`, source: `agent-${b.idx}`, target: item.id, type: "smoothstep", style: { stroke: item.edgeColor, strokeWidth: 1.5 } });
                });
            });
            return { nodes: ns, edges: es };
        }

        // No AI Agents — show device → direct service children (MCP/LLM)
        const direct = agents.filter(a => a.type === "MCP Server" || a.type === "LLM");
        const contentH = Math.max(direct.length, 1) * TOPO_ROW_H;
        ns.push({ id: "device", type: "topoNode", draggable: false, position: { x: COL1_X, y: centerIn(0, contentH) }, data: { component: { category: "external", type: "User", label: deviceLabel } } });
        direct.forEach((a, i) => {
            const cat = a.type === "LLM" ? "ai-model" : "mcp";
            const color = a.type === "LLM" ? "#ec4899" : "#9ca3af";
            ns.push({ id: `svc-${i}`, type: "topoNode", draggable: false, position: { x: COL2_X, y: i * TOPO_ROW_H }, data: { component: { category: cat, type: a.type, label: a.endpoint, collectionId: cat === "mcp" ? a.collectionIds?.[0] : undefined } } });
            es.push({ id: `e-d-s${i}`, source: "device", target: `svc-${i}`, type: "smoothstep", style: { stroke: color, strokeWidth: 1.5 } });
        });
        return { nodes: ns, edges: es };
    }, [agents, device.endpoint, device.username, agentDetails, agentTools, mcpTools]);

    // No height passed — same fixed box the Agentic Assets page graph uses. focusNodeId opens the
    // view on the device this flyout is about, rather than fitting every agent branch at once.
    return <AssetTopologyGraph nodes={nodes} edges={edges} focusNodeId="device" />;
}

// ─── User analysis section ─────────────────────────────────────────────────────

function UserAnalysisSection({ username, startTimestamp, endTimestamp }) {
    const [analysis, setAnalysis] = useState(null);
    const [loading, setLoading] = useState(true);

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

    const sortedTopicHierarchy = useMemo(() => {
        const h = analysis?.topicHierarchy;
        if (!h || typeof h !== "object") return {};
        const entries = Object.entries(h).sort((a, b) => {
            const sumA = Object.values(a[1] || {}).reduce((s, v) => s + v, 0);
            const sumB = Object.values(b[1] || {}).reduce((s, v) => s + v, 0);
            return sumB - sumA;
        });
        return Object.fromEntries(entries);
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

            {Object.keys(sortedTopicHierarchy).length > 0 && (
                <VerticalStack gap="2" inlineAlign="start">
                    <Divider />
                    <Text variant="headingXs" color="subdued">Topics Queried</Text>
                    <TopicsGuardrailList topicHierarchy={sortedTopicHierarchy} username={username} />
                </VerticalStack>
            )}
        </VerticalStack>
    );
}

// ─── Overview tab ─────────────────────────────────────────────────────────────

const SEV_ORDER = { critical: 0, high: 1, medium: 2, low: 3 };

function OverviewTab({ device, agents, collections, onTabChange, startTimestamp, endTimestamp, violationsTotal }) {
    const [agentTools, setAgentTools] = useState({});

    const aiAgents = useMemo(() => agents.filter(a => a.type === "AI Agent"), [agents]);

    useEffect(() => {
        if (!aiAgents.length) { setAgentTools({}); return; }
        let cancelled = false;
        (async () => {
            try {
                // allSettled at both levels — one failing collection used to blank the tools list for
                // every agent in the flyout, not just its own.
                const settled = await Promise.allSettled(aiAgents.map(async (agent, idx) => {
                    const ids = agent.collectionIds || [];
                    if (!ids.length) return [idx, []];
                    const bundles = await Promise.allSettled(ids.map(id => agenticObserveApi.fetchAgentBuiltinToolsData(id)));
                    const seen = new Set();
                    const tools = [];
                    bundles.forEach((b) => {
                        if (b.status !== "fulfilled") return;
                        (b.value || []).forEach((tool) => {
                            if (!tool?.name || seen.has(tool.name)) return;
                            seen.add(tool.name);
                            tools.push(tool);
                        });
                    });
                    return [idx, tools];
                }));
                const entries = settled.filter((s) => s.status === "fulfilled").map((s) => s.value);
                if (!cancelled) setAgentTools(Object.fromEntries(entries));
            } catch {
                if (!cancelled) setAgentTools({});
            }
        })();
        return () => { cancelled = true; };
    }, [aiAgents]);

    // One batch call for every AI Agent's mcpServers/skillCount/pluginNames (context graph's
    // col3Items) instead of one fetchAgenticAssetDetail per agent — a device can show 10+ agents.
    const [agentDetails, setAgentDetails] = useState(new Map());
    useEffect(() => {
        const groupKeys = [...new Set(aiAgents.map(a => a.groupKey).filter(Boolean))];
        if (!groupKeys.length) { setAgentDetails(new Map()); return; }
        let cancelled = false;
        api.fetchAgenticAssetDetailsBatch({ groupKeys, rowType: "agent" })
            .then(byGroupKey => { if (!cancelled) setAgentDetails(byGroupKey); })
            .catch(() => { if (!cancelled) setAgentDetails(new Map()); });
        return () => { cancelled = true; };
    }, [aiAgents]);

    // Each MCP server's own tools, keyed by collection id. Fetched here rather than inside
    // AssetTopologyGraph because the graph rows are laid out here — tools need reserved rows or
    // they land on top of the next MCP's row.
    const mcpCollectionIds = useMemo(() => {
        const ids = new Set();
        agentDetails.forEach(d => Object.values(d?.mcpServerCollectionIds || {}).forEach(arr => {
            if (arr?.[0]) ids.add(arr[0]);
        }));
        return [...ids];
    }, [agentDetails]);

    const [mcpTools, setMcpTools] = useState({});
    useEffect(() => {
        if (!mcpCollectionIds.length) { setMcpTools({}); return; }
        let cancelled = false;
        agenticObserveApi.fetchCollectionStiBundlesBatch(mcpCollectionIds)
            .then(bundles => {
                if (cancelled) return;
                const next = {};
                bundles.forEach((b, id) => {
                    const { tools } = buildMcpComponentsFromStis(b.stiEndpoints, b.apiInfoList, b.id, b.auditRows);
                    next[id] = (tools || []).map(t => t.name).filter(Boolean);
                });
                setMcpTools(next);
            })
            .catch(() => { if (!cancelled) setMcpTools({}); });
        return () => { cancelled = true; };
    }, [mcpCollectionIds]);

    const inlineToolCount = useMemo(
        () => Object.values(agentTools).reduce((n, tools) => n + (tools?.length || 0), 0),
        [agentTools],
    );
    const inlineLlmCount = aiAgents.length > 0
        ? aiAgents.filter((agent) => {
            const agentIdSet = new Set((agent.collectionIds || []).map(Number));
            const deviceId = agent.path?.[0];
            return collections.some((c) => {
                if (!agentIdSet.has(Number(c.id))) return false;
                const hostName = c.hostName || c.displayName || c.name || "";
                return hostName.includes(".ai-agent.") && extractEndpointId(hostName) === deviceId;
            });
        }).length
        : 0;

    const { aiCount, mcpCount, llmCount, totalV } = useMemo(() => ({
        aiCount:  aiAgents.length,
        mcpCount: agents.filter(a => a.type === "MCP Server").length,
        llmCount: agents.filter(a => a.type === "LLM").length + inlineLlmCount,
        // device.violations is an exact-hostName join and can undercount vs. the Violations
        // tab's own query (loose host/Claude-config attribution). Once that tab has actually
        // loaded (violationsTotal, lifted from DeviceFlyout), prefer its real total.
        totalV: violationsTotal ?? ((device.violations?.critical || 0) + (device.violations?.high || 0) + (device.violations?.medium || 0) + (device.violations?.low || 0)),
    }), [agents, aiAgents.length, device.violations, inlineLlmCount, violationsTotal]);

    const osLabel = useMemo(() => {
        if (device.os === "mac") return "macOS";
        if (device.os === "windows") return "Windows";
        if (device.os === "linux") return "Linux";
        return "Unknown OS";
    }, [device.os]);

    const rawFactors = useMemo(() => computeRiskFactors(device, agents), [device, agents]);
    const factors    = useMemo(() => [...rawFactors].sort((a, b) => (SEV_ORDER[a.severity] ?? 99) - (SEV_ORDER[b.severity] ?? 99)), [rawFactors]);

    const stats = useMemo(() => {
        const rows = [
            { label: aiCount  === 1 ? "AI Agent"   : "AI Agents",   value: aiCount  },
            { label: mcpCount === 1 ? "MCP Server" : "MCP Servers", value: mcpCount },
            { label: llmCount === 1 ? "LLM"        : "LLMs",        value: llmCount },
        ];
        if (inlineToolCount > 0) {
            rows.push({ label: inlineToolCount === 1 ? "Tool" : "Tools", value: inlineToolCount });
        }
        rows.push({ label: totalV === 1 ? "Violation" : "Violations", value: totalV });
        return rows;
    }, [aiCount, mcpCount, llmCount, inlineToolCount, totalV]);

    const safeVal = (v) => (v && v !== "-" ? v : null);
    const deviceDetails = useMemo(() => [
        { label: "User",      value: safeVal(device.username) },
        { label: "OS",        value: osLabel },
        { label: "Last Seen", value: safeVal(device.lastTraffic) },
        device.hasPersonalAccount
            ? { label: "Account", value: "Personal account", isWarning: true }
            : { label: "Account", value: "Corporate" },
    ], [device, osLabel]);

    return (
        <Box padding="4">
            <VerticalStack gap="5">
                <HorizontalGrid columns={stats.length} gap="3">
                    {stats.map(s => (
                        <VerticalStack gap="1" key={s.label}>
                            <Text variant="heading2xl" as="p">{s.value}</Text>
                            <Text variant="bodySm" color="subdued">{s.label}</Text>
                        </VerticalStack>
                    ))}
                </HorizontalGrid>

                <VerticalStack gap="2">
                    <Text variant="headingXs" color="subdued">Context graph</Text>
                    <TopologyGraph device={device} agents={agents} agentDetails={agentDetails} agentTools={agentTools} mcpTools={mcpTools} />
                </VerticalStack>

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
                                    ? () => { window.location.href = `/dashboard/observe/endpoints?device=${encodeURIComponent(deviceId)}`; }
                                    : undefined;
                            } else if (f.type === "malicious_skill") {
                                const maliciousAgents = agents.filter(a => a.isMalicious);
                                const firstSkill = maliciousAgents[0];
                                handleClick = firstSkill
                                    ? () => { window.location.href = `/dashboard/observe/agentic-assets?asset=${encodeURIComponent(firstSkill.rawServiceName || firstSkill.endpoint)}`; }
                                    : () => { window.location.href = "/dashboard/observe/agentic-assets"; };
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

const AGENTS_COL_DEFS = buildAgentsColDefs();

// Server-side paginated — reuses the same fetchDeviceEndpointsSummary(parentDeviceId) endpoint
// the main Endpoints grid's own tree-expand rows already call (AgenticObserveAction.java's
// buildDeviceChildren, now paginated/sorted/searched there too, so both call sites benefit).
function AgenticsTab({ deviceId }) {
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const mongoOrder = sortOrder ? -sortOrder : -1; // AG-Grid asc/desc convention is inverted vs Mongo
        return api.fetchDeviceEndpointsSummary({
            parentDeviceId: deviceId,
            skip,
            limit: limit || 20,
            sortKey,
            sortOrder: mongoOrder,
            queryValue: searchString || undefined,
        }).then((res) => ({
            value: res.rows || [],
            total: res.total || 0,
        }));
    }, [deviceId]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        if (!isAgentNavigable(e.data)) return;
        const assetId = e.data.rawServiceName || e.data.endpoint;
        const params = new URLSearchParams({ asset: assetId, type: e.data.type });
        window.location.href = `/dashboard/observe/agentic-assets?${params}`;
    }, []);

    return (
        <AgGridTable
            key={deviceId}
            columnDefs={AGENTS_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onServerFetch={onServerFetch}
            serverSideRowModel
            getRowId={(params) => params.data.id}
            onRowClicked={handleRowClick}
            getRowStyle={({ data }) => isAgentNavigable(data) ? { cursor: "pointer" } : { cursor: "default" }}
            noOuterBorder
            searchPlaceholder="Search assets..."
            paginationPageSize={20}
            paginationPageSizeSelector={[20, 50, 100]}
            sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            domLayout="normal" />
    );
}

// ─── Violations tab ───────────────────────────────────────────────────────────

// Server-side paginated/searched/sorted — mirrors ViolationsTab.jsx's (Agentic Assets flyout)
// onServerFetch exactly, scoped by this device's own hostNames/deviceId instead of an asset's.
// claudeDeviceIds always includes this device's own id (not just hosts already seen ending in
// ".claude") so an orphan claude-config-scanner event still attributes correctly even if no real
// Claude collection has been seen for this device yet — matches the original client-side filter.
function ViolationsTab({ hostNames = [], deviceId, startTimestamp, endTimestamp, onTotalChange }) {
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        if (!hostNames.length && !deviceId) {
            return Promise.resolve({ value: [], total: 0 });
        }

        const claudeDeviceIds = new Set(
            hostNames
                .filter(h => { const parts = h.split("."); return parts[parts.length - 1]?.toLowerCase() === "claude"; })
                .map(h => h.split(".")[0])
                .filter(Boolean)
        );
        if (deviceId) claudeDeviceIds.add(deviceId);
        const looseHostKeys = hostNames.map(h => deviceServiceKey(h)).filter(Boolean);

        const mongoOrder = sortOrder ? -sortOrder : -1;
        const sortBySeverity = sortKey === "severity";

        return fetchAgenticViolationsPage({
            startTimestamp, endTimestamp,
            hosts: hostNames,
            looseHostKeys,
            claudeDeviceIds: Array.from(claudeDeviceIds),
            skip,
            limit: limit || 20,
            sort: sortBySeverity ? { severity: mongoOrder } : { detectedAt: mongoOrder },
            sortBySeverity,
            searchText: searchString || undefined,
        }).then((res) => {
            onTotalChange?.(res.total ?? 0);
            return {
                value: res.violations.map((r) => ({
                    ...r,
                    time: r.timeEpoch ? func.formatChatTimestamp(r.timeEpoch) : "",
                })),
                total: res.total,
            };
        });
    }, [hostNames, deviceId, startTimestamp, endTimestamp, onTotalChange]);

    const handleViolationClick = useCallback((e) => {
        if (!e.data) return;
        openViolationInThreatActivity(e.data);
    }, []);

    return (
        <AgGridTable
            key={deviceId}
            columnDefs={VIOLATIONS_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onServerFetch={onServerFetch}
            serverSideRowModel
            getRowId={(params) => params.data.refId || `${params.data.host}-${params.data.timeEpoch}-${params.data.title}`}
            onRowClicked={handleViolationClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            noOuterBorder
            searchPlaceholder="Search violations..."
            paginationPageSize={20}
            paginationPageSizeSelector={[20, 50, 100]}
            sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            domLayout="normal" />
    );
}

// ─── Main DeviceFlyout ────────────────────────────────────────────────────────

export default function DeviceFlyout({ device, agents, show, onClose, onAgentClick, deviceHostNames = [], collections = [], startTimestamp, endTimestamp }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const deviceId = device?.path?.[0] || device?.deviceId;
    // See OverviewTab/ViolationsTab below - device.violations undercounts vs. the tab's own
    // query, so once the Violations tab has actually loaded, its real total wins everywhere
    // in this flyout. Reset per device so a stale total never carries over to the next one.
    const [violationsTotal, setViolationsTotal] = useState(null);
    useEffect(() => { setViolationsTotal(null); }, [deviceId]);

    // Minimal identity only — the MCP agent resolves this device's collections and fetches
    // its endpoints/components/violations on demand via akto_agentic_asset_details (deviceId).
    const chatMetadata = useMemo(() => buildAgenticObserveChatMetadata("device", {
        deviceEndpoint: device?.endpoint,
        deviceId: device?.path?.[0],
    }), [device]);

    const tabs = useMemo(() => {
        if (!device) return [];
        const assetTotalV = (device.violations?.critical || 0) + (device.violations?.high || 0) + (device.violations?.medium || 0) + (device.violations?.low || 0);
        const totalV = violationsTotal ?? assetTotalV;
        return [
            { id: "overview",   content: "Overview" },
            { id: "assets",     content: `Agentic Assets (${(agents || []).length})` },
            { id: "violations", content: `Violations (${totalV})` },
        ];
    }, [device, agents, violationsTotal]);

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
                {selectedTab === 0 && <OverviewTab device={device} agents={agents || []} collections={collections} onTabChange={setSelectedTab} startTimestamp={startTimestamp} endTimestamp={endTimestamp} violationsTotal={violationsTotal} />}
                {selectedTab === 1 && <AgenticsTab deviceId={deviceId} />}
                {selectedTab === 2 && <ViolationsTab hostNames={deviceHostNames} deviceId={deviceId} startTimestamp={startTimestamp} endTimestamp={endTimestamp} onTotalChange={setViolationsTotal} />}
            </Box>
        </AgenticFlyoutShell>
    );
}

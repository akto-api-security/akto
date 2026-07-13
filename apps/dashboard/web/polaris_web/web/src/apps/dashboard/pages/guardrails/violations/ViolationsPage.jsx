import React, { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import {
    Badge,
    Box,
    Button,
    Card,
    HorizontalGrid,
    HorizontalStack,
    Modal,
    Text,
    VerticalStack,
} from "@shopify/polaris";

import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import DonutChart from "@/apps/dashboard/components/shared/DonutChart";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";
import AgenticStatsCard from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import AgenticTopListCard from "@/apps/dashboard/pages/observe/agentic/AgenticTopListCard";
import AssetIcon from "@/apps/dashboard/pages/observe/agentic/AssetIcon";
import { SeverityBadge } from "@/apps/dashboard/pages/observe/agentic/AgenticCellRenderers";
import { OsIcon, TYPE_CLASS_MAP } from "@/apps/dashboard/pages/observe/agentic/DeviceEndpoints";
import func from "@/util/func";
import values from "@/util/values";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import PersistStore from "@/apps/main/PersistStore";
import LocalStore from "@/apps/main/LocalStorageStore";
import NewLayoutTooltip from "@/apps/dashboard/pages/observe/agentic/NewLayoutTooltip";
import { isEndpointSecurityCategory } from "@/apps/main/labelHelper";

import { fetchEndpointShieldUsernameMap, getUsernameForCollection } from "@/apps/dashboard/pages/observe/api_collections/endpointShieldHelper";
import { formatDisplayName } from "@/apps/dashboard/pages/observe/agentic/mcpClientHelper";
import { extractServiceName } from "@/apps/dashboard/pages/observe/agentic/constants";

import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import guardrailsApi from "../api";
import threatDetectionApi from "@/apps/dashboard/pages/threat_detection/api";
import ViolationFlyout from "./ViolationFlyout";
import { SPARKLINE_LABELS, normalizeReasonPunctuation, coerceToText, sanitizeDisplayText } from "./violationsData";

// ─── Method → display type mapping ──────────────────────────────────────────────

const METHOD_TO_TYPE = {
    POST: "Prompt",
    SKILL: "Skill",
    TOOL: "Tool",
    CONFIG: "Config",
    LLM: "LLM",
};

// ─── Cell renderers ─────────────────────────────────────────────────────────────

function TypeCellRenderer({ value }) {
    if (!value) return null;
    return (
        <Box as="span" className={TYPE_CLASS_MAP[value] || "agentic-type-DEFAULT"}>
            <Badge size="small">{value}</Badge>
        </Box>
    );
}

function detectOs(host) {
    if (!host) return null;
    const h = host.toLowerCase();
    if (h.includes("mac")) return "mac";
    if (h.includes("windows")) return "windows";
    if (h.includes("linux")) return "linux";
    return null;
}

// Strip leading 32-char hex device UUID and return a readable name for the asset.
// "841e96caaafa4b088571496c7472eb2a.claude-cli-project.filesystem" → "Claude CLI Project"
// "vulnerable-agent-kong.akto.io" (no UUID prefix) → returned as-is
// Returns the service/agent name from a hostname regardless of how many dot-segments it has.
// Handles: device.agent.service (3 parts), device.service (2 parts), and hex-prefixed formats.
function getAssetServiceName(raw) {
    if (!raw) return null;
    // hex-prefixed legacy: <32hexchars>.<rest>
    const hex = raw.match(/^[0-9a-f]{32}\.(.+)$/i);
    if (hex) return hex[1].split('.')[0];
    const parts = raw.split('.');
    if (parts.length >= 3) {
        const svc = extractServiceName(raw);
        return (svc && svc !== raw) ? svc : parts[parts.length - 1];
    }
    if (parts.length === 2) return parts[1]; // device.service format
    return raw;
}

function formatAssetDisplayName(raw) {
    if (!raw) return null;
    return formatDisplayName(getAssetServiceName(raw));
}

function AssetCellRenderer({ value, data }) {
    if (!value) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <AssetIcon type={null} assetTagValue={data?.agenticAssetTag || value} size={24} />
            <Box width="100%" overflowX="hidden">
                <Text variant="bodySm" fontWeight="medium" truncate>{value}</Text>
            </Box>
        </HorizontalStack>
    );
}

function SeverityCellRenderer({ value }) {
    if (!value) return null;
    return <SeverityBadge severity={value} />;
}

function UserCellRenderer({ value, data }) {
    if (!value) return null;
    const os = detectOs(data?.userHost);
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <AssetIcon type="OS" assetTagValue={os} size={20} />
            <Box minWidth="0" overflowX="hidden">
                <Text variant="bodySm" fontWeight="medium" truncate>{value}</Text>
            </Box>
        </HorizontalStack>
    );
}

function ActionCellRenderer({ value }) {
    if (!value) return null;
    const status = value === "Blocked" ? "critical" : "warning";
    return <Badge size="small" status={status}>{value}</Badge>;
}

function EvidenceCellRenderer({ value }) {
    if (!value) return null;
    return <Text variant="bodySm" truncate>{value}</Text>;
}

const STATUS_LABEL = { ACTIVE: "Open", FIXED: "Fixed", IGNORED: "Ignored", UNDER_REVIEW: "In Review" };
const STATUS_DOT_COLOR = { ACTIVE: "#9642FC", FIXED: "#5BC0DE", IGNORED: "#F5C451", UNDER_REVIEW: "#637381" };
function StatusCellRenderer({ value }) {
    if (!value) return null;
    const key = String(value).toUpperCase();
    const label = STATUS_LABEL[key] || value;
    const color = STATUS_DOT_COLOR[key];
    return (
        <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
            {color && <span className="agentic-dot" style={{ "--dot-color": color }} />}
            <span style={{ fontSize: "12px" }}>{label}</span>
        </span>
    );
}

// ─── Column definitions ─────────────────────────────────────────────────────────

const SEVERITY_RANK = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
const severityComparator = (a, b) => (SEVERITY_RANK[a] ?? 99) - (SEVERITY_RANK[b] ?? 99);

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: true,
    cellStyle: { display: "flex", alignItems: "center" },
};

const COL_DEFS = [
    {
        field: "detected",
        headerName: "Detected",
        minWidth: 150,
        filter: false,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        valueFormatter: p => p.value != null ? func.epochToDateTime(p.value) : "",
    },
    {
        field: "type",
        headerName: "Type",
        minWidth: 100,
        filter: "agSetColumnFilter",
        cellRenderer: TypeCellRenderer,
    },
    {
        field: "evidenceText",
        headerName: "Evidence",
        width: 200,
        minWidth: 200,
        suppressAutoSize: true,
        sortable: false,
        filter: false,
        cellRenderer: EvidenceCellRenderer,
    },
    {
        field: "severity",
        headerName: "Severity",
        minWidth: 110,
        sort: "asc",
        comparator: severityComparator,
        filter: "agSetColumnFilter",
        cellRenderer: SeverityCellRenderer,
    },
    {
        field: "user",
        headerName: "User",
        minWidth: 140,
        cellRenderer: UserCellRenderer,
    },
    {
        field: "agenticAsset",
        headerName: "Agentic Asset",
        minWidth: 160,
        cellRenderer: AssetCellRenderer,
    },
    {
        field: "action",
        headerName: "Actions",
        minWidth: 110,
        filter: "agSetColumnFilter",
        cellRenderer: ActionCellRenderer,
    },
    {
        field: "policyName",
        headerName: "Policy Triggered",
        minWidth: 160,
        filter: "agSetColumnFilter",
    },
    {
        field: "_status",
        headerName: "Status",
        minWidth: 110,
        filter: "agSetColumnFilter",
        cellRenderer: StatusCellRenderer,
    },
];

const AUTO_SIZE_STRATEGY = { type: "fitCellContents" };

// ─── Data helpers ────────────────────────────────────────────────────────────────

const SEVERITY_COLORS = {
    CRITICAL: "#DF2909",
    HIGH: "#FED3D1",
    MEDIUM: "#FFD79D",
    LOW: "#E4E5E7",
};

const STATUS_COLORS = {
    OPEN: "#9642FC",
    FIXED: "#5BC0DE",
    IGNORED: "#F5C451",
};

const TYPE_COLORS = {
    Prompt: "#5BC0DE",
    Skill: "#C4CDD5",
    Config: "#F5C451",
    Tool: "#A4E8C4",
    "Tool Call": "#A4E8C4",
    LLM: "#F4A09C",
    Other: "#E4E5E7",
};

function parseMetadata(raw) {
    if (!raw) return {};
    if (typeof raw === "object") return raw;
    try { return JSON.parse(raw); } catch {}
    // Fallback: parse YAML-like format — key: "value" one per line
    const result = {};
    for (const line of raw.split('\n')) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        const colonIdx = trimmed.indexOf(':');
        if (colonIdx < 1) continue;
        const key = trimmed.slice(0, colonIdx).trim();
        let val = trimmed.slice(colonIdx + 1).trim();
        if (val.startsWith('"') && val.endsWith('"')) val = val.slice(1, -1).replace(/\\"/g, '"');
        result[key] = val;
    }
    return result;
}

function parseAktoPayload(payloadStr) {
    if (!payloadStr) return {};
    try {
        const outer = JSON.parse(payloadStr);
        const safeJson = s => { try { return JSON.parse(s); } catch { return null; } };
        // Support both camelCase (Akto format) and snake_case (session-manager format)
        const reqStr = outer.requestPayload || outer.request_body;
        const respStr = outer.responsePayload || outer.response_body;
        const req = reqStr ? safeJson(reqStr) : null;
        const resp = respStr ? safeJson(respStr) : null;
        return { req, resp, raw: outer };
    } catch { return {}; }
}

function deriveAgenticType(url, method) {
    const lower = (url || "").toLowerCase();
    // match by URL path segments (no leading slash required)
    if (lower.includes("tool"))                                        return "Tool";
    if (lower.includes("skill"))                                       return "Skill";
    if (lower.includes("resource"))                                    return "Resource";
    if (lower.includes("prompt"))                                      return "Prompt";
    if (lower.includes("config") || lower.includes("setting"))        return "Config";
    if (lower.includes("mcp") || lower.includes("server"))            return "Tool";
    // LLM-style API endpoints → Prompt
    if (lower.includes("message") || lower.includes("completion") || lower.includes("chat")) return "Prompt";
    // fall back to HTTP method, then default to Prompt for unknown agentic violations
    const m = method ? String(method).toUpperCase() : null;
    return METHOD_TO_TYPE[m] || "Prompt";
}

function transformEvent(event, policiesMap, collectionsMap, usernameMap) {
    const meta = parseMetadata(event.metadata);
    const typeLabel = deriveAgenticType(event.url, event.method);

    // Extract behaviour from response payload then metadata; default to "Flagged"
    const { req: reqPayload, resp: respPayload } = parseAktoPayload(event.payload);
    const rawBehaviour = respPayload?.error?.data?.behaviour || meta.behaviour || meta.nbehaviour || null;
    const action = rawBehaviour === "block" ? "Blocked"
        : (rawBehaviour === "warn" || rawBehaviour === "flag") ? "Flagged"
        : rawBehaviour ? func.toSentenceCase(rawBehaviour)
        : "Flagged";

    const rawHost = event.host || event.actor || null;
    const resolvedUser = getUsernameForCollection({ displayName: rawHost }, usernameMap || {});
    const userDisplay = (resolvedUser && resolvedUser !== "-") ? resolvedUser : (rawHost ? rawHost.split('.')[0] : "-");

    const rawAsset = collectionsMap?.[event.apiCollectionId] || meta.agenticAsset || meta.agentName || event.host || null;
    const agenticAssetTag = rawAsset ? getAssetServiceName(rawAsset) : null;

    // Prompt & Tool -> requestPayload.body; Skill -> responsePayload.evidence; Config
    // (and anything else) -> requestPayload.evidence — same mapping as the flyout.
    const isPromptOrTool = typeLabel === "Prompt" || typeLabel === "Tool";
    const primaryValue = sanitizeDisplayText(coerceToText(isPromptOrTool
        ? (reqPayload?.body || null)
        : typeLabel === "Skill" ? (respPayload?.evidence || null) : (reqPayload?.evidence || null)), 300);

    return {
        id: event.id,
        apiCollectionId: event.apiCollectionId,
        detected: event.timestamp,
        type: typeLabel,
        violation: meta.rule_violated || meta.nrule_violated || meta.nruleViolated || event.subCategory || event.filterId || "-",
        severity: (event.severity || "HIGH").toUpperCase(),
        evidenceText: primaryValue || normalizeReasonPunctuation(meta.reason) || "-",
        user: userDisplay,
        userHost: rawHost,
        agenticAsset: formatAssetDisplayName(rawAsset),
        agenticAssetRaw: rawAsset,
        agenticAssetTag,
        action,
        policyName: meta.policy_name || meta.npolicy_name || policiesMap[event.filterId] || event.filterId || "-",
        _status: event.status || "ACTIVE",
        payload: event.payload || null,
        metadata: event.metadata || null,
        sessionId: event.sessionId || null,
        deviceId: rawHost,
    };
}

function computeSummary(rows) {
    const total = rows.length;

    // severity breakdown
    const sevCounts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
    rows.forEach(r => { if (sevCounts[r.severity] !== undefined) sevCounts[r.severity]++; });
    const totalBreakdown = ["CRITICAL", "HIGH", "MEDIUM", "LOW"].map(k => ({
        label: k.charAt(0) + k.slice(1).toLowerCase(),
        count: sevCounts[k],
        color: SEVERITY_COLORS[k],
        key: k,
    }));

    // open count
    const statusCounts = { ACTIVE: 0, IGNORED: 0, FIXED: 0 };
    rows.forEach(r => { const s = r._status?.toUpperCase(); if (s in statusCounts) statusCounts[s]++; });

    const openBreakdown = [
        { label: "Open",    count: statusCounts.ACTIVE,  color: STATUS_COLORS.OPEN,    key: "OPEN" },
        { label: "Fixed",   count: statusCounts.FIXED,   color: STATUS_COLORS.FIXED,   key: "FIXED" },
        { label: "Ignored", count: statusCounts.IGNORED, color: STATUS_COLORS.IGNORED, key: "IGNORED" },
    ];

    // top users
    const userMap = {};
    rows.forEach(r => {
        if (!r.user || r.user === "-") return;
        if (!userMap[r.user]) userMap[r.user] = { count: 0, host: r.userHost };
        userMap[r.user].count += 1;
    });
    const topUsers = Object.entries(userMap)
        .sort((a, b) => b[1].count - a[1].count)
        .slice(0, 5)
        .map(([name, data], i) => ({
            id: `u${i}`, name, count: data.count, os: detectOs(data.host),
            sparkline: [0, 0, 0, 0, 0, 0, data.count],
        }));

    // top policies
    const policyMap = {};
    rows.forEach(r => { if (r.policyName && r.policyName !== "-") policyMap[r.policyName] = (policyMap[r.policyName] || 0) + 1; });
    const topPolicies = Object.entries(policyMap)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([name, count], i) => ({
            id: `p${i}`, name, count,
            sparkline: [0, 0, 0, 0, 0, 0, count],
        }));

    // violations by type
    const typeMap = {};
    rows.forEach(r => { if (r.type) typeMap[r.type] = (typeMap[r.type] || 0) + 1; });
    const byType = {};
    Object.entries(typeMap).forEach(([t, c]) => {
        byType[t] = { text: c, color: TYPE_COLORS[t] || "#999", filterKey: t };
    });

    return {
        totalSummary: { total, delta: 0, sparkline: [0, 0, 0, 0, 0, 0, total], breakdown: totalBreakdown },
        openSummary: { total: statusCounts.ACTIVE, delta: 0, sparkline: [0, 0, 0, 0, 0, 0, statusCounts.ACTIVE], breakdown: openBreakdown },
        topUsers,
        topPolicies,
        byType,
    };
}

// ─── Leaderboard rows → AgenticTopListCard format ────────────────────────────────

function buildTopListRows(items) {
    return items.map((item) => ({
        id: item.id,
        name: item.name,
        type: item.type,
        os: item.os,
        renderValue: () => (
            <HorizontalStack gap="3" blockAlign="center" align="end" wrap={false}>
                <Text variant="bodyMd">{item.count.toLocaleString("en-US")}</Text>
                <SmoothAreaChart tickPositions={item.sparkline} color="#EF4444" height={28} width={90} labels={SPARKLINE_LABELS} enableHover />
            </HorizontalStack>
        ),
    }));
}

// ─── Dashboard summary section ───────────────────────────────────────────────────

function ViolationsDashboard({ totalSummary, openSummary, topUsers, topPolicies, byType, severityFilter, onSeverityFilter, statusFilter, onStatusFilter, policyFilter, onPolicyFilter, onClearPolicyFilter, userFilter, onUserFilter, onClearUserFilter, typeFilter, onTypeFilter, onClearTypeFilter }) {
    const policyRows = buildTopListRows(topPolicies).map((row) => ({
        ...row,
        onClick: (r) => onPolicyFilter?.(r.name),
    }));

    const userRows = buildTopListRows(topUsers).map((row) => ({
        ...row,
        onClick: (r) => onUserFilter?.(r.name),
    }));

    return (
        <VerticalStack gap="4">
            <HorizontalGrid columns={2} gap="4">
                <AgenticStatsCard
                    title="Total Violations"
                    total={totalSummary.total}
                    delta={totalSummary.delta}
                    deltaColor="subdued"
                    sparklineCounts={totalSummary.sparkline}
                    sparklineColor="#EF4444"
                    sparklineLabels={SPARKLINE_LABELS}
                    breakdown={totalSummary.breakdown}
                    onFilterClick={onSeverityFilter}
                    activeFilter={severityFilter}
                />
                <AgenticStatsCard
                    title="Open Violations"
                    total={openSummary.total}
                    delta={openSummary.delta}
                    deltaColor="subdued"
                    sparklineCounts={openSummary.sparkline}
                    sparklineColor="#EF4444"
                    sparklineLabels={SPARKLINE_LABELS}
                    breakdown={openSummary.breakdown}
                    onFilterClick={onStatusFilter}
                    activeFilter={statusFilter}
                />
            </HorizontalGrid>

            <HorizontalGrid columns={3} gap="4">
                <AgenticTopListCard
                    title="Violations by Top Users"
                    columns={[{ label: "User" }, { label: "Violations" }]}
                    rows={userRows}
                    renderIcon={(row) => <OsIcon os={row.os} size={20} />}
                    activeRows={userFilter}
                    onClearSelection={onClearUserFilter}
                />
                <AgenticTopListCard
                    title="Top Policies Triggered"
                    columns={[{ label: "Policy" }, { label: "Count" }]}
                    rows={policyRows}
                    renderIcon={() => null}
                    activeRows={policyFilter}
                    onClearSelection={onClearPolicyFilter}
                />
                <Card padding="0">
                    <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="headingSm">Violations by Type</Text>
                            {(typeFilter?.size ?? 0) > 0 && (
                                <Button plain onClick={onClearTypeFilter}>Clear selection</Button>
                            )}
                        </HorizontalStack>
                    </Box>
                    <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
                        <VerticalStack gap="2">
                            <HorizontalStack align="center">
                                <DonutChart
                                    data={byType}
                                    title={totalSummary.total}
                                    subtitle="Violations"
                                    size={180}
                                    pieInnerSize="55%"
                                />
                            </HorizontalStack>
                            {Object.keys(byType).length > 0 && (
                                <HorizontalStack gap="2" wrap align="center">
                                    {Object.entries(byType).map(([label, seg]) => {
                                        const active = typeFilter?.has(seg.filterKey ?? label);
                                        return (
                                            <Box
                                                key={label}
                                                onClick={() => onTypeFilter?.(seg.filterKey ?? label)}
                                                className={active ? "agentic-chip agentic-chip--active" : "agentic-chip"}
                                            >
                                                <HorizontalStack gap="1" blockAlign="center">
                                                    <Box className="agentic-dot" style={{ "--dot-color": seg.color }} />
                                                    <Text variant="bodySm" color="subdued">{label} ({seg.text})</Text>
                                                </HorizontalStack>
                                            </Box>
                                        );
                                    })}
                                </HorizontalStack>
                            )}
                        </VerticalStack>
                    </Box>
                </Card>
            </HorizontalGrid>
        </VerticalStack>
    );
}

// ─── Page ────────────────────────────────────────────────────────────────────────

function Violations() {
    const navigate = useNavigate();
    const newLayout = LocalStore((state) => state.guardrailViolationsNewLayout);
    const setGuardrailViolationsNewLayout = LocalStore((state) => state.setGuardrailViolationsNewLayout);

    const legacyPath = isEndpointSecurityCategory() ? "/dashboard/protection/threat-activity" : "/dashboard/guardrails/activity";
    // New layout is available to demo accounts plus this specific account; everyone else stays on the legacy page.
    const canUseNewLayout = func.isDemoAccount() || window.ACTIVE_ACCOUNT === 1726615470;

    useEffect(() => {
        if (!canUseNewLayout || !newLayout) {
            navigate(legacyPath, { replace: true });
        }
    }, [navigate, legacyPath, canUseNewLayout, newLayout]);

    const handleLayoutToggle = useCallback((checked) => {
        setGuardrailViolationsNewLayout(checked);
        if (!checked) navigate(legacyPath);
    }, [navigate, setGuardrailViolationsNewLayout, legacyPath]);

    const [rows, setRows] = useState([]);
    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(false);
    const [selectedViolation, setSelectedViolation] = useState(null);
    const [bulkSelectedCount, setBulkSelectedCount] = useState(0);
    const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
    const gridRef = useRef(null);
    const prevSelectedIdRef = useRef(null);
    const [severityFilter, setSeverityFilter] = useState(new Set());
    const [statusFilter, setStatusFilter] = useState(new Set());
    const [policyFilter, setPolicyFilter] = useState(new Set());
    const [userFilter, setUserFilter] = useState(new Set());
    const [typeFilter, setTypeFilter] = useState(new Set());
    const collectionsMap = PersistStore((state) => state.collectionsMap);

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[4],
    );

    useEffect(() => {
        async function load() {
            setLoading(true);
            try {
                const startTimestamp = currDateRange?.period?.since
                    ? Math.floor(Date.parse(currDateRange.period.since) / 1000)
                    : null;
                const endTimestamp = currDateRange?.period?.until
                    ? Math.floor(Date.parse(currDateRange.period.until) / 1000)
                    : null;

                const PAGE_SIZE = 1000;

                const [firstResp, policiesResp, usernameMap] = await Promise.all([
                    guardrailsApi.fetchViolations(startTimestamp, endTimestamp, 0, PAGE_SIZE),
                    guardrailsApi.fetchGuardrailPolicies(),
                    fetchEndpointShieldUsernameMap(),
                ]);

                // Build filterId → name map from policies
                const policiesMap = {};
                (policiesResp?.guardrailPolicies || []).forEach(p => {
                    if (p.hexId) policiesMap[p.hexId] = p.name;
                });

                let events = firstResp?.maliciousEvents || [];
                const total = firstResp?.total ?? events.length;
                if (total > PAGE_SIZE) {
                    const extraPages = Math.ceil((total - PAGE_SIZE) / PAGE_SIZE);
                    const rest = await Promise.all(
                        Array.from({ length: extraPages }, (_, i) =>
                            guardrailsApi.fetchViolations(startTimestamp, endTimestamp, (i + 1) * PAGE_SIZE, PAGE_SIZE)
                        )
                    );
                    events = [...events, ...rest.flatMap(r => r?.maliciousEvents || [])];
                }
                const transformed = events.map(e => transformEvent(e, policiesMap, collectionsMap, usernameMap));
                setRows(transformed);
                setSummary(computeSummary(transformed));
            } catch (e) {
                setRows([]);
                setSummary(computeSummary([]));
            } finally {
                setLoading(false);
            }
        }
        load();
    }, [currDateRange]);

    const handleSeverityFilter = useCallback((key) => {
        setSeverityFilter((prev) => {
            const next = new Set(prev);
            next.has(key) ? next.delete(key) : next.add(key);
            return next;
        });
    }, []);

    const handleStatusFilter = useCallback((key) => {
        setStatusFilter((prev) => {
            const next = new Set(prev);
            next.has(key) ? next.delete(key) : next.add(key);
            return next;
        });
    }, []);

    const handlePolicyFilter = useCallback((name) => {
        setPolicyFilter((prev) => {
            const next = new Set(prev);
            next.has(name) ? next.delete(name) : next.add(name);
            return next;
        });
    }, []);

    const handleUserFilter = useCallback((name) => {
        setUserFilter((prev) => {
            const next = new Set(prev);
            next.has(name) ? next.delete(name) : next.add(name);
            return next;
        });
    }, []);

    const handleTypeFilter = useCallback((key) => {
        setTypeFilter((prev) => {
            const next = new Set(prev);
            next.has(key) ? next.delete(key) : next.add(key);
            return next;
        });
    }, []);

    const handleClearUserFilter = useCallback(() => setUserFilter(new Set()), []);
    const handleClearPolicyFilter = useCallback(() => setPolicyFilter(new Set()), []);
    const handleClearTypeFilter = useCallback(() => setTypeFilter(new Set()), []);

    // ─── Bulk actions ──────────────────────────────────────────────────────────
    const getSelectedIds = useCallback(() => (
        (gridRef.current?.api?.getSelectedRows() || []).map(r => r.id).filter(Boolean)
    ), []);

    const clearBulkSelection = useCallback(() => {
        gridRef.current?.api?.deselectAll();
        setBulkSelectedCount(0);
    }, []);

    const handleBulkStatusUpdate = useCallback(async (status, pastTenseLabel) => {
        const ids = getSelectedIds();
        if (!ids.length) return;
        try {
            const response = await threatDetectionApi.updateMaliciousEventStatus({ eventIds: ids, status });
            if (response?.updateSuccess) {
                func.setToast(true, false, `${ids.length} event${ids.length === 1 ? "" : "s"} ${pastTenseLabel} successfully`);
                setRows(prev => prev.map(r => ids.includes(r.id) ? { ...r, _status: status } : r));
                clearBulkSelection();
            } else {
                func.setToast(true, true, "Failed to update selected events");
            }
        } catch {
            func.setToast(true, true, "Failed to update selected events");
        }
    }, [getSelectedIds, clearBulkSelection]);

    const handleBulkDelete = useCallback(async () => {
        const ids = getSelectedIds();
        setDeleteConfirmOpen(false);
        if (!ids.length) return;
        try {
            const response = await threatDetectionApi.deleteMaliciousEvents({ eventIds: ids });
            if (response?.deleteSuccess) {
                func.setToast(true, false, `${ids.length} event${ids.length === 1 ? "" : "s"} deleted successfully`);
                setRows(prev => prev.filter(r => !ids.includes(r.id)));
                clearBulkSelection();
            } else {
                func.setToast(true, true, "Failed to delete selected events");
            }
        } catch {
            func.setToast(true, true, "Failed to delete selected events");
        }
    }, [getSelectedIds, clearBulkSelection]);

    const bulkActions = useMemo(() => [
        { label: "Mark for Review", onAction: () => handleBulkStatusUpdate("UNDER_REVIEW", "marked for review") },
        { label: "Ignore", onAction: () => handleBulkStatusUpdate("IGNORED", "ignored") },
        { label: "Delete", destructive: true, onAction: () => setDeleteConfirmOpen(true) },
    ], [handleBulkStatusUpdate]);

    const STATUS_KEY_TO_ROW = { OPEN: "ACTIVE", FIXED: "FIXED", IGNORED: "IGNORED" };
    // Memoized so selecting a row (which only updates selectedViolation) doesn't produce a
    // new array reference each render — AG Grid treats a new rowData reference as fresh data
    // and resets scroll to the top, which is why opening the flyout used to jump the table.
    const filteredRows = useMemo(() => rows.filter((r) => {
        if (severityFilter.size > 0 && !severityFilter.has(r.severity)) return false;
        if (statusFilter.size > 0) {
            const rowStatus = [...statusFilter].map(k => STATUS_KEY_TO_ROW[k] || k);
            if (!rowStatus.includes(r._status)) return false;
        }
        if (policyFilter.size > 0 && !policyFilter.has(r.policyName)) return false;
        if (userFilter.size > 0 && !userFilter.has(r.user)) return false;
        if (typeFilter.size > 0 && !typeFilter.has(r.type)) return false;
        return true;
    }), [rows, severityFilter, statusFilter, policyFilter, userFilter, typeFilter]);

    const handleRowClick = (e) => {
        if (e?.data) setSelectedViolation(e.data);
    };

    useEffect(() => {
        const api = gridRef.current?.api;
        if (!api) return;
        // Redraw only the previously-selected and newly-selected rows so the
        // grid viewport doesn't jump (full redrawRows() resets scroll position).
        const ids = new Set([prevSelectedIdRef.current, selectedViolation?.id].filter(Boolean));
        if (ids.size > 0) {
            const nodes = [];
            api.forEachNode(n => { if (ids.has(n.data?.id)) nodes.push(n); });
            if (nodes.length) api.redrawRows({ rowNodes: nodes });
        }
        prevSelectedIdRef.current = selectedViolation?.id ?? null;
    }, [selectedViolation]);

    const getRowClass = useCallback((params) => {
        return params.data?.id === selectedViolation?.id ? "violations-row-selected" : undefined;
    }, [selectedViolation]);

    const emptyState = !loading && rows.length === 0 && (
        <Box padding="8">
            <HorizontalStack align="center">
                <Text color="subdued">No violations found for the selected time range.</Text>
            </HorizontalStack>
        </Box>
    );

    const tableComponent = (
        <Box key="table" className="violations-table-wrap">
            {emptyState || (
                <AgGridTable
                    rowData={filteredRows}
                    columnDefs={COL_DEFS}
                    defaultColDef={DEFAULT_COL_DEF}
                    autoSizeStrategy={AUTO_SIZE_STRATEGY}
                    searchPlaceholder="Search violations"
                    onRowClicked={handleRowClick}
                    suppressRowClickSelection
                    getRowStyle={() => ({ cursor: "pointer" })}
                    getRowClass={getRowClass}
                    gridRef={gridRef}
                    rowSelection="multiple"
                    onSelectionChanged={e => setBulkSelectedCount(e.api.getSelectedRows().length)}
                    bulkActionCount={bulkSelectedCount}
                    bulkActions={bulkActions}
                    onClearBulk={clearBulkSelection}
                    pagination
                    paginationPageSize={100}
                    paginationPageSizeSelector={[20, 50, 100]}
                    height={500}
                    domLayout="normal"
                />
            )}
        </Box>
    );

    const defaultSummary = computeSummary([]);
    const activeSummary = summary || defaultSummary;

    const components = loading
        ? [<SpinnerCentered key="loading" />]
        : [
            <ViolationsDashboard
                key="dashboard"
                totalSummary={activeSummary.totalSummary}
                openSummary={activeSummary.openSummary}
                topUsers={activeSummary.topUsers}
                topPolicies={activeSummary.topPolicies}
                byType={activeSummary.byType}
                severityFilter={severityFilter}
                onSeverityFilter={handleSeverityFilter}
                statusFilter={statusFilter}
                onStatusFilter={handleStatusFilter}
                policyFilter={policyFilter}
                onPolicyFilter={handlePolicyFilter}
                onClearPolicyFilter={handleClearPolicyFilter}
                userFilter={userFilter}
                onUserFilter={handleUserFilter}
                onClearUserFilter={handleClearUserFilter}
                typeFilter={typeFilter}
                onTypeFilter={handleTypeFilter}
                onClearTypeFilter={handleClearTypeFilter}
            />,
            tableComponent,
            <ViolationFlyout
                key="flyout"
                violation={selectedViolation}
                show={selectedViolation !== null}
                onClose={() => setSelectedViolation(null)}
                allRows={rows}
            />,
            <Modal
                key="delete-confirm"
                open={deleteConfirmOpen}
                onClose={() => setDeleteConfirmOpen(false)}
                title="Delete selected events"
                primaryAction={{ content: "Delete", destructive: true, onAction: handleBulkDelete }}
                secondaryActions={[{ content: "Cancel", onAction: () => setDeleteConfirmOpen(false) }]}
            >
                <Modal.Section>
                    <Text variant="bodyMd" color="subdued">
                        {`This will permanently delete ${bulkSelectedCount} selected event${bulkSelectedCount === 1 ? "" : "s"}. This action cannot be undone.`}
                    </Text>
                </Modal.Section>
            </Modal>,
        ];

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText="Violations"
                    tooltipContent="A real-time log of every guardrail trigger across your agentic environment. Blocked prompts, suspicious tool calls, policy breaches, and more. Use this page to investigate incidents, understand what was sent, and take action."
                />
            }
            isFirstPage
            secondaryActions={canUseNewLayout && <NewLayoutTooltip checked={newLayout} onChange={handleLayoutToggle} />}
            primaryAction={
                <DateRangeFilter
                    initialDispatch={currDateRange}
                    dispatch={(dateObj) =>
                        dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
                    }
                />
            }
            components={components}
        />
    );
}

export default Violations;

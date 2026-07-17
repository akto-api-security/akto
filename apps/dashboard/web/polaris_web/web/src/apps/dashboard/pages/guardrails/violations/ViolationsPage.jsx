import React, { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import {
    Badge,
    Box,
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
import InfoTooltipIcon from "@/apps/dashboard/components/shared/InfoTooltipIcon";
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
import P95LatencyGraph from "@/apps/dashboard/components/charts/P95LatencyGraph";
import threatDetectionApi from "@/apps/dashboard/pages/threat_detection/api";
import { getDashboardCategory, mapLabel } from "@/apps/main/labelHelper";
import ViolationFlyout from "./ViolationFlyout";
import { normalizeReasonPunctuation, coerceToText, sanitizeDisplayText } from "./violationsData";

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

function getAssetServiceName(raw) {
    if (!raw) return null;
    const hex = raw.match(/^[0-9a-f]{32}\.(.+)$/i);
    if (hex) return hex[1].split('.')[0];
    const parts = raw.split('.');
    if (parts.length >= 3) {
        const svc = extractServiceName(raw);
        return (svc && svc !== raw) ? svc : parts[parts.length - 1];
    }
    if (parts.length === 2) return parts[1];
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

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: false,
    cellStyle: { display: "flex", alignItems: "center" },
};

// Column defs are built dynamically so we can inject backend filter values.
function buildColDefs(filterValues) {
    return [
        {
            field: "detected",
            headerName: "Detected",
            minWidth: 150,
            valueFormatter: p => p.value != null ? func.epochToDateTime(p.value) : "",
        },
        {
            field: "type",
            headerName: "Type",
            minWidth: 100,
            sortable: false,
            cellRenderer: TypeCellRenderer,
        },
        {
            field: "evidenceText",
            headerName: "Evidence",
            width: 200,
            minWidth: 200,
            suppressAutoSize: true,
            sortable: false,
            cellRenderer: EvidenceCellRenderer,
        },
        {
            field: "severity",
            headerName: "Severity",
            minWidth: 110,
            filter: "agSetColumnFilter",
            filterParams: { values: ["CRITICAL", "HIGH", "MEDIUM", "LOW"] },
            cellRenderer: SeverityCellRenderer,
        },
        {
            field: "user",
            headerName: "User",
            minWidth: 140,
            filter: "agSetColumnFilter",
            filterParams: { values: filterValues.hosts || [] },
            cellRenderer: UserCellRenderer,
        },
        {
            field: "agenticAsset",
            headerName: "Agentic Asset",
            minWidth: 160,
            sortable: false,
            cellRenderer: AssetCellRenderer,
        },
        {
            field: "action",
            headerName: "Actions",
            minWidth: 110,
            sortable: false,
            cellRenderer: ActionCellRenderer,
        },
        {
            field: "policyName",
            headerName: "Policy Triggered",
            minWidth: 160,
            filter: "agSetColumnFilter",
            filterParams: { values: filterValues.subCategory || [] },
        },
        {
            field: "_status",
            headerName: "Status",
            minWidth: 110,
            sortable: false,
            cellRenderer: StatusCellRenderer,
        },
    ];
}

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
    UNDER_REVIEW: "#637381",
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
        const reqStr = outer.requestPayload || outer.request_body;
        const respStr = outer.responsePayload || outer.response_body;
        const req = reqStr ? safeJson(reqStr) : null;
        const resp = respStr ? safeJson(respStr) : null;
        return { req, resp, raw: outer };
    } catch { return {}; }
}

function deriveSkillOrToolName(url) {
    if (!url) return null;
    const skillMatch = url.match(/\/skills\/([^/?#]+)/i);
    if (skillMatch) return skillMatch[1];
    const toolMatch = url.match(/\/tools\/([^/?#]+)/i);
    if (toolMatch) return toolMatch[1];
    const mcpToolMatch = url.match(/\/mcp\/([^/?#]+)/i);
    if (mcpToolMatch) return mcpToolMatch[1];
    return null;
}

function deriveAgenticType(url, method) {
    const lower = (url || "").toLowerCase();
    if (lower.includes("tool"))                                        return "Tool";
    if (lower.includes("skill"))                                       return "Skill";
    if (lower.includes("resource"))                                    return "Resource";
    if (lower.includes("prompt"))                                      return "Prompt";
    if (lower.includes("config") || lower.includes("setting"))        return "Config";
    if (lower.includes("mcp") || lower.includes("server"))            return "Tool";
    if (lower.includes("message") || lower.includes("completion") || lower.includes("chat")) return "Prompt";
    const m = method ? String(method).toUpperCase() : null;
    return METHOD_TO_TYPE[m] || "Prompt";
}

// Transform a single backend event into a table row.
// Kept lightweight — runs only on the current page of results (not all data).
function transformEvent(event, collectionsMap, usernameMap) {
    const meta = parseMetadata(event.metadata);
    const typeLabel = deriveAgenticType(event.url, event.method);

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
    const skillOrToolName = deriveSkillOrToolName(event.url);

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
        agenticAsset: skillOrToolName || formatAssetDisplayName(rawAsset),
        agenticAssetRaw: rawAsset,
        agenticAssetTag: skillOrToolName ? (typeLabel === "Skill" ? "skill" : typeLabel === "Tool" ? "tool" : agenticAssetTag) : agenticAssetTag,
        action,
        policyName: meta.policy_name || meta.npolicy_name || event.filterId || "-",
        _status: event.status || "ACTIVE",
        payload: event.payload || null,
        metadata: event.metadata || null,
        sessionId: event.sessionId || null,
        deviceId: rawHost,
    };
}

// ─── Dashboard summary section ───────────────────────────────────────────────────

function ViolationsDashboard({ summaryData, loading: summaryLoading, onSeverityClick, activeSeverityFilter, onPolicyClick, activePolicyFilter, onClearPolicySelection, onHostClick, activeHostFilter, onClearHostSelection, onTypeClick, activeTypeFilter, selectedCard, onOpenCardClick, onOtherCardClick, onOtherBreakdownClick, activeStatusValue, latencyData, startTimestamp, endTimestamp }) {
    if (summaryLoading) return <SpinnerCentered />;
    if (!summaryData) return null;

    const { severityDistribution, categoryTotal, statusCounts, topPolicies, topHosts, byType } = summaryData;

    const totalBreakdown = ["CRITICAL", "HIGH", "MEDIUM", "LOW"].map(k => ({
        label: k.charAt(0) + k.slice(1).toLowerCase(),
        count: severityDistribution[k] || 0,
        color: SEVERITY_COLORS[k],
        key: k,
    }));

    const otherBreakdown = [
        { label: "Under Review", count: statusCounts.UNDER_REVIEW || 0, color: STATUS_COLORS.UNDER_REVIEW || "#F5A623", key: "UNDER_REVIEW" },
        { label: "Ignored",      count: statusCounts.IGNORED || 0,      color: STATUS_COLORS.IGNORED, key: "IGNORED" },
    ];

    const policyRows = topPolicies.map((item, i) => ({
        id: `p${i}`,
        name: item.name,
        count: item.count,
        onClick: () => onPolicyClick?.(item.name),
        renderValue: () => <Text variant="bodyMd">{item.count.toLocaleString("en-US")}</Text>,
    }));

    const hostRows = (topHosts || []).slice(0, 5).map((item, i) => ({
        id: `h${i}`,
        name: item.name,
        count: item.count,
        os: detectOs(item.host),
        onClick: () => onHostClick?.(item.host),
        renderValue: () => <Text variant="bodyMd">{item.count.toLocaleString("en-US")}</Text>,
    }));

    // Latency graph is Akto-internal only (matches the same gate on ThreatDetectionPage.jsx).
    // Everyone else gets Open/Other Violations side by side instead of stacked, since there's
    // no third column to fill the space next to them.
    // TEMP: forced off to preview the non-Akto layout — revert before shipping.
    // const isAktoUser = window.USER_NAME?.includes('@akto.io');
    const isAktoUser = false;

    const openCard = (
        <Box
            className="violations-card-wrap"
            style={selectedCard === "open" ? { outline: "1px solid var(--p-color-border-critical)" } : undefined}
            onClick={onOpenCardClick}
        >
            <AgenticStatsCard
                title="Open Violations"
                titleTooltip="Active violations that need attention, broken down by severity. Click a severity to filter the table below."
                total={statusCounts.ACTIVE || 0}
                delta={0}
                deltaColor="subdued"
                breakdown={totalBreakdown}
                onFilterClick={onSeverityClick}
                activeFilter={activeSeverityFilter}
                bodyGap="4"
            />
        </Box>
    );

    const otherCard = (
        <Box
            className="violations-card-wrap"
            style={selectedCard === "other" ? { outline: "1px solid var(--p-color-border-critical)" } : undefined}
            onClick={onOtherCardClick}
        >
            <AgenticStatsCard
                title="Other Violations"
                titleTooltip="Violations that are under review or ignored. Click a status to filter the table."
                total={(statusCounts.UNDER_REVIEW || 0) + (statusCounts.IGNORED || 0)}
                delta={0}
                deltaColor="subdued"
                breakdown={otherBreakdown}
                onFilterClick={onOtherBreakdownClick}
                activeFilter={selectedCard === "other" ? new Set([activeStatusValue]) : undefined}
                bodyGap="4"
            />
        </Box>
    );

    return (
        <VerticalStack gap="4">
            {isAktoUser ? (
                <HorizontalGrid columns={2} gap="4" alignItems="start">
                    <VerticalStack gap="4">
                        {openCard}
                        {otherCard}
                    </VerticalStack>
                    <P95LatencyGraph
                        title={`${mapLabel("Guardrail", getDashboardCategory())} Detection Latency`}
                        subtitle="95th percentile latency metrics for guardrail detection"
                        dataType="threat-security"
                        startTimestamp={startTimestamp}
                        endTimestamp={endTimestamp}
                        latencyData={latencyData}
                        height={230}
                    />
                </HorizontalGrid>
            ) : (
                <HorizontalGrid columns={2} gap="4" alignItems="center">
                    {openCard}
                    {otherCard}
                </HorizontalGrid>
            )}

            <HorizontalGrid columns={3} gap="4">
                <AgenticTopListCard
                    title="Violations by Top Users"
                    titleTooltip="Top 5 users by number of violations. Click a user to filter the table below."
                    columns={[{ label: "User" }, { label: "Violations" }]}
                    rows={hostRows}
                    renderIcon={(row) => <OsIcon os={row.os} size={20} />}
                    activeRows={activeHostFilter}
                    onClearSelection={onClearHostSelection}
                />
                <AgenticTopListCard
                    title="Top Policies Triggered"
                    titleTooltip="Top 5 guardrail policies by number of active violations. Click a policy to filter the table below."
                    columns={[{ label: "Policy" }, { label: "Count" }]}
                    rows={policyRows}
                    renderIcon={() => null}
                    activeRows={activePolicyFilter}
                    onClearSelection={onClearPolicySelection}
                />
                <Card padding="0">
                    <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
                        <HorizontalStack gap="1" blockAlign="center">
                            <Text variant="headingSm">Violations by Type</Text>
                            <InfoTooltipIcon content="Active violations grouped by type. Click a segment or label to filter the table below." />
                        </HorizontalStack>
                    </Box>
                    <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockEnd="4">
                        <VerticalStack gap="2">
                            <HorizontalStack align="center">
                                <DonutChart
                                    data={byType}
                                    title={categoryTotal}
                                    subtitle="Violations"
                                    size={180}
                                    pieInnerSize="55%"
                                    onSegmentClick={onTypeClick}
                                />
                            </HorizontalStack>
                            {Object.keys(byType).length > 0 && (
                                <HorizontalStack gap="2" wrap align="center">
                                    {Object.entries(byType).map(([label, seg]) => (
                                        <Box
                                            key={label}
                                            className="agentic-chip"
                                            style={activeTypeFilter === label ? { borderColor: "var(--p-color-border)", background: "var(--p-color-bg-subdued)" } : undefined}
                                            onClick={() => onTypeClick?.(label)}
                                        >
                                            <HorizontalStack gap="1" blockAlign="center">
                                                <Box className="agentic-dot" style={{ "--dot-color": seg.color }} />
                                                <Text variant="bodySm" color={activeTypeFilter === label ? undefined : "subdued"} fontWeight={activeTypeFilter === label ? "semibold" : undefined}>{label} ({seg.text})</Text>
                                            </HorizontalStack>
                                        </Box>
                                    ))}
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
    const [summaryData, setSummaryData] = useState(null);
    const [summaryLoading, setSummaryLoading] = useState(true);
    const [selectedViolation, setSelectedViolation] = useState(null);
    const [bulkSelectedCount, setBulkSelectedCount] = useState(0);
    const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
    const [filterValues, setFilterValues] = useState({ hosts: [], subCategory: [] });
    const [latencyData, setLatencyData] = useState([]);
    const [activeSeverityFilter, setActiveSeverityFilter] = useState(new Set());
    const [activePolicyFilter, setActivePolicyFilter] = useState(new Set());
    const [activeTypeFilter, setActiveTypeFilter] = useState(null);
    const [selectedCard, setSelectedCard] = useState("open");          // "open" or "other"
    const [activeStatusValue, setActiveStatusValue] = useState("ACTIVE"); // drives backend statusFilter
    const gridRef = useRef(null);
    const prevSelectedIdRef = useRef(null);
    const gridFilterKey = useRef(`violations-${Date.now()}`);
    const collectionsMap = PersistStore((state) => state.collectionsMap);
    const usernameMapRef = useRef({});

    useEffect(() => {
        const key = gridFilterKey.current;
        const oldKey = window.location.pathname + "/ag-grid";
        const { filtersMap: fm, setFiltersMap: sfm } = PersistStore.getState();
        if (fm[oldKey]) {
            const cleaned = { ...fm };
            delete cleaned[oldKey];
            sfm(cleaned);
        }
        return () => {
            const { filtersMap, setFiltersMap } = PersistStore.getState();
            if (filtersMap[key]) {
                const next = { ...filtersMap };
                delete next[key];
                setFiltersMap(next);
            }
        };
    }, []);

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[4],
    );

    const getTimeEpoch = useCallback((key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    }, [currDateRange]);

    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    // ─── Load username map once ──────────────────────────────────────────────
    useEffect(() => {
        fetchEndpointShieldUsernameMap().then(map => {
            usernameMapRef.current = map;
        });
    }, []);

    // ─── Fetch filter values from backend ────────────────────────────────────
    useEffect(() => {
        threatDetectionApi.fetchFiltersThreatTable(startTimestamp, endTimestamp).then(res => {
            setFilterValues({
                hosts: (res?.hosts || []).filter(h => h && h.trim() !== '' && h !== '-'),
                subCategory: res?.subCategory || [],
            });
        });
    }, [startTimestamp, endTimestamp]);

    const colDefs = useMemo(() => buildColDefs(filterValues), [filterValues]);

    // ─── Card click → AG Grid column filter ─────────────────────────────────
    const applyGridFilter = useCallback((colId, values) => {
        const api = gridRef.current?.api;
        if (!api) return;
        const model = values.length > 0 ? { filterType: "set", values } : null;
        api.setColumnFilterModel(colId, model).then(() => api.onFilterChanged());
    }, []);

    const handleSeverityClick = useCallback((key) => {
        setActiveSeverityFilter(prev => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key); else next.add(key);
            applyGridFilter("severity", [...next]);
            return next;
        });
    }, [applyGridFilter]);

    const handlePolicyClick = useCallback((name) => {
        setActivePolicyFilter(prev => {
            const next = new Set(prev);
            if (next.has(name)) next.delete(name); else next.add(name);
            applyGridFilter("policyName", [...next]);
            return next;
        });
    }, [applyGridFilter]);

    const handleClearPolicySelection = useCallback(() => {
        setActivePolicyFilter(new Set());
        applyGridFilter("policyName", []);
    }, [applyGridFilter]);

    const [activeHostFilter, setActiveHostFilter] = useState(new Set());

    const handleHostClick = useCallback((host) => {
        setActiveHostFilter(prev => {
            const next = new Set(prev);
            if (next.has(host)) next.delete(host); else next.add(host);
            applyGridFilter("user", [...next]);
            return next;
        });
    }, [applyGridFilter]);

    const handleClearHostSelection = useCallback(() => {
        setActiveHostFilter(new Set());
        applyGridFilter("user", []);
    }, [applyGridFilter]);

    const handleTypeClick = useCallback((typeName) => {
        const mapping = summaryData?.typeToSubCategories || {};
        const subCategories = mapping[typeName] || [];
        if (activeTypeFilter === typeName) {
            setActiveTypeFilter(null);
            applyGridFilter("policyName", []);
        } else {
            setActiveTypeFilter(typeName);
            applyGridFilter("policyName", subCategories);
        }
    }, [applyGridFilter, summaryData, activeTypeFilter]);

    // ─── Card selection (Open vs Other) ─────────────────────────────────────
    const [tableKey, setTableKey] = useState(0);
    const triggerTableRefresh = useCallback(() => setTableKey(k => k + 1), []);

    const handleOpenCardClick = useCallback(() => {
        if (selectedCard === "open") return;
        setSelectedCard("open");
        setActiveStatusValue("ACTIVE");
        triggerTableRefresh();
    }, [selectedCard, triggerTableRefresh]);

    const handleOtherCardClick = useCallback(() => {
        if (selectedCard === "other") return;
        setSelectedCard("other");
        setActiveStatusValue("UNDER_REVIEW");
        triggerTableRefresh();
    }, [selectedCard, triggerTableRefresh]);

    const handleOtherBreakdownClick = useCallback((key) => {
        setSelectedCard("other");
        setActiveStatusValue(prev => prev === key ? "UNDER_REVIEW" : key);
        triggerTableRefresh();
    }, [triggerTableRefresh]);

    // ─── Fetch summary stats from existing backend APIs ─────────────────────
    // Replaces the old client-side computeSummary() that required all data loaded.
    // Uses the same APIs as ThreatDashboardPage: fetchCountBySeverity, fetchThreatCategoryCount, getDailyThreatActorsCount.
    // Context-source header is auto-added by the request interceptor — no special handling needed.
    useEffect(() => {
        async function loadSummary() {
            setSummaryLoading(true);
            try {
                const results = await Promise.allSettled([
                    threatDetectionApi.fetchCountBySeverity(startTimestamp, endTimestamp, "ACTIVE"),
                    threatDetectionApi.fetchThreatCategoryCount(startTimestamp, endTimestamp, activeStatusValue),
                    threatDetectionApi.getDailyThreatActorsCount(startTimestamp, endTimestamp, []),
                    threatDetectionApi.fetchThreatTopNData(startTimestamp, endTimestamp, [], 5),
                ]);

                const severityResp = results[0].status === 'fulfilled' ? results[0].value : {};
                const categoryResp = results[1].status === 'fulfilled' ? results[1].value : {};
                const dailyResp    = results[2].status === 'fulfilled' ? results[2].value : {};
                const topNResp     = results[3].status === 'fulfilled' ? results[3].value : {};

                // Severity counts
                const severityDistribution = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
                let totalCount = 0;
                (severityResp?.categoryCounts || []).forEach(item => {
                    const sev = String(item.subCategory || item.severity || '').toUpperCase();
                    if (severityDistribution[sev] !== undefined) {
                        severityDistribution[sev] = item.count || 0;
                        totalCount += item.count || 0;
                    }
                });

                // Status counts from daily actors response
                const statusCounts = {
                    ACTIVE: dailyResp?.totalActiveStatus || 0,
                    IGNORED: dailyResp?.totalIgnoredStatus || 0,
                    UNDER_REVIEW: dailyResp?.totalUnderReviewStatus || 0,
                    FIXED: 0,
                };

                // Top policies from category counts
                const subcategoryMap = {};
                (categoryResp?.categoryCounts || []).forEach(item => {
                    const sub = item.subCategory || item.category || "Unknown";
                    subcategoryMap[sub] = (subcategoryMap[sub] || 0) + (item.count || 0);
                });
                const topPolicies = Object.entries(subcategoryMap)
                    .sort((a, b) => b[1] - a[1])
                    .slice(0, 5)
                    .map(([name, count]) => ({ name, count }));

                // By type — derive from category data, also build reverse map for filtering
                const byType = {};
                const typeToSubCategories = {};
                Object.entries(subcategoryMap).forEach(([name, count]) => {
                    const lower = name.toLowerCase();
                    let type = "Other";
                    if (lower.includes("prompt") || lower.includes("injection")) type = "Prompt";
                    else if (lower.includes("skill") || lower.includes("malicious_skill")) type = "Skill";
                    else if (lower.includes("config") || lower.includes("setting")) type = "Config";
                    else if (lower.includes("tool") || lower.includes("mcp")) type = "Tool";
                    else if (lower.includes("llm")) type = "LLM";

                    if (!byType[type]) byType[type] = { text: 0, color: TYPE_COLORS[type] || "#999", filterKey: type };
                    byType[type].text += count;
                    if (!typeToSubCategories[type]) typeToSubCategories[type] = [];
                    typeToSubCategories[type].push(name);
                });
                const categoryTotal = Object.values(byType).reduce((sum, v) => sum + v.text, 0);

                // Top hosts from topN response
                const topHosts = (topNResp?.topHosts || []).map(h => ({
                    name: h.host || "-",
                    count: h.attacks || 0,
                    host: h.host || "",
                }));

                setSummaryData({ severityDistribution, totalCount, categoryTotal, statusCounts, topPolicies, topHosts, byType, typeToSubCategories });
            } catch {
                setSummaryData(null);
            } finally {
                setSummaryLoading(false);
            }
        }
        loadSummary();
    }, [startTimestamp, endTimestamp, activeStatusValue]);

    // ─── Fetch latency data ──────────────────────────────────────────────────
    useEffect(() => {
        threatDetectionApi.fetchGuardrailLatency(startTimestamp, endTimestamp)
            .then(res => {
                const metrics = res?.result?.metrics || [];
                const byTimestamp = {};
                metrics.forEach(m => {
                    if (!byTimestamp[m.timestamp]) byTimestamp[m.timestamp] = {};
                    byTimestamp[m.timestamp][m.metricId] = m.value;
                });
                const raw = Object.entries(byTimestamp)
                    .map(([ts, vals]) => {
                        const req = vals.GUARDRAIL_REQUEST_LATENCY || 0;
                        const resp = vals.GUARDRAIL_RESPONSE_LATENCY || 0;
                        return { timestamp: parseInt(ts), incomingRequestP95: req, outputResultP95: resp, totalP95: req + resp };
                    })
                    .sort((a, b) => a.timestamp - b.timestamp);
                const MAX_POINTS = 100;
                if (raw.length <= MAX_POINTS) {
                    setLatencyData(raw);
                } else {
                    const bucketSize = Math.ceil(raw.length / MAX_POINTS);
                    const downsampled = [];
                    for (let i = 0; i < raw.length; i += bucketSize) {
                        const bucket = raw.slice(i, i + bucketSize);
                        const avgOf = field => bucket.reduce((sum, x) => sum + x[field], 0) / bucket.length;
                        downsampled.push({
                            timestamp: bucket[Math.floor(bucket.length / 2)].timestamp,
                            incomingRequestP95: avgOf('incomingRequestP95'),
                            outputResultP95: avgOf('outputResultP95'),
                            totalP95: avgOf('totalP95'),
                        });
                    }
                    setLatencyData(downsampled);
                }
            })
            .catch(() => setLatencyData([]));
    }, [startTimestamp, endTimestamp]);

    // ─── Server-side data fetch for AG Grid (replaces fetch-all-then-filter) ──
    // Uses the existing fetchSuspectSampleData API that SusDataTable also uses.
    // AgGridTable's onServerFetch mode handles pagination, sort, and search automatically.
    const onServerFetch = useCallback(({ filters, sortKey, sortOrder, skip, limit, searchString }) => {
        const pageSize = limit || 50;
        const severityFilter = filters?.severity || [];
        const hostFilter = filters?.user || [];
        const policyFilter = filters?.policyName || [];
        const statusFilter = activeStatusValue;

        // AgGridTable sends sortOrder: -1 for asc, 1 for desc (opposite of MongoDB convention)
        const mongoSort = sortOrder ? -sortOrder : -1;
        const isSeveritySort = sortKey === "severity";
        const SORT_FIELD_MAP = { detected: "detectedAt", severity: "severity" };
        const sort = sortKey ? { [SORT_FIELD_MAP[sortKey] || sortKey]: mongoSort } : { detectedAt: -1 };

        return threatDetectionApi.fetchSuspectSampleData(
            skip,
            [],             // ips
            [],             // apiCollectionIds
            [],             // urls
            [],             // types
            sort,
            startTimestamp,
            endTimestamp,
            policyFilter.length > 0 ? policyFilter : [],  // latestAttack (filters by filterId/subCategory)
            pageSize,
            statusFilter,   // statusFilter — defaults to "ACTIVE", can be changed via Status column filter
            undefined,      // successfulExploit
            undefined,      // label
            hostFilter.length > 0 ? hostFilter : undefined, // hosts
            searchString && searchString.length >= 3 ? searchString : undefined,
            undefined,      // method
            isSeveritySort, // sortBySeverity — triggers aggregation-based rank sort in backend
            severityFilter.length > 0 ? severityFilter : undefined,
        ).then(result => {
            const events = result?.maliciousEvents || [];
            const transformed = events.map(e => transformEvent(e, collectionsMap, usernameMapRef.current));
            setRows(transformed);
            return { value: transformed, total: result?.total || 0 };
        });
    }, [startTimestamp, endTimestamp, collectionsMap, activeStatusValue]);

    // ─── Bulk actions ──────────────────────────────────────────────────────────
    // Under Server-Side Row Model, "select all" tracks selection as an abstract
    // {selectAll: true, toggledNodes} flag rather than concrete node references, which
    // leaves api.getSelectedRows() empty the instant select-all is used (it only reads a
    // separate map that select-all never populates). node.isSelected() correctly reflects
    // the select-all state per row though, so walk loaded nodes ourselves instead.
    const getSelectedIds = useCallback(() => {
        const ids = [];
        gridRef.current?.api?.forEachNode(node => {
            if (!node.stub && node.isSelected() && node.data?.id) ids.push(node.data.id);
        });
        return ids;
    }, []);

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
                clearBulkSelection();
                triggerTableRefresh();
            } else {
                func.setToast(true, true, "Failed to update selected events");
            }
        } catch {
            func.setToast(true, true, "Failed to update selected events");
        }
    }, [getSelectedIds, clearBulkSelection, triggerTableRefresh]);

    const handleBulkDelete = useCallback(async () => {
        const ids = getSelectedIds();
        setDeleteConfirmOpen(false);
        if (!ids.length) return;
        try {
            const response = await threatDetectionApi.deleteMaliciousEvents({ eventIds: ids });
            if (response?.deleteSuccess) {
                func.setToast(true, false, `${ids.length} event${ids.length === 1 ? "" : "s"} deleted successfully`);
                clearBulkSelection();
                triggerTableRefresh();
            } else {
                func.setToast(true, true, "Failed to delete selected events");
            }
        } catch {
            func.setToast(true, true, "Failed to delete selected events");
        }
    }, [getSelectedIds, clearBulkSelection, triggerTableRefresh]);

    const bulkActions = useMemo(() => [
        { label: "Mark for Review", onAction: () => handleBulkStatusUpdate("UNDER_REVIEW", "marked for review") },
        { label: "Ignore", onAction: () => handleBulkStatusUpdate("IGNORED", "ignored") },
        { label: "Delete", destructive: true, onAction: () => setDeleteConfirmOpen(true) },
    ], [handleBulkStatusUpdate]);

    const handleRowClick = (e) => {
        if (e?.data) setSelectedViolation(e.data);
    };

    useEffect(() => {
        const api = gridRef.current?.api;
        if (!api) return;
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

    const tableHeading = selectedCard === "open"
        ? "All Open Violations"
        : activeStatusValue === "IGNORED"
            ? "All Ignored Violations"
            : "All Under Review Violations";

    const tableComponent = (
        <Box key="table" className="violations-table-wrap">
            <Box paddingBlockEnd="3">
                <Text variant="headingSm">{tableHeading}</Text>
            </Box>
            <AgGridTable
                key={`violations-grid-${tableKey}-${startTimestamp}-${endTimestamp}`}
                rowData={rows}
                columnDefs={colDefs}
                defaultColDef={DEFAULT_COL_DEF}
                autoSizeStrategy={AUTO_SIZE_STRATEGY}
                searchPlaceholder="Search violations"
                onRowClicked={handleRowClick}
                suppressRowClickSelection
                getRowStyle={() => ({ cursor: "pointer" })}
                getRowClass={getRowClass}
                gridRef={gridRef}
                rowSelection={{
                    mode: "multiRow",
                    // Dedicated checkbox column (colDef-level checkboxSelection is deprecated in
                    // AG Grid v32+ and, combined with rowModelType="serverSide", stopped rendering
                    // altogether — this is the supported replacement).
                    checkboxes: true,
                    headerCheckbox: true,
                    // selectAll: "currentPage"/"filtered" only work for rowModelType="clientSide"
                    // (AG Grid warns and ignores it otherwise) — SSRM's header checkbox always
                    // does the abstract "select all" below, regardless of this setting. We handle
                    // reading the actual selection ourselves (see getSelectedIds/onSelectionChanged)
                    // instead of fighting that, so this is left at the SSRM-only default.
                    enableClickSelection: false,
                }}
                onSelectionChanged={(e) => {
                    let count = 0;
                    e.api.forEachNode(node => { if (!node.stub && node.isSelected()) count++; });
                    setBulkSelectedCount(count);
                }}
                bulkActionCount={bulkSelectedCount}
                bulkActions={bulkActions}
                onClearBulk={clearBulkSelection}
                paginationPageSize={50}
                paginationPageSizeSelector={[20, 50, 100]}
                height={500}
                domLayout="normal"
                onServerFetch={onServerFetch}
                filterStateUrl={gridFilterKey.current}
                serverSideRowModel
                getRowId={(params) => params.data.id}
            />
        </Box>
    );

    const components = [
        <ViolationsDashboard
            key="dashboard"
            summaryData={summaryData}
            loading={summaryLoading}
            onSeverityClick={handleSeverityClick}
            activeSeverityFilter={activeSeverityFilter}
            onPolicyClick={handlePolicyClick}
            activePolicyFilter={activePolicyFilter}
            onClearPolicySelection={handleClearPolicySelection}
            onHostClick={handleHostClick}
            activeHostFilter={activeHostFilter}
            onClearHostSelection={handleClearHostSelection}
            onTypeClick={handleTypeClick}
            activeTypeFilter={activeTypeFilter}
            selectedCard={selectedCard}
            onOpenCardClick={handleOpenCardClick}
            onOtherCardClick={handleOtherCardClick}
            onOtherBreakdownClick={handleOtherBreakdownClick}
            activeStatusValue={activeStatusValue}
            latencyData={latencyData}
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
        />,
        tableComponent,
        <ViolationFlyout
            key="flyout"
            violation={selectedViolation}
            show={selectedViolation !== null}
            onClose={() => setSelectedViolation(null)}
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

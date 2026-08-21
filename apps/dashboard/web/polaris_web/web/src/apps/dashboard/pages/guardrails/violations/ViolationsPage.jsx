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
    RadioButton,
    Tabs,
    Text,
    TextField,
    Tooltip,
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
import SessionStore from "@/apps/main/SessionStore";
import LocalStore from "@/apps/main/LocalStorageStore";
import guardrailApi from "@/apps/dashboard/pages/guardrails/api";
import { buildApprovedByPolicy, isServerApproved } from "@/apps/dashboard/pages/guardrails/utils";
import { resolveComplianceClauseMap, mergePolicyComplianceMap } from "@/apps/dashboard/pages/threat_detection/utils/formatUtils";
import NewLayoutTooltip from "@/apps/dashboard/pages/observe/agentic/NewLayoutTooltip";
import { isEndpointSecurityCategory, isAgenticSecurityCategory } from "@/apps/main/labelHelper";

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

function RuleViolatedCellRenderer({ value }) {
    if (!value || value === "-") return <Text variant="bodySm" color="subdued">-</Text>;
    return <Text variant="bodySm" truncate>{value}</Text>;
}

// Frameworks only (not every clause) - the flyout shows the full clause list per framework.
function ComplianceCellRenderer({ data }) {
    const frameworks = Object.keys(data?.complianceMap || {});
    if (frameworks.length === 0) return <Text variant="bodySm" color="subdued">-</Text>;
    const [first, ...rest] = frameworks;
    return (
        <HorizontalStack gap="1" wrap={false} blockAlign="center">
            <Badge size="small">{first}</Badge>
            {rest.length > 0 && (
                <Tooltip content={rest.join(", ")} dismissOnMouseOut>
                    <Badge size="small">{`+${rest.length}`}</Badge>
                </Tooltip>
            )}
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

// Needs Approval tab only. Stops the click from bubbling into the row's onRowClicked (which
// would otherwise open the ViolationFlyout instead of the approve modal).
function ApproveCellRenderer({ data, onApprove }) {
    return (
        <div onClick={(e) => e.stopPropagation()}>
            <Button size="slim" onClick={() => onApprove?.(data)}>Approve</Button>
        </div>
    );
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

// Column defs are built dynamically so we can inject backend filter values. showApprove/onApprove
// add the Needs Approval tab's Action column (mirrors SusDataTable.jsx's conditional "Action" header).
function buildColDefs(filterValues, showApprove, onApprove) {
    const cols = [
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
            // Critical first by default. Declared on the column (rather than defaulting inside
            // onServerFetch) so the header's sort indicator matches what's actually requested:
            // asc here means ascending severityRank, and the backend ranks CRITICAL as 1.
            sort: "asc",
        },
        // Atlas only: the username map comes from Endpoint Shield metadata, which Argus has no
        // equivalent of - there the column would just repeat the host shown in Agentic Asset.
        ...(isEndpointSecurityCategory() ? [{
            field: "user",
            headerName: "User",
            minWidth: 140,
            filter: "agSetColumnFilter",
            filterParams: { values: filterValues.hosts || [] },
            cellRenderer: UserCellRenderer,
        }] : []),
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
            field: "violation",
            headerName: "Rule Violated",
            minWidth: 150,
            sortable: false,
            cellRenderer: RuleViolatedCellRenderer,
        },
        {
            field: "complianceMap",
            headerName: "Compliance",
            minWidth: 140,
            sortable: false,
            cellRenderer: ComplianceCellRenderer,
        },
        {
            field: "_status",
            headerName: "Status",
            minWidth: 110,
            sortable: false,
            cellRenderer: StatusCellRenderer,
        },
    ];
    if (showApprove) {
        cols.push({
            field: "_approve",
            headerName: "Action",
            minWidth: 110,
            sortable: false,
            cellRenderer: ApproveCellRenderer,
            cellRendererParams: { onApprove },
        });
    }
    return cols;
}

// scaleUpToFitGridWidth proportionally stretches columns after content-sizing so they fill the
// grid width — without it, tabs whose rows all have short/uniform values (e.g. Misconfigured
// Settings: every row is "Codex" / "codex_config_risk" / "High") size narrower than the grid and
// leave a blank gutter after the last column.
const AUTO_SIZE_STRATEGY = { type: "fitCellContents", scaleUpToFitGridWidth: true };

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

// Classify a violation by its POLICY name. This is the grouping used by the "Violations by Type"
// pie, and the table's Type column uses it too so the two stay consistent (a policy like
// "llm-test" is LLM in both). Distinct from deriveAgenticType, which classifies by request shape.
function classifyPolicyType(name) {
    const lower = (name || "").toLowerCase();
    if (lower.includes("prompt") || lower.includes("injection"))       return "Prompt";
    if (lower.includes("skill") || lower.includes("malicious_skill"))  return "Skill";
    if (lower.includes("config") || lower.includes("setting"))         return "Config";
    if (lower.includes("tool") || lower.includes("mcp"))               return "Tool";
    if (lower.includes("llm"))                                         return "LLM";
    return "Other";
}

// Transform a single backend event into a table row.
// Kept lightweight — runs only on the current page of results (not all data).
function transformEvent(event, collectionsMap, usernameMap, guardrailComplianceMap) {
    const meta = parseMetadata(event.metadata);
    // typeLabel (request-shape) still drives evidence/asset-tag logic below;
    // the Type column itself uses the policy classification so it matches the pie.
    const typeLabel = deriveAgenticType(event.url, event.method);
    const policyName = meta.policy_name || meta.npolicy_name || event.filterId || "-";

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
        // Only link the asset when its collection actually resolves - events can carry ids that
        // aren't in api_collections, and the inventory page spins forever on those.
        assetLinkable: !!collectionsMap?.[event.apiCollectionId],
        detected: event.timestamp,
        // Raw filterId/host/behaviour kept as explicit fields (not just folded into policyName/user)
        // for the Needs Approval tab's client-side filter and "Approve server" action, which need
        // the exact values the backend expects (approveServerForPolicy takes policyName + serverId).
        filterId: event.filterId,
        host: rawHost,
        behaviourRaw: rawBehaviour,
        type: classifyPolicyType(policyName),
        violation: meta.rule_violated || meta.nrule_violated || meta.nruleViolated || event.subCategory || event.filterId || "-",
        severity: (event.severity || "HIGH").toUpperCase(),
        // Same resolver the old UI and the flyout use, so the column, the flyout and the
        // compliance report all agree on a row's clauses.
        complianceMap: resolveComplianceClauseMap(event, true, {}, guardrailComplianceMap || {}),
        evidenceText: primaryValue || normalizeReasonPunctuation(meta.reason) || "-",
        user: userDisplay,
        userHost: rawHost,
        agenticAsset: skillOrToolName || formatAssetDisplayName(rawAsset),
        agenticAssetRaw: rawAsset,
        agenticAssetTag: skillOrToolName ? (typeLabel === "Skill" ? "skill" : typeLabel === "Tool" ? "tool" : agenticAssetTag) : agenticAssetTag,
        action,
        policyName,
        _status: event.status || "ACTIVE",
        payload: event.payload || null,
        metadata: event.metadata || null,
        sessionId: event.sessionId || null,
        deviceId: rawHost,
    };
}

// ─── Dashboard summary section ───────────────────────────────────────────────────

function ViolationsDashboard({ summaryData, usernameMap, loading: summaryLoading, onSeverityClick, activeSeverityFilter, onPolicyClick, activePolicyFilter, onClearPolicySelection, onHostClick, activeHostFilter, onClearHostSelection, onAssetClick, activeAssetFilter, onClearAssetSelection, onTypeClick, activeTypeFilter, selectedCard, onOpenCardClick, onOtherCardClick, onOtherBreakdownClick, activeStatusValue, currentTab, latencyData, startTimestamp, endTimestamp }) {
    if (summaryLoading) return <SpinnerCentered />;
    if (!summaryData) return null;

    const { severityDistribution, categoryTotal, statusCounts, topPolicies, topHosts, byType, skillsEvaluationsCount, misconfiguredSettingsCount } = summaryData;

    const totalBreakdown = ["CRITICAL", "HIGH", "MEDIUM", "LOW"].map(k => ({
        label: k.charAt(0) + k.slice(1).toLowerCase(),
        count: severityDistribution[k] || 0,
        color: SEVERITY_COLORS[k],
        key: k,
    }));

    const otherBreakdown = [
        { label: "Under Review", count: statusCounts.UNDER_REVIEW || 0, color: STATUS_COLORS.UNDER_REVIEW || "#F5A623", key: "UNDER_REVIEW" },
        { label: "Ignored",      count: statusCounts.IGNORED || 0,      color: STATUS_COLORS.IGNORED, key: "IGNORED" },
        // Skills Evaluations / Misconfigured Settings are partitions of ACTIVE (not real statuses),
        // fetched the same way the tabs themselves count their rows (skillEvaluationMode/
        // configEvaluationMode "only") — NOT derived from byType, which excludes /skills/ events
        // entirely and buckets Config by category text (a skill's "Config Mutation" sub-category
        // would otherwise get miscounted as Misconfigured Settings).
        // Atlas only - their tabs are gated the same way below, and their counts are never
        // fetched on Argus (wantsPartitionCounts), so listing them there would show a dead 0
        // whose click target doesn't exist.
        ...(isEndpointSecurityCategory() ? [
            { label: "Skills Evaluations",     count: skillsEvaluationsCount || 0,     color: TYPE_COLORS.Skill,  key: "SKILLS_EVALUATIONS" },
            { label: "Misconfigured Settings", count: misconfiguredSettingsCount || 0, color: TYPE_COLORS.Config, key: "MISCONFIGURED_SETTINGS" },
        ] : []),
    ];

    const policyRows = topPolicies.map((item, i) => ({
        id: `p${i}`,
        name: item.name,
        count: item.count,
        onClick: () => onPolicyClick?.(item.name),
        renderValue: () => <Text variant="bodyMd">{item.count.toLocaleString("en-US")}</Text>,
    }));

    // Argus: label each host exactly the way the table's Agentic Asset column does
    // (formatAssetDisplayName), then merge hosts that collapse to the same label - e.g. two
    // different *.amazonaws.com hosts both render as "Com". Keeps the card and the column in sync
    // without needing a server-side asset aggregation; the underlying hosts stay on the row so a
    // click can filter by all of them.
    const assetGroups = new Map();
    (topHosts || []).forEach((item) => {
        const label = formatAssetDisplayName(item.host) || item.name || "-";
        const entry = assetGroups.get(label) || { label, count: 0, hosts: [] };
        entry.count += item.count;
        if (item.host) entry.hosts.push(item.host);
        assetGroups.set(label, entry);
    });
    const assetRows = [...assetGroups.values()]
        .sort((a, b) => b.count - a.count)
        .slice(0, 5)
        .map((g, i) => ({
            id: `a${i}`,
            name: g.label,
            hosts: g.hosts,
            // Same tag the table's Agentic Asset cell feeds AssetIcon, so the card shows the
            // matching product logo/favicon instead of a bare row.
            assetTagValue: getAssetServiceName(g.hosts[0]) || g.label,
            count: g.count,
            onClick: () => onAssetClick?.(g.hosts),
            renderValue: () => <Text variant="bodyMd">{g.count.toLocaleString("en-US")}</Text>,
        }));

    // The card highlights by row.name; the filter holds the hosts behind each label.
    const activeAssetNames = new Set(
        assetRows.filter(r => r.hosts.some(h => activeAssetFilter?.has(h))).map(r => r.name)
    );

    const hostRows = (topHosts || []).slice(0, 5).map((item, i) => {
        const resolvedUser = getUsernameForCollection({ displayName: item.host }, usernameMap || {});
        const displayName = (resolvedUser && resolvedUser !== "-") ? resolvedUser : (item.host ? item.host.split('.')[0] : item.name);
        return {
        id: `h${i}`,
        name: displayName,
        count: item.count,
        os: detectOs(item.host),
        onClick: () => onHostClick?.(item.host),
        renderValue: () => <Text variant="bodyMd">{item.count.toLocaleString("en-US")}</Text>,
        };
    });

    // Latency graph is Akto-internal only (matches the same gate on ThreatDetectionPage.jsx),
    // and hidden on the demo account, which has no Agent Gateway writing GUARDRAIL_* metrics.
    // Everyone else gets Open/Other Violations side by side instead of stacked, since there's
    // no third column to fill the space next to them.
    const isAktoUser = window.USER_NAME?.includes('@akto.io') && !func.isDemoAccount();

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

    const isOtherCardFamily = selectedCard === "other" || selectedCard === "other-view";
    const otherCard = (
        <Box
            className="violations-card-wrap"
            style={isOtherCardFamily ? { outline: "1px solid var(--p-color-border-critical)" } : undefined}
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
                // currentTab (not activeStatusValue) drives the highlight — activeStatusValue is
                // hardcoded to "ACTIVE" for Skills Evaluations/Misconfigured Settings/Needs Approval
                // (required for the backend fetch), so it'd never match those breakdown keys.
                activeFilter={isOtherCardFamily ? new Set([currentTab.toUpperCase()]) : undefined}
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
                    title={isEndpointSecurityCategory() ? "Violations by Top Users" : "Violations by Top Agentic Assets"}
                    titleTooltip={isEndpointSecurityCategory()
                        ? "Top 5 users by number of violations. Click a user to filter the table below."
                        : "Top 5 agentic assets by number of violations. Click an asset to filter the table below."}
                    columns={[{ label: isEndpointSecurityCategory() ? "User" : "Agentic Asset" }, { label: "Violations" }]}
                    rows={isEndpointSecurityCategory() ? hostRows : assetRows}
                    // Atlas rows are devices (OS icon); Argus rows are assets, so use the same
                    // AssetIcon lookup the table's Agentic Asset column uses.
                    renderIcon={isEndpointSecurityCategory()
                        ? (row) => <OsIcon os={row.os} size={20} />
                        : (row) => <AssetIcon type={null} assetTagValue={row.assetTagValue} size={20} />}
                    activeRows={isEndpointSecurityCategory() ? activeHostFilter : activeAssetNames}
                    onClearSelection={isEndpointSecurityCategory() ? onClearHostSelection : onClearAssetSelection}
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

    // Atlas and Argus both reach Guardrail Activity via /protection/threat-activity
    // (see LeftNav); only MCP Security / Gen AI use /guardrails/activity.
    const legacyPath = (isEndpointSecurityCategory() || isAgenticSecurityCategory())
        ? "/dashboard/protection/threat-activity"
        : "/dashboard/guardrails/activity";

    useEffect(() => {
        if (!newLayout) {
            navigate(legacyPath, { replace: true });
        }
    }, [navigate, legacyPath, newLayout]);

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
    const [latencyData, setLatencyData] = useState(null);
    const [activeSeverityFilter, setActiveSeverityFilter] = useState(new Set());
    const [activePolicyFilter, setActivePolicyFilter] = useState(new Set());
    const [activeTypeFilter, setActiveTypeFilter] = useState(null);
    // Type (pie) filter is driven straight into the server fetch instead of the policyName
    // set-filter: the pie's subcategory names come from a different endpoint than the set
    // filter's known values, so setColumnFilterModel would silently drop them.
    const [activeTypeSubCategories, setActiveTypeSubCategories] = useState([]);

    // Single source of truth for the tab bar, mirroring the old UI's SusDataTable tab ids exactly:
    // 'active' | 'under_review' | 'ignored' | 'needs_approval' | 'skills_evaluations' |
    // 'misconfigured_settings'.
    const [currentTab, setCurrentTab] = useState("active");
    const isSkillsEvaluationsTab = currentTab === "skills_evaluations";
    const isNeedsApprovalTab = currentTab === "needs_approval";
    const isMisconfiguredTab = currentTab === "misconfigured_settings";
    // Needs Approval, Skills Evaluations, and Misconfigured Settings are all views over ACTIVE
    // events narrowed by other means (client-side behaviour filter / skillEvaluationMode /
    // configEvaluationMode below), not their own status value - same convention as
    // SusDataTable's effectiveStatus.
    const activeStatusValue = (isSkillsEvaluationsTab || isNeedsApprovalTab || isMisconfiguredTab) ? "ACTIVE" : currentTab.toUpperCase();
    // Drives the summary cards' outline - neither "open" nor "other" card highlights on these
    // orthogonal views, since they're not a status.
    const selectedCard = currentTab === "active" ? "open" : ((isSkillsEvaluationsTab || isNeedsApprovalTab || isMisconfiguredTab) ? "other-view" : "other");
    const gridRef = useRef(null);
    const prevSelectedIdRef = useRef(null);
    const gridFilterKey = useRef(`violations-${Date.now()}`);
    const collectionsMap = PersistStore((state) => state.collectionsMap);
    const usernameMapRef = useRef({});
    const guardrailComplianceMapRef = useRef({});
    const [usernameMap, setUsernameMap] = useState({});

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

    // values.ranges has no 30-day preset (it jumps 7 days -> 2 months), so pass the range inline.
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        func.getLast30DaysRange(),
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
            setUsernameMap(map);
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

    // Top Policies filter is driven straight into the server fetch (see onServerFetch), NOT the
    // policyName set-filter: the policy names come from a different endpoint than the set filter's
    // known values, so setColumnFilterModel would silently drop them and the table wouldn't filter.
    const handlePolicyClick = useCallback((name) => {
        setActivePolicyFilter(prev => {
            const next = new Set(prev);
            if (next.has(name)) next.delete(name); else next.add(name);
            return next;
        });
    }, []);

    const handleClearPolicySelection = useCallback(() => {
        setActivePolicyFilter(new Set());
    }, []);

    const [activeHostFilter, setActiveHostFilter] = useState(new Set());
    // Argus: the card filters by collection id, not by the (hidden) user column.
    const [activeAssetFilter, setActiveAssetFilter] = useState(new Set());

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
            setActiveTypeSubCategories([]);
        } else {
            setActiveTypeFilter(typeName);
            setActiveTypeSubCategories(subCategories);
        }
    }, [summaryData, activeTypeFilter]);

    // Re-fetch the server-side rows whenever the pie's type filter changes (skip the initial mount).
    const typeFilterFirstRun = useRef(true);
    useEffect(() => {
        if (typeFilterFirstRun.current) { typeFilterFirstRun.current = false; return; }
        gridRef.current?.api?.refreshServerSide({ purge: true });
    }, [activeTypeSubCategories]);

    // ─── Card selection (Open vs Other) ─────────────────────────────────────
    const [tableKey, setTableKey] = useState(0);
    const triggerTableRefresh = useCallback(() => setTableKey(k => k + 1), []);

    const handleAssetClick = useCallback((hosts) => {
        const list = Array.isArray(hosts) ? hosts : [hosts];
        setActiveAssetFilter(prev => {
            const next = new Set(prev);
            const isActive = list.some(h => next.has(h));
            list.forEach(h => { if (isActive) next.delete(h); else next.add(h); });
            return next;
        });
        triggerTableRefresh();
    }, [triggerTableRefresh]);

    const handleClearAssetSelection = useCallback(() => {
        setActiveAssetFilter(new Set());
        triggerTableRefresh();
    }, [triggerTableRefresh]);


    const handleOpenCardClick = useCallback(() => {
        if (currentTab === "active") return;
        setCurrentTab("active");
        triggerTableRefresh();
    }, [currentTab, triggerTableRefresh]);

    const handleOtherCardClick = useCallback(() => {
        if (currentTab === "under_review" || currentTab === "ignored") return;
        setCurrentTab("under_review");
        triggerTableRefresh();
    }, [currentTab, triggerTableRefresh]);

    const handleOtherBreakdownClick = useCallback((key) => {
        const target = key === "IGNORED" ? "ignored"
            : key === "SKILLS_EVALUATIONS" ? "skills_evaluations"
            : key === "MISCONFIGURED_SETTINGS" ? "misconfigured_settings"
            : "under_review";
        setCurrentTab(prev => prev === target ? "under_review" : target);
        triggerTableRefresh();
    }, [triggerTableRefresh]);

    const handleTabSelect = useCallback((tabId) => {
        if (tabId === currentTab) return;
        setCurrentTab(tabId);
        triggerTableRefresh();
    }, [currentTab, triggerTableRefresh]);

    // ─── Needs Approval: inline "Approve server" action ─────────────────────
    // Mirrors SusDataTable.jsx (old UI) exactly — approveRow holds the raw row being approved.
    const guardrailApprovedByPolicy = SessionStore((state) => state.guardrailApprovedByPolicy);
    const setGuardrailApprovedByPolicy = SessionStore((state) => state.setGuardrailApprovedByPolicy);
    const setGuardrailComplianceMap = SessionStore((state) => state.setGuardrailComplianceMap);
    const [approveRow, setApproveRow] = useState(null);
    const [approveMode, setApproveMode] = useState("ALWAYS"); // ALWAYS | DURATION
    const [approveDays, setApproveDays] = useState("7");
    const [approveLoading, setApproveLoading] = useState(false);

    const openInlineApprove = useCallback((row) => {
        setApproveMode("ALWAYS");
        setApproveDays("7");
        setApproveRow(row);
    }, []);

    // Refetch policies and refresh the approved-servers map (e.g. right after an approve), so the
    // just-approved server drops off the Needs Approval tab immediately.
    const refreshApprovedByPolicy = useCallback(async () => {
        try {
            const resp = await guardrailApi.fetchGuardrailPolicies();
            setGuardrailApprovedByPolicy(buildApprovedByPolicy(resp?.guardrailPolicies));
        } catch (error) {
            console.error('Error refreshing approved servers:', error);
        }
    }, [setGuardrailApprovedByPolicy]);

    useEffect(() => {
        refreshApprovedByPolicy();
    }, [refreshApprovedByPolicy]);

    // Compliance clauses for the Compliance column. Same two sources the old UI's SusDataTable
    // uses: per-capability infos, merged with any clauses defined on the policies themselves.
    useEffect(() => {
        Promise.all([
            threatDetectionApi.fetchGuardrailComplianceInfos(),
            guardrailApi.fetchGuardrailPolicies(),
        ]).then(([complianceResp, policiesResp]) => {
            const capabilityMap = {};
            (complianceResp?.guardrailComplianceInfos || []).forEach((entry) => {
                const capability = (entry._id || '').replace('guardrails/', '').replace('.conf', '');
                if (capability) capabilityMap[capability] = entry.mapComplianceToListClauses;
            });
            mergePolicyComplianceMap(capabilityMap, policiesResp?.guardrailPolicies);
            guardrailComplianceMapRef.current = capabilityMap;
            setGuardrailComplianceMap(capabilityMap);
            triggerTableRefresh();
        }).catch((error) => {
            console.error('Error loading guardrail compliance:', error);
        });
    }, [setGuardrailComplianceMap, triggerTableRefresh]);

    const submitInlineApprove = useCallback(async () => {
        const policyName = approveRow?.filterId;
        const serverId = approveRow?.host;
        if (!policyName) { func.setToast(true, true, "Could not resolve the policy for this event"); return; }
        if (!serverId || serverId === '-') { func.setToast(true, true, "Could not resolve the server for this event"); return; }
        let value = 0;
        if (approveMode === "DURATION") {
            value = parseInt(approveDays, 10);
            if (!Number.isInteger(value) || value <= 0) { func.setToast(true, true, "Enter a valid number of days"); return; }
        }
        setApproveLoading(true);
        try {
            // request util rejects (and toasts the backend error) on non-2xx, so reaching here = success.
            await guardrailApi.approveServerForPolicy({
                policyName,
                approvedServerId: serverId,
                approvedServerName: serverId,
                approvalMode: approveMode,
                approvalValue: value,
            });
            const scope = approveMode === "DURATION" ? `for ${value} day(s)` : "always";
            func.setToast(true, false, `Approved ${serverId} ${scope}`);
            setApproveRow(null);
            await refreshApprovedByPolicy();
            triggerTableRefresh();
        } catch {
            // Error toast already surfaced by the request interceptor; keep the modal open.
        } finally {
            setApproveLoading(false);
        }
    }, [approveRow, approveMode, approveDays, refreshApprovedByPolicy, triggerTableRefresh]);

    const colDefs = useMemo(() => buildColDefs(filterValues, isNeedsApprovalTab, openInlineApprove), [filterValues, isNeedsApprovalTab, openInlineApprove]);

    // ─── Fetch summary stats from existing backend APIs ─────────────────────
    // Replaces the old client-side computeSummary() that required all data loaded.
    // Uses the same APIs as ThreatDashboardPage: fetchCountBySeverity, fetchThreatCategoryCount, getDailyThreatActorsCount.
    // Context-source header is auto-added by the request interceptor — no special handling needed.
    useEffect(() => {
        async function loadSummary() {
            setSummaryLoading(true);
            try {
                // fetchThreatCategoryCount excludes /skills/ events unconditionally on the backend
                // (ThreatUtils.excludeSkillEndpointFilter), so Skills Evaluations can never be derived
                // from categoryResp/byType — it'd always read 0. Misconfigured Settings also can't
                // reliably reuse byType: it buckets by category TEXT (e.g. a skill's "Config Mutation"
                // sub-category also matches "config"), not by the actual /config/ URL partition. Get
                // both counts the same way the tabs themselves do: skillEvaluationMode/configEvaluationMode
                // "only", limit 1, read .total. Atlas (ENDPOINT) only — undefined elsewhere skips the calls.
                const wantsPartitionCounts = isEndpointSecurityCategory();
                // getDailyThreatActorsCount's totalActiveStatus (below, dailyResp) excludes /skills/
                // events server-side (ThreatUtils.excludeSkillEndpointFilter in ThreatActorService.java)
                // but has NO equivalent config-exclusion filter anywhere in that file, so it overcounts
                // Active by however many /config/ (Misconfigured Settings) events exist — the table
                // itself (fetchSuspectSampleData with skillEvaluationMode/configEvaluationMode:
                // "exclude") excludes both and is the source of truth. Rather than touch the shared
                // ThreatActorService backend (which also feeds 5+ other dashboard widgets), get the
                // corrected Active/Under Review/Ignored counts the same way the grid does, scoped to
                // just this page.
                const results = await Promise.allSettled([
                    threatDetectionApi.fetchCountBySeverity(startTimestamp, endTimestamp, "ACTIVE"),
                    threatDetectionApi.fetchThreatCategoryCount(startTimestamp, endTimestamp, activeStatusValue),
                    threatDetectionApi.getDailyThreatActorsCount(startTimestamp, endTimestamp, []),
                    threatDetectionApi.fetchThreatTopNData(startTimestamp, endTimestamp, [], 5),
                    wantsPartitionCounts
                        ? threatDetectionApi.fetchSuspectSampleData(0, [], [], [], [], {}, startTimestamp, endTimestamp, [], 1, "ACTIVE", undefined, undefined, undefined, undefined, undefined, false, [], "only", undefined)
                        : Promise.resolve(null),
                    wantsPartitionCounts
                        ? threatDetectionApi.fetchSuspectSampleData(0, [], [], [], [], {}, startTimestamp, endTimestamp, [], 1, "ACTIVE", undefined, undefined, undefined, undefined, undefined, false, [], undefined, "only")
                        : Promise.resolve(null),
                    wantsPartitionCounts
                        ? threatDetectionApi.fetchSuspectSampleData(0, [], [], [], [], {}, startTimestamp, endTimestamp, [], 1, "ACTIVE", undefined, undefined, undefined, undefined, undefined, false, [], "exclude", "exclude")
                        : Promise.resolve(null),
                    wantsPartitionCounts
                        ? threatDetectionApi.fetchSuspectSampleData(0, [], [], [], [], {}, startTimestamp, endTimestamp, [], 1, "UNDER_REVIEW", undefined, undefined, undefined, undefined, undefined, false, [], "exclude", "exclude")
                        : Promise.resolve(null),
                    wantsPartitionCounts
                        ? threatDetectionApi.fetchSuspectSampleData(0, [], [], [], [], {}, startTimestamp, endTimestamp, [], 1, "IGNORED", undefined, undefined, undefined, undefined, undefined, false, [], "exclude", "exclude")
                        : Promise.resolve(null),
                ]);

                const severityResp = results[0].status === 'fulfilled' ? results[0].value : {};
                const categoryResp = results[1].status === 'fulfilled' ? results[1].value : {};
                const dailyResp    = results[2].status === 'fulfilled' ? results[2].value : {};
                const topNResp     = results[3].status === 'fulfilled' ? results[3].value : {};
                const skillsCountResp = results[4].status === 'fulfilled' ? results[4].value : null;
                const configCountResp = results[5].status === 'fulfilled' ? results[5].value : null;
                const activeCountResp = results[6].status === 'fulfilled' ? results[6].value : null;
                const underReviewCountResp = results[7].status === 'fulfilled' ? results[7].value : null;
                const ignoredCountResp = results[8].status === 'fulfilled' ? results[8].value : null;
                const skillsEvaluationsCount = skillsCountResp?.total || 0;
                const misconfiguredSettingsCount = configCountResp?.total || 0;

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

                // Status counts: prefer the corrected, skill+config-excluded totals (activeCountResp
                // etc.) over dailyResp's raw totalActiveStatus/etc, which overcounts by however many
                // Misconfigured Settings events exist (see comment above). Falls back to dailyResp
                // when partition counts aren't applicable (non-Endpoint-Security accounts) or a
                // request failed.
                const statusCounts = {
                    ACTIVE: activeCountResp?.total ?? (dailyResp?.totalActiveStatus || 0),
                    IGNORED: ignoredCountResp?.total ?? (dailyResp?.totalIgnoredStatus || 0),
                    UNDER_REVIEW: underReviewCountResp?.total ?? (dailyResp?.totalUnderReviewStatus || 0),
                    FIXED: 0,
                };

                // Top policies from category counts. category over subCategory - subCategory can
                // be a raw config-path string (e.g. "mcp_servers.computer-use.command" for a
                // config-risk finding), not a name meant to stand alone as a policy label.
                const subcategoryMap = {};
                (categoryResp?.categoryCounts || []).forEach(item => {
                    const sub = item.category || item.subCategory || "Unknown";
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
                    const type = classifyPolicyType(name);

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

                setSummaryData({ severityDistribution, totalCount, categoryTotal, statusCounts, topPolicies, topHosts, byType, typeToSubCategories, skillsEvaluationsCount, misconfiguredSettingsCount });
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
        const severityFilter = filters?.severity || [];
        // Argus has no User column - the asset card's selection is the only host filter there.
        const hostFilter = [...new Set([...(filters?.user || []), ...activeAssetFilter])];
        // Union the column filter, the "Top Policies" card selection, and the pie's type filter
        // (all map to the backend latestAttack).
        const policyFilter = [...new Set([...(filters?.policyName || []), ...activePolicyFilter, ...activeTypeSubCategories])];
        const statusFilter = activeStatusValue;
        // Skills Evaluations / Misconfigured Settings partitions (Atlas/ENDPOINT only): "only" on
        // their own tab, "exclude" on Active (both at once, so Active shows neither) - same
        // convention as SusDataTable.jsx. Backend applies these only when contextSource ===
        // ENDPOINT; undefined (no-op) for Agentic accounts or the other tabs.
        const skillEvaluationMode = isEndpointSecurityCategory()
            ? (isSkillsEvaluationsTab ? "only" : (currentTab === "active" ? "exclude" : undefined))
            : undefined;
        const configEvaluationMode = isEndpointSecurityCategory()
            ? (isMisconfiguredTab ? "only" : (currentTab === "active" ? "exclude" : undefined))
            : undefined;
        // Needs Approval is a CLIENT-side view over ACTIVE events (no server-side "behaviour"
        // filter exists) — fetch one big page and filter after mapping, same as SusDataTable.jsx.
        const effectiveSkip = isNeedsApprovalTab ? 0 : skip;
        const pageSize = isNeedsApprovalTab ? 200 : (limit || 50);

        // AgGridTable sends sortOrder: -1 for asc, 1 for desc (opposite of MongoDB convention)
        const mongoSort = sortOrder ? -sortOrder : -1;
        const isSeveritySort = sortKey === "severity";
        const SORT_FIELD_MAP = { detected: "detectedAt", severity: "severity" };
        const sort = sortKey ? { [SORT_FIELD_MAP[sortKey] || sortKey]: mongoSort } : { detectedAt: -1 };

        return threatDetectionApi.fetchSuspectSampleData(
            effectiveSkip,
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
            skillEvaluationMode,
            configEvaluationMode,
        ).then(result => {
            const events = result?.maliciousEvents || [];
            let transformed = events.map(e => transformEvent(e, collectionsMap, usernameMapRef.current, guardrailComplianceMapRef.current));
            let total = result?.total || 0;
            // Needs Approval: keep only approval-behaviour rows, and drop rows whose (policy, server)
            // is already approved for that policy — same filter as SusDataTable.jsx.
            if (isNeedsApprovalTab) {
                transformed = transformed.filter(r =>
                    String(r.behaviourRaw || '').toLowerCase() === 'approval' &&
                    !isServerApproved(guardrailApprovedByPolicy, r.filterId, r.host)
                );
                total = transformed.length;
            }
            setRows(transformed);
            return { value: transformed, total };
        });
    }, [startTimestamp, endTimestamp, collectionsMap, activeStatusValue, activeTypeSubCategories, activePolicyFilter, activeAssetFilter, currentTab, isSkillsEvaluationsTab, isMisconfiguredTab, isNeedsApprovalTab, guardrailApprovedByPolicy]);

    // Reload the grid when the Top Policies card selection changes (skip the initial mount).
    const policyFilterFirstRun = useRef(true);
    useEffect(() => {
        if (policyFilterFirstRun.current) { policyFilterFirstRun.current = false; return; }
        gridRef.current?.api?.refreshServerSide({ purge: true });
    }, [activePolicyFilter]);

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

    // Active/Under Review/Ignored/Skills Evaluations mirror SusDataTable.jsx (old UI) exactly.
    // Misconfigured Settings is new here - both it and Skills Evaluations are gated to Endpoint
    // Security, matching where skillEvaluationMode/configEvaluationMode actually take effect
    // server-side.
    const tabItems = useMemo(() => {
        // Live counts (Active/Under Review/Ignored) come straight from the same
        // getDailyThreatActorsCount response summaryData already holds - matching the tab-count
        // badges GithubServerTable renders for the old UI's equivalent tabs. Skills Evaluations
        // and Misconfigured Settings don't get a number (client-side/server-partition views with
        // no cheap total to show upfront, same as old UI's "Beta" labels).
        const statusCounts = summaryData?.statusCounts || {};
        const withCount = (label, key) => {
            const count = statusCounts[key];
            return typeof count === "number" ? `${label} (${count.toLocaleString()})` : label;
        };
        const items = [
            { id: "active", content: withCount("Active", "ACTIVE") },
            { id: "under_review", content: withCount("Under Review", "UNDER_REVIEW") },
            { id: "ignored", content: withCount("Ignored", "IGNORED") },
        ];
        if (isEndpointSecurityCategory()) {
            // Tabs' `badge` prop is declared in this Polaris version's types but not actually
            // rendered by the component - `content` is also strictly a string, not a node, so
            // there's no way to attach a real Badge here. Folding "(Beta)" into the label is the
            // only thing that reliably renders.
            items.push({ id: "needs_approval", content: "Needs Approval (Beta)" });
            items.push({ id: "skills_evaluations", content: "Skills Evaluations (Beta)" });
            items.push({ id: "misconfigured_settings", content: "Misconfigured Settings (Beta)" });
        }
        return items;
    }, [summaryData]);
    const selectedTabIndex = Math.max(0, tabItems.findIndex(t => t.id === currentTab));

    const tableComponent = (
        <Box key="table" className="violations-table-wrap">
            <Box paddingBlockEnd="3">
                <Tabs
                    tabs={tabItems}
                    selected={selectedTabIndex}
                    onSelect={(index) => handleTabSelect(tabItems[index].id)}
                />
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
            usernameMap={usernameMap}
            loading={summaryLoading}
            onSeverityClick={handleSeverityClick}
            activeSeverityFilter={activeSeverityFilter}
            onPolicyClick={handlePolicyClick}
            activePolicyFilter={activePolicyFilter}
            onClearPolicySelection={handleClearPolicySelection}
            onHostClick={handleHostClick}
            activeHostFilter={activeHostFilter}
            onClearHostSelection={handleClearHostSelection}
            onAssetClick={handleAssetClick}
            activeAssetFilter={activeAssetFilter}
            onClearAssetSelection={handleClearAssetSelection}
            onTypeClick={handleTypeClick}
            activeTypeFilter={activeTypeFilter}
            selectedCard={selectedCard}
            onOpenCardClick={handleOpenCardClick}
            onOtherCardClick={handleOtherCardClick}
            onOtherBreakdownClick={handleOtherBreakdownClick}
            activeStatusValue={activeStatusValue}
            currentTab={currentTab}
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
        <Modal
            key="approve-server"
            open={approveRow !== null}
            onClose={() => setApproveRow(null)}
            title="Approve server"
            primaryAction={{ content: "Approve", loading: approveLoading, onAction: submitInlineApprove }}
            secondaryActions={[{ content: "Cancel", onAction: () => setApproveRow(null) }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <Text variant="bodyMd">
                        Approving <Text as="span" fontWeight="semibold">{approveRow?.host || "this server"}</Text> will
                        allow it to bypass the <Text as="span" fontWeight="semibold">{approveRow?.filterId || "policy"}</Text> guardrail policy on future requests.
                    </Text>
                    <VerticalStack gap="2">
                        <RadioButton
                            label="Always"
                            name="approveMode"
                            checked={approveMode === "ALWAYS"}
                            onChange={() => setApproveMode("ALWAYS")}
                        />
                        <RadioButton
                            label="Number of days"
                            name="approveMode"
                            checked={approveMode === "DURATION"}
                            onChange={() => setApproveMode("DURATION")}
                        />
                    </VerticalStack>
                    {approveMode === "DURATION" && (
                        <TextField
                            label="Number of days"
                            type="number"
                            min={1}
                            value={approveDays}
                            onChange={setApproveDays}
                            autoComplete="off"
                        />
                    )}
                </VerticalStack>
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
            secondaryActions={<NewLayoutTooltip checked={newLayout} onChange={handleLayoutToggle} />}
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

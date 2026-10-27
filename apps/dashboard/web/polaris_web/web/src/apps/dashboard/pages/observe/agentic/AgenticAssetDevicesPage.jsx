import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { Badge, Box, Button, HorizontalStack, VerticalStack, Text, TextField, DataTable } from "@shopify/polaris";
import { ArrowLeftMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";
import TooltipText from "../../../components/shared/TooltipText";
import api from "../api";
import { saveAs } from "file-saver";
import useTable from "@/apps/dashboard/components/tables/TableContext";
import TableStore from "@/apps/dashboard/components/tables/TableStore";
import useCollectionBulkActions from "../api_collections/useCollectionBulkActions";
import { ChildRow, CollectionCheckbox, childCell, deviceRowId, expandGroupedSelection, useClearSelectionOnMount } from "../api_collections/groupedSelection";
import func from "@/util/func";
import transform from "../transform";
import SkillComponentsView from "./SkillComponentsView";
import { fetchAgentMarkdownFromCollections } from "./PluginComponentsView";
import { settledValue, logRejected } from "./constants";
import MisconfiguredBadge from "./MisconfiguredBadge";

/**
 * Endpoint/device-wise view for ONE agentic asset — what clicking a row on the legacy Agentic
 * Assets page opens instead of navigating to Inventory (which needed the whole account's
 * collections just to build a filter). Matches AgentEndpointTreeTable.jsx's expandable
 * device -> children UI (used by Inventory's own agent-tree mode) field-for-field, but sourced
 * from fetchAgenticAssetEndpointsPage — server-paginated at the DEVICE level and scoped to just
 * this asset's own collectionIds, never account-wide (AgentEndpointTreeTable itself is fed by
 * ApiCollections.jsx's full getAllCollectionsBasic() fetch, which is exactly what this page exists
 * to avoid). Old-layout look (PageWithMultipleCards + GithubServerTable, standard back arrow),
 * not a flyout, per explicit direction.
 */

// "unknown"/unset reads as disabled — an unreported status isn't proof a plugin is active.
const isPluginEnabled = (status) => String(status).toLowerCase() === "enabled";

// Plugin rows only — lets you tell a device's plugin status at a glance without expanding the row.
const PLUGIN_STATUS_HEADER = {
    title: "Status", text: "Status", value: "pluginStatusComp", textValue: "pluginStatus", boxWidth: "90px",
};

const PLUGIN_SCOPE_HEADER = {
    title: "Scope", text: "Scope", value: "pluginScopeComp", textValue: "pluginScope", boxWidth: "90px",
};

const PLUGIN_LAST_SEEN_HEADER = {
    title: "Last seen", text: "Last seen", value: "lastTraffic", numericValue: "lastSeenEpoch", sortActive: true, boxWidth: "80px",
};

const getParentHeaders = (rowType) => [
    { title: "", text: "", value: "collapsibleIcon", type: CellType.COLLAPSIBLE, boxWidth: "32px" },
    { title: "Endpoint ID", text: "Endpoint ID", value: "displayNameComp", textValue: "endpointId" },
    { title: "Username", text: "Username", value: "usernameComp", textValue: "username", boxWidth: "100px" },
    ...(rowType === "plugin" ? [PLUGIN_STATUS_HEADER, PLUGIN_SCOPE_HEADER] : []),
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Risk score of this device is the maximum risk score across its own collections</Text>} title="Risk score" />,
        text: "Risk score", value: "riskScoreComp", textValue: "riskScore", numericValue: "riskScore",
        sortActive: true, boxWidth: "80px",
    },
    {
        title: "Sensitive data", text: "Sensitive data", value: "sensitiveSubTypes",
        tooltipContent: <Text variant="bodySm">Types of sensitive data seen across this device's own collections</Text>,
        boxWidth: "160px",
    },
    rowType === "plugin" ? PLUGIN_LAST_SEEN_HEADER : {
        title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time this device's traffic was seen</Text>} title="Last traffic seen" />,
        text: "Last traffic seen", value: "lastTraffic", numericValue: "lastSeenEpoch", sortActive: true, boxWidth: "80px",
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Time when this device was first discovered</Text>} title="Discovered" />,
        text: "Discovered", value: "discovered", numericValue: "startTs", sortActive: true, boxWidth: "80px",
    },
    { title: "Endpoint tags", text: "Endpoint tags", value: "endpointTagsComp", boxWidth: "160px" },
];

const SORT_OPTIONS = [
    { label: "Endpoint ID", value: "endpointId asc", directionLabel: "A-Z", sortKey: "endpointId", columnIndex: 2 },
    { label: "Endpoint ID", value: "endpointId desc", directionLabel: "Z-A", sortKey: "endpointId", columnIndex: 2 },
    { label: "Username", value: "username asc", directionLabel: "A-Z", sortKey: "username", columnIndex: 3 },
    { label: "Username", value: "username desc", directionLabel: "Z-A", sortKey: "username", columnIndex: 3 },
    { label: "Risk score", value: "riskScore desc", directionLabel: "Highest", sortKey: "riskScore", columnIndex: 4 },
    { label: "Risk score", value: "riskScore asc", directionLabel: "Lowest", sortKey: "riskScore", columnIndex: 4 },
    { label: "Last traffic seen", value: "lastSeenEpoch desc", directionLabel: "Newest", sortKey: "lastSeenEpoch", columnIndex: 6 },
    { label: "Last traffic seen", value: "lastSeenEpoch asc", directionLabel: "Oldest", sortKey: "lastSeenEpoch", columnIndex: 6 },
    { label: "Discovered", value: "startTs desc", directionLabel: "Newest", sortKey: "startTs", columnIndex: 7 },
    { label: "Discovered", value: "startTs asc", directionLabel: "Oldest", sortKey: "startTs", columnIndex: 7 },
];

const getSortOptions = (rowType) => rowType === "plugin"
    ? SORT_OPTIONS.map(o => o.sortKey === "lastSeenEpoch" ? { ...o, label: "Last seen" } : o)
    : SORT_OPTIONS;

const ENDPOINT_TAGS_FILTER_DEF = { key: "endpointTags", label: "Endpoint tags", choices: [
    { label: "Contains personal account", value: "Contains personal account" },
    { label: "Local MCP Server", value: "Local MCP Server" },
    { label: "Misconfigured", value: "Misconfigured" },
    { label: "Malicious Skills", value: "Malicious Skills" },
    { label: "Owner", value: "Owner" },
] };

// McpClientRegistry canonical group keys the ai-agent-email filter is offered for. "claude1" is
// Claude Desktop (the raw collection tag ai-agent=claude-desktop folds into it via
// resolveClientKey); "claude2" would be Claude CLI. Scoped to Claude Desktop for now — add keys
// here AND to AI_AGENT_EMAIL_GROUP_KEYS in AgenticObserveAction.java, which enforces the same scope
// server-side so a stale URL can't apply this filter to another asset.
const AI_AGENT_EMAIL_GROUP_KEYS = ["claude1"];

// Free-text regex box for the ai-agent-email filter, supplied via GithubServerTable's renderFilter
// escape hatch (same pattern as SusDataTable's risk-score control) so the shared table needs no
// new filter type. Debounced because the table refetches on every appliedFilters change and
// handleFilterStatusChange is not itself debounced — committing per keystroke would fire one
// request per character typed.
const AI_AGENT_EMAIL_DEBOUNCE_MS = 400;

function AiAgentEmailFilterControl({ selected, onChange }) {
    const committed = selected?.[0] || "";
    const [draft, setDraft] = useState(committed);
    // Resync when the value changes from outside this control (chip removed, "Clear all",
    // a filters= URL restored on load).
    useEffect(() => { setDraft(committed); }, [committed]);

    const timerRef = useRef(null);
    useEffect(() => () => clearTimeout(timerRef.current), []);

    const handleChange = useCallback((value) => {
        setDraft(value);
        clearTimeout(timerRef.current);
        const trimmed = value.trim();
        timerRef.current = setTimeout(() => onChange(trimmed ? [trimmed] : []), AI_AGENT_EMAIL_DEBOUNCE_MS);
    }, [onChange]);

    return (
        <TextField
            label="AI agent email"
            labelHidden
            autoComplete="off"
            value={draft}
            onChange={handleChange}
            placeholder="Regex, e.g. .*@gmail\.com$"
            helpText="Matches anywhere in the email unless anchored with ^ or $"
        />
    );
}

const resourceName = { singular: "endpoint", plural: "endpoints" };

// Rows under an expanded device: one row of the device table per collection (with its own
// checkbox), plus the config row, cell for cell with the device row's headings. child.name is
// already the field the server picked (serviceName for agent/skill rows, the calling source's id
// for service/llm rows — see AgenticObserveAction.groupCollectionsByEndpointId's useServiceName branch).
function ChildrenTable({ parentRowId, children, rowType, misconfiguredChildId, onOpenBundle }) {
    const navigate = useNavigate();
    const { selectedItems } = useTable();
    const headings = getParentHeaders(rowType);

    const handleChildClick = useCallback((child) => {
        // Plugin rows: the child IS the plugin's own metadata endpoint — open the bundled
        // components list instead of navigating to its raw endpoint in Inventory.
        if (rowType === "plugin") { onOpenBundle?.(); return; }
        const bundlesSkills = rowType === "skill" || ((rowType === "agent" || rowType === "service") && (child.skillCount || 0) > 0);
        const isPluginCollapsedService = rowType === "service" && !!child.owningPluginName;
        const scope = bundlesSkills ? "?agentic_view=skills" : (isPluginCollapsedService ? "?agentic_view=mcp" : "");
        navigate(`/dashboard/observe/inventory/${child.id}${scope}`);
    }, [navigate, rowType, onOpenBundle]);

    const handleConfigClick = useCallback(() => {
        if (!misconfiguredChildId) return;
        navigate(`/dashboard/observe/inventory/${misconfiguredChildId}?agentic_view=config`);
    }, [navigate, misconfiguredChildId]);

    const configRow = misconfiguredChildId ? (
        <ChildRow
            key="config"
            onClick={handleConfigClick}
            cells={headings.map((header) => childCell(header, header.value === "displayNameComp" ? (
                <HorizontalStack gap="1" align="start" wrap={false}>
                    <Text variant="bodyMd" as="span">config</Text>
                    <MisconfiguredBadge />
                </HorizontalStack>
            ) : null))}
        />
    ) : null;

    const childRows = (children || []).map((child) => {
        const childRiskScore = child.riskScore || 0;
        const displayValue = child.name || "-";
        const contentFor = (header) => {
            switch (header.value) {
                case "collapsibleIcon":
                    return <CollectionCheckbox collectionId={child.id} parentRowId={parentRowId} />;
                case "displayNameComp":
                    return (
                        <HorizontalStack gap="1" align="start" wrap={false}>
                            <Box maxWidth="200px"><TooltipText tooltip={displayValue} text={displayValue} /></Box>
                            {child.type && <Badge size="small">{child.type}</Badge>}
                            {(child.skillCount || 0) > 0 && (
                                <Badge size="small" status="info">{`${child.skillCount} ${child.skillCount === 1 ? "skill" : "skills"}`}</Badge>
                            )}
                            {child.owningPluginName && <Badge size="small" status="info">{`Plugin: ${child.owningPluginName}`}</Badge>}
                            {child.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                            {child.hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                            {child.hasLocalMcpServer && <Badge size="small" status="critical">Local MCP Server</Badge>}
                        </HorizontalStack>
                    );
                case "pluginStatusComp":
                    return (
                        <Badge size="small" status={isPluginEnabled(child.pluginStatus) ? "success" : "warning"}>
                            {isPluginEnabled(child.pluginStatus) ? "enabled" : "disabled"}
                        </Badge>
                    );
                case "pluginScopeComp":
                    return child.pluginScope || "-";
                case "riskScoreComp":
                    return transform.wrapRiskScoreTooltip(
                        <Badge status={transform.getStatus(childRiskScore)} size="small">{childRiskScore}</Badge>,
                        childRiskScore, child.baseRiskScore, child.baseRiskScoreReason
                    );
                case "sensitiveSubTypes":
                    return transform.prettifySubtypes(child.sensitiveInRespTypes || []);
                case "lastTraffic":
                    return func.prettifyEpoch(child.lastSeenEpoch || 0);
                case "discovered":
                    return func.prettifyEpoch(child.startTs || 0);
                default:
                    return null;
            }
        };
        return (
            <ChildRow
                key={`child-${child.id}`}
                selected={selectedItems.includes(parentRowId) || selectedItems.includes(child.id)}
                onClick={() => handleChildClick(child)}
                cells={headings.map((header) => childCell(header, contentFor(header), true))}
            />
        );
    });

    return <>{configRow}{childRows}</>;
}

function PluginBundleContent({ bundle, pluginCollectionId, navigate }) {
    const [selectedSkill, setSelectedSkill] = useState(null);
    const [selectedAgent, setSelectedAgent] = useState(null);
    const servers = bundle?.mcpServers || [];
    const skills = bundle?.skills || [];
    const agents = bundle?.agents || [];

    const openServer = (name) => {
        const ids = bundle?.mcpServerCollectionIds?.[name] || [];
        if (!ids.length) return;
        navigate(`/dashboard/observe/inventory/${ids[0]}`);
    };

    if (selectedSkill) {
        return (
            <Box>
                <Box paddingBlockEnd="3">
                    <Button plain icon={ArrowLeftMinor} onClick={() => setSelectedSkill(null)} />
                </Box>
                <SkillComponentsView asset={{ collectionIds: [pluginCollectionId], name: selectedSkill }} hideOwningPlugin />
            </Box>
        );
    }

    if (selectedAgent) {
        return (
            <Box>
                <Box paddingBlockEnd="3">
                    <Button plain icon={ArrowLeftMinor} onClick={() => setSelectedAgent(null)} />
                </Box>
                <SkillComponentsView
                    asset={{ collectionIds: [pluginCollectionId], name: selectedAgent }}
                    hideOwningPlugin
                    entityLabel="agent"
                    fetchMarkdown={fetchAgentMarkdownFromCollections}
                />
            </Box>
        );
    }

    if (servers.length === 0 && skills.length === 0 && agents.length === 0) {
        return <Box padding="4"><Text variant="bodySm" color="subdued">No components reported for this plugin yet.</Text></Box>;
    }

    const rows = [
        ...servers.map((name) => [
            <div key={`srv-${name}`} style={{ cursor: "pointer" }} onClick={() => openServer(name)}><Text variant="bodyMd">{name}</Text></div>,
            <div key={`srv-type-${name}`} style={{ cursor: "pointer" }} onClick={() => openServer(name)}><Badge size="small">MCP Server</Badge></div>,
        ]),
        ...skills.map((name) => [
            <div key={`skl-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedSkill(name)}><Text variant="bodyMd">{name}</Text></div>,
            <div key={`skl-type-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedSkill(name)}><Badge size="small" status="info">Skill</Badge></div>,
        ]),
        ...agents.map((name) => [
            <div key={`agt-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedAgent(name)}><Text variant="bodyMd">{name}</Text></div>,
            <div key={`agt-type-${name}`} style={{ cursor: "pointer" }} onClick={() => setSelectedAgent(name)}><Badge size="small" status="new">Agent</Badge></div>,
        ]),
    ];

    return (
        <DataTable
            columnContentTypes={["text", "text"]}
            headings={["Name", "Type"]}
            rows={rows}
            hasZebraStripingOnData
        />
    );
}

function shapeEndpointRow(row, { rowType, onOpenBundle }) {
    const riskScore = row.riskScore || 0;
    const endpointTags = [
        ...(row.hasPersonalAccount ? ["Contains personal account"] : []),
        ...(row.hasLocalMcpServer ? ["Local MCP Server"] : []),
        ...(row.hasMisconfiguredConfig ? ["Misconfigured"] : []),
        ...(row.hasMaliciousSkill ? ["Malicious Skills"] : []),
        ...(row.hasOwnerTag ? ["Owner"] : []),
    ];
    const children = row.children || [];
    const misconfiguredChildId = row.hasMisconfiguredConfig
        ? (children.find((c) => c.hasMisconfiguredConfig)?.id ?? null)
        : null;

    return {
        ...row,
        // Selecting the device selects all its collections (see groupedSelection)
        id: deviceRowId(row.endpointId),
        name: `endpoint-${row.endpointId}`,
        displayNameComp: (
            <HorizontalStack gap="1" align="start" wrap={false}>
                <Box maxWidth="200px">
                    <TooltipText tooltip={row.endpointId} text={row.endpointId} textProps={{ variant: "headingSm" }} />
                </Box>
                <Badge size="small" status="new">{row.childCount || children.length}</Badge>
                {row.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                {row.hasLocalMcpServer && <Badge size="small" status="critical">Local MCP Server</Badge>}
                {row.hasMisconfiguredConfig && <MisconfiguredBadge />}
                {row.hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                {row.hasOwnerTag && <Badge size="small" status="success">Owner</Badge>}
                {row.hasOwnerTag && row.environmentName && (
                    <Badge size="small" status="info">{`Env: ${row.environmentName}`}</Badge>
                )}
            </HorizontalStack>
        ),
        usernameComp: (
            <Box maxWidth="100px">
                <TooltipText tooltip={row.username || "-"} text={row.username || "-"} />
            </Box>
        ),
        pluginStatusComp: rowType === "plugin" ? (
            <Badge size="small" status={isPluginEnabled(row.pluginStatus) ? "success" : "warning"}>
                {isPluginEnabled(row.pluginStatus) ? "enabled" : "disabled"}
            </Badge>
        ) : "-",
        pluginScopeComp: rowType === "plugin" ? (row.pluginScope || "-") : "-",
        riskScoreComp: transform.wrapRiskScoreTooltip(
            <Badge status={transform.getStatus(riskScore)} size="small">{riskScore}</Badge>,
            riskScore, row.baseRiskScore, row.baseRiskScoreReason
        ),
        sensitiveSubTypes: transform.prettifySubtypes(row.sensitiveInRespTypes || []),
        lastTraffic: func.prettifyEpoch(row.lastSeenEpoch || 0),
        discovered: func.prettifyEpoch(row.startTs || 0),
        endpointTagsComp: endpointTags.length > 0 ? endpointTags.join(", ") : "-",
        isTerminal: false,
        collapsibleRow: (
            <ChildrenTable
                parentRowId={deviceRowId(row.endpointId)}
                children={children}
                rowType={rowType}
                misconfiguredChildId={misconfiguredChildId}
                onOpenBundle={onOpenBundle}
            />
        ),
    };
}

export default function AgenticAssetDevicesPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const groupKey = searchParams.get("groupKey") || "";
    const rowType = searchParams.get("rowType") || "";
    const assetName = searchParams.get("name") || "Asset";
    const assetType = searchParams.get("type") || "";

    // Matches ApiCollections.jsx's own agent-tree header exactly (getFilteredPageTitle's
    // "<Type> - <Name>" template + TitleWithInfo's info icon) — that page and this one are the
    // two places this exact asset's device breakdown is shown, so the header should read the same
    // regardless of which one a click landed on.
    const [description, setDescription] = useState("");

    const pageTitle = useMemo(() => (
        <VerticalStack gap="1">
            <TitleWithInfo
                tooltipContent={`Viewing devices for ${assetType || "asset"} ${assetName}`}
                titleText={assetType ? `${assetType} - ${assetName}` : assetName}
                docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
            />
            {description && <Text variant="bodyMd">{description}</Text>}
        </VerticalStack>
    ), [assetType, assetName, description]);

    // Same "Explore mode" primaryAction ApiCollections.jsx always shows next to its title —
    // generic query-explorer shortcut, not scoped to this asset, kept only for header parity.
    const exploreModeAction = useMemo(() => (
        <Button primary onClick={() => navigate("/dashboard/observe/query_mode")}>Explore mode</Button>
    ), [navigate]);

    // True only until this one asset's own collectionIds resolve (lazy — mirrors the new layout's
    // flyout fetching fetchAgenticAssetDetail once per asset instead of shipping collectionIds on
    // every row of the main grid). Navigating here happens immediately on row-click, before this
    // resolves, so the "loading" feedback lives on this page instead of freezing the previous one.
    const [loading, setLoading] = useState(true);
    const collectionIdsRef = useRef([]);
    // Device rows seen so far, by id — bulk actions act on every collection grouped under a selected device.
    const rowsByIdRef = useRef(new Map());
    // collection id -> { device, child }, for the bulk actions' per-collection lookups
    const childByIdRef = useRef(new Map());
    const [refreshKey, setRefreshKey] = useState(0);
    // Plugin rows only — the MCP servers/skills this plugin bundles, so old-UI can list them with a
    // direct redirect link (same server-side data new-UI's PluginComponentsView table shows).
    const [pluginBundle, setPluginBundle] = useState(null);
    const [showBundleFlyout, setShowBundleFlyout] = useState(false);
    const [owningPluginName, setOwningPluginName] = useState(null);

    // Endpoint ID/Username filter choices — matches AgentEndpointTreeTable.jsx's own enumerated
    // facets (this asset's device count is small enough to list directly, no distinct-values
    // endpoint needed). The server computes these from the full unfiltered set on every response,
    // so content is stable across calls for the same asset; only update state when it actually
    // changes to avoid a re-render every single fetch.
    const [filterChoices, setFilterChoices] = useState({ endpointIds: [], usernames: [] });
    const updateFilterChoicesIfChanged = useCallback((endpointIds, usernames) => {
        setFilterChoices((prev) => {
            const sameEndpointIds = prev.endpointIds.length === endpointIds.length && prev.endpointIds.every((v, i) => v === endpointIds[i]);
            const sameUsernames = prev.usernames.length === usernames.length && prev.usernames.every((v, i) => v === usernames[i]);
            return (sameEndpointIds && sameUsernames) ? prev : { endpointIds, usernames };
        });
    }, []);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        (async () => {
            try {
                // trafficMap/riskScoreMap/sensitiveMap are deliberately NOT fetched here — unlike
                // the list pages (Endpoints.jsx etc.), which fetch those account-wide maps once to
                // enrich every row of a big grid, this page only ever needs a handful of entries out
                // of them for one asset's own devices. fetchAgenticAssetEndpointsPage computes those
                // three server-side, scoped to apiCollectionIds, instead of requiring this page to
                // pull the whole account's map just to read a few entries back out of it.
                // Usernames are resolved server-side now, so this page no longer fetches that map.
                const [detailSettled] = await Promise.allSettled([
                    api.fetchAgenticAssetDetail({ groupKey, rowType }),
                ]);
                if (cancelled) return;
                logRejected("AgenticAssetDevicesPage mount", { detail: detailSettled });
                const detail = settledValue(detailSettled, {});
                collectionIdsRef.current = detail?.collectionIds || [];
                setDescription(detail?.description || "");
                setPluginBundle(rowType === "plugin" ? {
                    mcpServers: detail?.pluginMcpServers || [],
                    mcpServerCollectionIds: detail?.pluginMcpServerCollectionIds || {},
                    skills: detail?.pluginSkills || [],
                    agents: detail?.pluginAgents || [],
                } : null);
                setOwningPluginName(rowType !== "plugin" ? (detail?.owningPluginName || null) : null);
            } catch {
                if (!cancelled) { collectionIdsRef.current = []; setDescription(""); setPluginBundle(null); setOwningPluginName(null); }
            } finally {
                if (!cancelled) { setLoading(false); setRefreshKey((k) => k + 1); }
            }
        })();
        return () => { cancelled = true; };
    }, [groupKey, rowType]);

    const fetchTableData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        const mongoSortOrder = sortOrder === -1 ? 1 : -1;
        const filters = {};
        if (filtersObj?.endpointTags?.length) filters.endpointTags = filtersObj.endpointTags;
        if (filtersObj?.endpointId?.length) filters.endpointId = filtersObj.endpointId;
        if (filtersObj?.username?.length) filters.username = filtersObj.username;
        // Gated the same way filtersDef is. GithubServerTable persists applied filters under a key
        // derived from the pathname only (this route is static — the asset lives in the query
        // string), so a filter applied on one asset is replayed on the next. Without this guard we
        // would keep sending aiAgentEmail after navigating to an asset that never offered it.
        if (filtersObj?.aiAgentEmail?.length && rowType === "agent" && AI_AGENT_EMAIL_GROUP_KEYS.includes(groupKey)) {
            filters.aiAgentEmail = filtersObj.aiAgentEmail;
        }
        const res = await api.fetchAgenticAssetEndpointsPage({
            apiCollectionIds: collectionIdsRef.current,
            rowType,
            groupKey,
            skip, limit, sortKey: sortKey || "riskScore", sortOrder: mongoSortOrder, queryValue,
            filters: Object.keys(filters).length ? filters : undefined,
        });
        updateFilterChoicesIfChanged(res.distinctEndpointIds || [], res.distinctUsernames || []);
        const rows = (res.endpoints || []).map((row) => shapeEndpointRow(row, { rowType, onOpenBundle: () => setShowBundleFlyout(true) }));
        rows.forEach((row) => {
            rowsByIdRef.current.set(row.id, row);
            (row.children || []).forEach((child) => childByIdRef.current.set(child.id, { device: row, child }));
        });
        return { value: rows, total: res.total || 0 };
    }, [rowType, groupKey, updateFilterChoicesIfChanged]);

    const filtersDef = useMemo(() => {
        const defs = [ENDPOINT_TAGS_FILTER_DEF];
        if (filterChoices.endpointIds.length) {
            defs.push({ key: "endpointId", label: "Endpoint ID", choices: filterChoices.endpointIds.map((v) => ({ label: v, value: v })) });
        }
        if (filterChoices.usernames.length) {
            defs.push({ key: "username", label: "Username", choices: filterChoices.usernames.map((v) => ({ label: v, value: v })) });
        }
        // Free-text regex over the collection's ai-agent-email tag. Offered only for the asset
        // groups in AI_AGENT_EMAIL_GROUP_KEYS (Claude Desktop today) — on every other asset page
        // the chip is simply not rendered. choices:[] because renderFilter supplies the control.
        if (rowType === "agent" && AI_AGENT_EMAIL_GROUP_KEYS.includes(groupKey)) {
            defs.push({
                key: "aiAgentEmail",
                label: "AI agent email",
                title: "AI agent email",
                choices: [],
                renderFilter: ({ selected, onChange }) => (
                    <AiAgentEmailFilterControl selected={selected} onChange={onChange} />
                ),
            });
        }
        return defs;
    }, [filterChoices, rowType, groupKey]);

    useClearSelectionOnMount();
    const { selectItems } = useTable();

    const collectionIdsOfDevice = (id) => (rowsByIdRef.current.get(id)?.children || []).map((c) => c.id);
    const findChild = (collectionId) => childByIdRef.current.get(collectionId) || null;

    const { collectionActions, setTagsAction } = useCollectionBulkActions({
        rowOf: (id) => findChild(id)?.child,
        getTags: () => Object.fromEntries([...childByIdRef.current.values()].map(({ child }) => [child.id, child.envType || []])),
        onDone: () => {
            selectItems([]);
            TableStore.getState().setSelectedItems([]);
            // Remounts the table, which refetches the devices
            rowsByIdRef.current = new Map();
            childByIdRef.current = new Map();
            setRefreshKey((k) => k + 1);
        },
    });

    const exportCsv = (collectionIds) => {
        const wrap = (value) => '"' + String(value ?? "-").replace(/"/g, '""') + '"';
        let csv = ["Collection ID", "Endpoint ID", "Username", "Name", "Type", "Risk score", "Last traffic seen", "Discovered"].join(",") + "\r\n";
        collectionIds.forEach((id) => {
            const found = findChild(id);
            if (!found) return;
            const { device, child } = found;
            csv += [child.id, device.endpointId, device.username, child.name, child.type, child.riskScore || 0,
                func.prettifyEpoch(child.lastSeenEpoch || 0), func.prettifyEpoch(child.startTs || 0)].map(wrap).join(",") + "\r\n";
        });
        saveAs(new Blob([csv], { type: "text/csv;charset=utf-8" }), `${assetName} collections.csv`);
        func.setToast(true, false, "CSV exported successfully");
    };

    const promotedBulkActions = (selectedIds) => {
        const collectionIds = expandGroupedSelection(selectedIds, collectionIdsOfDevice);
        if (collectionIds.length === 0) return [];
        return [
            { content: "Export as CSV", onAction: () => exportCsv(collectionIds) },
            ...collectionActions(collectionIds),
            setTagsAction(collectionIds),
        ];
    };

    const disambiguateLabel = useCallback((key, value) => func.convertToDisambiguateLabelObj(value, null, 2), []);
    const parentHeaders = useMemo(() => getParentHeaders(rowType), [rowType]);
    const sortOptions = useMemo(() => getSortOptions(rowType), [rowType]);

    if (loading) {
        return (
            <PageWithMultipleCards
                title={pageTitle}
                primaryAction={exploreModeAction}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <>
            <PageWithMultipleCards
                title={pageTitle}
                primaryAction={exploreModeAction}
                components={[
                    ...(owningPluginName ? [
                        <Box key="owning-plugin" paddingBlockEnd="2">
                            <HorizontalStack gap="1" blockAlign="center">
                                <Badge size="small" status="info">{owningPluginName}</Badge>
                                <Text variant="bodySm" color="subdued">{rowType === "skill" ? "uses this skill" : "uses this MCP Server"}</Text>
                            </HorizontalStack>
                        </Box>,
                    ] : []),
                    <GithubServerTable
                        key={`asset-endpoints-${groupKey}-${rowType}-${refreshKey}`}
                        fetchData={fetchTableData}
                        pageLimit={20}
                        sortOptions={sortOptions}
                        resourceName={resourceName}
                        filters={filtersDef}
                        headers={parentHeaders}
                        selectable={true}
                        isMultipleItemsSelected={true}
                        selectAllAddsRowIds={true}
                        promotedBulkActions={promotedBulkActions}
                        headings={parentHeaders}
                        useNewRow={true}
                        condensedHeight={true}
                        disambiguateLabel={disambiguateLabel}
                        supportsNegationFilter={false}
                    />,
                ]}
            />
            {pluginBundle && (
                <FlyLayout
                    title="Plugin Components"
                    show={showBundleFlyout}
                    setShow={setShowBundleFlyout}
                    components={[
                        <PluginBundleContent
                            key="plugin-bundle"
                            bundle={pluginBundle}
                            pluginCollectionId={collectionIdsRef.current?.[0]}
                            navigate={navigate}
                        />,
                    ]}
                />
            )}
        </>
    );
}

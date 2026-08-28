import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Box, Badge, HorizontalStack, Text } from "@shopify/polaris";
import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import api from "../api";
import func from "@/util/func";
import transform from "../transform";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import { CollectionIcon } from "../../../components/shared/CollectionIcon";
import useTable from "@/apps/dashboard/components/tables/TableContext";
import NewLayoutTooltip from "./NewLayoutTooltip";
import {
    getHeaders,
    resourceName,
    PAGE_LIMIT,
    fetchAndCacheSkillApiData,
    fetchAndCacheAgenticTrafficRiskBundle,
    fetchAndCacheAgenticSensitiveInfo,
} from "./constants";
import { CLIENT_TYPES, ROW_TYPES } from "./mcpClientHelper";
import MisconfiguredBadge from "./MisconfiguredBadge";

const definedTableTabs = ['All', 'AI Agents', 'SaaS Agents', 'MCP Servers', 'LLMs', 'Skills', 'Plugins'];

// "unknown" is treated as disabled — an unreported status is not proof a plugin is active.
const isPluginEnabled = (status) => String(status).toLowerCase() === 'enabled';

// Plugins have no endpoints, risk score, or sensitive data of their own — show which agent they
// belong to instead (the parent/child relationship other rows show via the tree/dropdown), plus
// their reported metadata.
const pluginParentAgentHeader = {
    title: 'AI Agent', text: 'AI Agent', value: 'pluginParentAgentComp', textValue: 'pluginParentAgent', boxWidth: '160px',
};
const pluginMetadataHeaders = [
    { title: 'Status', text: 'Status', value: 'pluginStatusComp', textValue: 'pluginStatus', boxWidth: '90px' },
    { title: 'Scope', text: 'Scope', value: 'pluginScope', boxWidth: '80px' },
    { title: 'Marketplace', text: 'Marketplace', value: 'pluginMarketplace', boxWidth: '160px' },
];

const TAB_TO_CLIENT_TYPE = {
    all: undefined,
    ai_agents: CLIENT_TYPES.AI_AGENT,
    saas_agents: CLIENT_TYPES.SAAS_AGENT,
    mcp_servers: CLIENT_TYPES.MCP_SERVER,
    llms: CLIENT_TYPES.LLM,
    skills: CLIENT_TYPES.SKILL,
    plugins: CLIENT_TYPES.PLUGIN,
};


// Real backend-sortable fields only (AgenticObserveAction.buildSummaryComparator) — "Type" isn't
// supported server-side (matches the new layout's own SORT_FIELD_MAP in AgenticAssetsPage.jsx,
// which doesn't offer it either), so it's dropped here rather than silently falling back to
// risk-score sort. Default (first option) is Risk score, highest first.
// columnIndex is headings' 0-based array position + 1 (GithubServerTable.handleSort's own
// convention — confirmed against this same file's default `sortOptions` export, which uses
// 2/4/5/7 for these identical columns), NOT the raw headings position.
const sortOptions = [
    { label: "Risk score", value: "riskScore desc", directionLabel: "Highest", sortKey: "riskScore", columnIndex: 5 },
    { label: "Risk score", value: "riskScore asc", directionLabel: "Lowest", sortKey: "riskScore", columnIndex: 5 },
    { label: "Name", value: "name asc", directionLabel: "A-Z", sortKey: "name", columnIndex: 2 },
    { label: "Name", value: "name desc", directionLabel: "Z-A", sortKey: "name", columnIndex: 2 },
    { label: "Endpoints", value: "endpointsCount desc", directionLabel: "Highest", sortKey: "endpointsCount", columnIndex: 4 },
    { label: "Endpoints", value: "endpointsCount asc", directionLabel: "Lowest", sortKey: "endpointsCount", columnIndex: 4 },
    { label: "Last traffic seen", value: "lastSeenEpoch desc", directionLabel: "Newest", sortKey: "lastSeenEpoch", columnIndex: 7 },
    { label: "Last traffic seen", value: "lastSeenEpoch asc", directionLabel: "Oldest", sortKey: "lastSeenEpoch", columnIndex: 7 },
];

// Shared choices for the "Tag" / "Endpoint tags" filter toggles — same tag values either way.
const TAG_FILTER_CHOICES = [
    { label: "Contains personal account", value: "Contains personal account" },
    { label: "Local MCP Server", value: "Local MCP Server" },
    { label: "Misconfigured", value: "Misconfigured" },
    { label: "Malicious Skill", value: "Malicious Skill" },
];

// Filter-only header (not a visible column) for the malicious/misconfigured tags on skills —
// drives the "Tag" filter facet server-side (AgenticObserveAction.fetchAgenticAssetsSummary's
// "tags" Set Filter branch) instead of the old client-side facet over a fully-loaded array.
const tagFilterHeader = {
    title: "Tag", text: "Tag", value: "assetTags",
    filterKey: "assetTags", filterLabel: "Tag", showFilter: true,
};

function getRiskScoreStatus(riskScore) {
    if (riskScore >= 4.5) return "critical";
    if (riskScore >= 4) return "attention";
    if (riskScore >= 2.5) return "warning";
    if (riskScore > 0) return "info";
    return "success";
}

// Turns one server-computed row (AgenticObserveAction.fetchAgenticAssetsSummary) into the shape
// this page's headers/cell-renderers expect — mirrors AgenticAssetsPage.jsx's own shapeRow (the
// new layout). Skill rows' risk score and misconfigured badge come from a separate, skill-name-
// keyed enrichment call (fetchAndCacheSkillApiData), not classifyAllGroups' collection-tag-based
// fields — those are deliberately not computed for skill rows, matching prettifyGroupData's
// original client-side behavior. isMalicious for skill rows IS computed server-side already
// (AgenticObserveAction's own account-wide maliciousSkillKeys cache — no client round-trip needed).
function shapeRow(row, { skillScoreMap = {} } = {}) {
    const isSkill = row.rowType === ROW_TYPES.SKILL;
    const isPlugin = row.rowType === ROW_TYPES.PLUGIN;
    // Fan-out rows borrow their parent agent collection's flags, so suppress them on both.
    // Misconfigured is an Agent/MCP-server-only concept — Skill rows never show it, so it stays
    // gated on !isFanout unlike owningPluginName below (which the server computes per-skill-group).
    const isFanout = isSkill || isPlugin;
    // Plugin riskScore is already the plugin's own (overwritten server-side), not the agent's.
    const riskScore = isSkill ? (skillScoreMap[row.name] || 0) : (row.riskScore || 0);

    const showPersonal = row.hasPersonalAccount && !isFanout;
    const showLocalMcp = row.hasLocalMcpServer && !isFanout;
    const showMisconfigured = row.hasMisconfiguredConfig && !isFanout;
    const showMalicious = isSkill && row.isMalicious;
    const owningPluginName = !isPlugin && row.owningPluginName;

    const groupNameDisplay = (showPersonal || showLocalMcp || showMisconfigured || showMalicious || owningPluginName) ? (
        <HorizontalStack gap="2" align="start" wrap={false}>
            <Text>{row.name}</Text>
            {showPersonal && <Badge size="small" status="warning">Contains personal account</Badge>}
            {showLocalMcp && <Badge size="small" status="critical">Local MCP Server</Badge>}
            {showMisconfigured && <MisconfiguredBadge deviceCount={row.misconfiguredDeviceCount} />}
            {showMalicious && <Badge size="small" status="critical">Malicious</Badge>}
            {owningPluginName && <Badge size="small" status="info">{`${owningPluginName} plugin`}</Badge>}
        </HorizontalStack>
    ) : row.name;

    return {
        ...row,
        groupName: row.name,
        groupNameDisplay,
        riskScore,
        riskScoreComp: riskScore ? <Badge status={getRiskScoreStatus(riskScore)} size="small">{riskScore}</Badge> : "-",
        sensitiveSubTypes: transform.prettifySubtypes(row.sensitiveInRespTypes || [], false),
        lastTraffic: row.lastSeenEpoch > 0 ? func.prettifyEpoch(row.lastSeenEpoch) : "-",
        detectedTimestamp: row.lastSeenEpoch,
        iconComp: (
            <Box>
                <CollectionIcon assetTagValue={row.groupKey} displayName={row.name} />
            </Box>
        ),
        pluginVersion: row.pluginVersion || "-",
        pluginScope: row.pluginScope || "-",
        pluginMarketplace: row.pluginMarketplace || "-",
        pluginStatus: row.pluginStatus || "",
        pluginStatusComp: isPlugin ? (
            <Badge size="small" status={isPluginEnabled(row.pluginStatus) ? "success" : "warning"}>
                {isPluginEnabled(row.pluginStatus) ? "enabled" : "disabled"}
            </Badge>
        ) : "-",
        // Already formatted server-side (McpClientRegistry.formatDisplayName) — e.g. "Claude", not
        // the raw "claude"/"claudecli" tag value.
        pluginParentAgent: row.pluginParentAgent || "-",
        pluginParentAgentComp: isPlugin && row.pluginParentAgent ? (
            <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                <CollectionIcon assetTagValue={row.pluginParentAgent} displayName={row.pluginParentAgent} />
                <Text variant="bodyMd">{row.pluginParentAgent}</Text>
            </HorizontalStack>
        ) : "-",
        assetTags: [
            ...(showPersonal ? ["Contains personal account"] : []),
            ...(showLocalMcp ? ["Local MCP Server"] : []),
            ...(showMisconfigured ? ["Misconfigured"] : []),
            ...(showMalicious ? ["Malicious Skill"] : []),
        ],
    };
}

function Endpoints() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [tableLoading, setTableLoading] = useState(false);
    const agenticNewLayout = LocalStore((state) => state.agenticNewLayout);
    const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

    useEffect(() => {
        if (agenticNewLayout) {
            navigate("/dashboard/observe/agentic-assets", { replace: true });
        }
    }, [navigate, agenticNewLayout]);

    const [stats, setStats] = useState({ totalAssets: 0, totalEndpoints: 0, countsByType: {} });
    const [refreshKey, setRefreshKey] = useState(0);

    const { tabsInfo } = useTable();
    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab = tableSelectedTab[window.location.pathname] || "ai_agents";
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected] = useState(func.getTableTabIndexById(1, definedTableTabs, initialSelectedTab));

    // Everything fetchTableData/shapeRow need but the paginated table endpoint doesn't carry
    // itself — populated once at mount (Tier 1: trafficMap/riskScoreMap/sensitiveMap; Tier 2,
    // async: skill risk/malicious/misconfigured data), read (not reacted to) by fetchTableData.
    const enrichRef = useRef({
        trafficMap: {}, riskScoreMap: {}, sensitiveMap: {}, usernameMap: {},
        skillScoreMap: {},
    });
    // Progressively populated from each page's distinctUsernames (server-computed, current page
    // only) — same pattern as AgenticAssetDevicesPage.jsx's filterChoices/updateFilterChoicesIfChanged.
    const [usernameChoices, setUsernameChoices] = useState([]);

    // headings drives the rendered COLUMNS; headers drives FILTERS/CSV export (GithubServerTable's
    // own convention) — tagFilterHeader is filter-only, so it belongs in headers, not headings.
    const headings = useMemo(() => {
        const h = getHeaders();
        h[1] = { ...h[1], value: "groupNameDisplay" };
        if (selectedTab === "plugins") {
            return [
                ...h.filter((col) => col.value !== "lastTraffic"),
                pluginParentAgentHeader,
                ...pluginMetadataHeaders,
            ];
        }
        return h;
    }, [selectedTab]);
    const headers = useMemo(() => [...headings, tagFilterHeader], [headings]);
    // GithubServerTable renders filter chips from a separate `filters` prop (Polaris IndexFilters
    // shape: {key, label, choices}) — headers' filterKey only feeds filterOperators/CSV export,
    // it does NOT surface a UI facet on its own (confirmed via UsersAndDevices.jsx's filtersDef).
    const filtersDef = useMemo(() => {
        const defs = [
            { key: "assetTags", label: "Tag", choices: TAG_FILTER_CHOICES },
            // Same choices as "Tag" — a separate toggle, merged into the same backend filter below.
            { key: "endpointTags", label: "Endpoint tags", choices: TAG_FILTER_CHOICES },
        ];
        if (usernameChoices.length) {
            defs.push({ key: "username", label: "Username", choices: usernameChoices.map((u) => ({ label: u, value: u })) });
        }
        return defs;
    }, [usernameChoices]);

    const activeSortOptions = useMemo(
        () => (selectedTab === "plugins" ? sortOptions.filter((o) => o.sortKey !== "lastSeenEpoch") : sortOptions),
        [selectedTab],
    );


    const tableCountObj = func.getTabsCount(definedTableTabs, {
        _counts: {
            all: stats.totalAssets,
            ai_agents: stats.countsByType[CLIENT_TYPES.AI_AGENT] || 0,
            saas_agents: stats.countsByType[CLIENT_TYPES.SAAS_AGENT] || 0,
            mcp_servers: stats.countsByType[CLIENT_TYPES.MCP_SERVER] || 0,
            llms: stats.countsByType[CLIENT_TYPES.LLM] || 0,
            skills: stats.countsByType[CLIENT_TYPES.SKILL] || 0,
            plugins: stats.countsByType[CLIENT_TYPES.PLUGIN] || 0,
        },
    });
    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tableCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo,
    // Polaris' Tab type has exactly one badge slot (string) — concatenating "Beta" into it would
    // read as one pill mixing a status label with a count. Put "Beta" in the tab's own label text
    // instead, so badge stays a clean, separate count pill.
    ).map((tab) => (tab.id === 'plugins' ? { ...tab, content: `${tab.content} (Beta)` } : tab));

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            // getLastTrafficSeen/getRiskScoreInfo are cached (PersistStore, 2-min TTL, in-flight-
            // deduped) — this leaner sibling of fetchAndCacheAgenticCollectionsBundle skips
            // getAllCollectionsBasic entirely (see AgenticAssetsPage.jsx's own switch to it).
            // Sensitive info is cached separately since only this page + UsersAndDevices.jsx use it.
            const [trafficRiskBundle, sensitiveMap, shieldResult] = await Promise.all([
                fetchAndCacheAgenticTrafficRiskBundle({ api, PersistStore }),
                fetchAndCacheAgenticSensitiveInfo({ api, PersistStore }),
                fetchEndpointShieldUserMetadata().catch(() => ({})),
            ]);
            if (!isMountedRef.current) return;

            const { trafficMap = {}, riskScoreMap = {} } = trafficRiskBundle || {};
            const { usernameMap = {} } = shieldResult || {};
            enrichRef.current = { ...enrichRef.current, trafficMap, riskScoreMap, sensitiveMap, usernameMap };
            setRefreshKey((k) => k + 1); // mount the table now that enrichRef is populated
            setLoading(false);

            api.fetchAgenticAssetsStats({ trafficMap, riskScoreMap }).then((result) => {
                if (isMountedRef.current) setStats(result);
            }).catch(() => {});

            // Async enrichment — single account-wide call (no per-collection N+1), updates skill risk
            // scores/misconfigured flags after initial render, then re-fetches the current table page
            // (via refreshKey) so badges appear without losing sort/search/tab. Doesn't read
            // maliciousSkillKeys here — "Malicious" (row.isMalicious) is computed server-side in
            // fetchAgenticAssetsSummary (AgenticObserveAction.getOrBuildSkillData's own account-wide
            // cache) instead of requiring the whole set re-POSTed on every paginated request.
            fetchAndCacheSkillApiData([], { api, PersistStore }).then(({ skillScoreMap }) => {
                if (!isMountedRef.current) return;
                enrichRef.current = {
                    ...enrichRef.current,
                    skillScoreMap: skillScoreMap || {},
                };
                setRefreshKey((k) => k + 1);
            }).catch(() => {});
        } catch {
            setLoading(false);
        }
    }

    useEffect(() => {
        const isMountedRef = { current: true };
        fetchData(isMountedRef);
        return () => { isMountedRef.current = false; };
    }, []);

    const fetchTableData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        setTableLoading(true);
        try {
            const { trafficMap, riskScoreMap, sensitiveMap, usernameMap, skillScoreMap } = enrichRef.current;
            // GithubServerTable: asc=-1/desc=1, inverted vs Mongo (matches AgenticAssetsPage.jsx/
            // NhiGovernanceIdentitiesAction's own onServerFetch convention).
            const mongoSortOrder = sortOrder === -1 ? 1 : -1;
            const clientType = TAB_TO_CLIENT_TYPE[selectedTab];
            const tagValues = filtersObj?.assetTags;
            const endpointTagValues = filtersObj?.endpointTags;
            const usernameValues = filtersObj?.username;
            const filters = {};
            if (clientType) filters.type = [clientType];
            // Union "Tag" and "Endpoint tags" — two UI filters, one backend field.
            const combinedTags = [...new Set([...(tagValues || []), ...(endpointTagValues || [])])];
            if (combinedTags.length) filters.tags = combinedTags;
            if (usernameValues?.length) filters.username = usernameValues;

            const res = await api.fetchAgenticAssetsSummary({
                skip,
                limit,
                sortKey: sortKey || "riskScore",
                sortOrder: mongoSortOrder,
                queryValue,
                trafficMap, riskScoreMap, sensitiveMap, usernameMap,
                filters: Object.keys(filters).length ? filters : undefined,
            });
            // Same progressive-population pattern as AgenticAssetDevicesPage.jsx — union rather than
            // replace, so choices survive across page turns instead of shrinking to just the current page.
            const newUsernames = res.distinctUsernames || [];
            if (newUsernames.length) {
                setUsernameChoices((prev) => Array.from(new Set([...prev, ...newUsernames])).sort());
            }
            // Misconfigured is an Agent/MCP-server-only concept — Skill rows never show it (see
            // shapeRow's own comment), so no misconfiguredSkills lookup is threaded through here.
            const rows = (res.rows || []).map((row) => shapeRow(row, { skillScoreMap }));
            return { value: rows, total: res.total || 0 };
        } finally {
            setTableLoading(false);
        }
    }, [selectedTab]);

    const disambiguateLabel = useCallback((key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }, []);

    // Row click navigates immediately (no await first) to a dedicated, paginated device/endpoint
    // view scoped to just this one asset — groupKey/rowType/name/type travel via query params, the
    // destination page does its own lazy fetchAgenticAssetDetail + fetchAgenticAssetDevicesPage.
    // Deliberately NOT navigating to Inventory any more: that needed the whole account's collections
    // (getAllCollectionsBasic) just to build a filter, and navigating only after that resolved made
    // every click feel like it froze for several seconds with no feedback. Navigating first means the
    // loading spinner the user sees is on the new page, not a stuck click on this one.
    const handleRowClick = useCallback((row) => {
        const params = new URLSearchParams({
            groupKey: row.groupKey || "",
            rowType: row.rowType || "",
            name: row.name || "",
            type: row.clientType || "",
        });
        navigate(`/dashboard/observe/agentic-assets-legacy/devices?${params.toString()}`);
    }, [navigate]);

    const summaryItems = useMemo(() => [
        {
            title: "Agentic assets",
            data: transform.formatNumberWithCommas(stats.totalAssets),
        },
        {
            title: "Total endpoints",
            data: transform.formatNumberWithCommas(stats.totalEndpoints),
        },
    ], [stats]);

    const pageTitle = useMemo(() => (
        <TitleWithInfo
            tooltipContent="View agentic assets"
            titleText={"Agentic assets"}
            docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
        />
    ), []);

    const layoutToggle = (
        <NewLayoutTooltip checked={false} onChange={() => { setAgenticNewLayout(true); navigate("/dashboard/observe/agentic-assets"); }} />
    );

    if (loading) {
        return (
            <PageWithMultipleCards
                title={pageTitle}
                isFirstPage={true}
                secondaryActions={layoutToggle}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={pageTitle}
            isFirstPage={true}
            secondaryActions={layoutToggle}
            components={[
                <SummaryCardInfo summaryItems={summaryItems} key="summary" />,
                <GithubServerTable
                    key={`endpoints-table-${selectedTab}-${refreshKey}`}
                    fetchData={fetchTableData}
                    pageLimit={PAGE_LIMIT}
                    sortOptions={activeSortOptions}
                    resourceName={resourceName}
                    filters={filtersDef}
                    headers={headers}
                    selectable={false}
                    mode={IndexFiltersMode.Default}
                    headings={headings}
                    useNewRow={true}
                    condensedHeight={true}
                    disambiguateLabel={disambiguateLabel}
                    tableTabs={tableTabs}
                    onSelect={(i) => setSelected(i)}
                    selected={selected}
                    onRowClick={handleRowClick}
                    rowClickable={true}
                    supportsNegationFilter={false}
                    loading={tableLoading}
                    loadingText="Loading agentic assets..."
                />,
            ]}
        />
    );
}

export default Endpoints;

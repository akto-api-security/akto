import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { Badge, HorizontalStack, Text, IndexFiltersMode, Tooltip } from "@shopify/polaris";
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
import NewLayoutTooltip from "./NewLayoutTooltip";
import EditDeviceTagsModal from "./EditDeviceTagsModal";
import {
    getHeaders,
    getSortOptionsWithoutIconColumn,
    PAGE_LIMIT,
    INVENTORY_PATH,
    INVENTORY_FILTER_KEY,
    buildAgenticInventoryFilterForRow,
    fetchAndCacheSkillApiData,
    // Note: this page's malicious-skill badge stays name-only (enrichRef.maliciousSkills), not the
    // collection-scoped hasMaliciousSkillInCollections/maliciousSkillKeys AgenticAssetsPage.jsx now
    // uses — HostGroupSummary only tracks a flat skill-name set per group, not per-collection skill
    // keys. Slightly less precise (a same-named skill on a different user could false-positive this
    // badge); a real fix would need a small Java-side addition, not done here to bound this change.
    fetchAndCacheAgenticCollectionsBundle,
    fetchAndCacheAgenticSensitiveInfo,
    settledValue,
    logRejected,
} from "./constants";
import { CLIENT_TYPES, hasMisconfiguredConfigTag } from "./mcpClientHelper";
import AgenticCountBadges from "./AgenticCountBadges";

const definedTableTabs = ["Users", "Devices"];

const usersAndDevicesCountColumnOpts = {
    endpointsColumnLabel: "Agentic assets",
    endpointsColumnBoxWidth: "120px",
};

// AG Grid/GithubServerTable field name -> backend sortKey (AgenticObserveAction.buildHostGroupComparator).
const SORT_FIELD_MAP = { groupName: "name", riskScore: "riskScore", endpointsCount: "endpointsCount", detectedTimestamp: "lastSeenEpoch" };

const getRiskScoreStatus = (riskScore) => {
    if (riskScore >= 4.5) return "critical";
    if (riskScore >= 4) return "attention";
    if (riskScore >= 2.5) return "warning";
    if (riskScore > 0) return "info";
    return "success";
};

// Compact "key=value" badge for the single Tags column — just the first tag inline, the
// rest collapsed behind a "+N" badge with a tooltip listing them.
const MAX_VISIBLE_TAGS = 1;
function buildTagsDisplay(tags) {
    if (!tags || tags.length === 0) return null;
    const shown = tags.slice(0, MAX_VISIBLE_TAGS);
    const rest = tags.slice(MAX_VISIBLE_TAGS);
    return (
        <HorizontalStack gap="1" wrap={false}>
            {shown.map((t, i) => (
                <Badge key={`${t.key}-${i}`} size="small" status="info">{`${t.key}=${t.value}`}</Badge>
            ))}
            {rest.length > 0 && (
                <Tooltip content={rest.map((t) => `${t.key}=${t.value}`).join(", ")} dismissOnMouseOut>
                    <Badge size="small" status="info">{`+${rest.length}`}</Badge>
                </Tooltip>
            )}
        </HorizontalStack>
    );
}

function UsersAndDevices() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [tableLoading, setTableLoading] = useState(false);
    const agenticNewLayout = LocalStore((state) => state.agenticNewLayout);
    const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

    useEffect(() => {
        if (agenticNewLayout) {
            navigate("/dashboard/observe/endpoints", { replace: true });
        }
    }, [navigate, agenticNewLayout]);

    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab = tableSelectedTab[window.location.pathname] || "users";
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected] = useState(func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab));
    const isUsersTab = selectedTab === "users";

    const filtersMap = PersistStore((state) => state.filtersMap);
    const setFiltersMap = PersistStore((state) => state.setFiltersMap);

    const [stats, setStats] = useState({ usersCount: 0, devicesCount: 0, usersAgenticAssetsTotal: 0, devicesAgenticAssetsTotal: 0, usernames: [], tagKeys: [] });
    const [refreshKey, setRefreshKey] = useState(0);

    const [tagUsernames, setTagUsernames] = useState(null);

    // Everything the paginated fetch needs but the row itself doesn't carry, plus a stash of the
    // most-recently-fetched page's full row objects (for the bulk "Edit device tags" action, which
    // only gets selected IDs from GithubServerTable — selection is only ever made on currently
    // rendered rows, so this is always in sync).
    const enrichRef = useRef({
        trafficMap: {}, riskScoreMap: {}, sensitiveMap: {},
        maliciousSkills: new Set(),
    });
    const lastRowsRef = useRef([]);

    useEffect(() => {
        const isMountedRef = { current: true };
        (async () => {
            try {
                const [collectionsBundle, sensitiveMap] = await Promise.all([
                    fetchAndCacheAgenticCollectionsBundle({ api, PersistStore }),
                    fetchAndCacheAgenticSensitiveInfo({ api, PersistStore }),
                ]);
                if (!isMountedRef.current) return;

                const { trafficMap = {}, riskScoreMap = {} } = collectionsBundle || {};

                enrichRef.current = {
                    ...enrichRef.current,
                    trafficMap, riskScoreMap, sensitiveMap: sensitiveMap || {},
                };
                setLoading(false);
                setRefreshKey((k) => k + 1);

                fetchAndCacheSkillApiData([], { api, PersistStore })
                    .then(({ maliciousSkills }) => {
                        if (!isMountedRef.current || !maliciousSkills?.size) return;
                        enrichRef.current = { ...enrichRef.current, maliciousSkills };
                    })
                    .catch(() => {});
            } catch (e) {
                // eslint-disable-next-line no-console
                console.error("UsersAndDevices mount fetch failed:", e);
                if (isMountedRef.current) setLoading(false);
            }
        })();
        return () => { isMountedRef.current = false; };
    }, []);

    const loadStats = useCallback(async () => {
        try {
            const { trafficMap, riskScoreMap } = enrichRef.current;
            // Identity maps are no longer sent — AgenticObserveAction resolves them server-side now.
            const result = await api.fetchUsersAndDevicesStats({ trafficMap, riskScoreMap });
            setStats(result);
        } catch (e) {
            // eslint-disable-next-line no-console
            console.error("fetchUsersAndDevicesStats failed:", e);
        }
    }, []);

    useEffect(() => {
        if (refreshKey === 0) return;
        loadStats();
    }, [loadStats, refreshKey]);

    const buildGroupNameDisplay = useCallback((row, extraBadges = []) => {
        const badges = [...extraBadges];
        if (row.hasPersonalAccount) badges.push(<Badge key="personal" size="small" status="warning">Contains personal account</Badge>);
        if (row.hasLocalMcpServer) badges.push(<Badge key="local-mcp" size="small" status="critical">Local MCP Server</Badge>);
        if (!badges.length) return row.groupName;
        return (
            <HorizontalStack gap="2" align="start" wrap={false}>
                <Text>{row.groupName}</Text>
                {badges}
            </HorizontalStack>
        );
    }, []);

    // Mirrors the original prettifyGroupData/buildGroupNameDisplay pipeline, operating on one
    // server-paginated page of rows instead of the full in-memory array.
    const prettifyRows = useCallback((rows) => {
        const maliciousSkills = enrichRef.current.maliciousSkills;
        return rows.map((row) => {
            const hasMalicious = (row.uniqueSkillNames || []).some((s) => maliciousSkills.has(s));
            const extraBadges = [
                <AgenticCountBadges
                    key="counts"
                    countsByType={row.countsByType || {}}
                    skillCount={(row.uniqueSkillNames || []).length}
                    pluginCount={row.countsByType?.[CLIENT_TYPES.PLUGIN] || 0}
                />,
            ];
            if (hasMalicious) extraBadges.push(<Badge key="malicious" size="small" status="critical">Malicious Skills</Badge>);
            return {
                ...row,
                groupNameDisplay: buildGroupNameDisplay(row, extraBadges),
                tagsDisplay: buildTagsDisplay(row.tags),
                sensitiveSubTypes: transform.prettifySubtypes(row.sensitiveInRespTypes || [], false),
                sensitiveSubTypesVal: (row.sensitiveInRespTypes || []).join(" ") || "-",
                riskScoreComp: row.riskScore != null ? transform.wrapRiskScoreTooltip(
                    <Badge status={getRiskScoreStatus(row.riskScore)} size="small">{row.riskScore}</Badge>,
                    row.riskScore, null, null, row.riskScoreFactors
                ) : "-",
                detectedTimestamp: row.lastSeenEpoch,
                lastTraffic: func.prettifyEpoch(row.lastSeenEpoch),
            };
        });
    }, [buildGroupNameDisplay]);

    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        setTableLoading(true);
        try {
            const { trafficMap, riskScoreMap, sensitiveMap } = enrichRef.current;
            const mappedSortKey = SORT_FIELD_MAP[sortKey] || "riskScore";
            const mongoSortOrder = sortOrder === -1 ? 1 : -1; // GithubServerTable: asc=-1/desc=1, inverted vs Mongo
            const filters = {};
            if (filtersObj?.tags?.length) filters.tags = filtersObj.tags;
            if (filtersObj?.username?.length) filters.username = filtersObj.username;
            const res = await api.fetchUsersAndDevicesSummary({
                groupBy: isUsersTab ? "user" : "device",
                skip, limit, sortKey: mappedSortKey, sortOrder: mongoSortOrder, queryValue, filters,
                trafficMap, riskScoreMap, sensitiveMap,
            });
            const prettified = prettifyRows(res.rows || []);
            lastRowsRef.current = prettified;
            return { value: prettified, total: res.total || 0 };
        } finally {
            setTableLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isUsersTab, prettifyRows]);

    const headers = useMemo(() => {
        const h = getHeaders({
            primaryColumnTitle: isUsersTab ? "User" : "Device",
            primaryColumnText: isUsersTab ? "User" : "Device",
            includeIconColumn: false,
            includeUserColumns: isUsersTab,
            ...usersAndDevicesCountColumnOpts,
        });
        h[0] = { ...h[0], value: "groupNameDisplay" };
        return h;
    }, [isUsersTab]);

    const sortOptionsNoIcon = useMemo(
        () => getSortOptionsWithoutIconColumn(usersAndDevicesCountColumnOpts),
        [],
    );

    const filtersDef = useMemo(() => (isUsersTab ? [
        { key: "tags", label: "Tags", choices: (stats.tagKeys || []).map((t) => ({ label: t, value: t })) },
    ] : [
        // Devices tab has no Tags column (includeUserColumns=false above), but each row's owner
        // username is already resolved server-side (HostGroupSummary.username) — filterable even
        // without a dedicated visible column, same as searching by it already implicitly works via groupName.
        { key: "username", label: "User", choices: (stats.usernames || []).map((u) => ({ label: u, value: u })) },
    ]), [isUsersTab, stats.tagKeys, stats.usernames]);

    const disambiguateLabel = useCallback((key, value) => func.convertToDisambiguateLabelObj(value, null, 2), []);

    const handleSelectedTab = useCallback((selectedIndex) => {
        setSelected(selectedIndex);
        const tab = selectedIndex === 0 ? "users" : "devices";
        setSelectedTab(tab);
        setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tab });
    }, [tableSelectedTab, setTableSelectedTab]);

    const handleRowClick = useCallback((row) => {
        const updatedFiltersMap = { ...filtersMap };
        const filterPayload = buildAgenticInventoryFilterForRow(row);
        if (filterPayload) {
            updatedFiltersMap[INVENTORY_FILTER_KEY] = filterPayload;
        } else {
            delete updatedFiltersMap[INVENTORY_FILTER_KEY];
        }
        delete updatedFiltersMap[`${INVENTORY_FILTER_KEY}agent-tree/`];
        setFiltersMap(updatedFiltersMap);
        setTableSelectedTab({ ...tableSelectedTab, [INVENTORY_PATH]: "hostname" });
        setTimeout(() => navigate(INVENTORY_PATH), 0);
    }, [filtersMap, setFiltersMap, navigate, tableSelectedTab, setTableSelectedTab]);

    const openEditTagModal = useCallback((usernames) => setTagUsernames(usernames), []);
    const promotedBulkActions = useCallback((selectedIds) => {
        if (!isUsersTab) return [];
        return [{ content: 'Edit device tags', onAction: () => openEditTagModal(selectedIds), requires: 'api/bulkUpdateUserDeviceTag' }];
    }, [isUsersTab, openEditTagModal]);

    const summaryItems = useMemo(() => [
        {
            title: isUsersTab ? "Users" : "Devices",
            data: transform.formatNumberWithCommas(isUsersTab ? stats.usersCount : stats.devicesCount),
        },
        {
            title: "Agentic assets",
            data: transform.formatNumberWithCommas(isUsersTab ? stats.usersAgenticAssetsTotal : stats.devicesAgenticAssetsTotal),
        },
    ], [isUsersTab, stats]);

    const summaryComponent = useMemo(() => <SummaryCardInfo summaryItems={summaryItems} key="summary" />, [summaryItems]);

    const resourceName = useMemo(
        () => (isUsersTab ? { singular: "user", plural: "users" } : { singular: "device", plural: "devices" }),
        [isUsersTab],
    );

    const tableTabs = useMemo(() => ([
        { id: "users", content: `Users (${transform.formatNumberWithCommas(stats.usersCount)})` },
        { id: "devices", content: `Devices (${transform.formatNumberWithCommas(stats.devicesCount)})` },
    ]), [stats]);

    const pageTitle = useMemo(() => (
        <TitleWithInfo
            tooltipContent="View agentic activity by user or device; open inventory with the same filters as Agentic assets."
            titleText="Users and devices"
            docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
        />
    ), []);

    const layoutToggle = (
        <NewLayoutTooltip checked={false} onChange={() => { setAgenticNewLayout(true); navigate("/dashboard/observe/endpoints"); }} />
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
        <>
            <PageWithMultipleCards
                title={pageTitle}
                isFirstPage={true}
                secondaryActions={layoutToggle}
                components={[
                    summaryComponent,
                    <GithubServerTable
                        key={`users-and-devices-${selectedTab}-${refreshKey}`}
                        pageLimit={PAGE_LIMIT}
                        fetchData={fetchData}
                        sortOptions={sortOptionsNoIcon}
                        resourceName={resourceName}
                        filters={filtersDef}
                        headers={headers}
                        headings={headers}
                        selectable={isUsersTab}
                        mode={IndexFiltersMode.Default}
                        useNewRow={true}
                        condensedHeight={true}
                        disambiguateLabel={disambiguateLabel}
                        onRowClick={handleRowClick}
                        promotedBulkActions={promotedBulkActions}
                        tableTabs={tableTabs}
                        selected={selected}
                        onSelect={handleSelectedTab}
                        supportsNegationFilter={false}
                        loading={tableLoading}
                        loadingText={isUsersTab ? "Loading users..." : "Loading devices..."}
                    />,
                ]}
            />
            {tagUsernames && (
                <EditDeviceTagsModal
                    usernames={tagUsernames}
                    rows={lastRowsRef.current}
                    onClose={() => setTagUsernames(null)}
                    onSaved={() => { setTagUsernames(null); setRefreshKey((k) => k + 1); }}
                />
            )}
        </>
    );
}

export default UsersAndDevices;

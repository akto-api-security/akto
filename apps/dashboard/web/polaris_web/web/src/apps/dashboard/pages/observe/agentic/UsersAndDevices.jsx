import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { Badge, HorizontalStack, Text, Modal, FormLayout, Banner, VerticalStack, IndexFiltersMode, TextField, Button, Box, Divider, Tooltip, Checkbox } from "@shopify/polaris";
import { DeleteMinor } from "@shopify/polaris-icons";
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
import settingRequests from "../../settings/api";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import NewLayoutTooltip from "./NewLayoutTooltip";
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
} from "./constants";
import { hasMisconfiguredConfigTag } from "./mcpClientHelper";

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

    // entries: [{key, value, source}] — one row per raw tag for the selected user(s); a key can
    // have more than one row (different sources, e.g. manual + Okta, all coexisting). Only
    // source==='manual' rows are ever edited/removed here.
    // isBulk (>1 user selected): existing per-user tags are never prefilled/shown, since they can
    // differ across users and silently copying one user's values onto the rest would be a data-loss
    // footgun. Bulk mode only supports adding a new tag (applied to all) or clearing all manual tags.
    const emptyEditTagModal = { active: false, usernames: [], isBulk: false, entries: [], originalManualKeys: [], clearAll: false, newKey: '', newValue: '', saving: false };
    const [editTagModal, setEditTagModal] = useState(emptyEditTagModal);

    // Everything the paginated fetch needs but the row itself doesn't carry, plus a stash of the
    // most-recently-fetched page's full row objects (for the bulk "Edit device tags" action, which
    // only gets selected IDs from GithubServerTable — selection is only ever made on currently
    // rendered rows, so this is always in sync).
    const enrichRef = useRef({
        trafficMap: {}, riskScoreMap: {}, sensitiveMap: {}, usernameMap: {}, userMetadataMap: {},
        maliciousSkills: new Set(),
    });
    const lastRowsRef = useRef([]);

    useEffect(() => {
        const isMountedRef = { current: true };
        (async () => {
            try {
                const [collectionsBundle, sensitiveMap, shieldResult] = await Promise.all([
                    fetchAndCacheAgenticCollectionsBundle({ api, PersistStore }),
                    fetchAndCacheAgenticSensitiveInfo({ api, PersistStore }),
                    fetchEndpointShieldUserMetadata(),
                ]);
                if (!isMountedRef.current) return;

                const { trafficMap = {}, riskScoreMap = {} } = collectionsBundle || {};
                const { usernameMap = {}, userMetadataMap = {} } = shieldResult || {};

                enrichRef.current = {
                    ...enrichRef.current,
                    trafficMap, riskScoreMap, sensitiveMap: sensitiveMap || {}, usernameMap, userMetadataMap,
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

    const tagsByUsernameFor = useCallback((userMetadataMap) => Object.fromEntries(
        Object.entries(userMetadataMap || {}).map(([u, m]) => [u, m.tags || []])
    ), []);

    const loadStats = useCallback(async () => {
        try {
            const { trafficMap, riskScoreMap, usernameMap, userMetadataMap } = enrichRef.current;
            const result = await api.fetchUsersAndDevicesStats({
                trafficMap, riskScoreMap, usernameMap, userMetadataMap,
                tagsByUsername: tagsByUsernameFor(userMetadataMap),
            });
            setStats(result);
        } catch (e) {
            // eslint-disable-next-line no-console
            console.error("fetchUsersAndDevicesStats failed:", e);
        }
    }, [tagsByUsernameFor]);

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
            const extraBadges = hasMalicious ? [<Badge key="malicious" size="small" status="critical">Malicious Skills</Badge>] : [];
            return {
                ...row,
                groupNameDisplay: buildGroupNameDisplay(row, extraBadges),
                tagsDisplay: buildTagsDisplay(row.tags),
                sensitiveSubTypes: transform.prettifySubtypes(row.sensitiveInRespTypes || [], false),
                sensitiveSubTypesVal: (row.sensitiveInRespTypes || []).join(" ") || "-",
                riskScoreComp: row.riskScore != null ? (
                    <Badge status={getRiskScoreStatus(row.riskScore)} size="small">{row.riskScore}</Badge>
                ) : "-",
                detectedTimestamp: row.lastSeenEpoch,
                lastTraffic: func.prettifyEpoch(row.lastSeenEpoch),
            };
        });
    }, [buildGroupNameDisplay]);

    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        setTableLoading(true);
        try {
            const { trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap } = enrichRef.current;
            const mappedSortKey = SORT_FIELD_MAP[sortKey] || "riskScore";
            const mongoSortOrder = sortOrder === -1 ? 1 : -1; // GithubServerTable: asc=-1/desc=1, inverted vs Mongo
            const filters = {};
            if (filtersObj?.tags?.length) filters.tags = filtersObj.tags;
            if (filtersObj?.username?.length) filters.username = filtersObj.username;
            const res = await api.fetchUsersAndDevicesSummary({
                groupBy: isUsersTab ? "user" : "device",
                skip, limit, sortKey: mappedSortKey, sortOrder: mongoSortOrder, queryValue, filters,
                trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap,
                tagsByUsername: tagsByUsernameFor(userMetadataMap),
            });
            const prettified = prettifyRows(res.rows || []);
            lastRowsRef.current = prettified;
            return { value: prettified, total: res.total || 0 };
        } finally {
            setTableLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isUsersTab, prettifyRows, tagsByUsernameFor]);

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

    const openEditTagModal = useCallback((usernames) => {
        if (usernames.length > 1) {
            // Bulk mode: don't prefill from any one user's tags — they can differ across the
            // selection, and pre-filling from just one would risk silently overwriting the rest.
            setEditTagModal({
                active: true,
                usernames,
                isBulk: true,
                entries: [],
                originalManualKeys: [],
                clearAll: false,
                newKey: '',
                newValue: '',
                saving: false,
            });
            return;
        }
        const firstUser = lastRowsRef.current.find((r) => usernames.includes(r.id));
        // One row per raw tag — a key can have more than one value/source at once (e.g. a
        // manually-set "team" alongside an Okta-synced "team"), and all of them coexist rather
        // than one overriding the other, so all are shown.
        const entries = (firstUser?.tags || []).map((t) => ({ key: t.key, value: t.value, source: t.source }));
        setEditTagModal({
            active: true,
            usernames,
            isBulk: false,
            entries,
            originalManualKeys: entries.filter((e) => e.source === 'manual').map((e) => e.key),
            clearAll: false,
            newKey: '',
            newValue: '',
            saving: false,
        });
    }, []);

    const closeEditTagModal = useCallback(() => {
        setEditTagModal(emptyEditTagModal);
    }, []);

    // Only manual rows are ever edited/removed here — tags from any other source (e.g. Okta) are
    // shown read-only, since this dashboard action only ever writes manual-source tags and a
    // synced value would just be overwritten again on the next sync anyway.
    // Identified by index, not key — a key can now have multiple manual values (e.g. team=akto
    // and team=razorpay both applying at once), so key alone no longer uniquely names an entry.
    const updateEntryValue = useCallback((idx, value) => {
        setEditTagModal((prev) => ({
            ...prev,
            entries: prev.entries.map((e, i) => (i === idx && e.source === 'manual' ? { ...e, value } : e)),
        }));
    }, []);

    const removeEntry = useCallback((idx) => {
        setEditTagModal((prev) => ({
            ...prev,
            entries: prev.entries.filter((e, i) => !(i === idx && e.source === 'manual')),
        }));
    }, []);

    const addEntry = useCallback(() => {
        const key = editTagModal.newKey.trim().toLowerCase();
        const value = editTagModal.newValue.trim();
        if (!key) {
            func.setToast(true, true, "Device tag key cannot be empty");
            return;
        }
        // A key can have multiple manual values at once (e.g. team=akto and team=razorpay both
        // apply) — only guard against adding the exact same key+value pair twice.
        if (editTagModal.entries.some((e) => e.key === key && e.value === value && e.source === 'manual')) {
            func.setToast(true, true, "This tag already exists.");
            return;
        }
        setEditTagModal((prev) => ({
            ...prev,
            entries: [...prev.entries, { key, value, source: 'manual' }],
            newKey: '',
            newValue: '',
        }));
    }, [editTagModal.newKey, editTagModal.newValue, editTagModal.entries]);

    const saveEditTag = useCallback(async () => {
        setEditTagModal((prev) => ({ ...prev, saving: true }));
        try {
            const selectedRows = lastRowsRef.current.filter((r) => editTagModal.usernames.includes(r.id));
            const groupNames = selectedRows.map((r) => r.groupName).filter(Boolean);

            // Only manual rows are ever sent — this action only writes the "manual" source, never
            // touching tags synced in from elsewhere. A key with no non-empty manual values left
            // (all cleared, or removed down to nothing) is sent as [] — an explicit clear — so
            // clearing/removing every value un-pins the manual override instead of just hiding it
            // locally; a same-key tag from another source (if any) is untouched either way.
            const manualEntries = editTagModal.entries.filter((e) => e.source === 'manual');
            const tags = {};
            manualEntries.forEach((e) => {
                if (!(e.key in tags)) tags[e.key] = [];
                if (e.value) tags[e.key].push(e.value);
            });
            if (editTagModal.isBulk) {
                // "Clear all" in bulk mode clears every manually-set key any selected row currently
                // has — computed from the union of their real tags, never guessed from one row's set.
                // Tags from other sources aren't affected.
                if (editTagModal.clearAll) {
                    selectedRows.forEach((r) => (r.tags || []).forEach((t) => {
                        if (t?.key && t.source === 'manual' && !(t.key in tags)) tags[t.key] = [];
                    }));
                }
            } else {
                const currentManualKeys = new Set(manualEntries.map((e) => e.key));
                editTagModal.originalManualKeys.forEach((k) => { if (!currentManualKeys.has(k)) tags[k] = []; });
            }

            await settingRequests.bulkUpdateUserDeviceTag(groupNames, tags);

            // fetchData/loadStats build tagsByUsername from enrichRef.current.userMetadataMap, which
            // was only ever fetched once at mount — without refreshing it here, the table/stats
            // refetch below would keep sending the pre-edit tags and the save would look like it
            // silently did nothing. force=true also bypasses fetchEndpointShieldUserMetadata's TTL
            // cache, which would otherwise likely still be warm from that same mount fetch.
            const shieldResult = await fetchEndpointShieldUserMetadata(true);
            enrichRef.current = {
                ...enrichRef.current,
                usernameMap: shieldResult?.usernameMap || {},
                userMetadataMap: shieldResult?.userMetadataMap || {},
            };

            func.setToast(true, false, "Tags updated successfully");
            closeEditTagModal();
            setRefreshKey((k) => k + 1);
        } catch {
            func.setToast(true, true, "Failed to update tags");
            setEditTagModal((prev) => ({ ...prev, saving: false }));
        }
    }, [editTagModal, closeEditTagModal]);

    const promotedBulkActions = useCallback((selectedIds) => {
        if (!isUsersTab) return [];
        return [{ content: 'Edit device tags', onAction: () => openEditTagModal(selectedIds) }];
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

    const hasNonManualEntry = editTagModal.entries.some((e) => e.source && e.source !== 'manual');

    const editTagModalComp = (
        <Modal
            open={editTagModal.active}
            onClose={closeEditTagModal}
            title={`Edit device tags — ${editTagModal.usernames?.length > 1 ? `${editTagModal.usernames.length} users` : (lastRowsRef.current.find((r) => editTagModal.usernames?.[0] === r.id)?.groupName || '')}`}
            primaryAction={{ content: 'Save', onAction: saveEditTag, loading: editTagModal.saving }}
            secondaryActions={[{ content: 'Cancel', onAction: closeEditTagModal }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {editTagModal.isBulk ? (
                        <Banner tone="info">
                            <Text variant="bodySm">
                                Editing {editTagModal.usernames.length} users at once — their existing tags may differ, so they aren't shown here. A tag you add below is applied to all of them; use "Clear all" to remove every manually-set device tag from all of them instead.
                            </Text>
                        </Banner>
                    ) : hasNonManualEntry && (
                        <Banner tone="info">
                            <Text variant="bodySm">
                                Tags synced from elsewhere (e.g. Okta) are shown for reference and can't be edited here — they're managed at the source and will reappear on the next sync. A manual tag can coexist with one of these on the same key; both apply.
                            </Text>
                        </Banner>
                    )}
                    <FormLayout>
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Existing tags</Text>
                            {editTagModal.entries.length === 0 ? (
                                <Text variant="bodySm" color="subdued">
                                    {editTagModal.isBulk ? "Not shown for multiple users — see note above." : "No device tags yet."}
                                </Text>
                            ) : (
                                editTagModal.entries.map((entry, idx) => {
                                    const isManual = entry.source === 'manual';
                                    return (
                                        <HorizontalStack key={`${entry.key}|${entry.source}|${idx}`} gap="2" blockAlign="center" wrap={false}>
                                            <Box width="100px">
                                                <Text variant="bodyMd" fontWeight="medium" truncate>{entry.key}</Text>
                                            </Box>
                                            <Box minWidth="0" width="100%">
                                                <TextField
                                                    label={entry.key}
                                                    labelHidden
                                                    value={entry.value}
                                                    onChange={isManual ? (v) => updateEntryValue(idx, v) : () => {}}
                                                    disabled={!isManual}
                                                    autoComplete="off"
                                                />
                                            </Box>
                                            <Box minWidth="72px">
                                                {isManual ? (
                                                    <Button plain destructive icon={DeleteMinor} onClick={() => removeEntry(idx)} accessibilityLabel={`Remove ${entry.key}`} />
                                                ) : (
                                                    <Badge size="small">{entry.source}</Badge>
                                                )}
                                            </Box>
                                        </HorizontalStack>
                                    );
                                })
                            )}
                        </VerticalStack>
                        <Divider />
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Add a tag</Text>
                            <HorizontalStack gap="2" blockAlign="end" wrap={false}>
                                <Box minWidth="0" width="100%">
                                    <TextField
                                        label="Key"
                                        placeholder="e.g. department"
                                        value={editTagModal.newKey}
                                        onChange={(v) => setEditTagModal((prev) => ({ ...prev, newKey: v }))}
                                        autoComplete="off"
                                    />
                                </Box>
                                <Box minWidth="0" width="100%">
                                    <TextField
                                        label="Value"
                                        value={editTagModal.newValue}
                                        onChange={(v) => setEditTagModal((prev) => ({ ...prev, newValue: v }))}
                                        autoComplete="off"
                                    />
                                </Box>
                                <Box paddingBlockStart="6"><Button onClick={addEntry}>Add</Button></Box>
                            </HorizontalStack>
                        </VerticalStack>
                        {editTagModal.isBulk && (
                            <Checkbox
                                label={`Clear all manually-set device tags for these ${editTagModal.usernames.length} users`}
                                checked={editTagModal.clearAll}
                                onChange={(checked) => setEditTagModal((prev) => ({ ...prev, clearAll: checked }))}
                            />
                        )}
                    </FormLayout>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );

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
            {editTagModalComp}
        </>
    );
}

export default UsersAndDevices;

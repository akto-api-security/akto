import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Badge, HorizontalStack, Text, Modal, FormLayout, Banner, VerticalStack, TextField, Button, Box, Divider, Tooltip, Checkbox } from "@shopify/polaris";
import { DeleteMinor } from "@shopify/polaris-icons";
import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import api from "../api";
import func from "@/util/func";
import transform from "../transform";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import useTable from "@/apps/dashboard/components/tables/TableContext";
import settingRequests from "../../settings/api";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import NewLayoutTooltip from "./NewLayoutTooltip";
import {
    getHeaders,
    getSortOptionsWithoutIconColumn,
    INVENTORY_PATH,
    INVENTORY_FILTER_KEY,
    PAGE_LIMIT,
    groupCollectionsByUser,
    groupCollectionsByDevice,
    buildAgenticInventoryFilterForRow,
    fetchAndCacheSkillApiData,
    hasMaliciousSkillInCollections,
    buildTagFilterValues,
} from "./constants";
import { hasMisconfiguredConfigTag } from "./mcpClientHelper";

const definedTableTabs = ["Users", "Devices"];

const usersAndDevicesCountColumnOpts = {
    endpointsColumnLabel: "Agentic assets",
    endpointsColumnBoxWidth: "120px",
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
    const [loading, setLoading] = useState(false);
    const agenticNewLayout = LocalStore((state) => state.agenticNewLayout);
    const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

    useEffect(() => {
        if (agenticNewLayout) {
            navigate("/dashboard/observe/endpoints", { replace: true });
        }
    }, [navigate, agenticNewLayout]);
    const [data, setData] = useState({ users: [], devices: [] });
    const [userEnrichVersion, setUserEnrichVersion] = useState(0);
    const [summaryData, setSummaryData] = useState({ profileCount: 0, collectionCount: 0 });
    // entries: [{key, value, source}] — one row per raw tag for the selected user(s); a key can
    // have more than one row (different sources, e.g. manual + Okta, all coexisting). Only
    // source==='manual' rows are ever edited/removed here.
    // isBulk (>1 user selected): existing per-user tags are never prefilled/shown, since they can
    // differ across users and silently copying one user's values onto the rest would be a data-loss
    // footgun. Bulk mode only supports adding a new tag (applied to all) or clearing all manual tags.
    const emptyEditTagModal = { active: false, usernames: [], isBulk: false, entries: [], originalManualKeys: [], clearAll: false, newKey: '', newValue: '', saving: false };
    const [editTagModal, setEditTagModal] = useState(emptyEditTagModal);

    const { tabsInfo } = useTable();
    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab = tableSelectedTab[window.location.pathname] || "users";
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected] = useState(func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab));

    const filtersMap = PersistStore((state) => state.filtersMap);
    const setFiltersMap = PersistStore((state) => state.setFiltersMap);

    const dataRef = useRef(data);
    useEffect(() => { dataRef.current = data; }, [data]);

    const tableCountObj = func.getTabsCount(definedTableTabs, data);
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo);

    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex);
    };

    const headers = useMemo(() => {
        const h = getHeaders({
            primaryColumnTitle: selectedTab === "users" ? "User" : "Device",
            primaryColumnText: selectedTab === "users" ? "User" : "Device",
            includeIconColumn: false,
            includeUserColumns: selectedTab === "users",
            ...usersAndDevicesCountColumnOpts,
        });
        h[0] = { ...h[0], value: "groupNameDisplay" };
        return h;
    }, [selectedTab]);

    const sortOptionsNoIcon = useMemo(
        () => getSortOptionsWithoutIconColumn(usersAndDevicesCountColumnOpts),
        [],
    );

    const getRiskScoreStatus = useCallback((riskScore) => {
        if (riskScore >= 4.5) return "critical";
        if (riskScore >= 4) return "attention";
        if (riskScore >= 2.5) return "warning";
        if (riskScore > 0) return "info";
        return "success";
    }, []);

    const buildGroupNameDisplay = useCallback((group, extraBadges = []) => {
        const badges = [...extraBadges];
        if (group.hasPersonalAccount) badges.push(<Badge key="personal" size="small" status="warning">Contains personal account</Badge>);
        if (group.hasLocalMcpServer) badges.push(<Badge key="local-mcp" size="small" status="critical">Local MCP Server</Badge>);
        if (badges.length === 0) return group.groupName;
        return (
            <HorizontalStack gap="2" align="start" wrap={false}>
                <Text>{group.groupName}</Text>
                {badges}
            </HorizontalStack>
        );
    }, []);

    const prettifyGroupData = useCallback(
        (groups) => {
            return groups.map((group) => ({
                ...group,
                groupNameDisplay: buildGroupNameDisplay(group),
                tagsDisplay: buildTagsDisplay(group.tags),
                sensitiveSubTypes: transform.prettifySubtypes(group.sensitiveInRespTypes || [], false),
                riskScoreComp:
                    group.riskScore !== null ? (
                        <Badge status={getRiskScoreStatus(group.riskScore)} size="small">
                            {group.riskScore}
                        </Badge>
                    ) : (
                        "-"
                    ),
            }));
        },
        [getRiskScoreStatus, buildGroupNameDisplay],
    );

    const applyMaliciousBadgeToUsers = useCallback((maliciousSkillKeys, isMountedRef) => {
        if (!isMountedRef.current) return;
        const enrichRow = (row) => {
            // Scoped to each collection's own tagged skills: a same-named skill owned by another
            // user/agent must not mark this row as malicious.
            const hasMalicious = hasMaliciousSkillInCollections(maliciousSkillKeys, row.collections);
            // Tag-based, same signal groupCollectionsByDevice already uses — no extra API call needed.
            const hasMisconfigured = (row.collections || []).some((c) => hasMisconfiguredConfigTag(c.envType));
            const extraBadges = [];
            if (hasMalicious) extraBadges.push(<Badge key="malicious" size="small" status="critical">Malicious Skills</Badge>);
            return { ...row, hasMaliciousSkill: hasMalicious, hasMisconfiguredConfig: hasMisconfigured, groupNameDisplay: buildGroupNameDisplay(row, extraBadges) };
        };
        setData((prev) => ({
            users: prev.users.map(enrichRow),
            devices: prev.devices.map(enrichRow),
        }));
        setUserEnrichVersion((v) => v + 1);
    }, [buildGroupNameDisplay]);

    const enrichUsersWithMaliciousSkills = useCallback(async (userRows, isMountedRef = { current: true }) => {
        // Misconfigured badge is tag-based (applyMaliciousBadgeToUsers) and needs no fetch, so it's
        // applied unconditionally. Only collections that actually have skills need the malicious-skill
        // lookup; querying every collection fires one request per collection for no benefit on the rest.
        const allCollectionIds = [];
        userRows.forEach((row) => {
            (row.collections || []).forEach((c) => {
                if (Array.isArray(c.skills) && c.skills.length > 0 && !allCollectionIds.includes(c.id)) {
                    allCollectionIds.push(c.id);
                }
            });
        });
        if (!allCollectionIds.length) {
            applyMaliciousBadgeToUsers(new Set(), isMountedRef);
            return;
        }

        const { maliciousSkillKeys } = await fetchAndCacheSkillApiData(allCollectionIds, { api, PersistStore });

        if (!isMountedRef.current) return;
        applyMaliciousBadgeToUsers(maliciousSkillKeys || new Set(), isMountedRef);
    }, [applyMaliciousBadgeToUsers]);

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            const [apiCollectionsResp, trafficInfoResp, riskScoreResp, sensitiveInfoResp, shieldResult] =
                await Promise.all([
                    api.getAllCollectionsBasic(),
                    api.getLastTrafficSeen(),
                    api.getRiskScoreInfo(),
                    api.getSensitiveInfoForCollections(),
                    fetchEndpointShieldUserMetadata(),
                ]);

            if (!isMountedRef.current) return;

            const collections = apiCollectionsResp.apiCollections || [];
            const trafficMap = trafficInfoResp || {};
            const riskScoreMap = riskScoreResp?.riskScoreOfCollectionsMap || {};
            const sensitiveMap = sensitiveInfoResp?.sensitiveSubtypesInCollection || {};
            const { usernameMap = {}, userMetadataMap = {} } = shieldResult || {};

            const userGroups = prettifyGroupData(
                groupCollectionsByUser(collections, trafficMap, sensitiveMap, riskScoreMap, usernameMap, userMetadataMap),
            );
            const deviceGroups = prettifyGroupData(
                groupCollectionsByDevice(collections, trafficMap, sensitiveMap, riskScoreMap),
            );

            setData({
                users: userGroups,
                devices: deviceGroups,
            });
            setLoading(false);

            enrichUsersWithMaliciousSkills([...userGroups, ...deviceGroups], isMountedRef);
        } catch {
            setLoading(false);
        }
    }

    useEffect(() => {
        const isMountedRef = { current: true };
        fetchData(isMountedRef);
        return () => {
            isMountedRef.current = false;
        };
    }, []);

    useEffect(() => {
        const userLen = data.users.length;
        const deviceLen = data.devices.length;
        const rows = selectedTab === "users" ? data.users : data.devices;
        setSummaryData({
            profileCount: selectedTab === "users" ? userLen : deviceLen,
            collectionCount: rows.reduce((sum, row) => sum + (row.endpointsCount ?? row.hostNames?.length ?? 0), 0),
        });
    }, [selectedTab, data.users, data.devices]);

    const disambiguateLabel = useCallback((key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }, []);

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
        const firstUser = data.users.find((u) => usernames.includes(u.id));
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
    }, [data.users]);

    const closeEditTagModal = useCallback(() => {
        setEditTagModal(emptyEditTagModal);
    }, []);

    // Only manual rows are ever edited/removed here — tags from any other source (e.g. Okta) are
    // shown read-only, since this dashboard action only ever writes manual-source tags and a
    // synced value would just be overwritten again on the next sync anyway.
    const updateEntryValue = useCallback((key, value) => {
        setEditTagModal((prev) => ({
            ...prev,
            entries: prev.entries.map((e) => (e.key === key && e.source === 'manual' ? { ...e, value } : e)),
        }));
    }, []);

    const removeEntry = useCallback((key) => {
        setEditTagModal((prev) => ({
            ...prev,
            entries: prev.entries.filter((e) => !(e.key === key && e.source === 'manual')),
        }));
    }, []);

    const addEntry = useCallback(() => {
        const key = editTagModal.newKey.trim().toLowerCase();
        const value = editTagModal.newValue.trim();
        if (!key) {
            func.setToast(true, true, "Device tag key cannot be empty");
            return;
        }
        // Only checked against other manual rows — a manual value is allowed alongside an
        // existing non-manual value for the same key, they simply coexist.
        if (editTagModal.entries.some((e) => e.key === key && e.source === 'manual')) {
            func.setToast(true, true, "This device tag key already has a manual value in the list below.");
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
            const selectedUsers = data.users.filter((u) => editTagModal.usernames.includes(u.id));
            const groupNames = selectedUsers.map((u) => u.groupName).filter(Boolean);

            // Only manual rows are ever sent — this action only writes the "manual" source, never
            // touching tags synced in from elsewhere. Empty value = explicit clear. Manual keys
            // removed from the original prefill are also sent as an explicit clear, so "remove"
            // actually un-pins the manual override instead of just hiding it locally; a same-key
            // tag from another source (if any) is untouched either way.
            const manualEntries = editTagModal.entries.filter((e) => e.source === 'manual');
            const tags = {};
            manualEntries.forEach((e) => { tags[e.key] = e.value ? [e.value] : []; });
            if (editTagModal.isBulk) {
                // "Clear all" in bulk mode clears every manually-set key any selected user
                // currently has — computed from the union of their real tags, never guessed from
                // one user's set. Tags from other sources aren't affected.
                if (editTagModal.clearAll) {
                    selectedUsers.forEach((u) => (u.tags || []).forEach((t) => {
                        if (t?.key && t.source === 'manual' && !(t.key in tags)) tags[t.key] = [];
                    }));
                }
            } else {
                const currentManualKeys = new Set(manualEntries.map((e) => e.key));
                editTagModal.originalManualKeys.forEach((k) => { if (!currentManualKeys.has(k)) tags[k] = []; });
            }

            await settingRequests.bulkUpdateUserDeviceTag(groupNames, tags);
            setData((prev) => ({
                ...prev,
                users: prev.users.map((u) => {
                    if (!editTagModal.usernames.includes(u.id)) return u;
                    const remainingTags = (u.tags || []).filter((t) => !(t.source === 'manual' && t.key in tags));
                    const newManualTags = manualEntries
                        .filter((e) => e.value)
                        .map((e) => ({ key: e.key, value: e.value, source: 'manual' }));
                    const merged = [...remainingTags, ...newManualTags];
                    return {
                        ...u,
                        tags: merged,
                        tagsDisplay: buildTagsDisplay(merged),
                        tagFilterValues: buildTagFilterValues(merged),
                    };
                }),
            }));
            setUserEnrichVersion((v) => v + 1);
            func.setToast(true, false, "Tags updated successfully");
            closeEditTagModal();
        } catch {
            func.setToast(true, true, "Failed to update tags");
            setEditTagModal((prev) => ({ ...prev, saving: false }));
        }
    }, [editTagModal, data.users, closeEditTagModal]);

    const handleRowClick = useCallback(
        (row) => {
            const updatedFiltersMap = { ...filtersMap };
            const filterPayload = buildAgenticInventoryFilterForRow(row);
            if (filterPayload) {
                updatedFiltersMap[INVENTORY_FILTER_KEY] = filterPayload;
            } else {
                delete updatedFiltersMap[INVENTORY_FILTER_KEY];
            }
            // The agent-tree subview keeps its own filter slot; clear it so the
            // previous user's hostnames don't leak into this user's view.
            delete updatedFiltersMap[`${INVENTORY_FILTER_KEY}agent-tree/`];

            setFiltersMap(updatedFiltersMap);

            setTableSelectedTab({
                ...tableSelectedTab,
                [INVENTORY_PATH]: "hostname",
            });

            setTimeout(() => navigate(INVENTORY_PATH), 0);
        },
        [filtersMap, setFiltersMap, navigate, tableSelectedTab, setTableSelectedTab],
    );

    const summaryItems = useMemo(
        () => [
            {
                title: selectedTab === "users" ? "Users" : "Devices",
                data: transform.formatNumberWithCommas(summaryData.profileCount),
            },
            {
                title: "Agentic assets",
                data: transform.formatNumberWithCommas(summaryData.collectionCount),
            },
        ],
        [summaryData, selectedTab],
    );

    const summaryComponent = useMemo(() => <SummaryCardInfo summaryItems={summaryItems} key="summary" />, [summaryItems]);

    const resourceName = useMemo(
        () =>
            selectedTab === "users"
                ? { singular: "user", plural: "users" }
                : { singular: "device", plural: "devices" },
        [selectedTab],
    );

    const promotedBulkActions = useCallback((selectedIds) => {
        if (selectedTab !== 'users') return [];
        return [{
            content: 'Edit device tags',
            onAction: () => openEditTagModal(selectedIds),
        }];
    }, [selectedTab, openEditTagModal]);

    const tableComponent = useMemo(() => {
        const commonTabProps = { tableTabs, onSelect: handleSelectedTab, selected };
        const tableKey = selectedTab === "users" ? `table-users-${userEnrichVersion}` : "table";
        return (
            <GithubSimpleTable
                key={tableKey}
                pageLimit={PAGE_LIMIT}
                data={data[selectedTab]}
                sortOptions={sortOptionsNoIcon}
                resourceName={resourceName}
                filters={[]}
                headers={headers}
                selectable={selectedTab === 'users'}
                mode={IndexFiltersMode.Filtering}
                headings={headers}
                useNewRow={true}
                condensedHeight={true}
                disambiguateLabel={disambiguateLabel}
                prettifyPageData={(pageData) => pageData}
                onRowClick={handleRowClick}
                promotedBulkActions={promotedBulkActions}
                {...commonTabProps}
            />
        );
    }, [data, selectedTab, userEnrichVersion, headers, disambiguateLabel, handleRowClick, promotedBulkActions, tableTabs, selected, resourceName]);

    const pageTitle = useMemo(
        () => (
            <TitleWithInfo
                tooltipContent="View agentic activity by user or device; open inventory with the same filters as Agentic assets."
                titleText="Users and devices"
                docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
            />
        ),
        [],
    );

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
            title={`Edit device tags \u2014 ${editTagModal.usernames?.length > 1 ? `${editTagModal.usernames.length} users` : (data.users.find((u) => editTagModal.usernames?.[0] === u.id)?.groupName || '')}`}
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
                                    {editTagModal.isBulk ? "Not shown for multiple users \u2014 see note above." : "No device tags yet."}
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
                                                    onChange={isManual ? (v) => updateEntryValue(entry.key, v) : () => {}}
                                                    disabled={!isManual}
                                                    autoComplete="off"
                                                />
                                            </Box>
                                            <Box minWidth="72px">
                                                {isManual ? (
                                                    <Button plain destructive icon={DeleteMinor} onClick={() => removeEntry(entry.key)} accessibilityLabel={`Remove ${entry.key}`} />
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
                components={[summaryComponent, tableComponent]}
            />
            {editTagModalComp}
        </>
    );
}

export default UsersAndDevices;

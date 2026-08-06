import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { Badge, HorizontalStack, Text, Modal, FormLayout, Banner, VerticalStack, Autocomplete, IndexFiltersMode } from "@shopify/polaris";
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

function UsersAndDevices() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
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

    const [stats, setStats] = useState({ usersCount: 0, devicesCount: 0, usersAgenticAssetsTotal: 0, devicesAgenticAssetsTotal: 0, teams: [], roles: [] });
    const [refreshKey, setRefreshKey] = useState(0);
    const [editTagModal, setEditTagModal] = useState({ active: false, rows: [], team: '', userRole: '', teamSource: 'sso', roleSource: 'sso', ssoHintTeam: '', ssoHintRole: '', saving: false });

    // Everything the paginated fetch needs but the row itself doesn't carry, plus a stash of the
    // most-recently-fetched page's full row objects (for the bulk "Edit team & role" action, which
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

    const loadStats = useCallback(async () => {
        try {
            const { trafficMap, riskScoreMap, usernameMap, userMetadataMap } = enrichRef.current;
            const result = await api.fetchUsersAndDevicesStats({ trafficMap, riskScoreMap, usernameMap, userMetadataMap });
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
            const extraBadges = hasMalicious ? [<Badge key="malicious" size="small" status="critical">Malicious Skills</Badge>] : [];
            return {
                ...row,
                groupNameDisplay: buildGroupNameDisplay(row, extraBadges),
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
        const { trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap } = enrichRef.current;
        const mappedSortKey = SORT_FIELD_MAP[sortKey] || "riskScore";
        const mongoSortOrder = sortOrder === -1 ? 1 : -1; // GithubServerTable: asc=-1/desc=1, inverted vs Mongo
        const filters = {};
        if (filtersObj?.team?.length) filters.team = filtersObj.team;
        if (filtersObj?.userRole?.length) filters.userRole = filtersObj.userRole;
        const res = await api.fetchUsersAndDevicesSummary({
            groupBy: isUsersTab ? "user" : "device",
            skip, limit, sortKey: mappedSortKey, sortOrder: mongoSortOrder, queryValue, filters,
            trafficMap, riskScoreMap, sensitiveMap, usernameMap, userMetadataMap,
        });
        const prettified = prettifyRows(res.rows || []);
        lastRowsRef.current = prettified;
        return { value: prettified, total: res.total || 0 };
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
        { key: "team", label: "Team", choices: (stats.teams || []).map((t) => ({ label: t, value: t })) },
        { key: "userRole", label: "User role", choices: (stats.roles || []).map((r) => ({ label: r, value: r })) },
    ] : []), [isUsersTab, stats.teams, stats.roles]);

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
        const rows = lastRowsRef.current.filter((r) => usernames.includes(r.id));
        if (!rows.length) return;
        const first = rows[0];
        const teamSrc = first?.teamSource || 'sso';
        const roleSrc = first?.roleSource || 'sso';
        setEditTagModal({
            active: true,
            rows,
            team: teamSrc === 'manual' ? (first?.team || '') : '',
            userRole: roleSrc === 'manual' ? (first?.userRole || '') : '',
            teamSource: teamSrc,
            roleSource: roleSrc,
            ssoHintTeam: teamSrc === 'sso' ? (first?.team || '') : '',
            ssoHintRole: roleSrc === 'sso' ? (first?.userRole || '') : '',
            saving: false,
        });
    }, []);

    const closeEditTagModal = useCallback(() => {
        setEditTagModal({ active: false, rows: [], team: '', userRole: '', teamSource: 'sso', roleSource: 'sso', ssoHintTeam: '', ssoHintRole: '', saving: false });
    }, []);

    const saveEditTag = useCallback(async () => {
        setEditTagModal((prev) => ({ ...prev, saving: true }));
        try {
            const groupNames = editTagModal.rows.map((r) => r.groupName).filter(Boolean);
            await settingRequests.bulkUpdateUserDeviceTag(groupNames, editTagModal.team, editTagModal.userRole);
            func.setToast(true, false, "Team and role updated successfully");
            closeEditTagModal();
            setRefreshKey((k) => k + 1);
        } catch {
            func.setToast(true, true, "Failed to update team and role");
            setEditTagModal((prev) => ({ ...prev, saving: false }));
        }
    }, [editTagModal, closeEditTagModal]);

    const promotedBulkActions = useCallback((selectedIds) => {
        if (!isUsersTab) return [];
        return [{ content: 'Edit team & role', onAction: () => openEditTagModal(selectedIds) }];
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

    const editTagModalComp = (
        <Modal
            open={editTagModal.active}
            onClose={closeEditTagModal}
            title={`Edit team & role — ${editTagModal.rows.length > 1 ? `${editTagModal.rows.length} users` : (editTagModal.rows[0]?.groupName || '')}`}
            primaryAction={{ content: 'Save', onAction: saveEditTag, loading: editTagModal.saving }}
            secondaryActions={[{ content: 'Cancel', onAction: closeEditTagModal }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {(editTagModal.teamSource === 'sso' || editTagModal.roleSource === 'sso') && (
                        <Banner tone="info">
                            <Text variant="bodySm">
                                {editTagModal.teamSource === 'sso' && editTagModal.roleSource === 'sso'
                                    ? 'Team and role are currently managed by SSO. Saving will override SSO values and pin them to your manual entries.'
                                    : editTagModal.teamSource === 'sso'
                                        ? 'Team is currently managed by SSO. Saving will override the SSO value and pin it to your manual entry.'
                                        : 'Role is currently managed by SSO. Saving will override the SSO value and pin it to your manual entry.'
                                }
                            </Text>
                        </Banner>
                    )}
                    <FormLayout>
                        <Autocomplete
                            options={(() => {
                                const query = (editTagModal.team || '').toLowerCase();
                                const all = stats.teams || [];
                                return (query ? all.filter(t => t.toLowerCase().includes(query)) : all).map(t => ({ label: t, value: t }));
                            })()}
                            selected={editTagModal.team ? [editTagModal.team] : []}
                            onSelect={(sel) => setEditTagModal((prev) => ({ ...prev, team: sel[0] || '' }))}
                            textField={
                                <Autocomplete.TextField
                                    label="Team"
                                    value={editTagModal.team}
                                    onChange={(v) => setEditTagModal((prev) => ({ ...prev, team: v }))}
                                    placeholder={editTagModal.teamSource === 'sso' && editTagModal.ssoHintTeam ? `SSO: ${editTagModal.ssoHintTeam}` : 'e.g. Backend, DevOps'}
                                    autoComplete="off"
                                    helpText={editTagModal.teamSource === 'manual' ? 'Clear this field to fall back to SSO value.' : undefined}
                                />
                            }
                        />
                        <Autocomplete
                            options={(() => {
                                const query = (editTagModal.userRole || '').toLowerCase();
                                const all = stats.roles || [];
                                return (query ? all.filter(r => r.toLowerCase().includes(query)) : all).map(r => ({ label: r, value: r }));
                            })()}
                            selected={editTagModal.userRole ? [editTagModal.userRole] : []}
                            onSelect={(sel) => setEditTagModal((prev) => ({ ...prev, userRole: sel[0] || '' }))}
                            textField={
                                <Autocomplete.TextField
                                    label="User role"
                                    value={editTagModal.userRole}
                                    onChange={(v) => setEditTagModal((prev) => ({ ...prev, userRole: v }))}
                                    placeholder={editTagModal.roleSource === 'sso' && editTagModal.ssoHintRole ? `SSO: ${editTagModal.ssoHintRole}` : 'e.g. Engineer, Architect'}
                                    autoComplete="off"
                                    helpText={editTagModal.roleSource === 'manual' ? 'Clear this field to fall back to SSO value.' : undefined}
                                />
                            }
                        />
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
                        mode={IndexFiltersMode.Filtering}
                        useNewRow={true}
                        condensedHeight={true}
                        disambiguateLabel={disambiguateLabel}
                        onRowClick={handleRowClick}
                        promotedBulkActions={promotedBulkActions}
                        tableTabs={tableTabs}
                        selected={selected}
                        onSelect={handleSelectedTab}
                        supportsNegationFilter={false}
                    />,
                ]}
            />
            {editTagModalComp}
        </>
    );
}

export default UsersAndDevices;

import React, { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Badge, HorizontalStack, Text, Modal, TextField, FormLayout } from "@shopify/polaris";
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
} from "./constants";

const definedTableTabs = ["Users", "Devices"];

const usersAndDevicesCountColumnOpts = {
    endpointsColumnLabel: "Agentic assets",
    endpointsColumnBoxWidth: "120px",
};

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
    const [editTagModal, setEditTagModal] = useState({ active: false, usernames: [], team: '', userRole: '', saving: false });

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

    const applyMaliciousBadgeToUsers = useCallback((maliciousSkillsSet, misconfiguredCollectionIdsSet, isMountedRef) => {
        if (!isMountedRef.current) return;
        const enrichRow = (row) => {
            const skillNames = row.uniqueSkillNames || new Set(
                (row.collections || []).flatMap(c => Array.isArray(c.skills) ? c.skills.map(s => String(s).toLowerCase()) : [])
            );
            const hasMalicious = [...skillNames].some((s) => maliciousSkillsSet.has(s));
            const collectionIds = (row.collections || []).map((c) => c.id);
            const hasMisconfigured = collectionIds.some((id) => misconfiguredCollectionIdsSet.has(id));
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
        const allCollectionIds = [];
        userRows.forEach((row) => {
            (row.collections || []).forEach((c) => {
                if (!allCollectionIds.includes(c.id)) allCollectionIds.push(c.id);
            });
        });
        if (!allCollectionIds.length) return;

        const { maliciousSkills, misconfiguredCollectionIds } = await fetchAndCacheSkillApiData(allCollectionIds, { api, PersistStore });

        if (!isMountedRef.current) return;
        applyMaliciousBadgeToUsers(maliciousSkills, misconfiguredCollectionIds || new Set(), isMountedRef);
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
        const firstUser = data.users.find((u) => usernames.includes(u.id));
        setEditTagModal({
            active: true,
            usernames,
            team: firstUser?.team || '',
            userRole: firstUser?.userRole || '',
            saving: false,
        });
    }, [data.users]);

    const closeEditTagModal = useCallback(() => {
        setEditTagModal({ active: false, usernames: [], team: '', userRole: '', saving: false });
    }, []);

    const saveEditTag = useCallback(async () => {
        setEditTagModal((prev) => ({ ...prev, saving: true }));
        try {
            const selectedUsers = data.users.filter((u) => editTagModal.usernames.includes(u.id));
            await Promise.all(
                selectedUsers.map((u) =>
                    settingRequests.updateUserDeviceTag(u.groupName, editTagModal.team, editTagModal.userRole)
                )
            );
            setData((prev) => ({
                ...prev,
                users: prev.users.map((u) =>
                    editTagModal.usernames.includes(u.id)
                        ? { ...u, team: editTagModal.team, userRole: editTagModal.userRole }
                        : u
                ),
            }));
            func.setToast(true, false, "Team and role updated successfully");
            closeEditTagModal();
        } catch {
            func.setToast(true, true, "Failed to update team and role");
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
            content: 'Edit team & role',
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

    const editTagModalComp = (
        <Modal
            open={editTagModal.active}
            onClose={closeEditTagModal}
            title={`Edit team & role — ${editTagModal.usernames?.length > 1 ? `${editTagModal.usernames.length} users` : (data.users.find((u) => editTagModal.usernames?.[0] === u.id)?.groupName || '')}`}
            primaryAction={{ content: 'Save', onAction: saveEditTag, loading: editTagModal.saving }}
            secondaryActions={[{ content: 'Cancel', onAction: closeEditTagModal }]}
        >
            <Modal.Section>
                <FormLayout>
                    <TextField
                        label="Team"
                        value={editTagModal.team}
                        onChange={(v) => setEditTagModal((prev) => ({ ...prev, team: v }))}
                        placeholder="e.g. Backend, DevOps"
                        autoComplete="off"
                    />
                    <TextField
                        label="User role"
                        value={editTagModal.userRole}
                        onChange={(v) => setEditTagModal((prev) => ({ ...prev, userRole: v }))}
                        placeholder="e.g. Engineer, Architect"
                        autoComplete="off"
                    />
                </FormLayout>
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

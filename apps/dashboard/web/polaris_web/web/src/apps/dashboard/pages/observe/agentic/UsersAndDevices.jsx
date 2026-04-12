import React, { useEffect, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Badge } from "@shopify/polaris";
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
import useTable from "@/apps/dashboard/components/tables/TableContext";
import { fetchEndpointShieldUsernameMap } from "../api_collections/endpointShieldHelper";
import {
    getHeaders,
    getSortOptionsWithoutIconColumn,
    INVENTORY_PATH,
    INVENTORY_FILTER_KEY,
    PAGE_LIMIT,
    groupCollectionsByUser,
    groupCollectionsByDevice,
    buildAgenticInventoryFilterForRow,
} from "./constants";

const definedTableTabs = ["Users", "Devices"];

function UsersAndDevices() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState({ users: [], devices: [] });
    const [summaryData, setSummaryData] = useState({ profileCount: 0, collectionCount: 0 });

    const { tabsInfo } = useTable();
    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab = tableSelectedTab[window.location.pathname] || "users";
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected] = useState(func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab));

    const filtersMap = PersistStore((state) => state.filtersMap);
    const setFiltersMap = PersistStore((state) => state.setFiltersMap);

    const tableCountObj = func.getTabsCount(definedTableTabs, data);
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo);

    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex);
    };

    const headers = useMemo(
        () =>
            getHeaders({
                primaryColumnTitle: selectedTab === "users" ? "User" : "Device",
                primaryColumnText: selectedTab === "users" ? "User" : "Device",
                includeIconColumn: false,
            }),
        [selectedTab],
    );

    const sortOptionsNoIcon = useMemo(() => getSortOptionsWithoutIconColumn(), []);

    const getRiskScoreStatus = useCallback((riskScore) => {
        if (riskScore >= 4.5) return "critical";
        if (riskScore >= 4) return "attention";
        if (riskScore >= 2.5) return "warning";
        if (riskScore > 0) return "info";
        return "success";
    }, []);

    const prettifyGroupData = useCallback(
        (groups) => {
            return groups.map((group) => ({
                ...group,
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
        [getRiskScoreStatus],
    );

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            const [apiCollectionsResp, trafficInfoResp, riskScoreResp, sensitiveInfoResp, usernameMap] =
                await Promise.all([
                    api.getAllCollectionsBasic(),
                    api.getLastTrafficSeen(),
                    api.getRiskScoreInfo(),
                    api.getSensitiveInfoForCollections(),
                    fetchEndpointShieldUsernameMap(),
                ]);

            if (!isMountedRef.current) return;

            const collections = apiCollectionsResp.apiCollections || [];
            const trafficMap = trafficInfoResp || {};
            const riskScoreMap = riskScoreResp?.riskScoreOfCollectionsMap || {};
            const sensitiveMap = sensitiveInfoResp?.sensitiveSubtypesInCollection || {};

            const userGroups = prettifyGroupData(
                groupCollectionsByUser(collections, trafficMap, sensitiveMap, riskScoreMap, usernameMap || {}),
            );
            const deviceGroups = prettifyGroupData(
                groupCollectionsByDevice(collections, trafficMap, sensitiveMap, riskScoreMap),
            );

            setData({
                users: userGroups,
                devices: deviceGroups,
            });
            setLoading(false);
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
            collectionCount: rows.reduce((sum, row) => sum + (row.hostNames?.length || 0), 0),
        });
    }, [selectedTab, data.users, data.devices]);

    const disambiguateLabel = useCallback((key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }, []);

    const handleRowClick = useCallback(
        (row) => {
            const updatedFiltersMap = { ...filtersMap };
            const filterPayload = buildAgenticInventoryFilterForRow(row);
            if (filterPayload) {
                updatedFiltersMap[INVENTORY_FILTER_KEY] = filterPayload;
            } else {
                delete updatedFiltersMap[INVENTORY_FILTER_KEY];
            }

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

    const tableComponent = useMemo(() => {
        const commonTabProps = { tableTabs, onSelect: handleSelectedTab, selected };
        return (
            <GithubSimpleTable
                key="table"
                pageLimit={PAGE_LIMIT}
                data={data[selectedTab]}
                sortOptions={sortOptionsNoIcon}
                resourceName={resourceName}
                filters={[]}
                headers={headers}
                selectable={false}
                mode={IndexFiltersMode.Default}
                headings={headers}
                useNewRow={true}
                condensedHeight={true}
                disambiguateLabel={disambiguateLabel}
                prettifyPageData={(pageData) => pageData}
                onRowClick={handleRowClick}
                {...commonTabProps}
            />
        );
    }, [data, selectedTab, headers, disambiguateLabel, handleRowClick, tableTabs, selected, resourceName]);

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

    if (loading) {
        return (
            <PageWithMultipleCards
                title={pageTitle}
                isFirstPage={true}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={pageTitle}
            isFirstPage={true}
            components={[summaryComponent, tableComponent]}
        />
    );
}

export default UsersAndDevices;

import React, { useEffect, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Badge, Box } from "@shopify/polaris";
import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import api from "../api";
import dashboardApi from "../../dashboard/api";
import func from "@/util/func";
import transform from "../transform";
import PersistStore from "../../../../main/PersistStore";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";
import { CollectionIcon } from "../../../components/shared/CollectionIcon";
import { getHeaders, sortOptions, resourceName, INVENTORY_PATH, INVENTORY_FILTER_KEY, ASSET_TAG_KEY_VALUES, groupCollectionsByTag, createEnvTypeFilter } from "./constants";

function Endpoints() {
    const navigate = useNavigate();
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [summaryData, setSummaryData] = useState({
        totalEndpoints: 0,
        totalGroups: 0,
        totalCollections: 0
    });

    const setAllCollections = PersistStore((state) => state.setAllCollections);
    const filtersMap = PersistStore((state) => state.filtersMap);
    const setFiltersMap = PersistStore((state) => state.setFiltersMap);
    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);

    const headers = useMemo(() => getHeaders(), []);

    const prettifyGroupData = useCallback((groups) => {
        return groups.map((group) => ({
                ...group,
                iconComp: (
                    <Box>
                        <CollectionIcon
                        hostName={group.firstCollection?.hostName}
                            assetTagValue={group.tagValue}
                            displayName={group.groupName}
                        />
                    </Box>
                ),
            riskScoreComp: <Badge status={transform.getStatus(group.riskScore)} size="small">{group.riskScore}</Badge>,
                sensitiveSubTypes: transform.prettifySubtypes(group.sensitiveInRespTypes, false),
        }));
    }, []);

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            const results = await Promise.allSettled([
                api.getAllCollectionsBasic(),
                api.getLastTrafficSeen(),
                api.getRiskScoreInfo(),
                api.getSensitiveInfoForCollections(),
                dashboardApi.fetchEndpointsCount(0, 0),
            ]);

            const apiCollectionsResp = results[0].status === "fulfilled" ? results[0].value : { apiCollections: [] };
            const trafficInfo = results[1].status === "fulfilled" ? results[1].value : {};
            const riskScoreResp = results[2].status === "fulfilled" ? results[2].value : {};
            const sensitiveResp = results[3].status === "fulfilled" ? results[3].value : {};
            const endpointsResp = results[4].status === "fulfilled" ? results[4].value : {};

            if (!isMountedRef.current) return;

            const collections = apiCollectionsResp.apiCollections || [];
            const riskScoreMap = riskScoreResp.riskScoreOfCollectionsMap || {};
            const sensitiveInfoMap = sensitiveResp.sensitiveSubtypesInCollection || {};
            const trafficInfoMap = trafficInfo || {};

            setAllCollections(collections);

            const groupedData = groupCollectionsByTag(collections, sensitiveInfoMap, riskScoreMap, trafficInfoMap);
            const prettifiedData = prettifyGroupData(groupedData);

            setData(prettifiedData);

            const totalEndpoints = collections.reduce(
                (sum, c) => sum + (c.deactivated ? 0 : c.urlsCount || 0),
                0
            );

            setSummaryData({
                totalEndpoints: endpointsResp.newCount || totalEndpoints,
                totalGroups: groupedData.length,
                totalCollections: collections.filter((c) => !c.deactivated).length,
            });

            setLoading(false);
        } catch {
            setLoading(false);
        }
    }

    useEffect(() => {
        const isMountedRef = { current: true };
        fetchData(isMountedRef);
        return () => { isMountedRef.current = false; };
    }, []);

    const disambiguateLabel = useCallback((key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }, []);

    const handleRowClick = useCallback((row) => {
        const updatedFiltersMap = { ...filtersMap };

        if (!row.tagKey || !row.tagValue) {
            const allTagValues = data
                .filter(item => ASSET_TAG_KEY_VALUES.includes(item.tagKey) && item.tagValue)
                .map(item => `${item.tagKey}=${item.tagValue}`);
            
            if (allTagValues.length > 0) {
                updatedFiltersMap[INVENTORY_FILTER_KEY] = createEnvTypeFilter(allTagValues, true);
            } else {
                delete updatedFiltersMap[INVENTORY_FILTER_KEY];
            }
        } else {
            const filterValue = `${row.tagKey}=${row.tagValue}`;
            updatedFiltersMap[INVENTORY_FILTER_KEY] = createEnvTypeFilter([filterValue], false);
        }

        setFiltersMap(updatedFiltersMap);
        
        // Always navigate to the hostname tab
        setTableSelectedTab({
            ...tableSelectedTab,
            [INVENTORY_PATH]: "hostname"
        });
        
        setTimeout(() => navigate(INVENTORY_PATH), 0);
    }, [filtersMap, data, setFiltersMap, navigate, tableSelectedTab, setTableSelectedTab]);

    const summaryItems = useMemo(() => [
        {
            title: "Agentic assets",
            data: transform.formatNumberWithCommas(summaryData.totalGroups),
        },
        {
            title: "Total endpoints",
            data: transform.formatNumberWithCommas(summaryData.totalCollections),
        },
    ], [summaryData.totalGroups, summaryData.totalCollections]);

    const tableComponent = useMemo(() => (
        <GithubSimpleTable
            pageLimit={100}
            data={data}
            sortOptions={sortOptions}
            resourceName={resourceName}
            filters={[]}
            headers={headers}
            selectable={false}
            mode={IndexFiltersMode.Filtering}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            disambiguateLabel={disambiguateLabel}
            prettifyPageData={(pageData) => pageData}
            onRowClick={handleRowClick}
        />
    ), [data, headers, disambiguateLabel, handleRowClick]);

    const pageTitle = useMemo(() => (
                <TitleWithInfo
                    tooltipContent="View API endpoints grouped by tags for better organization and analysis."
                    titleText={"Agentic assets"}
                    docsUrl="https://docs.akto.io/api-inventory/concepts"
                />
    ), []);

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
            components={[
                <SummaryCardInfo summaryItems={summaryItems} key="summary" />,
                tableComponent
            ]}
        />
    );
}

export default Endpoints;

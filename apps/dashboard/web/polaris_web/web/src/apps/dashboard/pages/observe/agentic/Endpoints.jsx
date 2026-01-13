import React, { useEffect, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Badge, Box } from "@shopify/polaris";
import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import func from "@/util/func";
import { fetchCollectionsData } from "../collectionsDataUtils";
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
            const { collections, trafficInfoMap, riskScoreMap, sensitiveInfoMap, endpointsCount } = await fetchCollectionsData();

            if (!isMountedRef.current) return;

            setAllCollections(collections);

            const groupedData = groupCollectionsByTag(collections, sensitiveInfoMap, riskScoreMap, trafficInfoMap);
            setData(prettifyGroupData(groupedData));

            setSummaryData({
                totalEndpoints: endpointsCount || collections.reduce((sum, c) => sum + (c.deactivated ? 0 : c.urlsCount || 0), 0),
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
        setTimeout(() => navigate(INVENTORY_PATH), 0);
    }, [filtersMap, data, setFiltersMap, navigate]);

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
                    titleText={mapLabel("Endpoints", getDashboardCategory())}
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

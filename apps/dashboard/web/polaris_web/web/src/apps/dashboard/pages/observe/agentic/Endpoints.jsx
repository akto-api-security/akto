import React, { useEffect, useState, useCallback, useMemo } from "react";
import { IndexFiltersMode, Box, Badge } from "@shopify/polaris";
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
import { CollectionIcon } from "../../../components/shared/CollectionIcon";
import { 
    getHeaders, 
    sortOptions, 
    resourceName, 
    INVENTORY_PATH, 
    INVENTORY_FILTER_KEY, 
    groupCollectionsByAgent, 
    groupCollectionsByService,
    createEnvTypeFilter,
    createHostnameFilter,
    extractEndpointId,
    ROW_TYPES
} from "./constants";

function Endpoints() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState([]);
    const [summaryData, setSummaryData] = useState({ totalAssets: 0, totalEndpoints: 0 });

    const setAllCollections = PersistStore((state) => state.setAllCollections);
    const filtersMap = PersistStore((state) => state.filtersMap);
    const setFiltersMap = PersistStore((state) => state.setFiltersMap);
    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);

    const headers = useMemo(() => getHeaders(), []);

    const getRiskScoreStatus = useCallback((riskScore) => {
        if (riskScore >= 4.5) return "critical";
        if (riskScore >= 4) return "attention";
        if (riskScore >= 2.5) return "warning";
        if (riskScore > 0) return "info";
        return "success";
    }, []);

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
            sensitiveSubTypes: transform.prettifySubtypes(group.sensitiveInRespTypes || [], false),
            riskScoreComp: group.riskScore !== null ? (
                <Badge status={getRiskScoreStatus(group.riskScore)} size="small">
                    {group.riskScore}
                </Badge>
            ) : "-",
        }));
    }, [getRiskScoreStatus]);

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            // Fetch all required data in parallel
            const [
                apiCollectionsResp,
                trafficInfoResp,
                riskScoreResp,
                sensitiveInfoResp
            ] = await Promise.all([
                api.getAllCollectionsBasic(),
                api.getLastTrafficSeen(),
                api.getRiskScoreInfo(),
                api.getSensitiveInfoForCollections()
            ]);

            if (!isMountedRef.current) return;

            const collections = apiCollectionsResp.apiCollections || [];
            setAllCollections(collections);

            // Extract maps from responses
            const trafficMap = trafficInfoResp || {};
            const riskScoreMap = riskScoreResp?.riskScoreOfCollectionsMap || {};
            const sensitiveMap = sensitiveInfoResp?.sensitiveSubtypesInCollection || {};

            // Group collections by agents (discovery sources) and services (discovered endpoints)
            const agentGroups = groupCollectionsByAgent(collections, trafficMap, sensitiveMap);
            const serviceGroups = groupCollectionsByService(collections, trafficMap, sensitiveMap, riskScoreMap);

            const prettifiedAgents = prettifyGroupData(agentGroups);
            const prettifiedServices = prettifyGroupData(serviceGroups);

            // Combine all data
            const allData = [...prettifiedAgents, ...prettifiedServices];

            // Calculate unique endpoint IDs across all collections
            const uniqueEndpointIds = new Set();
            collections.forEach((c) => {
                if (c.deactivated) return;
                const hostName = c.hostName || c.displayName || c.name;
                const endpointId = extractEndpointId(hostName);
                if (endpointId) {
                    uniqueEndpointIds.add(endpointId);
                }
            });

            setSummaryData({
                totalAssets: allData.length,
                totalEndpoints: uniqueEndpointIds.size
            });

            setData(allData);
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

        if (row.rowType === ROW_TYPES.AGENT) {
            // Agent row clicked - filter by agent tag to show resources discovered by this agent
            if (row.tagKey && row.tagValue) {
                const filterValue = `${row.tagKey}=${row.tagValue}`;
                updatedFiltersMap[INVENTORY_FILTER_KEY] = createEnvTypeFilter([filterValue], false);
            }
        } else if (row.rowType === ROW_TYPES.SERVICE) {
            // Service row clicked - filter by all hostnames for this service
            if (row.hostNames && row.hostNames.length > 0) {
                updatedFiltersMap[INVENTORY_FILTER_KEY] = createHostnameFilter(row.hostNames);
            }
        } else {
            // Fallback: clear filters
            delete updatedFiltersMap[INVENTORY_FILTER_KEY];
        }

        setFiltersMap(updatedFiltersMap);
        
        // Navigate to the hostname tab in inventory
        setTableSelectedTab({
            ...tableSelectedTab,
            [INVENTORY_PATH]: "hostname"
        });
        
        setTimeout(() => navigate(INVENTORY_PATH), 0);
    }, [filtersMap, setFiltersMap, navigate, tableSelectedTab, setTableSelectedTab]);

    const summaryItems = useMemo(() => [
        {
            title: "Agentic assets",
            data: transform.formatNumberWithCommas(summaryData.totalAssets),
        },
        {
            title: "Total endpoints",
            data: transform.formatNumberWithCommas(summaryData.totalEndpoints),
        },
    ], [summaryData]);

    const summaryComponent = useMemo(() => (
        <SummaryCardInfo summaryItems={summaryItems} key="summary" />
    ), [summaryItems]);

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
            tooltipContent="View agentic assets"
            titleText={"Agentic assets"}
            docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
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
            components={[summaryComponent, tableComponent]}
        />
    );
}

export default Endpoints;

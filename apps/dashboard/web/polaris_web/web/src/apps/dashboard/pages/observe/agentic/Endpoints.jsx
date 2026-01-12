import React, { useEffect, useState } from "react";
import { Text, IndexFiltersMode, Badge, Box, Icon } from "@shopify/polaris";
import { AutomationMajor, MagicMajor } from "@shopify/polaris-icons";
import { useNavigate } from "react-router-dom";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import api from "../api";
import dashboardApi from "../../dashboard/api";
import func from "@/util/func";
import transform from "../transform";
import PersistStore from "../../../../main/PersistStore";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";
import IconCacheService from "@/services/IconCacheService";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";

const MCP_CLIENT_TAG_KEY = "mcp-client";
const UNKNOWN_GROUP = "Unknown";

const iconCacheService = new IconCacheService();

const GroupIconRenderer = React.memo(({ hostName, displayName, tagsList }) => {
    const [iconData, setIconData] = React.useState(null);

    React.useEffect(() => {
        if (!hostName || hostName.trim() === '') {
            return;
        }
        let isMounted = true;
        const fetchIcon = async () => {
            try {
                const imageData = await iconCacheService.getIconData(hostName);
                if (imageData && isMounted) {
                    setIconData(imageData);
                }
            } catch (error) {
                // Silently fail
            }
        };

        fetchIcon();

        return () => {
            isMounted = false;
        };
    }, [hostName]);

    if (iconData) {
        return (
            <img
                src={`data:image/png;base64,${iconData}`}
                alt={`${hostName} icon`}
                style={{width: '20px', height: '20px', borderRadius: '2px'}}
                onError={() => {
                    setIconData(null);
                }}
            />
        );
    }

    // Fallback icons
    if (tagsList?.some(tag => tag.name === "gen-ai")) {
        const iconSource = tagsList.some(tag => tag.name === "AI Agent") ? AutomationMajor : MagicMajor;
        return <Icon source={iconSource} color={"base"} />;
    }

    if (tagsList?.some(tag => tag.name === "mcp-server")) {
        return <img src={MCPIcon} alt="MCP icon" style={{width: '20px', height: '20px', borderRadius: '2px'}} />;
    }

    const defaultIcon = displayName?.toLowerCase().startsWith('mcp') ? MCPIcon : LaptopIcon;
    return <img src={defaultIcon} alt="default icon" style={{width: '20px', height: '20px', borderRadius: '2px'}} />;
});

const getStatus = (riskScore) => {
    if(riskScore >= 4.5){
        return "critical"
    }else if(riskScore >= 4){
        return "attention"
    }else if(riskScore >= 2.5){
        return "warning"
    }else if(riskScore > 0){
        return "info"
    }else{
        return "success"
    }
};

const headers = [
    ...((func.isDemoAccount() && (getDashboardCategory() === "Agentic Security" || getDashboardCategory() === "Endpoint Security")) ? [{
        title: "",
        text: "",
        value: "iconComp",
        isText: CellType.TEXT,
        boxWidth: '24px'
    }] : []),
    {
        title: "Endpoint group",
        text: "Endpoint group",
        value: "groupName",
        filterKey: "groupName",
        textValue: "groupName",
        showFilter: true,
    },
    {
        title: mapLabel("Total endpoints", getDashboardCategory()),
        text: mapLabel("Total endpoints", getDashboardCategory()),
        value: "urlsCount",
        isText: CellType.TEXT,
        sortActive: true,
        boxWidth: "80px",
        filterKey: "urlsCount",
        showFilter: true,
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Maximum risk score of the endpoints in this group</Text>} title="Risk score" />,
        value: "riskScoreComp",
        textValue: "riskScore",
        numericValue: "riskScore",
        text: "Risk Score",
        sortActive: true,
        boxWidth: "80px",
    },
    {
        title: "Collections count",
        text: "Collections count",
        value: "collectionsCount",
        isText: CellType.TEXT,
        sortActive: true,
        boxWidth: "80px",
    },
    {
        title: "Sensitive data",
        text: "Sensitive data",
        value: "sensitiveSubTypes",
        numericValue: "sensitiveInRespTypes",
        textValue: "sensitiveSubTypesVal",
        tooltipContent: <Text variant="bodySm">Types of data present in response of endpoints in this group</Text>,
        boxWidth: "160px",
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time an endpoint in this group was either discovered or seen again</Text>} title="Last traffic seen" />,
        text: "Last traffic seen",
        value: "lastTraffic",
        numericValue: "detectedTimestamp",
        isText: CellType.TEXT,
        sortActive: true,
        boxWidth: "80px",
    },
];

const sortOptions = [
    { label: "Endpoints", value: "urlsCount asc", directionLabel: "More", sortKey: "urlsCount", columnIndex: 1 },
    { label: "Endpoints", value: "urlsCount desc", directionLabel: "Less", sortKey: "urlsCount", columnIndex: 1 },
    { label: "Risk Score", value: "score asc", directionLabel: "High risk", sortKey: "riskScore", columnIndex: 2 },
    { label: "Risk Score", value: "score desc", directionLabel: "Low risk", sortKey: "riskScore", columnIndex: 2 },
    { label: "Collections", value: "collectionsCount asc", directionLabel: "More", sortKey: "collectionsCount", columnIndex: 3 },
    { label: "Collections", value: "collectionsCount desc", directionLabel: "Less", sortKey: "collectionsCount", columnIndex: 3 },
    { label: "Last traffic seen", value: "detected asc", directionLabel: "Recent first", sortKey: "detectedTimestamp", columnIndex: 5 },
    { label: "Last traffic seen", value: "detected desc", directionLabel: "Oldest first", sortKey: "detectedTimestamp", columnIndex: 5 },
];

const resourceName = {
    singular: "endpoint group",
    plural: "endpoint groups",
};

function Endpoints() {
    const navigate = useNavigate();
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [filters, setFilters] = useState([]);
    const [summaryData, setSummaryData] = useState({
        totalEndpoints: 0,
        totalGroups: 0,
        totalCollections: 0
    });

    const setAllCollections = PersistStore((state) => state.setAllCollections);

    const groupCollectionsByTag = (collections, sensitiveInfoMap, riskScoreMap, trafficInfoMap) => {
        const groups = {};

        collections.forEach((collection) => {
            if (collection.deactivated) return;

            let groupKey = UNKNOWN_GROUP;
            const mcpClientTag = collection.envType?.find(
                (tag) => tag.keyName === MCP_CLIENT_TAG_KEY
            );

            if (mcpClientTag) {
                groupKey = mcpClientTag.value;
            }

            if (!groups[groupKey]) {
                groups[groupKey] = {
                    groupName: groupKey,
                    tagKey: mcpClientTag ? MCP_CLIENT_TAG_KEY : null,
                    tagValue: mcpClientTag ? mcpClientTag.value : null,
                    collections: [],
                    urlsCount: 0,
                    riskScore: 0,
                    detectedTimestamp: 0,
                    collectionsCount: 0,
                    sensitiveInRespTypes: new Set(),
                    firstCollection: null,
                };
            }

            groups[groupKey].collections.push(collection);

            if (!groups[groupKey].firstCollection) {
                groups[groupKey].firstCollection = collection;
            }

            groups[groupKey].urlsCount += collection.urlsCount || 0;
            groups[groupKey].collectionsCount += 1;

            const collectionRiskScore = riskScoreMap[collection.id] || 0;
            groups[groupKey].riskScore = Math.max(
                groups[groupKey].riskScore,
                collectionRiskScore
            );

            const collectionTraffic = trafficInfoMap[collection.id] || 0;
            groups[groupKey].detectedTimestamp = Math.max(
                groups[groupKey].detectedTimestamp,
                collectionTraffic
            );

            const sensitiveTypes = sensitiveInfoMap[collection.id] || [];
            sensitiveTypes.forEach((type) => groups[groupKey].sensitiveInRespTypes.add(type));
        });

        return Object.values(groups).map((group) => {
            return {
                ...group,
                id: group.groupName,
                tagKey: group.tagKey,
                tagValue: group.tagValue,
                sensitiveInRespTypes: Array.from(group.sensitiveInRespTypes),
                sensitiveSubTypesVal: Array.from(group.sensitiveInRespTypes).join(" ") || "-",
                lastTraffic: func.prettifyEpoch(group.detectedTimestamp),
            };
        });
    };

    const prettifyGroupData = (groups) => {
        return groups.map((group) => {
            const firstCollection = group.firstCollection;

            return {
                ...group,
                iconComp: firstCollection ? (
                    <Box>
                        <GroupIconRenderer
                            hostName={firstCollection.hostName}
                            displayName={firstCollection.displayName}
                            tagsList={firstCollection.tagsList}
                        />
                    </Box>
                ) : null,
                riskScoreComp: <Badge status={getStatus(group.riskScore)} size="small">{group.riskScore}</Badge>,
                sensitiveSubTypes: transform.prettifySubtypes(group.sensitiveInRespTypes, false),
            };
        });
    };

    async function fetchData(isMountedRef = { current: true }) {
        try {
            setLoading(true);

            const apiPromises = [
                api.getAllCollectionsBasic(),
                api.getLastTrafficSeen(),
                api.getRiskScoreInfo(),
                api.getSensitiveInfoForCollections(),
                dashboardApi.fetchEndpointsCount(0, 0),
            ];

            const results = await Promise.allSettled(apiPromises);

            const apiCollectionsResp =
                results[0].status === "fulfilled" ? results[0].value : { apiCollections: [] };
            const trafficInfo = results[1].status === "fulfilled" ? results[1].value : {};
            const riskScoreResp = results[2].status === "fulfilled" ? results[2].value : {};
            const sensitiveResp = results[3].status === "fulfilled" ? results[3].value : {};
            const endpointsResp = results[4].status === "fulfilled" ? results[4].value : {};

            if (!isMountedRef.current) {
                return;
            }

            const collections = apiCollectionsResp.apiCollections || [];
            const riskScoreMap = riskScoreResp.riskScoreOfCollectionsMap || {};
            const sensitiveInfoMap = sensitiveResp.sensitiveSubtypesInCollection || {};
            const trafficInfoMap = trafficInfo || {};

            setAllCollections(collections);

            const groupedData = groupCollectionsByTag(
                collections,
                sensitiveInfoMap,
                riskScoreMap,
                trafficInfoMap
            );

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
        } catch (error) {
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

    const disambiguateLabel = (key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    };

    const handleRowClick = (row) => {
        if (!row.tagKey || !row.tagValue) {
            navigate('/dashboard/observe/inventory');
            return;
        }

        const targetPageKey = '/dashboard/observe/inventory/';
        let filtersMap = PersistStore.getState().filtersMap;
        if (filtersMap !== null && filtersMap.hasOwnProperty(targetPageKey)) {
            delete filtersMap[targetPageKey];
            PersistStore.getState().setFiltersMap(filtersMap);
        }

        const filterValue = `${row.tagKey}=${row.tagValue}`;
        const filters = `envType__${filterValue}`;
        const navigateUrl = `${window.location.origin}/dashboard/observe/inventory?filters=${encodeURIComponent(filters)}`;
        window.open(navigateUrl, "_blank");
    };

    const summaryItems = [
        {
            title: mapLabel("Total Endpoints", getDashboardCategory()),
            data: transform.formatNumberWithCommas(summaryData.totalEndpoints),
        },
        {
            title: "Endpoint Groups",
            data: transform.formatNumberWithCommas(summaryData.totalGroups),
        },
        {
            title: mapLabel("Total Collections", getDashboardCategory()),
            data: transform.formatNumberWithCommas(summaryData.totalCollections),
        },
    ];

    const tableComponent = (
        <GithubSimpleTable
            pageLimit={100}
            data={data}
            sortOptions={sortOptions}
            resourceName={resourceName}
            filters={filters}
            headers={headers}
            selectable={false}
            mode={IndexFiltersMode.Default}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            disambiguateLabel={disambiguateLabel}
            prettifyPageData={(pageData) => pageData}
            onRowClick={handleRowClick}
        />
    );

    const components = loading
        ? [<SpinnerCentered key="loading" />]
        : [<SummaryCardInfo summaryItems={summaryItems} key="summary" />, tableComponent];

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent="View API endpoints grouped by tags for better organization and analysis."
                    titleText={mapLabel("Endpoints", getDashboardCategory())}
                    docsUrl="https://docs.akto.io/api-inventory/concepts"
                />
            }
            isFirstPage={true}
            components={components}
        />
    );
}

export default Endpoints;

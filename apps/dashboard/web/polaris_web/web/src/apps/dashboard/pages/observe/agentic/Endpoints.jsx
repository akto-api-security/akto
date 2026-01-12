import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Text, IndexFiltersMode } from "@shopify/polaris";
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

const MCP_CLIENT_TAG_KEY = "mcp-client";
const UNKNOWN_GROUP = "Unknown";

const headers = [
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
        value: "riskScore",
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
                };
            }

            groups[groupKey].collections.push(collection);
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

        return Object.values(groups).map((group) => ({
            ...group,
            id: group.groupName,
            sensitiveInRespTypes: Array.from(group.sensitiveInRespTypes),
            sensitiveSubTypesVal: Array.from(group.sensitiveInRespTypes).join(" ") || "-",
            lastTraffic: func.prettifyEpoch(group.detectedTimestamp),
            nextUrl: null,
        }));
    };

    const prettifyGroupData = (groups) => {
        return groups.map((group) => ({
            ...group,
            sensitiveSubTypes: transform.prettifySubtypes(group.sensitiveInRespTypes, false),
        }));
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

    const handleRowClick = (row) => {
        if (row.tagKey && row.tagValue) {
            const tagParam = encodeURIComponent(`${row.tagKey}=${row.tagValue}`);
            navigate(`/dashboard/observe/inventory?tagFilter=${tagParam}`);
        } else {
            navigate(`/dashboard/observe/inventory?tagFilter=unknown`);
        }
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
            filters={[]}
            headers={headers}
            selectable={false}
            mode={IndexFiltersMode.Default}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            onRowClick={handleRowClick}
            prettifyPageData={(pageData) => pageData}
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

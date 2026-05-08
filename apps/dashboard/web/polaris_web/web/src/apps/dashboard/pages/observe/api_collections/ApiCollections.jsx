import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, IndexFiltersMode, Box, Popover, ActionList, ResourceItem, Avatar,  HorizontalStack, Icon} from "@shopify/polaris"
import MCPIcon from "@/assets/MCP_Icon.svg"
import LaptopIcon from "@/assets/Laptop.svg"
import { HideMinor, ViewMinor,FileMinor, AutomationMajor, MagicMajor } from '@shopify/polaris-icons';
import RegistryBadge from "../../../components/shared/RegistryBadge";
import api from "../api"
import dashboardApi from "../../dashboard/api"
import settingRequests from "../../settings/api"
import { useEffect,useState, useRef } from "react"
import func from "@/util/func"
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import { CircleTickMajor } from '@shopify/polaris-icons';
import ObserveStore from "../observeStore"
import PersistStore from "../../../../main/PersistStore"
import transform from "../transform"
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered"
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow"
import CreateNewCollectionModal from "./CreateNewCollectionModal"
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo"
import collectionApi from "./api"
import CollectionsPageBanner from "./component/CollectionsPageBanner"
import useTable from "@/apps/dashboard/components/tables/TableContext"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip"
import SearchableResourceList from "../../../components/shared/SearchableResourceList"
import ResourceListModal from "../../../components/shared/ResourceListModal"
import { saveAs } from 'file-saver'
// import dummyJson from "../../../components/shared/treeView/dummyJson"
import TreeViewTable from "../../../components/shared/treeView/TreeViewTable"
import TableStore from "../../../components/tables/TableStore";
import { useNavigate } from "react-router-dom";
import ReactFlow, {
    Background,  useNodesState,
    useEdgesState,
  
  } from 'react-flow-renderer';
import SetUserEnvPopupComponent from "./component/SetUserEnvPopupComponent";
import { getDashboardCategory, mapLabel, isMCPSecurityCategory, isAgenticSecurityCategory, isGenAISecurityCategory } from "../../../../main/labelHelper";
  
const CenterViewType = {
    Table: 0,
    Tree: 1,
    Graph: 2
  }

const API_COLLECTIONS_CACHE_DURATION_SECONDS = 5 * 60; // 5 minutes
const COLLECTIONS_LAZY_RENDER_THRESHOLD = 100; // Collections count above which we use lazy rendering optimization

const headers = [
    ...((isMCPSecurityCategory() || isAgenticSecurityCategory()) && func.isDemoAccount() ? [{
        title: "",
        text: "",
        value: "iconComp",
        isText: CellType.TEXT,
        boxWidth: '24px'
    }] : []),
    {
        title: mapLabel("API collection name", getDashboardCategory()),
        text: mapLabel("API collection name", getDashboardCategory()),
        value: "displayNameComp",
        filterKey: "displayName",
        textValue: 'displayName',
        showFilter: true,
        titleWithTooltip: <HeadingWithTooltip content="These API groups are computed periodically" title={mapLabel("API collection name", getDashboardCategory())} />
    },
    {
        title: mapLabel("Total endpoints", getDashboardCategory()),
        text: mapLabel("Total endpoints", getDashboardCategory()),
        value: "urlsCount",
        isText: CellType.TEXT,
        sortActive: true,
        mergeType: (a, b) => {
            return (a || 0) + (b || 0);
        },
        shouldMerge: true,
        boxWidth: '80px',
        filterKey: "urlsCount",
        showFilter: true,
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Risk score of collection is maximum risk score of the endpoints inside this collection</Text>} title="Risk score" />,
        value: 'riskScoreComp',
        textValue: 'riskScore',
        numericValue: 'riskScore',
        text: 'Risk Score',
        sortActive: true,
        mergeType: (a, b) => {
            return Math.max(a || 0, b || 0);
        },
        shouldMerge: true,
        boxWidth: '80px'
    },
    {   
        title: mapLabel('Test', getDashboardCategory()) + ' coverage',
        text: mapLabel('Test', getDashboardCategory()) + ' coverage', 
        value: 'coverage',
        isText: CellType.TEXT,
        tooltipContent: (<Text variant="bodySm">Percentage of endpoints tested successfully in the collection</Text>),
        mergeType: (a, b) => {
            return (a || 0) + (b || 0);
        },
        numericValue: 'testedEndpoints',
        shouldMerge: true,
        boxWidth: '80px'
    },
    {
        title: 'Issues', 
        text: 'Issues', 
        value: 'issuesArr',
        numericValue: 'severityInfo',
        textValue: 'issuesArrVal',
        tooltipContent: (<Text variant="bodySm">Severity and count of issues present in the collection</Text>),
        mergeType: (a, b) => {
            return {
                HIGH: ((a?.HIGH || 0) + (b?.HIGH || 0)),
                MEDIUM: ((a?.MEDIUM || 0) + (b?.MEDIUM || 0)),
                LOW: ((a?.LOW || 0) + (b?.LOW || 0)),
            };
        },
        shouldMerge: true,
        boxWidth: '140px'
    },
    {   
        title: 'Sensitive data',
        text: 'Sensitive data',
        value: 'sensitiveSubTypes',
        numericValue: 'sensitiveInRespTypes',
        textValue: 'sensitiveSubTypesVal',
        tooltipContent: (<Text variant="bodySm">Types of data type present in response of endpoint inside the collection</Text>),
        mergeType: (a, b) => {
            return [...new Set([...(a || []), ...(b || [])])];
        },
        shouldMerge: true,
        boxWidth: '160px'
    },
    {
        text: 'Collection tags',
        title: 'Collection tags',
        value: 'envTypeComp',
        filterKey: "envType",
        showFilter: true,
        textValue: 'envType',
        tooltipContent: (<Text variant="bodySm">Tags for an API collection to describe collection attributes such as environment type (staging, production) and other custom attributes</Text>),
    },
    {   
        title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time an endpoint within collection was either discovered for the first time or seen again</Text>} title="Last traffic seen" />, 
        text: 'Last traffic seen', 
        value: 'lastTraffic',
        numericValue: 'detectedTimestamp',
        isText: CellType.TEXT,
        sortActive: true,
        mergeType: (a, b) => {
            return Math.max(a || 0, b || 0);
        },
        shouldMerge: true,
        boxWidth: '80px'
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Time when collection was created</Text>} title="Discovered" />,
        text: 'Discovered',
        value: 'discovered',
        isText: CellType.TEXT,
        sortActive: true,
    },
    {
        title: "Description",
        text: 'Description',
        value: 'descriptionComp',
        textValue: 'description',
        filterKey: "description",
        tooltipContent: 'Description of the collection'
    },
    {
        title: "Out of " + mapLabel('Testing', getDashboardCategory()) + " scope",
        text: 'Out of ' + mapLabel('Testing', getDashboardCategory()) + ' scope',
        value: 'outOfTestingScopeComp',
        textValue: 'isOutOfTestingScope',
        filterKey: 'isOutOfTestingScope',
        tooltipContent: 'Whether the collection is excluded from testing '
    }
];

const tempSortOptions = [
    { label: 'Name', value: 'customGroupsSort asc', directionLabel: 'A-Z', sortKey: 'customGroupsSort', columnIndex: 1 },
    { label: 'Name', value: 'customGroupsSort desc', directionLabel: 'Z-A', sortKey: 'customGroupsSort', columnIndex: 1 },
]


const sortOptions = [
    { label: 'Endpoints', value: 'urlsCount asc', directionLabel: 'More', sortKey: 'urlsCount', columnIndex: 2 },
    { label: 'Endpoints', value: 'urlsCount desc', directionLabel: 'Less', sortKey: 'urlsCount' , columnIndex: 2},
    { label: 'Activity', value: 'deactivatedScore asc', directionLabel: 'Active', sortKey: 'deactivatedRiskScore' },
    { label: 'Activity', value: 'deactivatedScore desc', directionLabel: 'Inactive', sortKey: 'activatedRiskScore' },
    { label: 'Risk Score', value: 'score asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: 3 },
    { label: 'Risk Score', value: 'score desc', directionLabel: 'Low risk', sortKey: 'riskScore' , columnIndex: 3},
    { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: 9 },
    { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs' , columnIndex: 9},
    { label: 'Last traffic seen', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'detectedTimestamp', columnIndex: 8 },
    { label: 'Last traffic seen', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'detectedTimestamp' , columnIndex: 8},
  ];        


const resourceName = {
    singular: 'collection',
    plural: 'collections',
  };

const convertToNewData = (collectionsArr, sensitiveInfoMap, severityInfoMap, coverageMap, trafficInfoMap, riskScoreMap, isLoading) => {
    // Ensure collectionsArr is an array
    if (!Array.isArray(collectionsArr)) {
        return { prettify: [], normal: [] };
    }

    const newData = collectionsArr.map((c) => {
        if(c.deactivated){
            c.rowStatus = 'critical'
            c.disableClick = true
        }
        const tagsList = JSON.stringify(c?.tagsList || "")
        // Build result object directly without spread operator for better memory efficiency
        return {
            id: c.id,
            displayName: c.displayName,
            hostName: c.hostName,
            type: c.type,
            deactivated: c.deactivated,
            urlsCount: c.urlsCount,
            startTs: c.startTs,
            tagsList: c.tagsList,
            registryStatus: c.registryStatus,
            description: c.description,
            isOutOfTestingScope: c.isOutOfTestingScope,
            rowStatus: c.rowStatus,
            disableClick: c.disableClick,
            icon: CircleTickMajor,
            nextUrl: "/dashboard/observe/inventory/"+ c.id,
            envTypeOriginal: c?.envType,
            envType: c?.envType?.map(func.formatCollectionType),
            displayNameComp: (
                <HorizontalStack gap="2" align="start">
                    <Box maxWidth="30vw"><Text truncate fontWeight="medium">{c.displayName}</Text></Box>
                    {c.registryStatus === "available" && <RegistryBadge />}
                </HorizontalStack>
            ),
            testedEndpoints: c.urlsCount === 0 ? 0 : (coverageMap[c.id] || 0),
            sensitiveInRespTypes: sensitiveInfoMap[c.id] || [],
            severityInfo: severityInfoMap[c.id] || {},
            detected: func.prettifyEpoch(trafficInfoMap[c.id] || 0),
            detectedTimestamp: c.urlsCount === 0 ? 0 : (trafficInfoMap[c.id] || 0),
            riskScore: c.urlsCount === 0 ? 0 : (riskScoreMap[c.id] || 0),
            discovered: func.prettifyEpoch(c.startTs || 0),
            descriptionComp: (<Box maxWidth="350px"><Text>{c.description}</Text></Box>),
            outOfTestingScopeComp: c.isOutOfTestingScope ? (<Text>Yes</Text>) : (<Text>No</Text>),
            ...(((isMCPSecurityCategory() || isAgenticSecurityCategory()) && func.isDemoAccount() && tagsList.includes("mcp-server")) ? {
                iconComp: (<Box><img src={c.displayName?.toLowerCase().startsWith('mcp') ? MCPIcon : LaptopIcon} alt="icon" style={{width: '24px', height: '24px'}} /></Box>)
            } : ((isGenAISecurityCategory() || isAgenticSecurityCategory()) && func.isDemoAccount() && tagsList.includes("gen-ai")) ? {
                iconComp: (<Box><Icon source={tagsList.includes("AI Agent") ? AutomationMajor : MagicMajor} color={"base"}/></Box>)
            } : {})
        };
    })

    const prettifyData = transform.prettifyCollectionsData(newData, isLoading)
    return { prettify: prettifyData, normal: newData }
}

// Transform raw collection data to plain data (without JSX) for filtering/sorting
// This function is passed to the table component for lazy transformation
const transformRawCollectionData = (rawCollection, transformMaps) => {
    const trafficInfoMap = transformMaps?.trafficInfoMap || {};
    const coverageMap = transformMaps?.coverageMap || {};
    const riskScoreMap = transformMaps?.riskScoreMap || {};
    const severityInfoMap = transformMaps?.severityInfoMap || {};
    const sensitiveInfoMap = transformMaps?.sensitiveInfoMap || {};

    const detected = func.prettifyEpoch(trafficInfoMap[rawCollection.id] || 0);
    const discovered = func.prettifyEpoch(rawCollection.startTs || 0);
    const testedEndpoints = rawCollection.urlsCount === 0 ? 0 : (coverageMap[rawCollection.id] || 0);
    const riskScore = rawCollection.urlsCount === 0 ? 0 : (riskScoreMap[rawCollection.id] || 0);
    const envType = Array.isArray(rawCollection?.envType) ? rawCollection.envType.map(func.formatCollectionType) : [];

    let calcCoverage = '0%';
    if(!rawCollection.isOutOfTestingScope && rawCollection.urlsCount > 0){
        if(rawCollection.urlsCount < testedEndpoints){
            calcCoverage = '100%'
        } else {
            calcCoverage = Math.ceil((testedEndpoints * 100)/rawCollection.urlsCount) + '%'
        }
    } else if(rawCollection.isOutOfTestingScope){
        calcCoverage = 'N/A'
    }

    const severityInfo = severityInfoMap[rawCollection.id] || {};
    const sensitiveTypes = sensitiveInfoMap[rawCollection.id] || [];

    // Build issuesArrVal in same format as transform.getIssuesListText
    const sortedSeverityInfo = func.sortObjectBySeverity(severityInfo);
    let issuesArrVal = "-";
    if(Object.keys(sortedSeverityInfo).length > 0){
        issuesArrVal = "";
        Object.keys(sortedSeverityInfo).forEach((key) => {
            issuesArrVal += (key + ": " + sortedSeverityInfo[key] + " ");
        });
    }

    // Return minimal object - only fields needed for filtering, sorting, and categorization
    // JSX components will be created on-demand by prettifyPageData
    return {
        id: rawCollection.id,
        displayName: rawCollection.displayName,
        hostName: rawCollection.hostName,
        type: rawCollection.type,
        deactivated: rawCollection.deactivated,
        urlsCount: rawCollection.urlsCount,
        startTs: rawCollection.startTs,
        tagsList: rawCollection.tagsList,
        registryStatus: rawCollection.registryStatus,
        description: rawCollection.description,
        isOutOfTestingScope: rawCollection.isOutOfTestingScope,
        envType,
        envTypeOriginal: rawCollection?.envType,
        testedEndpoints,
        sensitiveInRespTypes: sensitiveTypes,
        sensitiveSubTypesVal: sensitiveTypes.join(' ') || '-',
        severityInfo,
        issuesArrVal: issuesArrVal,
        severityInfoCount: Object.keys(severityInfo).reduce((sum, key) => sum + (severityInfo[key] || 0), 0),
        sensitiveInRespCount: sensitiveTypes.length,
        detectedTimestamp: rawCollection.urlsCount === 0 ? 0 : (trafficInfoMap[rawCollection.id] || 0),
        riskScore,
        detected,
        discovered,
        coverage: calcCoverage,
        nextUrl: '/dashboard/observe/inventory/' + rawCollection.id,
        lastTraffic: detected,
        rowStatus: rawCollection.deactivated ? 'critical' : undefined,
        disableClick: rawCollection.deactivated || false,
        deactivatedRiskScore: rawCollection.deactivated ? (riskScore - 10) : riskScore,
        activatedRiskScore: -1 * (rawCollection.deactivated ? riskScore : (riskScore - 10)),
    };
};

const categorizeCollections = (prettifyArray) => {
    const envTypeObj = {};
    const hostnameCollections = [];
    const groupCollections = [];
    const customCollections = [];
    const activeCollections = [];
    const deactivatedCollectionsData = [];
    const collectionMap = new Map();

    prettifyArray.forEach((c) => {
        // Build environment map
        envTypeObj[c.id] = c.envTypeOriginal;
        collectionMap.set(c.id, c);

        // Categorize collections in single pass
        if (!c.deactivated) {
            activeCollections.push(c);
            if (c.hostName !== null && c.hostName !== undefined) {
                hostnameCollections.push(c);
            } else if (c.type === "API_GROUP") {
                groupCollections.push(c);
            } else {
                customCollections.push(c);
            }
        } else {
            deactivatedCollectionsData.push(c);
        }
    });

    return {
        envTypeObj,
        collectionMap,
        activeCollections,
        categorized: {
            all: prettifyArray,
            hostname: hostnameCollections,
            groups: groupCollections,
            custom: customCollections,
            deactivated: deactivatedCollectionsData,
        }
    };
};


function ApiCollections(props) {
    const {customCollectionDataFilter, onlyShowCollectionsTable, sendData} = props;

    const userRole = window.USER_ROLE

    const navigate = useNavigate();
    const [data, setData] = useState({'all': [], 'hostname':[], 'groups': [], 'custom': [], 'deactivated': [], 'untracked': []})
    const [active, setActive] = useState(false);
    const [loading, setLoading] = useState(false)

    const [summaryData, setSummaryData] = useState({totalEndpoints:0 , totalTestedEndpoints: 0, totalSensitiveEndpoints: 0, totalCriticalEndpoints: 0, totalAllowedForTesting: 0})
    const [hasUsageEndpoints, setHasUsageEndpoints] = useState(true)
    const [envTypeMap, setEnvTypeMap] = useState({})
    const [refreshData, setRefreshData] = useState(false)
    const [popover,setPopover] = useState(false)
    const [teamData, setTeamData] = useState([])
    const [usersCollection, setUsersCollection] = useState([])
    const [selectedItems, setSelectedItems] = useState([])
    const [normalData, setNormalData] = useState([])
    const [centerView, setCenterView] = useState(CenterViewType.Table);
    const [moreActions, setMoreActions] = useState(false);

    // const dummyData = dummyJson;

    const definedTableTabs = ['All', 'Hostname', 'Groups', 'Custom', 'Deactivated', 'Untracked']

    const { tabsInfo, selectItems } = useTable()
    const tableSelectedTab = PersistStore.getState().tableSelectedTab[window.location.pathname]
    const initialSelectedTab = tableSelectedTab || "hostname";
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab)
    let initialTabIdx = func.getTableTabIndexById(1, definedTableTabs, initialSelectedTab)
    const [selected, setSelected] = useState(initialTabIdx)
    
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    const setInventoryFlyout = ObserveStore(state => state.setInventoryFlyout)
    const setFilteredItems = ObserveStore(state => state.setFilteredItems) 
    const setSamples = ObserveStore(state => state.setSamples)
    const setSelectedUrl = ObserveStore(state => state.setSelectedUrl)
    const [deactivateCollections, setDeactivateCollections] = useState([])

    const resetFunc = () => {
        setInventoryFlyout(false)
        setFilteredItems([])
        setSamples("")
        setSelectedUrl({})
    }

    const showCreateNewCollectionPopup = () => {
        setActive(true)
    }

    const navigateToQueryPage = () => {
        navigate("/dashboard/observe/query_mode")
    }

    const allCollections = PersistStore(state => state.allCollections)
    // const allCollections = dummyData.allCollections;
    const setAllCollections = PersistStore(state => state.setAllCollections)
    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const setCollectionsRegistryStatusMap = PersistStore(state => state.setCollectionsRegistryStatusMap)
    const setTagCollectionsMap = PersistStore(state => state.setTagCollectionsMap)
    const setHostNameMap = PersistStore(state => state.setHostNameMap)
    const setCoverageMap = PersistStore(state => state.setCoverageMap)
    const setTrafficMap = PersistStore(state => state.setTrafficMap)

    // const lastFetchedResp = dummyData.lastFetchedResp
    // const lastFetchedSeverityResp = dummyData.lastFetchedSeverityResp
    // const lastFetchedSensitiveResp = dummyData.lastFetchedSensitiveResp
    const lastFetchedInfo = PersistStore.getState().lastFetchedInfo
    const lastFetchedResp = PersistStore.getState().lastFetchedResp
    const lastFetchedSeverityResp = PersistStore.getState().lastFetchedSeverityResp
    const lastFetchedSensitiveResp = PersistStore.getState().lastFetchedSensitiveResp
    const lastFetchedUntrackedResp = PersistStore.getState().lastFetchedUntrackedResp
    const setLastFetchedInfo = PersistStore.getState().setLastFetchedInfo
    const setLastFetchedResp = PersistStore.getState().setLastFetchedResp
    const setLastFetchedSeverityResp = PersistStore.getState().setLastFetchedSeverityResp
    const setLastFetchedSensitiveResp = PersistStore.getState().setLastFetchedSensitiveResp
    const setLastFetchedUntrackedResp = PersistStore.getState().setLastFetchedUntrackedResp
    const totalAPIs = PersistStore(state => state.totalAPIs)
    const setTotalAPIs = PersistStore(state => state.setTotalAPIs)
    const [allEdges, setAllEdges, onAllEdgesChange] = useEdgesState([])
    const [allNodes, setAllNodes, onAllNodesChange] = useNodesState([])

    // as riskScore cron runs every 5 min, we will cache the data and refresh in 5 mins
    // similarly call sensitive and severityInfo

    async function fetchData(isMountedRef = { current: true }, forceRefresh = false) {
        try {
            setLoading(true)
            const now = func.timeNow();
            // Check if we have fresh cached collections data
            // Cache is valid if: not forcing refresh, have collections, timestamp exists, within duration, and caching enabled
            const hasValidCache = !forceRefresh &&
                                 allCollections.length > 0 &&
                                 lastFetchedInfo.lastRiskScoreInfo > 0 && // Must have been fetched at least once
                                 (now - lastFetchedInfo.lastRiskScoreInfo) < API_COLLECTIONS_CACHE_DURATION_SECONDS &&
                                 func.isApiCollectionsCachingEnabled();

            if (hasValidCache) {
                try {
                    // Use cached data to populate the UI
                    const sensitiveInfoMap = lastFetchedSensitiveResp?.sensitiveInfoMap || {};
                    const severityInfoMap = lastFetchedSeverityResp || {};
                    const coverageMapCached = PersistStore.getState().coverageMap || {};
                    const riskScoreMap = lastFetchedResp?.riskScoreMap || {};
                    const trafficInfoMap = PersistStore.getState().trafficMap || {};

                    let finalArr = allCollections;
                    if(customCollectionDataFilter){
                        finalArr = finalArr.filter(customCollectionDataFilter)
                    }


                    // Guard: Prevent state update after unmount
                    if (!isMountedRef.current) {
                        return;
                    }

                    // Use the centralized transformation function with cache-specific maps
                    const cacheMaps = {
                        trafficInfoMap,
                        coverageMap: coverageMapCached,
                        riskScoreMap,
                        severityInfoMap,
                        sensitiveInfoMap
                    };

                    // OPTIMIZATION: For large datasets (>COLLECTIONS_LAZY_RENDER_THRESHOLD items), store RAW data + transform function
                    // Transformation happens on-demand in the table for each page (100 items at a time)
                    const shouldOptimize = finalArr.length > COLLECTIONS_LAZY_RENDER_THRESHOLD;

                    let lightweightData;
                    if (shouldOptimize) {
                        // Store ONLY raw data + lookup maps - minimal memory footprint
                        const rawData = finalArr;
                        rawData._lazyTransform = true;
                        rawData._transformMaps = cacheMaps;

                        // Transform for categorization only (no JSX components yet)
                        lightweightData = finalArr.map(c => transformRawCollectionData(c, cacheMaps));
                        lightweightData._lazyTransform = true;
                        lightweightData._transformMaps = cacheMaps;
                        lightweightData._transformedCache = lightweightData;
                    } else {
                        // Small dataset - transform all upfront
                        lightweightData = finalArr.map(c => transformRawCollectionData(c, cacheMaps));
                    }

                    const { categorized, envTypeObj } = categorizeCollections(lightweightData);

                    // Use transform.getSummaryData to match master behavior (excludes API_GROUP and deactivated)
                    const initialSummaryDataObj = transform.getSummaryData(lightweightData);
                    initialSummaryDataObj.totalSensitiveEndpoints = lastFetchedSensitiveResp?.sensitiveUrls || 0;
                    initialSummaryDataObj.totalCriticalEndpoints = lastFetchedResp?.criticalUrls || 0;

                    // React 18+ automatically batches these state updates into a single re-render
                    // IMPORTANT: Set data and summary BEFORE setting loading=false to avoid showing zeros
                    // Use cached untracked data
                    categorized.untracked = lastFetchedUntrackedResp || [];
                    setData(categorized);
                    setNormalData(lightweightData);
                    setEnvTypeMap(envTypeObj);
                    setSummaryData(initialSummaryDataObj);
                    setHasUsageEndpoints(true);

                    // Set loading to false AFTER all data is set
                    setLoading(false);

                    // Check if maps are already cached in PersistStore
                    const cachedCollectionsMap = PersistStore.getState().collectionsMap;

                    // Only calculate maps if they're not cached or cache is stale
                    if (!cachedCollectionsMap || Object.keys(cachedCollectionsMap).length === 0) {
                        // Calculate maps but DON'T call setters yet - do it asynchronously after render
                        const collectionsMapNew = func.mapCollectionIdToName(finalArr);
                        const hostNameMapNew = func.mapCollectionIdToHostName(finalArr);
                        const tagCollectionsMapNew = func.mapCollectionIdsToTagName(finalArr);
                        const registryStatusMapNew = func.mapCollectionIdToRegistryStatus(finalArr);

                        // Store in PersistStore asynchronously AFTER the UI has rendered
                        setTimeout(() => {
                            setCollectionsMap(collectionsMapNew);
                            setHostNameMap(hostNameMapNew);
                            setTagCollectionsMap(tagCollectionsMapNew);
                            setCollectionsRegistryStatusMap(registryStatusMapNew);
                        }, 0);
                    }

                    return; // Exit early, no API calls!
                } catch (error) {
                    // Fall through to fetch fresh data if cache processing fails
                    setLoading(true);
                }
            }
            // Build all API promises to run in parallel
            const shouldCallHeavyApis = (now - lastFetchedInfo.lastRiskScoreInfo) >= (5 * 60)
            
            let apiPromises = [
                api.getAllCollectionsBasic(),  // index 0
                api.getUserEndpoints(),         // index 1
                api.getCoverageInfoForCollections(), // index 2
                api.getLastTrafficSeen(),            // index 3
                collectionApi.fetchCountForHostnameDeactivatedCollections(), // index 4
                collectionApi.fetchCountForUningestedApis(), // index 5
                collectionApi.fetchUningestedApis(),        // index 6
            ];
            
            if(shouldCallHeavyApis){
                apiPromises = [
                    ...apiPromises,
                    ...[api.getRiskScoreInfo(), api.getSeverityInfoForCollections()] // indices 7,8 (removed getSensitiveInfoForCollections)
                ]
            }

            if(userRole === 'ADMIN' && func.checkForRbacFeature()) {
                apiPromises = [
                    ...apiPromises,
                    ...[api.getAllUsersCollections(), settingRequests.getTeamData()] // indices 10,11 or 7,8 if no heavy APIs
                ]
            }

            let results = await Promise.allSettled(apiPromises);
            
            // Extract collections response (index 0)
            const apiCollectionsResp = results[0].status === 'fulfilled' ? results[0].value : { apiCollections: [] };
            // Extract user endpoints (index 1)
            let hasUserEndpoints = results[1].status === 'fulfilled' ? results[1].value : false;
            

            // Extract metadata responses (with corrected indices)
            let coverageInfo = results[2].status === 'fulfilled' ? results[2].value : {};
            let trafficInfo = results[3].status === 'fulfilled' ? results[3].value : {};
            let deactivatedCountInfo = results[4].status === 'fulfilled' ? results[4].value : {};
            let uningestedApiCountInfo = results[5].status === 'fulfilled' ? results[5].value : {};
            let uningestedApiDetails = results[6].status === 'fulfilled' ? results[6].value : {};
            let riskScoreObj = lastFetchedResp
            let sensitiveInfo = lastFetchedSensitiveResp
            let severityObj = lastFetchedSeverityResp

        if(shouldCallHeavyApis){
            if(results[7]?.status === "fulfilled"){
                const res = results[7].value
                riskScoreObj = {
                    criticalUrls: res.criticalEndpointsCount,
                    riskScoreMap: res.riskScoreOfCollectionsMap
                }
            }

            // Skip results[8] - will fetch sensitive info asynchronously

            if(results[8]?.status === "fulfilled"){
                const res = results[8].value
                severityObj = res
            }

            // update the store which has the cached response
            setLastFetchedInfo({lastRiskScoreInfo: func.timeNow(), lastSensitiveInfo: func.timeNow()})
            setLastFetchedResp(riskScoreObj)
            setLastFetchedSeverityResp(severityObj)

        }
        setCoverageMap(coverageInfo)
        setTrafficMap(trafficInfo)

        let usersCollectionList = []
        let userList = []

        const index = !shouldCallHeavyApis ? 7 : 9 // Updated index after removing sensitive info

        if(userRole === 'ADMIN' && func.checkForRbacFeature()) {
            if(results[index]?.status === "fulfilled") {
                const res = results[index].value
                usersCollectionList = res
            }
            
            if(results[index+1]?.status === "fulfilled") {
                const res = results[index+1].value
                userList = res
                if (userList) {
                    userList = userList.filter(x => {
                        if (x?.role === "ADMIN") {
                            return false;
                        }
                        return true
                    })
                }
            }
            setUsersCollection(usersCollectionList)
            setTeamData(userList)
        }

        // Ensure all parameters are defined before calling convertToNewData
        const sensitiveInfoMap = sensitiveInfo?.sensitiveInfoMap || {};
        const severityInfoMap = severityObj || {};
        const coverageMap = coverageInfo || {};
        const trafficInfoMap = trafficInfo || {};
        const riskScoreMap = riskScoreObj?.riskScoreMap || {};

        // Guard: Prevent state update after unmount
        if (!isMountedRef.current) {
            return;
        }

        setLoading(false);
        let finalArr = apiCollectionsResp.apiCollections || [];
        if(customCollectionDataFilter){
            finalArr = finalArr.filter(customCollectionDataFilter)
        }

        // Process data - OPTIMIZATION: For large datasets (>COLLECTIONS_LAZY_RENDER_THRESHOLD items), store RAW data + transform function
        // Transformation happens on-demand in the table for each page (100 items at a time)
        const shouldOptimize = finalArr.length > COLLECTIONS_LAZY_RENDER_THRESHOLD;

        let dataObj;
        if (shouldOptimize) {
            // Store ONLY raw data + lookup maps - minimal memory footprint
            // Each item is just the raw API response (~10 properties), not the transformed object (~30 properties)
            const rawData = finalArr;

            // Attach metadata for lazy transformation
            rawData._lazyTransform = true;
            rawData._transformMaps = {
                sensitiveInfoMap,
                severityInfoMap,
                coverageMap,
                trafficInfoMap,
                riskScoreMap
            };

            dataObj = { prettify: rawData, normal: rawData };
        } else {
            // Small dataset (<500 items) - use old approach with JSX components
            dataObj = convertToNewData(finalArr, sensitiveInfoMap, severityInfoMap, coverageMap, trafficInfoMap, riskScoreMap, false);
        }

        // Ensure dataObj.prettify exists
        if (!dataObj.prettify) {
            return;
        }

        // For lazy transform, we need to transform the data first for categorization
        // The table will transform again on-demand for JSX creation, but categorization needs plain data
        let dataForCategorization = dataObj.prettify;
        if (dataObj.prettify._lazyTransform) {
            const maps = dataObj.prettify._transformMaps || {};

            // Use the centralized transformation function
            dataForCategorization = dataObj.prettify.map(c => transformRawCollectionData(c, maps));

            // Store transformed data back for table to use
            dataForCategorization._lazyTransform = true;
            dataForCategorization._transformMaps = maps;
            dataForCategorization._transformedCache = dataForCategorization;
            dataObj.prettify = dataForCategorization;
            dataObj.normal = dataForCategorization;
        }

        // Render first batch immediately to show UI fast
        const { envTypeObj, collectionMap, activeCollections, categorized } = categorizeCollections(dataForCategorization);

        setNormalData(dataObj.normal);
        let res = categorized;

        // Separate active and deactivated collections
        const deactivatedCollectionsCopy = res.deactivated.map((c)=>{
            if(deactivatedCountInfo.hasOwnProperty(c.id)){
                c.urlsCount = deactivatedCountInfo[c.id]
            }
            return c
        });

        setDeactivateCollections(deactivatedCollectionsCopy)

        // Initialize empty untracked array for immediate render
        res['untracked'] = [];
        setHasUsageEndpoints(hasUserEndpoints);

        // Use transform.getSummaryData to match master behavior (excludes API_GROUP and deactivated)
        const initialSummaryDataObj = transform.getSummaryData(dataObj.normal);
        initialSummaryDataObj.totalSensitiveEndpoints = sensitiveInfo?.sensitiveUrls || 0;
        initialSummaryDataObj.totalCriticalEndpoints = riskScoreObj?.criticalUrls || 0;

        // Render first batch immediately WITHOUT untracked processing to show UI fast
        setData(res);
        setEnvTypeMap(envTypeObj);
        setAllCollections(apiCollectionsResp.apiCollections || []);
        setSummaryData(initialSummaryDataObj);

        // Store untracked collections for use in async callbacks
        let untrackedCollectionsCache = [];

        // Process untracked API data asynchronously to avoid blocking UI
        setTimeout(() => {
            if (!isMountedRef.current) return;

            const untrackedApiDataMap = {};
            if (uningestedApiDetails && uningestedApiDetails.uningestedApiList) {
                uningestedApiDetails.uningestedApiList.forEach(api => {
                    const collectionId = api.apiCollectionId;
                    if (!untrackedApiDataMap[collectionId]) {
                        untrackedApiDataMap[collectionId] = [];
                    }
                    untrackedApiDataMap[collectionId].push(api);
                });
            }

            untrackedCollectionsCache = Object.entries(uningestedApiCountInfo || {})
                .filter(([_, count]) => count > 0)
                .map(([collectionId, untrackedCount]) => {
                    const collection = collectionMap.get(parseInt(collectionId));
                    return collection ? {
                        id: collection.id,
                        name: `untracked-${collection.id}`,
                        displayName: collection.displayName,
                        displayNameComp: collection.displayNameComp,
                        urlsCount: untrackedCount,
                        rowStatus: 'critical',
                        nextUrl: null,
                        deactivated: false,
                        collapsibleRow: untrackedApiDataMap[collectionId] ?
                            transform.getUntrackedApisCollapsibleRow(untrackedApiDataMap[collectionId]) : null,
                        collapsibleRowText: untrackedApiDataMap[collectionId] ? untrackedApiDataMap[collectionId].map(x => x.url).join(", ") : null,
                        severityInfo: {},
                        sensitiveInRespTypes: [],
                        detectedTimestamp: 0,
                        startTs: collection.startTs || 0,
                        testedEndpoints: 0,
                        riskScore: 0,
                    } : null;
                })
                .filter(Boolean);

            // Update data with untracked collections - Create a new object to ensure React detects the change
            setData(prevData => ({
                ...prevData,
                untracked: untrackedCollectionsCache
            }));

            // Cache the untracked data for future use
            setLastFetchedUntrackedResp(untrackedCollectionsCache);
        }, 0); // Execute immediately but asynchronously

        // Fetch endpoints count and sensitive info asynchronously
        Promise.all([
            dashboardApi.fetchEndpointsCount(0, 0),
            shouldCallHeavyApis ? api.getSensitiveInfoForCollections() : Promise.resolve(null)
        ]).then(([endpointsResponse, sensitiveResponse]) => {
            // Guard: Prevent state updates if component is unmounted
            if (!isMountedRef.current) {
                return;
            }

            // Update endpoints count
            if (endpointsResponse) {
                setTotalAPIs(endpointsResponse.newCount);
            }

            // Update sensitive info if available
            if(sensitiveResponse == null || sensitiveResponse === undefined){
                sensitiveResponse = {
                    sensitiveUrlsInResponse: lastFetchedSensitiveResp?.sensitiveUrls || 0,
                    sensitiveSubtypesInCollection: lastFetchedSensitiveResp?.sensitiveInfoMap || {}
                }

            }
            if (sensitiveResponse) {
                const newSensitiveInfo = {
                    sensitiveUrls: sensitiveResponse.sensitiveUrlsInResponse,
                    sensitiveInfoMap: sensitiveResponse.sensitiveSubtypesInCollection
                };

                // Update the store with new sensitive info
                setLastFetchedSensitiveResp(newSensitiveInfo);

                // Check if sensitive info actually changed to avoid unnecessary updates
                const sensitiveInfoChanged = JSON.stringify(sensitiveInfoMap) !== JSON.stringify(newSensitiveInfo.sensitiveInfoMap);

                if (sensitiveInfoChanged) {
                    // Only update sensitive fields in existing data instead of recreating everything
                    const updatedNormalData = dataObj.normal.map(item => ({
                        ...item,
                        sensitiveInRespTypes: newSensitiveInfo.sensitiveInfoMap[item.id] || []
                    }));

                    setNormalData(updatedNormalData);

                    // Update prettified data with new sensitive info
                    const updatedPrettifyData = dataObj.prettify.map(item => ({
                        ...item,
                        sensitiveInRespTypes: newSensitiveInfo.sensitiveInfoMap[item.id] || [],
                        sensitiveSubTypes: transform.prettifySubtypes(newSensitiveInfo.sensitiveInfoMap[item.id] || [], item.deactivated),
                        sensitiveSubTypesVal: (newSensitiveInfo.sensitiveInfoMap[item.id] || []).join(' ')
                    }));

                    // Re-categorize with updated data
                    const { categorized: updatedCategorized } = categorizeCollections(updatedPrettifyData);

                    // Update deactivated collections with counts
                    const updatedDeactivatedCollections = updatedCategorized.deactivated.map((c) => {
                        if(deactivatedCountInfo.hasOwnProperty(c.id)){
                            c.urlsCount = deactivatedCountInfo[c.id]
                        }
                        return c
                    });

                    updatedCategorized.deactivated = updatedDeactivatedCollections;

                    // Preserve existing untracked data or use cached version
                    setData(prevData => ({
                        ...updatedCategorized,
                        untracked: prevData.untracked || untrackedCollectionsCache
                    }));

                    // Update summary with new sensitive endpoints count
                    const updatedSummary = transform.getSummaryData(updatedNormalData);
                    updatedSummary.totalCriticalEndpoints = riskScoreObj.criticalUrls;
                    updatedSummary.totalSensitiveEndpoints = newSensitiveInfo.sensitiveUrls;
                    setSummaryData(updatedSummary);
                }
            }
        }).catch(error => {
        });

        if (res.hostname.length === 0 && (tableSelectedTab === undefined || tableSelectedTab.length === 0)) {
            setTimeout(() => {
                setSelectedTab("custom");
                setSelected(3);
            }, [100]);
        }
        setCollectionsMap(func.mapCollectionIdToName(activeCollections))
        setHostNameMap(func.mapCollectionIdToHostName(activeCollections))
        setTagCollectionsMap(func.mapCollectionIdsToTagName(activeCollections))
        setCollectionsRegistryStatusMap(func.mapCollectionIdToRegistryStatus(activeCollections))
        } catch (error) {
            setLoading(false);
        }
    }

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2)
    }

    async function fetchSvcToSvcGraphData() {
        setLoading(true)
        const {svcTosvcGraphEdges} = await api.findSvcToSvcGraphEdges()
        const {svcTosvcGraphNodes} = await api.findSvcToSvcGraphNodes()
        setLoading(false)

        setAllEdges(svcTosvcGraphEdges.map(x => {return { id: x.id, source: x.source, target: x.target}}))
        setAllNodes(svcTosvcGraphNodes.map((x, i) => {return { id: x.id, type: 'default', data: {label: x.id}, position: {x: (100 + 100*i), y: (100 + 100*i)} }}))
    }

    useEffect(() => {
        const isMountedRef = { current: true };

        fetchData(isMountedRef, false); // Use cache on mount
        resetFunc();

        // Cleanup function to prevent state updates after unmount
        return () => {
            isMountedRef.current = false;
        };
    }, [])
    const createCollectionModalActivatorRef = useRef();
    const resetResourcesSelected = () => {
        TableStore.getState().setSelectedItems([])
        selectItems([])
    }
    async function handleCollectionsAction(collectionIdList, apiFunction, toastContent, currentIsOutOfTestingScopeVal=null){
        const collectionIdListObj = collectionIdList.map(collectionId => ({ id: collectionId.toString() }))
        await (currentIsOutOfTestingScopeVal !== null
                ? apiFunction(collectionIdList, currentIsOutOfTestingScopeVal)
                : apiFunction(collectionIdListObj)).then(() => {
            func.setToast(true, false, `${collectionIdList.length} API collection${func.addPlurality(collectionIdList.length)} ${toastContent} successfully`)
        }).catch((error) => {
            func.setToast(true, true, error.message || 'Something went wrong!')
        })
        resetResourcesSelected();
        fetchData({ current: true }, true) // Force refresh after mutations
    }
    async function handleShareCollectionsAction(collectionIdList, userIdList, apiFunction){
        const userCollectionMap = {};

        for(const userId of userIdList) {
            const intUserId = parseInt(userId, 10);
            const userCollections = usersCollection[intUserId] || [];
            userCollectionMap[intUserId] = [...new Set([...userCollections, ...collectionIdList])];
        }

        await apiFunction(userCollectionMap);
        func.setToast(true, false, `${userIdList.length} Member${func.addPlurality(userIdList.length)}'s collections have been updated successfully`);
    }

    const exportCsv = (selectedResources = []) =>{
        const csvFileName = definedTableTabs[selected] + " Collections.csv"
        const selectedResourcesSet = new Set(selectedResources)
        if (!loading) {
            const wrapCsvValue = (value) => {
                const s = (value === null || value === undefined) ? '-' : String(value);
                return '"' + s.replace(/"/g, '""') + '"';
            }

            let headerTextToValueMap = Object.fromEntries(headers.map(x => [x.text, x.isText === CellType.TEXT ? x.value : x.textValue]).filter(x => x[0]?.length > 0));
            if(tableSelectedTab === "untracked"){
                headerTextToValueMap['URLs'] = "collapsibleRowText"
            }
            let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
            
            if(selectedResources.length === 0){
                data[tableSelectedTab].forEach(i => {
                    csv += Object.values(headerTextToValueMap).map(h => wrapCsvValue(i[h])).join(",") + "\r\n"
                })
            }else{
                data[tableSelectedTab].filter((i) => selectedResourcesSet.has(i.id)).forEach(i => {
                    csv += Object.values(headerTextToValueMap).map(h => wrapCsvValue(i[h])).join(",") + "\r\n"
                })
            }

            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, csvFileName) ;
            func.setToast(true, false,"CSV exported successfully")
        }
    }



    const promotedBulkActions = (selectedResourcesArr) => {
        let selectedResources;
        if(centerView === CenterViewType.Tree){
            selectedResources = selectedResourcesArr.flat();
        }else{
            selectedResources = selectedResourcesArr
        }
        let actions = [
            {
                content: 'Export as CSV',
                onAction: () => exportCsv(selectedResources)
            }
        ];
        const defaultApiGroups = allCollections.filter(x => x.type === "API_GROUP" && x.automated).map(x => x.id);
        const deactivated = deactivateCollections.map(x => x.id);
        const activated = allCollections.filter(x => { return !x.deactivated }).map(x => x.id);
        if (selectedResources.every(v => { return activated.includes(v) })) {
            actions.push(
                {
                    content: `Deactivate collection${func.addPlurality(selectedResources.length)}`,
                    onAction: () => {
                        const message = "Deactivating a collection will stop traffic ingestion and testing for this collection. Please sync the usage data via Settings > billing after deactivating a collection to reflect your updated usage. Are you sure, you want to deactivate this collection ?"
                        func.showConfirmationModal(message, "Deactivate collection", () => handleCollectionsAction(selectedResources, collectionApi.deactivateCollections, "deactivated") )
                    }
                }
            )
        } else if (selectedResources.every(v => { return deactivated.includes(v) })) {
            actions.push(
                {
                    content: `Reactivate collection${func.addPlurality(selectedResources.length)}`,
                    onAction: () =>  {
                        const message = "Please sync the usage data via Settings > billing after reactivating a collection to resume data ingestion and testing."
                        func.showConfirmationModal(message, "Activate collection", () => handleCollectionsAction(selectedResources, collectionApi.activateCollections, "activated"))
                    }
                }
            )
        }
        actions.push(
            {
                content: `Delete collection${func.addPlurality(selectedResources.length)}`,
                onAction: () => {
                    const deleteConfirmationMessage = `Are you sure, you want to delete collection${func.addPlurality(selectedResources.length)}?`
                    func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => handleCollectionsAction(selectedResources.filter(v => !defaultApiGroups.includes(v)), api.deleteMultipleCollections, "deleted"))
                }
            }
        )

        const apiCollectionShareRenderItem = (item) => {
            const { id, name, login, role } = item;
            const initials = func.initials(login)
            const media = <Avatar user size="medium" name={login} initials={initials} />
            const shortcutActions = [
                {
                    content: <Text color="subdued">{role}</Text>,
                    url: '#',
                    onAction: ((event) => event.preventDefault())
                }
            ]

            return (
                <ResourceItem
                    id={id}
                    key={id}
                    media={media}
                    shortcutActions={shortcutActions}
                    persistActions
                >
                    <Text variant="bodyMd" fontWeight="bold" as="h3">
                        {name}
                    </Text>
                    <Text variant="bodyMd">
                        {login}
                    </Text>
                </ResourceItem>
            );
        }

        const shareCollectionHandler = () => {
            if (selectedItems.length > 0) {
                handleShareCollectionsAction(selectedResources, selectedItems, api.updateUserCollections);
                return true
            } else {
                func.setToast(true, true, "No member is selected!");
                return false
            }
        };

        const handleSelectedItemsChange = (items) => {
            setSelectedItems(items);
        };

        const shareComponentChildrens = (
            <Box>
                <Box padding={5} background="bg-subdued-hover">
                    <Text fontWeight="medium">{`${selectedResources.length} collection${func.addPlurality(selectedResources.length)} selected`}</Text>
                </Box>
                    <SearchableResourceList
                        resourceName={'user'}
                        items={teamData}
                        renderItem={apiCollectionShareRenderItem}
                        isFilterControlEnabale={true}
                        selectable={true}
                        onSelectedItemsChange={handleSelectedItemsChange}
                    />
            </Box>
        )

        const shareContent = (
            <ResourceListModal
                isLarge={true}
                activatorPlaceaholder={"Share"}
                title={"Share collections"}
                primaryAction={shareCollectionHandler}
                component={shareComponentChildrens}
            />
        )

    let rbacAccess = func.checkForRbacFeature();
    if(userRole === 'ADMIN' && rbacAccess) {
        actions.push(
            {
                content: shareContent,
            }
        )
    }

        const toggleTypeContent = (
            <Popover
                activator={<div onClick={() => setPopover(!popover)}>Set tags</div>}
                onClose={() => {
                    setPopover(false)
                }}
                active={popover}
                autofocusTarget="first-node"
            >
                <Popover.Pane>
                    <SetUserEnvPopupComponent
                        popover={popover}
                        setPopover={setPopover}
                        tags={envTypeMap}
                        updateTags={updateTags}
                        apiCollectionIds={selectedResources}
                    />
                </Popover.Pane>
            </Popover>
        )

        const toggleEnvType = {
            content: toggleTypeContent
        }

        const allOutOfTestScopeFalse = selectedResources.every(id => {
            const collection = normalData.find(c => c.id === id);
            return collection && !collection.isOutOfTestingScope;
        })

        const allOutOfTestScopeTrue = selectedResources.every(id => {
            const collection = normalData.find(c => c.id === id);
            return collection && collection.isOutOfTestingScope;
        })

        let content = "";
        let toastContent = "";
        if(allOutOfTestScopeFalse){
            content = `Mark collection${func.addPlurality(selectedResources.length)} as out of testing scope`
            toastContent = "marked out of testing scope"
        }else if(allOutOfTestScopeTrue){
            content = `Mark collection${func.addPlurality(selectedResources.length)} as in testing scope`
            toastContent = "marked in testing scope"
        }

        if(content.length > 0 && toastContent.length > 0){
            actions.push(
                {
                    content: content,
                    onAction: () => handleCollectionsAction(selectedResources, collectionApi.toggleCollectionsOutOfTestScope, toastContent, allOutOfTestScopeTrue)
                }
            )
        }
        const bulkActionsOptions = [...actions];
        bulkActionsOptions.push(toggleEnvType)
        return bulkActionsOptions
    }
    const updateData = (dataMap) => {
        let copyObj = data;
        Object.keys(copyObj).forEach((key) => {
            data[key].length > 0 && data[key].forEach((c) => {
                const list = dataMap[c?.id]?.map(func.formatCollectionType);
                c['envType'] = list
                c['envTypeComp'] = transform.getCollectionTypeList(list, 1, false)
            })
        })
        setData(copyObj)
        setRefreshData(!refreshData)
    }

    const updateTags = async (apiCollectionIds, tagObj) => {
        let copyObj = await JSON.parse(JSON.stringify(envTypeMap))
        apiCollectionIds.forEach(id => {
            if(!copyObj[id]) {
                copyObj[id] = []
            }

            if(tagObj === null) {
                copyObj[id] = []
            } else {
                if(tagObj?.keyName?.toLowerCase() === 'envtype') {
                    const currentEnvIndex = copyObj[id].findIndex(tag => 
                        tag.keyName.toLowerCase() === 'envtype' &&
                        (tag.value.toLowerCase() === 'production' || tag.value.toLowerCase() === 'staging')
                    )

                    if (currentEnvIndex === -1) {
                        copyObj[id].push(tagObj)
                    } else {
                        const currentValue = copyObj[id][currentEnvIndex].value.toLowerCase()
                        if (tagObj.value.toLowerCase() !== currentValue) {
                            copyObj[id][currentEnvIndex] = tagObj
                        } else {
                            copyObj[id].splice(currentEnvIndex, 1)
                        }
                    }
                } else {
                    const index = copyObj[id].findIndex(tag => 
                        tag.keyName === tagObj.keyName && tag.value === tagObj.value
                    )

                    if (index === -1) {
                        copyObj[id].push(tagObj)
                    } else {
                        copyObj[id].splice(index, 1)
                    }
                }
            }
        })



        await api.updateEnvTypeOfCollection(tagObj === null ? tagObj : [tagObj], apiCollectionIds, tagObj === null).then((resp) => {
            func.setToast(true, false, "Tags updated successfully")
            setEnvTypeMap(copyObj)
            updateData(copyObj)
        })

        resetResourcesSelected();
        setPopover(false)
    }

    const modalComponent = <CreateNewCollectionModal
        key="modal"
        active={active}
        setActive={setActive}
        createCollectionModalActivatorRef={createCollectionModalActivatorRef}
        fetchData={fetchData}
    />

    let coverage = '0%';
    if(summaryData.totalAllowedForTesting !== 0){
        if(summaryData.totalAllowedForTesting < summaryData.totalTestedEndpoints){
            coverage = '100%'
        }else{
            coverage = Math.ceil((summaryData.totalTestedEndpoints * 100) / summaryData.totalAllowedForTesting) + '%'
        }
    }

      const summaryItems = [
        {
            title: mapLabel("Total APIs", getDashboardCategory()),
            data: transform.formatNumberWithCommas(totalAPIs),
        },
        {
            title: mapLabel("Critical APIs", getDashboardCategory()),
            data: transform.formatNumberWithCommas(summaryData.totalCriticalEndpoints || 0),
        },
        {
            title: mapLabel("Tested APIs (Coverage)", getDashboardCategory()),
            data: coverage
        },
        {
            title: mapLabel("Sensitive in response APIs", getDashboardCategory()),
            data: transform.formatNumberWithCommas(summaryData.totalSensitiveEndpoints || 0),
        }
    ]

    function switchToGraphView() {
        setCenterView(centerView === CenterViewType.Graph ? CenterViewType.Table : CenterViewType.Graph)
        fetchSvcToSvcGraphData()
    }

    const secondaryActionsComp = (
        <HorizontalStack gap={2}>
            <Popover
                active={moreActions}
                activator={(
                    <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                        More Actions
                    </Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setMoreActions(false) }}
            >
                <Popover.Pane fixed>
                    <ActionList
                        actionRole="menuitem"
                        sections={
                            [
                                {
                                    title: 'Export',
                                    items: [
                                        {
                                            content: 'Export as CSV',
                                            onAction: () => exportCsv(),
                                            prefix: <Box><Icon source={FileMinor} /></Box>
                                        }
                                    ]
                                },
                                {
                                    title: 'Switch view',
                                    items: [
                                        {
                                            content: centerView === CenterViewType.Tree ? "Hide tree view": "Display tree view",
                                            onAction: () => setCenterView(centerView === CenterViewType.Tree ? CenterViewType.Table : CenterViewType.Tree),
                                            prefix: <Box><Icon source={centerView === CenterViewType.Tree ? HideMinor : ViewMinor} /></Box>
                                        },
                                        window.USER_NAME && window.USER_NAME.endsWith("akto.io") &&{
                                            content: centerView === CenterViewType.Graph ? "Hide graph view": "Display graph view",
                                            onAction: () => switchToGraphView(),
                                            prefix: <Box><Icon source={centerView === CenterViewType.Graph ? HideMinor : ViewMinor} /></Box>
                                        }
                                    ]
                                }
                            ]
                        }
                    />
                </Popover.Pane>
            </Popover>
            <Button id={"create-new-collection-popup"} secondaryActions onClick={showCreateNewCollectionPopup}>Create new collection</Button>
        </HorizontalStack>
    )


    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex)
    }

    const filterTreeViewData = (data) => {
        return data.filter((x) => (!x?.deactivated && x?.type !== "API_GROUP" && x?.urlsCount > 1));
    }

    // Use titleWithTooltip for Groups tab (selected === 2)
    const dynamicHeaders = selected === 2 ? headers.map(h => h.titleWithTooltip ? {...h, title: h.titleWithTooltip} : h) : headers;

    // Ensure all headers have unique IDs for IndexTable headings to avoid duplicate key warnings
    const headingsWithIds = dynamicHeaders.map((header, index) => ({
        ...header,
        id: header.id || header.value || header.text || `header-${index}`,
        // Replace empty titles with a space to avoid empty string keys
        title: (typeof header.title === 'string' && header.title.trim() === '') ? ' ' : header.title
    }));

    const tableComponent = (
        centerView === CenterViewType.Tree ?
        <TreeViewTable
            collectionsArr={filterTreeViewData(normalData)}
            sortOptions={sortOptions}
            resourceName={resourceName}
            tableHeaders={headingsWithIds.filter((x) => x.shouldMerge !== undefined)}
            promotedBulkActions={promotedBulkActions}
        />:
        (centerView === CenterViewType.Table ?
        <GithubSimpleTable
            key={refreshData}
            pageLimit={100}
            data={data[selectedTab]}
            sortOptions={ selectedTab === 'groups' ? [...tempSortOptions, ...sortOptions] : sortOptions}
            resourceName={resourceName}
            filters={[]}
            disambiguateLabel={disambiguateLabel}
            headers={headingsWithIds}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            mode={IndexFiltersMode.Default}
            headings={headingsWithIds}
            useNewRow={true}
            condensedHeight={true}
            tableTabs={tableTabs}
            onSelect={handleSelectedTab}
            selected={selected}
            csvFileName={"Inventory"}
            prettifyPageData={(pageData) => selectedTab === 'untracked' ? transform.prettifyUntrackedCollectionsData(pageData) : transform.prettifyCollectionsData(pageData, false, selectedTab)}
            transformRawData={transformRawCollectionData}
        />:    <div style={{height: "800px"}}>

        <ReactFlow
            nodes={allNodes}
            edges={allEdges}
            onNodesChange={onAllNodesChange}
            onEdgesChange={onAllEdgesChange}
        >
            <Background color="#aaa" gap={16} />
        </ReactFlow>    </div>    
        )
    )

    const components = loading ? [<SpinnerCentered key={"loading"}/>]: [<SummaryCardInfo summaryItems={summaryItems} key="summary"/>, (!hasUsageEndpoints ? <CollectionsPageBanner key="page-banner" /> : null) ,modalComponent, tableComponent]

    if(onlyShowCollectionsTable){
        sendData(data)
        return (
            <Box paddingBlockStart={4} paddingInline={4}>
                {tableComponent}
            </Box>
        )
    }

    return(
        <PageWithMultipleCards
            title={
                <TitleWithInfo 
                    tooltipContent={"Akto automatically groups similar APIs into meaningful collections based on their subdomain names. "}
                    titleText={mapLabel("API Collections", getDashboardCategory())} 
                    docsUrl={"https://docs.akto.io/api-inventory/concepts"}
                />
            }
            primaryAction={<Button id={"explore-mode-query-page"} primary secondaryActions onClick={navigateToQueryPage}>Explore mode</Button>}
            isFirstPage={true}
            components={components}
            secondaryActions={secondaryActionsComp}
        />
    )
}

export default ApiCollections 
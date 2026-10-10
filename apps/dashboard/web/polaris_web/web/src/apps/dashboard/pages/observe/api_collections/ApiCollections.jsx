import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, IndexFiltersMode, Box, Popover, ActionList, ResourceItem, Avatar,  HorizontalStack, Icon, Modal, VerticalStack, Tooltip, Filters, ChoiceList} from "@shopify/polaris"
import { HideMinor, ViewMinor,FileMinor } from '@shopify/polaris-icons';
import RunTest from "./RunTest";
import api from "../api"
import dashboardApi from "../../dashboard/api"
import settingRequests from "../../settings/api"
import React, { useEffect, useState, useRef, useMemo } from "react"
import func from "@/util/func"
import GithubSimpleTable from "@/apps/dashboard/components/tables/GithubSimpleTable";
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";

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
import TreeViewTable from "../../../components/shared/treeView/TreeViewTable"
import TableStore from "../../../components/tables/TableStore";
import { useNavigate, useSearchParams } from "react-router-dom";
import ReactFlow, {
    Background,  useNodesState,
    useEdgesState,

  } from 'react-flow-renderer';
import SetUserEnvPopupComponent from "./component/SetUserEnvPopupComponent";
import { getDashboardCategory, mapLabel, isMCPSecurityCategory, isAgenticSecurityCategory, isEndpointSecurityCategory, isApiSecurityCategory, isDastCategory } from "../../../../main/labelHelper";
import useAgenticFilter, { FILTER_TYPES, parseFilterFromUrl } from "./useAgenticFilter";
import { AGENTIC_OBSERVE_BACK_PATHS, INVENTORY_FILTER_KEY, fetchAndCacheSkillApiData, fetchAndCacheAgenticTrafficRiskBundle, fetchAndCacheAgenticSensitiveInfo } from "../agentic/constants";
import AgentEndpointTreeTable from "./AgentEndpointTreeTable";
import { fetchEndpointShieldUsernameMap, getUsernameForCollection } from "./endpointShieldHelper";
import { sendQuery } from "../../agentic/services/agenticService";
import AgenticThinkingBox from "../../agentic/components/AgenticThinkingBox";
import ConversationHistory from "../../testing/TestRunResultPage/components/ConversationHistory";
import { usePermissions } from "@/util/permissions";
import AllowedAction from "../../../components/shared/AllowedAction";
  
const CenterViewType = {
    Table: 0,
    Tree: 1,
    Graph: 2
  }

const TREE_VIEW_MAX_COLLECTIONS = 1000; // the tree view needs every row, so it loads the top collections by endpoint count only
// how long a page waits for its coverage before showing the rows without them
const DETAILS_GRACE_MS = 500
const EMPTY_NARROWING = JSON.stringify({ queryValue: '', filters: {}, tagFilters: {} })
const PAGE_FETCH_LIMIT = 100; // the most rows the backend returns per page
const STATS_PENDING_RETRY_MS = 3000;
const STATS_PENDING_MAX_RETRIES = 5;
const allowedAccounts = [1736798101, 1718042191];


const headers = [
    ...((isMCPSecurityCategory() || isAgenticSecurityCategory() || isEndpointSecurityCategory() || isApiSecurityCategory() || isDastCategory()) ? [{
        title: "",
        text: "",
        value: "iconComp",
        isText: CellType.TEXT,
        boxWidth: '24px'
    }] : []),
    ...(isEndpointSecurityCategory() ? [
        {
            title: mapLabel("API collection name", getDashboardCategory()),
            text: mapLabel("API collection name", getDashboardCategory()),
            value: "displayNameComp",
            filterKey: "splitApiCollectionName",
            textValue: 'splitApiCollectionName',
            showFilter: true,
            titleWithTooltip: <HeadingWithTooltip content="These API groups are computed periodically" title="API Collection name" />
        },
        {
            title: "Endpoint ID",
            text: "Endpoint ID",
            value: "endpointId",
            filterKey: "endpointId",
            textValue: 'endpointId',
            showFilter: true,
            isText: CellType.TEXT,
            boxWidth: '100px'
        },
        {
            title: "Username",
            text: "Username",
            value: "username",
            filterKey: "username",
            textValue: 'username',
            showFilter: true,
            isText: CellType.TEXT,
            boxWidth: '150px'
        }
    ] : [{
        title: mapLabel("API collection name", getDashboardCategory()),
        text: mapLabel("API collection name", getDashboardCategory()),
        value: "displayNameComp",
        filterKey: "displayName",
        textValue: 'displayName',
        showFilter: true,
        titleWithTooltip: <HeadingWithTooltip content="These API groups are computed periodically" title={mapLabel("API collection name", getDashboardCategory())} />
    }]),
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
    ...(!isEndpointSecurityCategory() ? [{
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
    }] : []),
    ...(!isEndpointSecurityCategory() ? [{
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
    }] : []),
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
    ...(allowedAccounts.includes(Number(window.ACTIVE_ACCOUNT)) ? [{
        title: "Access Type",
        text: "Access Type",
        value: "accessType",
        textValue: "accessType",
        filterKey: "accessType",
        showFilter: true,
        isText: CellType.TEXT,
        boxWidth: '120px'
    }] : []),
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
    ...(!isEndpointSecurityCategory() ? [{
        title: "Out of " + mapLabel('Testing', getDashboardCategory()) + " scope",
        text: 'Out of ' + mapLabel('Testing', getDashboardCategory()) + ' scope',
        value: 'outOfTestingScopeComp',
        textValue: 'isOutOfTestingScope',
        filterKey: 'isOutOfTestingScope',
        tooltipContent: 'Whether the collection is excluded from testing '
    }] : [])
];

const isAtlas = isEndpointSecurityCategory()
const isArgus = isAgenticSecurityCategory()
const isAtlasArgus = isAtlas || isArgus;

// For Endpoint Security, we have an extra column (Endpoint ID) after API Collection name
const nameColIndex = isAtlasArgus ? 2 : 1;
const endpointColIndex = isAtlas ? 4 : (isArgus ? 3 : 2);
const discoveredColIndex = isArgus ? 10 : (isAtlas ? 9 : 9);
const trafficColIndex = isArgus ? 9 : (isAtlas ? 8 : 8);
const riskColIndex = isAtlas ? 5 : (isAtlasArgus ? 4 : 3)

const tempSortOptions = [
    { label: 'Name', value: 'customGroupsSort asc', directionLabel: 'A-Z', sortKey: 'customGroupsSort', columnIndex: nameColIndex},
    { label: 'Name', value: 'customGroupsSort desc', directionLabel: 'Z-A', sortKey: 'customGroupsSort', columnIndex: nameColIndex},
]

const sortOptions = [
    { label: mapLabel("Endpoints", getDashboardCategory()), value: 'urlsCount asc', directionLabel: 'More', sortKey: 'urlsCount', columnIndex: endpointColIndex },
    { label: mapLabel("Endpoints", getDashboardCategory()), value: 'urlsCount desc', directionLabel: 'Less', sortKey: 'urlsCount', columnIndex: endpointColIndex },
    { label: 'Risk Score', value: 'score asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: riskColIndex },
    { label: 'Risk Score', value: 'score desc', directionLabel: 'Low risk', sortKey: 'riskScore', columnIndex: riskColIndex },
    { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: discoveredColIndex},
    { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs', columnIndex: discoveredColIndex },
    { label: 'Last traffic seen', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'detectedTimestamp', columnIndex: trafficColIndex },
    { label: 'Last traffic seen', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'detectedTimestamp', columnIndex: trafficColIndex },
];


// the sort keys fetchApiCollectionsPage understands
const SERVER_SORT_KEYS = {
    endpoints: 'urlsCount',
    riskScore: 'riskScore',
    discovered: 'startTs',
    lastSeen: 'detectedTimestamp',
    name: 'customGroupsSort',
}

// the table's sort option value (first word) -> the server sort key
const SORT_VALUE_TO_SERVER_KEY = {
    urlsCount: SERVER_SORT_KEYS.endpoints,
    score: SERVER_SORT_KEYS.riskScore,
    discovered: SERVER_SORT_KEYS.discovered,
    detected: SERVER_SORT_KEYS.lastSeen,
    customGroupsSort: SERVER_SORT_KEYS.name,
}

// the selected tab id -> the tab name the backend filters on
const SERVER_TABS = {
    all: 'ALL',
    hostname: 'HOSTNAME',
    groups: 'GROUP',
    custom: 'CUSTOM',
    deactivated: 'DEACTIVATED',
    untracked: 'UNTRACKED',
}

const resourceName = {
    singular: 'collection',
    plural: 'collections',
  };

// Tested apis come from another collection and so arrive after a page's rows: everything in a row that
// derives from them. A query that failed shows "-".
const coverageFields = (collection, coverageMap, unavailable = false) => {
    const testedEndpoints = collection.urlsCount === 0 ? 0 : (coverageMap[collection.id] || 0);

    let coverage = '0%';
    if(!collection.isOutOfTestingScope && collection.urlsCount > 0){
        if(collection.urlsCount < testedEndpoints){
            coverage = '100%'
        } else {
            coverage = Math.ceil((testedEndpoints * 100)/collection.urlsCount) + '%'
        }
    } else if(collection.isOutOfTestingScope){
        coverage = 'N/A'
    }
    return { testedEndpoints, coverage: unavailable ? '-' : coverage }
}

// Open issues, which come with the rows
const issueFields = (collection, severityInfoMap) => {
    const severityInfo = severityInfoMap[collection.id] || {};
    // Build issuesArrVal in same format as transform.getIssuesListText
    const sortedSeverityInfo = func.sortObjectBySeverity(severityInfo);
    let issuesArrVal = "-";
    if(Object.keys(sortedSeverityInfo).length > 0){
        issuesArrVal = "";
        Object.keys(sortedSeverityInfo).forEach((key) => {
            issuesArrVal += (key + ": " + sortedSeverityInfo[key] + " ");
        });
    }

    return {
        severityInfo,
        issuesArrVal,
        severityInfoCount: Object.keys(severityInfo).reduce((sum, key) => sum + (severityInfo[key] || 0), 0),
    }
}

// Transform raw collection data to plain data (without JSX) for filtering/sorting
// This function is passed to the table component for lazy transformation
const transformRawCollectionData = (rawCollection, transformMaps) => {
    const trafficInfoMap = transformMaps?.trafficInfoMap || {};
    const coverageMap = transformMaps?.coverageMap || {};
    const riskScoreMap = transformMaps?.riskScoreMap || {};
    const severityInfoMap = transformMaps?.severityInfoMap || {};
    const sensitiveInfoMap = transformMaps?.sensitiveInfoMap || {};
    const usernameMap = transformMaps?.usernameMap || {};

    const detected = func.prettifyEpoch(trafficInfoMap[rawCollection.id] || 0);
    const discovered = func.prettifyEpoch(rawCollection.startTs || 0);
    const riskScore = rawCollection.urlsCount === 0 ? 0 : (riskScoreMap[rawCollection.id] || 0);
    const rawEnvType = rawCollection?.envType || null;

    const envType = Array.isArray(rawCollection?.envType) ? rawCollection.envType.map(func.formatCollectionType) : [];

    // Extract individual tag key-value pairs from raw envType for filtering
    const tagKeyValues = {};
    if (Array.isArray(rawEnvType)) {
        rawEnvType.forEach(tagObj => {
            if (tagObj?.keyName && tagObj?.value) {
                const key = `tagKey_${tagObj.keyName}`;
                // For each tag key, store the value
                if (tagKeyValues[key]) {
                    tagKeyValues[key] = `${tagKeyValues[key]}, ${tagObj.value}`;
                } else {
                    tagKeyValues[key] = tagObj.value;
                }
            }
        });
    }

    const sensitiveTypes = sensitiveInfoMap[rawCollection.id] || [];

    // Split collection name - always extract endpointId/sourceId/serviceName for agentic collections
    // Pattern: <endpoint-id>.<source-id>.<service-name>
    let splitApiCollectionName = rawCollection.displayName;
    let endpointId = '';
    let sourceId = '';
    let serviceName = '';
    // Always try to split if the name has dots (for agentic collections)
    const splitResult = transform.splitCollectionNameForEndpointSecurity(rawCollection.displayName);
    if (splitResult.endpointId) {
        endpointId = splitResult.endpointId;
        sourceId = splitResult.sourceId;
        serviceName = splitResult.serviceName;
    }
    // Only modify splitApiCollectionName for Endpoint Security category
    if (isEndpointSecurityCategory()) {
        splitApiCollectionName = splitResult.apiCollectionName;
    }

    // Return minimal object - only fields needed for filtering, sorting, and categorization
    // JSX components will be created on-demand by prettifyPageData
    return {
        id: rawCollection.id,
        displayName: rawCollection.displayName,
        splitApiCollectionName: splitApiCollectionName,
        endpointId: endpointId,
        sourceId: sourceId,
        serviceName: serviceName,
        hostName: rawCollection.hostName,
        type: rawCollection.type,
        deactivated: rawCollection.deactivated,
        urlsCount: rawCollection.urlsCount,
        startTs: rawCollection.startTs,
        tagsList: rawCollection.tagsList || rawCollection.envType || [],
        skills: rawCollection.skills,
        registryStatus: rawCollection.registryStatus,
        description: rawCollection.description,
        isOutOfTestingScope: rawCollection.isOutOfTestingScope,
        automated: rawCollection.automated,
        accessType: rawCollection.accessType ? rawCollection.accessType : "No Access Type",
        envType,
        envTypeOriginal: rawEnvType,
        ...coverageFields(rawCollection, coverageMap),
        ...issueFields(rawCollection, severityInfoMap),
        sensitiveInRespTypes: sensitiveTypes,
        sensitiveSubTypesVal: sensitiveTypes.join(' ') || '-',
        sensitiveInRespCount: sensitiveTypes.length,
        detectedTimestamp: trafficInfoMap[rawCollection.id] || 0,
        riskScore,
        baseRiskScore: rawCollection.baseRiskScore,
        baseRiskScoreReason: rawCollection.baseRiskScoreReason,
        detected,
        discovered,
        nextUrl: '/dashboard/observe/inventory/' + rawCollection.id,
        lastTraffic: detected,
        rowStatus: rawCollection.deactivated ? 'critical' : undefined,
        disableClick: rawCollection.deactivated || false,
        deactivatedRiskScore: rawCollection.deactivated ? (riskScore - 10) : riskScore,
        activatedRiskScore: -1 * (rawCollection.deactivated ? riskScore : (riskScore - 10)),
        username: getUsernameForCollection(rawCollection, usernameMap),
        ...tagKeyValues  // Add individual tag key-value pairs for filtering
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
    const { canCall } = usePermissions()

    const navigate = useNavigate();

    // /inventory is deprecated for Atlas (its left-nav always targets /agentic-assets directly, so
    // reaching /inventory here means a stale link/back-button); redirect unless embedded (e.g.
    // McpSecurityPage's tab). Argus has NO equivalent grouped page — its own left-nav intentionally
    // sends it to /inventory, and this component's isArgus-conditional rendering below is its real
    // view, so it must not be redirected away from itself.
    //
    // Exception within Atlas itself: UsersAndDevices.jsx's row click stores a device/agent filter
    // under INVENTORY_FILTER_KEY *before* navigating here, so this page can render that device's
    // endpoint tree (AgentEndpointTreeTable, see useTreeView below). Redirecting unconditionally
    // skipped that tree entirely and sent the user straight to the grouped assets page instead,
    // discarding the filter/context — so redirect only when there's no such filter, i.e. a stale
    // link/bookmark/back-button, not a drill-down.
    const inventoryPageFilters = PersistStore(state => state.filtersMap)?.[INVENTORY_FILTER_KEY];
    // The specific device/agent hostName list from that filter, when present — lets fetchData's
    // mount effect below take a scoped path (fetchCollectionsBasicForHostNames, a real DB $in
    // query) instead of api.getAllCollectionsBasic()'s whole-account fetch, which doesn't scale to
    // accounts with thousands of devices/collections just to filter down to one device's handful
    // client-side afterward (useAgenticFilter's hostName branch). Only hostName-type filters have
    // this scoped alternative; envType/tag-based filters still need the full account fetch to
    // match tag values, so they keep going through fetchData unchanged.
    const deviceHostNamesFilter = inventoryPageFilters?.filters?.find(f => f.key === 'hostName' && !f.value?.negated)?.value?.values;
    // The collections table is served one page at a time by the backend. Three views still need every
    // collection in the browser, because they filter on tags client side: the table embedded in another
    // page, and the agentic drill-downs (a filter handed over from the Endpoints page, in the store or url).
    const [urlSearchParams] = useSearchParams();
    const urlFilters = parseFilterFromUrl(urlSearchParams);
    const hasAgenticFilter = Boolean(
        inventoryPageFilters?.filters?.some(f => (f.key === 'envType' || f.key === 'hostName') && f.value?.values?.length > 0)
        || urlFilters.envTypeFilter || urlFilters.hostNameFilter
    );
    const scopedMode = Boolean(onlyShowCollectionsTable || customCollectionDataFilter || hasAgenticFilter);

    useEffect(() => {
        if (!onlyShowCollectionsTable && isEndpointSecurityCategory() && !inventoryPageFilters) {
            navigate("/dashboard/observe/agentic-assets", { replace: true });
        }
    }, [navigate, inventoryPageFilters]);

    const getAgenticObserveBackUrl = () => {
        if (!isEndpointSecurityCategory()) return undefined;
        try {
            const stack = JSON.parse(sessionStorage.getItem('pathnameStack') || '[]');
            if (stack.length >= 2) {
                const previousPath = stack[stack.length - 2];
                if (AGENTIC_OBSERVE_BACK_PATHS.includes(previousPath)) {
                    return previousPath;
                }
            }
        } catch (e) { /* ignore */ }
        return undefined;
    };
    const agenticObserveBackUrl = getAgenticObserveBackUrl();
    
    const [data, setData] = useState({'all': [], 'hostname':[], 'groups': [], 'custom': [], 'deactivated': [], 'untracked': []})
    const [active, setActive] = useState(false);
    const [loading, setLoading] = useState(false)

    const [summaryData, setSummaryData] = useState({totalEndpoints:0 , totalTestedEndpoints: 0, totalSensitiveEndpoints: 0, totalCriticalEndpoints: 0, totalAllowedForTesting: 0})
    const [hasUsageEndpoints, setHasUsageEndpoints] = useState(true)
    const [envTypeMap, setEnvTypeMap] = useState({})
    const [usernameMap, setUsernameMap] = useState({})
    const [refreshData, setRefreshData] = useState(false)
    const [popover,setPopover] = useState(false)
    const [teamData, setTeamData] = useState([])
    const [usersCollection, setUsersCollection] = useState([])

    // Get filtersMap from PersistStore first (needed for tag filter initialization)
    const filtersMap = PersistStore(state => state.filtersMap)
    const setFiltersMap = PersistStore(state => state.setFiltersMap)
    const pageKey = "/dashboard/observe/inventory/"

    // Tag filter state - initialize from persisted state
    const pageFiltersMap = filtersMap[pageKey];
    const [tagFiltersApplied, setTagFiltersApplied] = useState(pageFiltersMap?.tagFilters || {})
    const [selectedItems, setSelectedItems] = useState([])
    const [normalData, setNormalData] = useState([])
    const [centerView, setCenterView] = useState(CenterViewType.Table);
    const [moreActions, setMoreActions] = useState(false);
    const [showAnalysisModal, setShowAnalysisModal] = useState(false);
    const [analysisConversations, setAnalysisConversations] = useState([]);
    const [analysisLoading, setAnalysisLoading] = useState(false);
    const [analysisConversationId, setAnalysisConversationId] = useState(null);
    const [showMultiCollectionRunTest, setShowMultiCollectionRunTest] = useState(false);
    const [selectedCollectionIdsForTest, setSelectedCollectionIdsForTest] = useState([]);
    const [blockedSkillCollectionIds, setBlockedSkillCollectionIds] = useState(new Set());

    // const dummyData = dummyJson;

    // untracked collections are only served by the paginated view
    const definedTableTabs = scopedMode ? ['All', 'Hostname', 'Groups', 'Custom', 'Deactivated'] : ['All', 'Hostname', 'Groups', 'Custom', 'Deactivated', 'Untracked']

    const { tabsInfo, selectItems } = useTable()
    const tableSelectedTab = PersistStore.getState().tableSelectedTab[window.location.pathname]
    const initialSelectedTab = tableSelectedTab || "hostname";
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab)
    let initialTabIdx = func.getTableTabIndexById(1, definedTableTabs, initialSelectedTab)
    const [selected, setSelected] = useState(initialTabIdx)
    
    const [pageMeta, setPageMeta] = useState({ loaded: false, tabCounts: {}, tagChoices: {}, statsUpdatedAt: {}, statsPending: false })
    // the backend names its tab counts like the table's tab ids
    const tableCountObj = func.getTabsCount(definedTableTabs, scopedMode ? data : { _counts: pageMeta.tabCounts })
    const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

    const setInventoryFlyout = ObserveStore(state => state.setInventoryFlyout)
    const setFilteredItems = ObserveStore(state => state.setFilteredItems) 
    const setSamples = ObserveStore(state => state.setSamples)
    const setSelectedUrl = ObserveStore(state => state.setSelectedUrl)

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

    const setCoverageMap = PersistStore(state => state.setCoverageMap)
    const setTrafficMap = PersistStore(state => state.setTrafficMap)

    const totalAPIs = PersistStore(state => state.totalAPIs)
    const setTotalAPIs = PersistStore(state => state.setTotalAPIs)
    const [allEdges, setAllEdges, onAllEdgesChange] = useEdgesState([])
    const [allNodes, setAllNodes, onAllNodesChange] = useNodesState([])

    // Every row any loaded page returned, by id: bulk actions need the rows behind a selection, and
    // a selection can span pages.
    const rowsSeenRef = useRef(new Map())
    const forceRefreshRef = useRef(false)
    const statsRetriesRef = useRef(0)
    // the search and filters the table was last asked for, as JSON
    const narrowingRef = useRef(EMPTY_NARROWING)
    const [treeData, setTreeData] = useState([])
    const [treeLoading, setTreeLoading] = useState(false)
    // bumped when a page's coverage lands, so the table swaps them in
    const [detailsVersion, setDetailsVersion] = useState(0)

    const rowOf = (id) => normalDataById.get(id) || rowsSeenRef.current.get(id)

    // ---- paginated view -------------------------------------------------------------------------

    async function fetchPageMeta(isMountedRef = { current: true }) {
        try {
            const meta = await collectionApi.fetchApiCollectionsPageMeta()
            if (!isMountedRef.current) return
            const summary = meta?.summary || {}
            const tabCounts = meta?.tabCounts || {}
            // the meta's counts are of the whole account; while the table is narrowed, its own counts stand
            const narrowed = narrowingRef.current !== EMPTY_NARROWING
            if (narrowed) loadTabCounts(JSON.parse(narrowingRef.current), isMountedRef)
            setPageMeta(prev => ({
                loaded: true,
                tabCounts: narrowed ? prev.tabCounts : tabCounts,
                tagChoices: meta?.tagChoices || {},
                statsUpdatedAt: meta?.statsUpdatedAt || {},
                statsPending: !!meta?.statsPending,
            }))
            setSummaryData({
                totalEndpoints: 0,
                totalTestedEndpoints: summary.totalTestedEndpoints || 0,
                totalSensitiveEndpoints: summary.totalSensitiveEndpoints || 0,
                totalCriticalEndpoints: summary.totalCriticalEndpoints || 0,
                totalAllowedForTesting: summary.totalAllowedForTesting || 0,
            })
            setHasUsageEndpoints(meta?.hasUsageEndpoints !== false)
            if ((tabCounts.hostname || 0) === 0 && (tableSelectedTab === undefined || tableSelectedTab.length === 0)) {
                setSelectedTab("custom")
                setSelected(3)
            }
        } catch (error) {
            if (isMountedRef.current) setPageMeta(prev => ({ ...prev, loaded: true }))
        }
    }

    // The Total APIs tile: its own call so it never holds the header or the table back
    async function fetchTotalApis(isMountedRef) {
        try {
            const resp = await dashboardApi.fetchEndpointsCount(0, 0)
            if (resp && isMountedRef.current) setTotalAPIs(resp.newCount)
        } catch (error) {
            // the tile keeps its last known value
        }
    }

    // Raw rows of a page response -> the plain rows the table and the bulk actions work with.
    function toPlainRows(resp) {
        const transformMaps = {
            trafficInfoMap: resp.lastSeenMap || {},
            // coverage arrives separately (loadDetails), after the rows are shown
            coverageMap: {},
            riskScoreMap: resp.riskScoreMap || {},
            severityInfoMap: resp.severityInfoMap || {},
            sensitiveInfoMap: resp.sensitiveInfoMap || {},
            usernameMap,
        }
        return (resp.apiCollections || []).map(c => ({
            ...transformRawCollectionData(c, transformMaps),
            issuesUnavailable: !!resp.issuesUnavailable,
        }))
    }

    function toUntrackedRows(resp) {
        return (resp.untrackedRows || []).map(r => {
            const apis = r.uningestedApiList || []
            return {
                id: r.id,
                name: `untracked-${r.id}`,
                displayName: r.displayName,
                urlsCount: r.urlsCount,
                rowStatus: 'critical',
                nextUrl: null,
                deactivated: false,
                severityInfo: {},
                sensitiveInRespTypes: [],
                detectedTimestamp: 0,
                startTs: r.startTs || 0,
                testedEndpoints: 0,
                riskScore: 0,
                collapsibleRow: apis.length > 0 ? transform.getUntrackedApisCollapsibleRow(apis) : null,
                collapsibleRowText: apis.map(x => x.url).join(", "),
            }
        })
    }

    // Coverage of these rows, which the backend serves apart from the rows so that a slow query never
    // holds a page back: the same rows with that column filled in (or marked unavailable).
    async function loadDetails(rows) {
        const idChunks = []
        for (let i = 0; i < rows.length; i += PAGE_FETCH_LIMIT) {
            idChunks.push(rows.slice(i, i + PAGE_FETCH_LIMIT).map(r => r.id))
        }
        const responses = await Promise.all(idChunks.map(ids =>
            collectionApi.fetchApiCollectionsPageDetails(ids).catch(() => ({ coverageUnavailable: true }))))

        const coverageMap = Object.assign({}, ...responses.map(r => r.coverageMap || {}))
        const unavailable = responses.some(r => r.coverageUnavailable)
        if (!unavailable) {
            setCoverageMap({ ...PersistStore.getState().coverageMap, ...coverageMap })
        }
        return rows.map(r => ({
            ...r,
            ...coverageFields(r, coverageMap, unavailable),
            coverageUnavailable: unavailable,
            detailsLoaded: true,
        }))
    }

    // What narrows the table besides its tab: the search text, column filters and tag filters
    const narrowingOf = (filtersObj, queryValue) => {
        const filters = {}
        ;['isOutOfTestingScope', 'accessType'].forEach(key => {
            if (filtersObj?.[key]?.length) filters[key] = filtersObj[key]
        })
        const tagFilters = {}
        Object.entries(tagFiltersApplied).forEach(([key, values]) => {
            if (values?.length) tagFilters[key] = values
        })
        return { queryValue: queryValue || '', filters, tagFilters }
    }

    // The tab badges count what the search and filters leave; they change only when those change, not on sort or paging
    function refreshTabCountsIfNarrowingChanged(narrowing) {
        const signature = JSON.stringify(narrowing)
        if (signature === narrowingRef.current) return
        narrowingRef.current = signature
        loadTabCounts(narrowing)
    }

    function loadTabCounts(narrowing, isMountedRef = { current: true }) {
        const signature = JSON.stringify(narrowing)
        collectionApi.fetchApiCollectionsTabCounts(narrowing).then(tabCounts => {
            // a later change may have replaced this one while it was in flight
            if (isMountedRef.current && narrowingRef.current === signature) {
                setPageMeta(prev => ({ ...prev, tabCounts: tabCounts || prev.tabCounts }))
            }
        }).catch(() => {})
    }

    // The table's fetchData: one page, sorted and filtered by the backend.
    const fetchPageRows = async (sortValue, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        const serverSortKey = SORT_VALUE_TO_SERVER_KEY[sortValue]
        // the table passes -1 for the options labelled "asc" ("More", "High risk", "Recent first": largest
        // value first, i.e. mongo descending). Names are the opposite: its "asc" is A-Z.
        const mongoOrder = serverSortKey === SERVER_SORT_KEYS.name ? -sortOrder : sortOrder
        const force = forceRefreshRef.current
        forceRefreshRef.current = false

        const narrowing = narrowingOf(filtersObj, queryValue)
        refreshTabCountsIfNarrowingChanged(narrowing)

        const resp = await collectionApi.fetchApiCollectionsPage({
            skip, limit, sortKey: serverSortKey, sortOrder: mongoOrder, tab: SERVER_TABS[selectedTab],
            ...narrowing, force,
        })

        if (resp?.statsPending && statsRetriesRef.current < STATS_PENDING_MAX_RETRIES) {
            // the very first load of an account's numbers is still being computed: look again shortly
            statsRetriesRef.current += 1
            setTimeout(() => { fetchPageMeta(); setRefreshData(v => !v) }, STATS_PENDING_RETRY_MS)
        }

        let rows
        if (selectedTab === 'untracked') {
            rows = toUntrackedRows(resp)
            return { value: transform.prettifyUntrackedCollectionsData(rows), total: resp?.total || 0 }
        }
        rows = toPlainRows(resp)
        rows.forEach(r => rowsSeenRef.current.set(r.id, r))
        // other pages (home) read this map; keep it current with what this page has shown
        setTrafficMap({ ...PersistStore.getState().trafficMap, ...(resp.lastSeenMap || {}) })

        // Coverage is given a short head start: when it lands in time the table renders once, complete.
        // Only when they are slow do the rows show with "..." first and get patched in (a second render) later.
        const detailsPromise = loadDetails(rows)
        const grace = new Promise(resolve => setTimeout(() => resolve(null), DETAILS_GRACE_MS))
        const loaded = await Promise.race([detailsPromise, grace])
        if (loaded) {
            loaded.forEach(r => rowsSeenRef.current.set(r.id, r))
            return {
                value: transform.prettifyCollectionsData(loaded, false, selectedTab, activeFilterType),
                total: resp?.total || 0,
            }
        }
        detailsPromise.then(late => {
            late.forEach(r => rowsSeenRef.current.set(r.id, r))
            setDetailsVersion(v => v + 1)
        })
        return {
            value: transform.prettifyCollectionsData(rows, false, selectedTab, activeFilterType, true),
            total: resp?.total || 0,
        }
    }

    // A page of a tab with no search or filters, largest first by the given sort key
    const fetchUnfilteredPage = (tab, sortKey, skip, limit) => collectionApi.fetchApiCollectionsPage({
        skip, limit, sortKey, sortOrder: -1, tab, queryValue: '', filters: {}, tagFilters: {},
    })

    async function fetchAllRowsOfTab(tab) {
        const rows = []
        let total = Infinity
        for (let skip = 0; skip < total; skip += PAGE_FETCH_LIMIT) {
            const resp = await fetchUnfilteredPage(tab, SERVER_SORT_KEYS.endpoints, skip, PAGE_FETCH_LIMIT)
            total = resp?.total || 0
            const page = tab === SERVER_TABS.untracked ? toUntrackedRows(resp) : await loadDetails(toPlainRows(resp))
            if (page.length === 0) break
            rows.push(...page)
        }
        return rows
    }

    async function fetchTreeData() {
        setTreeLoading(true)
        try {
            const requests = []
            for (let skip = 0; skip < TREE_VIEW_MAX_COLLECTIONS; skip += PAGE_FETCH_LIMIT) {
                requests.push(fetchUnfilteredPage(SERVER_TABS.all, SERVER_SORT_KEYS.endpoints, skip, PAGE_FETCH_LIMIT))
            }
            const rows = await loadDetails((await Promise.all(requests)).flatMap(resp => toPlainRows(resp)))
            rows.forEach(r => rowsSeenRef.current.set(r.id, r))
            setTreeData(rows)
        } finally {
            setTreeLoading(false)
        }
    }

    // ---- scoped views: every collection, filtered in the browser ---------------------------------

    async function fetchScopedData(isMountedRef = { current: true }) {
        try {
            setLoading(true)
            const targeted = isEndpointSecurityCategory() && deviceHostNamesFilter?.length > 0
            const withUsernames = targeted || isEndpointSecurityCategory()
            const withCoverageAndIssues = !targeted && !isEndpointSecurityCategory()
            const [collectionsResp, trafficRiskBundle, sensitiveMap, coverageInfo, severityInfo, endpointUsernameMap] = await Promise.all([
                targeted ? api.fetchCollectionsBasicForHostNames(deviceHostNamesFilter) : api.getAllCollectionsBasic(),
                fetchAndCacheAgenticTrafficRiskBundle({ api, PersistStore }),
                fetchAndCacheAgenticSensitiveInfo({ api, PersistStore }),
                withCoverageAndIssues ? api.getCoverageInfoForCollections().catch(() => ({})) : Promise.resolve({}),
                withCoverageAndIssues ? api.getSeverityInfoForCollections().catch(() => ({})) : Promise.resolve({}),
                withUsernames ? fetchEndpointShieldUsernameMap() : Promise.resolve({}),
            ])
            if (!isMountedRef.current) return

            const allFetched = collectionsResp?.apiCollections || []
            if (!targeted) func.applyCollectionMaps(allFetched)

            const maps = {
                trafficInfoMap: trafficRiskBundle?.trafficMap || {},
                coverageMap: coverageInfo || {},
                riskScoreMap: trafficRiskBundle?.riskScoreMap || {},
                severityInfoMap: severityInfo || {},
                sensitiveInfoMap: sensitiveMap || {},
                usernameMap: endpointUsernameMap || {},
            }
            const finalArr = customCollectionDataFilter ? allFetched.filter(customCollectionDataFilter) : allFetched
            const lightweightData = finalArr.map(c => transformRawCollectionData(c, maps))
            const { categorized, envTypeObj } = categorizeCollections(lightweightData)

            categorized.untracked = []
            setData(categorized)
            setNormalData(lightweightData)
            setEnvTypeMap(envTypeObj)
            setUsernameMap(endpointUsernameMap || {})
            setSummaryData(transform.getSummaryData(lightweightData))
            setHasUsageEndpoints(true)
            setLoading(false)

            if (categorized.hostname.length === 0 && (tableSelectedTab === undefined || tableSelectedTab.length === 0)) {
                setSelectedTab("custom")
                setSelected(3)
            }
        } catch (error) {
            if (isMountedRef.current) setLoading(false)
        }
    }

    // After a change (create, delete, tags, ...): load again whatever this view shows.
    const refreshAll = () => {
        if (scopedMode) {
            return fetchScopedData({ current: true })
        }
        rowsSeenRef.current = new Map()
        func.refreshCollectionMapsAsync()
        fetchPageMeta()
        setRefreshData(v => !v)
    }

    // The "Refresh" next to the stats age: ask the backend to recompute the numbers now.
    const forceStatsRefresh = () => {
        forceRefreshRef.current = true
        setPageMeta(prev => ({ ...prev, statsPending: false }))
        setRefreshData(v => !v)
        setTimeout(() => fetchPageMeta(), 1500)
    }

    // Admin only: the member list and which collections each can see, for the Share action.
    async function fetchShareData() {
        if (!(userRole === 'ADMIN' && func.checkForRbacFeature())) return
        const [usersCollectionResp, teamResp] = await Promise.allSettled([api.getAllUsersCollections(), settingRequests.getTeamData()])
        if (usersCollectionResp.status === 'fulfilled') setUsersCollection(usersCollectionResp.value)
        if (teamResp.status === 'fulfilled' && teamResp.value) {
            setTeamData(teamResp.value.filter(x => x?.role !== "ADMIN"))
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

    // Use custom hook for Agentic filter detection and summary calculation
    const { filteredSummaryData, activeFilterTitle, activeFilterType, filteredCollections, activeFilterPlainTitle, activeFilterDescription } = useAgenticFilter(normalData);

    // Persist tag filters whenever they change (same pattern as existing filters)
    useEffect(() => {
        const currentState = PersistStore.getState();
        const currentFiltersMap = currentState.filtersMap;
        const currentFilters = currentFiltersMap[pageKey] || {};
        currentState.setFiltersMap({
            ...currentFiltersMap,
            [pageKey]: {
                ...currentFilters,
                tagFilters: tagFiltersApplied
            }
        });
    }, [tagFiltersApplied, pageKey]);

    useEffect(() => {
        if (activeFilterType !== FILTER_TYPES.SKILL) return;
        collectionApi.fetchBlockedSkillCollections()
            .then(resp => setBlockedSkillCollectionIds(new Set(resp.blockedCollectionIds || [])))
            .catch(() => {});
    }, [activeFilterType]);

    useEffect(() => {
        if (!activeFilterType) return;
        // Only collections that actually have skills need this lookup; querying every visible
        // collection fires one request per collection for no benefit on the rest.
        const collectionIds = normalData
            .filter(c => Array.isArray(c.skills) && c.skills.length > 0)
            .map(c => c.id)
            .filter(Boolean);
        if (!collectionIds.length) return;
        const alreadyCached = !!PersistStore.getState().skillRiskScoreCache?.ts;
        fetchAndCacheSkillApiData(collectionIds, { api, PersistStore })
            .then(() => { if (!alreadyCached) setRefreshData(v => !v); })
            .catch(() => {});
    }, [activeFilterType, normalData.length]);

    useEffect(() => {
        const isMountedRef = { current: true };

        if (scopedMode) {
            fetchScopedData(isMountedRef);
        } else {
            // the table fetches its own pages; this is the header (tab counts, summary, tag choices)
            fetchPageMeta(isMountedRef);
            func.refreshCollectionMapsAsync();
        }
        fetchTotalApis(isMountedRef);
        fetchShareData();
        resetFunc();

        // Cleanup function to prevent state updates after unmount
        return () => {
            isMountedRef.current = false;
        };
    }, [])
    useEffect(() => {
        if (!scopedMode && centerView === CenterViewType.Tree) {
            fetchTreeData();
        }
    }, [centerView])

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
        refreshAll() // reload after mutations
    }

    const getActiveSkillName = () => {
        const filterValue = filtersMap[INVENTORY_FILTER_KEY]?.filters?.find(f => f.key === 'envType')?.value?.values?.[0] || '';
        const eq = filterValue.indexOf('=');
        return eq >= 0 ? filterValue.slice(eq + 1) : '';
    };

    async function handleSkillUpdateAction(collectionIds, isSkillBlocked) {
        const ids = collectionIds.map(id => parseInt(id));
        const skillName = getActiveSkillName();
        const toastContent = isSkillBlocked ? 'blocked' : 'unblocked';
        await collectionApi.updateSkillBlockStatus(ids, skillName, isSkillBlocked)
            .then(() => {
                func.setToast(true, false, `Skill ${toastContent} successfully`);
                setBlockedSkillCollectionIds(prev => {
                    const next = new Set(prev);
                    ids.forEach(id => isSkillBlocked ? next.add(id) : next.delete(id));
                    return next;
                });
            })
            .catch((e) => func.setToast(true, true, e.message || 'Something went wrong!'));
        resetResourcesSelected();
        refreshAll();
    }

    async function handleUntrackedDelete(apiCollectionIds) {
        await api.deleteUntrackedCollections(apiCollectionIds).then(() => {
            func.setToast(true, false, `${apiCollectionIds.length} untracked collection${func.addPlurality(apiCollectionIds.length)} deleted successfully`)
        }).catch((error) => {
            func.setToast(true, true, error.message || 'Something went wrong!')
        })
        resetResourcesSelected();
        refreshAll()
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

    const exportCsv = async (selectedResources = []) =>{
        const csvFileName = definedTableTabs[selected] + " Collections.csv"
        if (loading) return

        const wrapCsvValue = (value) => {
            const s = (value === null || value === undefined) ? '-' : String(value);
            return '"' + s.replace(/"/g, '""') + '"';
        }

        let headerTextToValueMap = Object.fromEntries(headers.map(x => [x.text, x.isText === CellType.TEXT ? x.value : x.textValue]).filter(x => x[0]?.length > 0));
        if(tableSelectedTab === "untracked"){
            headerTextToValueMap['URLs'] = "collapsibleRowText"
        }

        let rows
        if (selectedResources.length > 0) {
            rows = selectedResources.map(id => rowOf(id)).filter(Boolean)
        } else if (scopedMode) {
            rows = data[tableSelectedTab] || []
        } else {
            // the whole tab, a page at a time
            func.setToast(true, false, "Exporting CSV, please wait...")
            rows = await fetchAllRowsOfTab(SERVER_TABS[selectedTab])
        }

        let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
        rows.forEach(i => {
            csv += Object.values(headerTextToValueMap).map(h => wrapCsvValue(i[h])).join(",") + "\r\n"
        })

        let blob = new Blob([csv], {
            type: "application/csvcharset=UTF-8"
        });
        saveAs(blob, csvFileName) ;
        func.setToast(true, false,"CSV exported successfully")
    }



    const normalDataById = useMemo(() => {
        const map = new Map();
        normalData.forEach(c => map.set(c.id, c));
        return map;
    }, [normalData]);

    // id -> raw tags, for the rows this page has loaded
    const tagsOfKnownRows = () => {
        const tags = {}
        rowsSeenRef.current.forEach((row, id) => { tags[id] = row.envTypeOriginal || [] })
        return tags
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
        if (tableSelectedTab === 'untracked') {
            actions.push({
                content: `Delete collection${func.addPlurality(selectedResources.length)}`,
                onAction: () => {
                    const deleteConfirmationMessage = `Are you sure, you want to delete these untracked API collection${func.addPlurality(selectedResources.length)}? This will remove them from the untracked list.`
                    func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => handleUntrackedDelete(selectedResources))
                },
                requires: 'api/deleteUntrackedCollections'
            });
            return actions;
        }

        if (activeFilterType === FILTER_TYPES.SKILL) {
            const selectedSet = new Set(selectedResources.map(id => parseInt(id)));
            const selectedEndpointIds = new Set(
                filteredCollections.filter(c => selectedSet.has(c.id)).map(c => c.endpointId)
            );
            const resolvedCollectionIds = filteredCollections
                .filter(c => selectedEndpointIds.has(c.endpointId))
                .map(c => c.id);

            const allBlocked = resolvedCollectionIds.length > 0 && resolvedCollectionIds.every(id => blockedSkillCollectionIds.has(id));
            const allUnblocked = resolvedCollectionIds.every(id => !blockedSkillCollectionIds.has(id));

            if (allUnblocked) {
                actions.push({
                    content: `Block skill`,
                    onAction: () => func.showConfirmationModal(
                        "Blocking this skill will flag it as blocked. Are you sure?",
                        "Block Skill",
                        () => handleSkillUpdateAction(resolvedCollectionIds, true)
                    ),
                    requires: 'api/updateSkillBlockStatus'
                });
            } else if (allBlocked) {
                actions.push({
                    content: `Unblock skill`,
                    onAction: () => handleSkillUpdateAction(resolvedCollectionIds, false),
                    requires: 'api/updateSkillBlockStatus'
                });
            }
            return actions;
        }

        const isDefaultApiGroup = (id) => rowOf(id)?.type === "API_GROUP" && rowOf(id)?.automated;
        const isActivated = (id) => rowOf(id) && !rowOf(id).deactivated;
        const isDeactivated = (id) => rowOf(id)?.deactivated;
        if (selectedResources.every(isActivated)) {
            actions.push(
                {
                    content: `Deactivate collection${func.addPlurality(selectedResources.length)}`,
                    onAction: () => {
                        const message = "Deactivating a collection will stop traffic ingestion and testing for this collection. Please sync the usage data via Settings > billing after deactivating a collection to reflect your updated usage. Are you sure, you want to deactivate this collection ?"
                        func.showConfirmationModal(message, "Deactivate collection", () => handleCollectionsAction(selectedResources, collectionApi.deactivateCollections, "deactivated") )
                    },
                    requires: 'api/deactivateCollections'
                }
            )
        } else if (selectedResources.every(isDeactivated)) {
            actions.push(
                {
                    content: `Reactivate collection${func.addPlurality(selectedResources.length)}`,
                    onAction: () =>  {
                        const message = "Please sync the usage data via Settings > billing after reactivating a collection to resume data ingestion and testing."
                        func.showConfirmationModal(message, "Activate collection", () => handleCollectionsAction(selectedResources, collectionApi.activateCollections, "activated"))
                    },
                    requires: 'api/activateCollections'
                }
            )
        }
        actions.push(
            {
                content: `Delete collection${func.addPlurality(selectedResources.length)}`,
                onAction: () => {
                    const deleteConfirmationMessage = `Are you sure, you want to delete collection${func.addPlurality(selectedResources.length)}?`
                    func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => handleCollectionsAction(selectedResources.filter(v => !isDefaultApiGroup(v)), api.deleteMultipleCollections, "deleted"))
                },
                requires: 'api/deleteMultipleCollections'
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
                requires: 'api/updateUserCollections'
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
                        tags={scopedMode ? envTypeMap : tagsOfKnownRows()}
                        updateTags={updateTags}
                        apiCollectionIds={selectedResources}
                    />
                </Popover.Pane>
            </Popover>
        )

        const toggleEnvType = {
            content: toggleTypeContent,
            requires: 'api/updateEnvType'
        }

        const allOutOfTestScopeFalse = selectedResources.every(id => {
            const collection = normalDataById.get(id);
            return collection && !collection.isOutOfTestingScope;
        })

        const allOutOfTestScopeTrue = selectedResources.every(id => {
            const collection = normalDataById.get(id);
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
                    onAction: () => handleCollectionsAction(selectedResources, collectionApi.toggleCollectionsOutOfTestScope, toastContent, allOutOfTestScopeTrue),
                    requires: 'api/toggleCollectionsOutOfTestScope'
                }
            )
        }

        // Add Run Test button for multi-collection testing (hidden for Atlas / Endpoint Security)
        if (selectedResources.length > 1 && !isEndpointSecurityCategory()) {
            actions.push({
                content: <Button id="bulk-run-test-button" primary disabled={!canCall('api/startTest')}>Run test</Button>,
                onAction: () => {
                    setSelectedCollectionIdsForTest(selectedResources);
                    setShowMultiCollectionRunTest(true);
                },
                requires: 'api/startTest'
            })
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
                    // Replace any existing envType tag (staging, production, QA, DEV, INTEG, UAT, PREPROD, INTERNAL, etc.)
                    const currentEnvIndex = copyObj[id].findIndex(tag =>
                        tag.keyName?.toLowerCase() === 'envtype' || tag.keyName?.toLowerCase() === 'usersetenvtype'
                    )

                    if (currentEnvIndex === -1) {
                        copyObj[id].push(tagObj)
                    } else {
                        const currentValue = copyObj[id][currentEnvIndex].value?.toLowerCase()
                        if (tagObj.value?.toLowerCase() !== currentValue) {
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


        await api.updateEnvTypeOfCollection(tagObj === null ? tagObj : [tagObj], apiCollectionIds, tagObj === null).then(() => {
            func.setToast(true, false, "Tags updated successfully")
            if (scopedMode) {
                setEnvTypeMap(copyObj)
                updateData(copyObj)
            } else {
                refreshAll()
            }
        })

        resetResourcesSelected();
        setPopover(false)
    }

    const modalComponent = <CreateNewCollectionModal
        key="modal"
        active={active}
        setActive={setActive}
        createCollectionModalActivatorRef={createCollectionModalActivatorRef}
        fetchData={refreshAll}
    />

    let coverage = '0%';
    if(summaryData.totalAllowedForTesting !== 0){
        if(summaryData.totalAllowedForTesting < summaryData.totalTestedEndpoints){
            coverage = '100%'
        }else{
            coverage = Math.ceil((summaryData.totalTestedEndpoints * 100) / summaryData.totalAllowedForTesting) + '%'
        }
    }

    // Use filtered summary data when a filter is active (from Endpoints page navigation)
    const displayTotalAPIs = filteredSummaryData ? filteredSummaryData.totalEndpoints : totalAPIs;

    // Get the appropriate title for unique sources based on filter type
    const getUniqueSourcesTitle = () => {
        switch (activeFilterType) {
            case FILTER_TYPES.BROWSER_LLM:
                return "Unique LLM sources";
            case FILTER_TYPES.AI_AGENT:
                return "Unique Agentic resource";
            case FILTER_TYPES.MCP_SERVER:
                return "Unique MCP sources";
            default:
                return "Unique sources";
        }
    };

    // Get the appropriate count for unique sources based on filter type
    const getUniqueSourcesCount = () => {
        // For AI Agent: count unique <3> (serviceName/resources)
        // For LLM and MCP Server: count unique <2> (sourceId)
        if (activeFilterType === FILTER_TYPES.AI_AGENT) {
            return filteredSummaryData?.uniqueResources || 0;
        }
        return filteredSummaryData?.uniqueSources || 0;
    };

    const summaryItems = [
        ...(!activeFilterTitle
            ? [
                  {
                      title: mapLabel("Total APIs", getDashboardCategory()),
                      data: transform.formatNumberWithCommas(displayTotalAPIs),
                  },
              ]
            : []),
    
        ...(!isEndpointSecurityCategory() && !activeFilterTitle
            ? [
                  {
                      title: mapLabel("Critical APIs", getDashboardCategory()),
                      data: transform.formatNumberWithCommas(
                          summaryData.totalCriticalEndpoints || 0
                      ),
                  },
                  {
                      title: mapLabel("Tested APIs (Coverage)", getDashboardCategory()),
                      data: coverage,
                  },
              ]
            : []),
    
        // For agentic filter: show Unique Endpoints and Unique Sources (except for AI Agent/Skill/Plugin which use tree view)
        ...(activeFilterTitle && activeFilterType !== FILTER_TYPES.AI_AGENT && activeFilterType !== FILTER_TYPES.SKILL
            && activeFilterType !== FILTER_TYPES.PLUGIN
            ? [
                  {
                      title: "Unique Endpoints",
                      data: transform.formatNumberWithCommas(
                          filteredSummaryData?.uniqueEndpoints || 0
                      ),
                  },
                  {
                      title: getUniqueSourcesTitle(),
                      data: transform.formatNumberWithCommas(getUniqueSourcesCount()),
                  },
              ]
            : []),
    
        // Only show sensitive data when NOT filtering by agentic collections
        ...(!activeFilterTitle
            ? [
                  {
                      title: mapLabel("Sensitive in response APIs", getDashboardCategory()),
                      data: transform.formatNumberWithCommas(summaryData.totalSensitiveEndpoints || 0),
                  },
              ]
            : []),
    ];

    function switchToGraphView() {
        setCenterView(centerView === CenterViewType.Graph ? CenterViewType.Table : CenterViewType.Graph)
        fetchSvcToSvcGraphData()
    }

    const processAnalysisQuery = async (query, metadata) => {
        try {
            setAnalysisLoading(true);

            // Add user message to conversation history
            const userMessage = {
                _id: 'user_' + Date.now(),
                role: 'user',
                message: query,
                creationTimestamp: func.timeNow()
            };
            setAnalysisConversations(prev => [...prev, userMessage]);

            // Call API
            const res = await sendQuery(query, analysisConversationId, "ANALYZE_DASHBOARD_DATA", metadata);

            if(res?.conversationId) {
                setAnalysisConversationId(res.conversationId);
            }

            // Add AI response to conversation history
            if(res?.response) {
                const aiMessage = {
                    _id: "assistant_" + Date.now(),
                    role: 'assistant',
                    message: res.response,
                    creationTimestamp: func.timeNow()
                };
                setAnalysisConversations(prev => [...prev, aiMessage]);
            }

            setAnalysisLoading(false);

        } catch (err) {
            console.error('Error processing query:', err);
            setAnalysisLoading(false);
        }
    };

    const handleAnalyzeDashboard = async () => {
        // Only the top collections by endpoints and by risk are analyzed, so the paginated view asks the
        // backend for just those, with room for the ones filtered out below (groups, deactivated, demos).
        let candidates = data['all'] || []
        if (!scopedMode) {
            const topBy = (sortKey) => fetchUnfilteredPage(SERVER_TABS.all, sortKey, 0, 50).then(toPlainRows)
            candidates = await loadDetails((await Promise.all([topBy(SERVER_SORT_KEYS.endpoints), topBy(SERVER_SORT_KEYS.riskScore)])).flat())
        }

        // filter out deactivated and API_GROUP types
        const allColl = candidates.filter(c => !c.deactivated && c.type !== "API_GROUP" && c.urlsCount > 0 && c.displayName !== "juice_shop_demo" && c.displayName !== "vulnerable_apis" && c.displayName !== "Default");

        const preparedCollections = allColl.map(c => ({
            id: c.id,
            name: c.displayName,
            totalEndpoints: c.urlsCount || 0,
            riskScore: c.riskScore,
            sensitiveData: c.sensitiveSubTypesVal,
            issues: c.issuesArrVal,
        }));

        // Sort by endpoints and risk score, get top 50 each
        const topByEndpoints = [...preparedCollections]
            .sort((a, b) => b.totalEndpoints - a.totalEndpoints)
            .slice(0, 25);

        const topByRiskScore = [...preparedCollections]
            .sort((a, b) => b.riskScore - a.riskScore)
            .slice(0, 25);

        // Combine and deduplicate
        const seenIds = new Set();
        const combinedCollections = [];

        [...topByEndpoints, ...topByRiskScore].forEach(col => {
            if (!seenIds.has(col.id)) {
                seenIds.add(col.id);
                combinedCollections.push(col);
            }
        });

        // Prepare metadata for API call
        const metadata = {
            type: "dashboard_collections",
            data: combinedCollections
        };

        // Reset state and open modal
        setAnalysisConversations([]);
        setAnalysisConversationId(null);
        setShowAnalysisModal(true);

        // Process initial query
        const initialQuery = "Analyze the dashboard data provided above. Focus on risk distribution, endpoint coverage, sensitive data exposure, issues count data with severity, and provide actionable recommendations, review and provide insight purely from the data provided above.";
        processAnalysisQuery(initialQuery, metadata);
    }

    const secondaryActionsComp = activeFilterType ? null : (
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
                                !activeFilterType && {
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
                            ].filter(Boolean)
                        }
                    />
                </Popover.Pane>
            </Popover>
            <AllowedAction allowed={canCall('api/chatAndStore')}><Button onClick={handleAnalyzeDashboard}>Analyze Inventory</Button></AllowedAction>
            {!activeFilterType && <AllowedAction allowed={canCall('api/createCollection') || canCall('api/createCustomCollection')}><Button id={"create-new-collection-popup"} secondaryActions onClick={showCreateNewCollectionPopup}>Create new collection</Button></AllowedAction>}
        </HorizontalStack>
    )


    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex)
    }

    const filterTreeViewData = (data) => {
        return data.filter((x) => (!x?.deactivated && x?.type !== "API_GROUP" && x?.urlsCount > 1));
    }

    const getModifiedHeaders = () => {
        let modifiedHeaders = selected === 2 
            ? headers.map(h => h.titleWithTooltip ? {...h, title: h.titleWithTooltip} : h) 
            : [...headers];
        
        // Helper function to move source column after endpoint ID for LLM/MCP
        const moveSourceColumnAfterEndpointId = (headers) => {
            const displayNameIdx = headers.findIndex(h => h.value === 'displayNameComp');
            const endpointIdIdx = headers.findIndex(h => h.value === 'endpointId');
            
            if (displayNameIdx !== -1 && endpointIdIdx !== -1 && displayNameIdx < endpointIdIdx) {
                // Remove displayNameComp from its current position
                const [displayNameHeader] = headers.splice(displayNameIdx, 1);
                // Find new endpointId index (it shifted after removal)
                const newEndpointIdIdx = headers.findIndex(h => h.value === 'endpointId');
                // Insert after endpointId
                headers.splice(newEndpointIdIdx + 1, 0, displayNameHeader);
            }
            return headers;
        };
        
        // Apply filter-type based modifications
        if (activeFilterType === FILTER_TYPES.BROWSER_LLM) {
            // Hide "Total endpoints" column for browser-llm
            modifiedHeaders = modifiedHeaders.filter(h => h.value !== 'urlsCount');
            // Rename column to "LLM source" with proper filter
            modifiedHeaders = modifiedHeaders.map(h => {
                if (h.value === 'displayNameComp') {
                    return { ...h, title: 'LLM source', text: 'LLM source', filterLabel: 'LLM source', textValue: 'sourceId', filterKey: 'sourceId', showFilter: true };
                }
                return h;
            });
            // Move source column after Endpoint ID
            modifiedHeaders = moveSourceColumnAfterEndpointId(modifiedHeaders);
        } else if (activeFilterType === FILTER_TYPES.AI_AGENT || activeFilterType === FILTER_TYPES.SKILL
                || activeFilterType === FILTER_TYPES.PLUGIN) {
            modifiedHeaders = modifiedHeaders.filter(h => h.value !== 'urlsCount');
            modifiedHeaders = modifiedHeaders.map(h => {
                if (h.value === 'displayNameComp') {
                    return { ...h, title: 'Agentic resource name', text: 'Agentic resource name', textValue: 'serviceName', showFilter: false };
                }
                return h;
            });
        } else if (activeFilterType === FILTER_TYPES.MCP_SERVER) {
            // Rename columns with proper filters
            modifiedHeaders = modifiedHeaders.map(h => {
                if (h.value === 'urlsCount') {
                    return { ...h, title: 'Total tools', text: 'Total tools', filterLabel: 'Total tools' };
                }
                if (h.value === 'displayNameComp') {
                    return { ...h, title: 'MCP Server source', text: 'MCP Server source', filterLabel: 'MCP Server source', textValue: 'sourceId', filterKey: 'sourceId', showFilter: true };
                }
                return h;
            });
            // Move source column after Endpoint ID
            modifiedHeaders = moveSourceColumnAfterEndpointId(modifiedHeaders);
        }

        // For API Security and DAST, only show icons column on hostname tab
        if ((isApiSecurityCategory() || isDastCategory()) && selectedTab !== 'hostname') {
            modifiedHeaders = modifiedHeaders.filter(h => h.value !== 'iconComp');
        }

        return modifiedHeaders;
    };
    const dynamicHeaders = getModifiedHeaders();

    // Get modified sort options based on filter type
    const getModifiedSortOptions = () => {
        let modifiedSortOptions = [...sortOptions];
        
        if (activeFilterType === FILTER_TYPES.BROWSER_LLM) {
            // Remove endpoints sorting for LLM
            modifiedSortOptions = modifiedSortOptions.filter(opt => opt.sortKey !== 'urlsCount');
        } else if (activeFilterType === FILTER_TYPES.AI_AGENT || activeFilterType === FILTER_TYPES.SKILL
                || activeFilterType === FILTER_TYPES.PLUGIN) {
            modifiedSortOptions = modifiedSortOptions.filter(opt => opt.sortKey !== 'urlsCount');
        } else if (activeFilterType === FILTER_TYPES.MCP_SERVER) {
            // Change "Endpoints" to "Tools" for MCP Servers
            modifiedSortOptions = modifiedSortOptions.map(opt => {
                if (opt.sortKey === 'urlsCount') {
                    return { ...opt, label: 'Tools' };
                }
                return opt;
            });
        }
        
        const allSortOptions = selectedTab === 'groups' ? [...tempSortOptions, ...modifiedSortOptions] : modifiedSortOptions;
        
        // This ensures column indices match the actual table structure
        const updatedSortOptions = allSortOptions.map(opt => {
            // Find the actual column index in dynamicHeaders based on sortKey or matching criteria
            let actualColumnIndex = -1;
            
            // Map sortKey to the header value field
            const sortKeyToValueMap = {
                'urlsCount': 'urlsCount',
                'riskScore': 'riskScoreComp',
                'startTs': 'discovered',
                'detectedTimestamp': 'lastTraffic',
                'customGroupsSort': 'displayNameComp'
            };
            
            const headerValue = sortKeyToValueMap[opt.sortKey];
            if (headerValue) {
                actualColumnIndex = dynamicHeaders.findIndex(h => h.value === headerValue);
            }
            
            // If found, use the actual index + 1 (1-based indexing for Polaris)
            if (actualColumnIndex !== -1) {
                return { ...opt, columnIndex: actualColumnIndex + 1 };
            }
            
            // Otherwise keep the original columnIndex (fallback)
            return opt;
        });
        
        return updatedSortOptions;
    };
    const dynamicSortOptions = getModifiedSortOptions();

    const { sortedTagKeys, tagKeyValues } = useMemo(() => {
        const availableTagKeys = new Set();
        const values = {}; // Store unique values for each tag key

        if (!scopedMode) {
            // the backend lists the tag keys and values across every collection, not just the loaded page
            Object.entries(pageMeta.tagChoices || {}).forEach(([keyName, choices]) => {
                if (keyName === 'envType') return;
                availableTagKeys.add(keyName);
                values[keyName] = new Set(choices);
            });
            return { sortedTagKeys: Array.from(availableTagKeys).sort(), tagKeyValues: values };
        }

        Object.values(envTypeMap || {}).forEach((envTypeArray) => {
            if (Array.isArray(envTypeArray)) {
                envTypeArray.forEach(tagObj => {
                    if (tagObj?.keyName && tagObj.keyName !== 'envType') {
                        availableTagKeys.add(tagObj.keyName);

                        // Collect unique values for each tag key
                        if (!values[tagObj.keyName]) {
                            values[tagObj.keyName] = new Set();
                        }
                        if (tagObj.value) {
                            values[tagObj.keyName].add(tagObj.value);
                        }
                    }
                });
            }
        });

        return { sortedTagKeys: Array.from(availableTagKeys).sort(), tagKeyValues: values };
    }, [envTypeMap, pageMeta.tagChoices, scopedMode]);

    // Create tag filter definitions for the Filters component
    const tagFilterDefinitions = sortedTagKeys.map(tagKeyName => ({
        key: `tag_${tagKeyName}`,
        label: tagKeyName,
        filter: (
            <ChoiceList
                title={tagKeyName}
                titleHidden
                choices={Array.from(tagKeyValues[tagKeyName] || []).map(val => ({
                    label: val,
                    value: val
                }))}
                selected={tagFiltersApplied[tagKeyName] || []}
                onChange={(value) => {
                    setTagFiltersApplied({
                        ...tagFiltersApplied,
                        [tagKeyName]: value
                    });
                }}
                allowMultiple
            />
        ),
    }));

    // Build applied tag filters list for display as chips
    const appliedTagFilterChips = [];
    Object.entries(tagFiltersApplied).forEach(([tagKey, selectedValues]) => {
        if (selectedValues && selectedValues.length > 0) {
            appliedTagFilterChips.push({
                key: `tag_${tagKey}`,
                label: `${tagKey}: ${selectedValues.join(', ')}`,
                onRemove: () => {
                    setTagFiltersApplied({
                        ...tagFiltersApplied,
                        [tagKey]: []
                    });
                }
            });
        }
    });

    // Handle clear all tag filters
    const handleClearAllTagFilters = () => {
        setTagFiltersApplied({});
    };

    // Ensure all headers have unique IDs for IndexTable headings to avoid duplicate key warnings
    const headingsWithIds = dynamicHeaders.map((header, index) => ({
        ...header,
        id: header.id || header.value || header.text || `header-${index}`,
        // Replace empty titles with a space to avoid empty string keys
        title: (typeof header.title === 'string' && header.title.trim() === '') ? ' ' : header.title
    }));

    // Check if we should use tree view (for agentic filter types) - only for Atlas (Endpoint Security)
    const useTreeView = isEndpointSecurityCategory() && (
                        activeFilterType === FILTER_TYPES.AI_AGENT || 
                        activeFilterType === FILTER_TYPES.MCP_SERVER || 
                        activeFilterType === FILTER_TYPES.BROWSER_LLM ||
                        activeFilterType === FILTER_TYPES.SKILL ||
                        activeFilterType === FILTER_TYPES.PLUGIN);
    
    const filteredDataByTags = useMemo(() => {
        if (centerView !== CenterViewType.Table) return [];
        return (data[selectedTab] || []).filter(item => {
            // Check if item matches ALL applied tag filters
            return Object.entries(tagFiltersApplied).every(([tagKey, selectedValues]) => {
                // If no values selected for this filter, show all items
                if (!selectedValues || selectedValues.length === 0) {
                    return true;
                }

                // Check if item's tags contain any of the selected values for this tag key
                const itemTags = envTypeMap[item.id] || [];
                return itemTags.some(tag =>
                    tag.keyName === tagKey && selectedValues.includes(tag.value)
                );
            });
        });
    }, [centerView, data, selectedTab, tagFiltersApplied, envTypeMap]);

    // The table's rows after a page's coverage landed: those rows built again from their updated plain rows
    const withLoadedDetails = (tableRows) => tableRows.map(row => {
        const plain = rowsSeenRef.current.get(row.id)
        return plain?.detailsLoaded
            ? transform.prettifyCollectionsData([plain], false, selectedTab, activeFilterType)[0]
            : row
    })

    // Column filters the backend applies; the table asks it for matching rows
    const serverFilters = [
        {
            key: 'isOutOfTestingScope',
            label: 'Out of testing scope',
            choices: [{ label: 'Yes', value: 'true' }, { label: 'No', value: 'false' }],
        },
    ];

    // When the backend last recomputed the numbers the table is sorted by
    const statsAgeText = (() => {
        const refreshedAts = ['ENDPOINTS_COUNT', 'RISK_SCORE', 'LAST_SEEN'].map(m => pageMeta.statsUpdatedAt?.[m] || 0);
        const oldest = Math.min(...refreshedAts);
        if (!oldest) return pageMeta.statsPending ? 'Calculating the latest numbers...' : '';
        return 'Updated ' + func.prettifyEpoch(oldest);
    })();

    // For agentic filters, use the tree view component grouped by endpoint ID
    const getTableComponent = () => {
        // Tree view for AI Agent, MCP Server, and LLM (grouped by endpoint ID with expandable resources)
        if (useTreeView && filteredCollections.length > 0) {
            return (
                <AgentEndpointTreeTable
                    key={`tree-${String(refreshData)}`}
                    collections={filteredCollections}
                    promotedBulkActions={promotedBulkActions}
                    filterType={activeFilterType}
                    showCategoryColumn={activeFilterPlainTitle}
                />
            );
        }
        
        // Standard tree view
        if (centerView === CenterViewType.Tree) {
            if (!scopedMode && treeLoading) {
                return <SpinnerCentered key="tree-loading" />;
            }
            return (
                <TreeViewTable
                    collectionsArr={filterTreeViewData(scopedMode ? normalData : treeData)}
                    sortOptions={dynamicSortOptions}
                    resourceName={resourceName}
                    tableHeaders={headingsWithIds.filter((x) => x.shouldMerge !== undefined)}
                    promotedBulkActions={promotedBulkActions}
                />
            );
        }
        
        // Graph view
        if (centerView === CenterViewType.Graph) {
            return (
                <div style={{height: "800px"}}>
                    <ReactFlow
                        nodes={allNodes}
                        edges={allEdges}
                        onNodesChange={onAllNodesChange}
                        onEdgesChange={onAllEdgesChange}
                    >
                        <Background color="#aaa" gap={16} />
                    </ReactFlow>
                </div>
            );
        }
        
            // Custom filter UI using Polaris Filters component
        const customFilterUI = sortedTagKeys.length > 0 && (
            <div>
                <div style={{ marginBottom: '8px', marginLeft: '16px' }}>
                    <Text variant="bodySm" as="span" style={{ fontWeight: '500', color: '#626262' }}>
                        Tags filter
                    </Text>
                </div>
                <Filters
                    filters={tagFilterDefinitions}
                    appliedFilters={appliedTagFilterChips}
                    onClearAll={handleClearAllTagFilters}
                    hideQueryField={true}
                />
            </div>
        );

        // Default view: the backend sorts, filters and pages; a new key re-fetches from the first page
        if (!scopedMode) {
            return (
                <GithubServerTable
                    key={`${selectedTab}-${String(refreshData)}-${JSON.stringify(tagFiltersApplied)}`}
                    filterStateUrl={"/dashboard/observe/inventory/"}
                    pageLimit={50}
                    pageSizeOptions={[20, 50, PAGE_FETCH_LIMIT]}
                    fetchData={fetchPageRows}
                    sortOptions={dynamicSortOptions}
                    resourceName={resourceName}
                    filters={serverFilters}
                    disambiguateLabel={disambiguateLabel}
                    headers={headingsWithIds}
                    headings={headingsWithIds}
                    selectable={true}
                    promotedBulkActions={promotedBulkActions}
                    mode={IndexFiltersMode.Default}
                    useNewRow={true}
                    condensedHeight={true}
                    tableTabs={tableTabs}
                    onSelect={handleSelectedTab}
                    selected={selected}
                    csvFileName={"Inventory"}
                    onExportCsv={() => exportCsv()}
                    customFilterContent={customFilterUI}
                    clientSideDataUpdateKey={detailsVersion}
                    clientSideDataTransformer={withLoadedDetails}
                />
            );
        }

        return (
            <GithubSimpleTable
                key={refreshData}
                filterStateUrl={"/dashboard/observe/inventory/"}
                pageLimit={100}
                data={filteredDataByTags}
                sortOptions={dynamicSortOptions}
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
                prettifyPageData={(pageData) => selectedTab === 'untracked' ? transform.prettifyUntrackedCollectionsData(pageData) : transform.prettifyCollectionsData(pageData, false, selectedTab, activeFilterType)}
                transformRawData={transformRawCollectionData}
                onExportCsv={() => exportCsv()}
                customFilterContent={customFilterUI}
            />
        );
    };
    
    const tableComponent = getTableComponent();

    // Hide summary card for tree view types (AI Agent, MCP Server, LLM)
    const showSummaryCard = !useTreeView;
    const statsFreshness = (!scopedMode && statsAgeText) ? (
        <HorizontalStack key="stats-freshness" gap="2" align="end" blockAlign="center">
            <Text variant="bodySm" color="subdued">{statsAgeText}</Text>
            <Button plain onClick={forceStatsRefresh}>Refresh</Button>
        </HorizontalStack>
    ) : null
    const summaryReady = scopedMode || pageMeta.loaded
    const components = loading ? [<SpinnerCentered key={"loading"}/>]: [((showSummaryCard && summaryReady) ? <SummaryCardInfo summaryItems={summaryItems} key="summary"/> : null), statsFreshness, (!hasUsageEndpoints ? <CollectionsPageBanner key="page-banner" /> : null) ,modalComponent, tableComponent]

    if(onlyShowCollectionsTable){
        sendData(data)
        return (
            <Box paddingBlockStart={4} paddingInline={4}>
                {tableComponent}
            </Box>
        )
    }

    // Dynamic title based on active filter and filter type
    const getFilteredPageTitle = () => {
        if (!activeFilterTitle) return mapLabel("API Collections", getDashboardCategory());
        if (activeFilterPlainTitle) return activeFilterTitle;

        switch (activeFilterType) {
            case FILTER_TYPES.BROWSER_LLM:
                return `LLM - ${activeFilterTitle}`;
            case FILTER_TYPES.AI_AGENT:
                return `AI Agent - ${activeFilterTitle}`;
            case FILTER_TYPES.MCP_SERVER:
                return `MCP Server - ${activeFilterTitle}`;
            case FILTER_TYPES.SKILL:
                return `Skill - ${activeFilterTitle}`;
            case FILTER_TYPES.PLUGIN:
                return `Plugin - ${activeFilterTitle}`;
            default:
                return `${activeFilterTitle}`;
        }
    };
    const pageTitle = getFilteredPageTitle();

    return(
        <>
            <PageWithMultipleCards
                title={
                    <VerticalStack gap="1">
                        <TitleWithInfo
                            tooltipContent={activeFilterTitle
                                ? `Viewing collections filtered by ${activeFilterTitle}`
                                : "Akto automatically groups similar APIs into meaningful collections based on their subdomain names. "}
                            titleText={pageTitle}
                            docsUrl={"https://docs.akto.io/api-inventory/concepts"}
                        />
                        {activeFilterDescription && <Text variant="bodySm">{activeFilterDescription}</Text>}
                    </VerticalStack>
                }
                primaryAction={<Button id={"explore-mode-query-page"} primary secondaryActions onClick={navigateToQueryPage}>Explore mode</Button>}
                isFirstPage={!agenticObserveBackUrl}
                backUrl={agenticObserveBackUrl}
                components={components}
                secondaryActions={secondaryActionsComp}
            />
            {showAnalysisModal && (
                <Modal
                    open={showAnalysisModal}
                    onClose={() => setShowAnalysisModal(false)}
                    title="Dashboard Analysis"
                    large
                >
                    <Modal.Section>
                        <Box style={{ minHeight: '400px', maxHeight: '60vh', overflowY: 'auto' }}>
                            {analysisLoading && analysisConversations.length === 0 ? (
                                <AgenticThinkingBox />
                            ) : (
                                <VerticalStack gap="4">
                                    <ConversationHistory conversations={analysisConversations} isInventory={true}/>
                                    {analysisLoading && <AgenticThinkingBox />}
                                </VerticalStack>
                            )}
                        </Box>
                    </Modal.Section>
                </Modal>
            )}
            {showMultiCollectionRunTest && (
                <RunTest
                    apiCollectionIds={selectedCollectionIdsForTest}
                    endpoints={[]}
                    filtered={false}
                    runTestFromOutside={true}
                    closeRunTest={() => {
                        setShowMultiCollectionRunTest(false);
                        resetResourcesSelected();
                    }}
                    disabled={false}
                />
            )}
        </>
    )
}

export default ApiCollections 
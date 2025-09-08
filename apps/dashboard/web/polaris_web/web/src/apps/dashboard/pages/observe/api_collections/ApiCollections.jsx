import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, IndexFiltersMode, Box, Popover, ActionList, ResourceItem, Avatar,  HorizontalStack, Icon} from "@shopify/polaris"
import { HideMinor, ViewMinor,FileMinor } from '@shopify/polaris-icons';
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
import TooltipText from "@/apps/dashboard/components/shared/TooltipText"
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
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";
  
const CenterViewType = {
    Table: 0,
    Tree: 1,
    Graph: 2
  }


const headers = [
    {
        title: mapLabel("API collection name", getDashboardCategory()),
        text: mapLabel("API collection name", getDashboardCategory()),
        value: "displayNameComp",
        filterKey: "displayName",
        textValue: 'displayName',
        showFilter: true
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
        boxWidth: '80px'
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
        title: 'Test coverage',
        text: 'Test coverage', 
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
        text: 'Collection type',
        title: 'Collection type',
        value: 'envTypeComp',
        filterKey: "envType",
        showFilter: true,
        textValue: 'envType',
        tooltipContent: (<Text variant="bodySm">Environment type for an API collection, Staging or Production </Text>),
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
        title: "Out of Testing scope",
        text: 'Out of Testing scope',
        value: 'outOfTestingScopeComp',
        isText: CellType.TEXT,
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
        console.error("collectionsArr is not an array:", collectionsArr);
        return { prettify: [], normal: [] };
    }

    const newData = collectionsArr.map((c) => {
        if(c.deactivated){
            c.rowStatus = 'critical'
            c.disableClick = true
        }
        return{
            ...c,
            detected: func.prettifyEpoch(c.startTs),
            icon: CircleTickMajor,
            nextUrl: "/dashboard/observe/inventory/"+ c.id,
            envType: c?.envType?.map(func.formatCollectionType),
            displayNameComp: (<Box maxWidth="30vw"><Text fontWeight="medium">{c.displayName}</Text></Box>),
            testedEndpoints: c.urlsCount === 0 ? 0 : (coverageMap[c.id] ? coverageMap[c.id] : 0),
            sensitiveInRespTypes: sensitiveInfoMap[c.id] ? sensitiveInfoMap[c.id] : [],
            severityInfo: severityInfoMap[c.id] ? severityInfoMap[c.id] : {},
            detected: func.prettifyEpoch(trafficInfoMap[c.id] || 0),
            detectedTimestamp: c.urlsCount === 0 ? 0 : (trafficInfoMap[c.id] || 0),
            riskScore: c.urlsCount === 0 ? 0 : (riskScoreMap[c.id] ? riskScoreMap[c.id] : 0),
            discovered: func.prettifyEpoch(c.startTs || 0),
            descriptionComp: (<Box maxWidth="350px"><Text>{c.description}</Text></Box>),
            outOfTestingScopeComp: c.isOutOfTestingScope ? (<Text>Yes</Text>) : (<Text>No</Text>),
            // outOfTestingScope: c.isOutOfTestingScope || false
        }
    })

    const prettifyData = transform.prettifyCollectionsData(newData, isLoading)
    return { prettify: prettifyData, normal: newData }
}

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
        envTypeObj[c.id] = c.envType;
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
    const [data, setData] = useState({'all': [], 'hostname':[], 'groups': [], 'custom': [], 'deactivated': [], 'Untracked': []})
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
    const setTagCollectionsMap = PersistStore(state => state.setTagCollectionsMap)
    const setHostNameMap = PersistStore(state => state.setHostNameMap)
    const setCoverageMap = PersistStore(state => state.setCoverageMap)

    // const lastFetchedResp = dummyData.lastFetchedResp
    // const lastFetchedSeverityResp = dummyData.lastFetchedSeverityResp
    // const lastFetchedSensitiveResp = dummyData.lastFetchedSensitiveResp
    const lastFetchedInfo = PersistStore.getState().lastFetchedInfo
    const lastFetchedResp = PersistStore.getState().lastFetchedResp
    const lastFetchedSeverityResp = PersistStore.getState().lastFetchedSeverityResp
    const lastFetchedSensitiveResp = PersistStore.getState().lastFetchedSensitiveResp
    const setLastFetchedInfo = PersistStore.getState().setLastFetchedInfo
    const setLastFetchedResp = PersistStore.getState().setLastFetchedResp
    const setLastFetchedSeverityResp = PersistStore.getState().setLastFetchedSeverityResp
    const setLastFetchedSensitiveResp = PersistStore.getState().setLastFetchedSensitiveResp
    const [totalAPIs, setTotalAPIs] = useState(0)
    const [allEdges, setAllEdges, onAllEdgesChange] = useEdgesState([])
    const [allNodes, setAllNodes, onAllNodesChange] = useNodesState([])

    // as riskScore cron runs every 5 min, we will cache the data and refresh in 5 mins
    // similarly call sensitive and severityInfo

    async function fetchData() {
        try {
            setLoading(true)
            
            // Build all API promises to run in parallel
            const shouldCallHeavyApis = (func.timeNow() - lastFetchedInfo.lastRiskScoreInfo) >= (5 * 60)
            
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

            // Execute all APIs in parallel
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
        setLoading(false);
        let finalArr = apiCollectionsResp.apiCollections || [];
        if(customCollectionDataFilter){ 
            finalArr = finalArr.filter(customCollectionDataFilter)
        }
            
        const dataObj = convertToNewData(finalArr, sensitiveInfoMap, severityInfoMap, coverageMap, trafficInfoMap, riskScoreMap, false);
        setNormalData(dataObj.normal)

        // Ensure dataObj.prettify exists
        if (!dataObj.prettify) {
            console.error("dataObj.prettify is undefined");
            return;
        }

        const { envTypeObj, collectionMap, activeCollections, categorized } = categorizeCollections(dataObj.prettify);
        let res = categorized;

        // Separate active and deactivated collections
        const deactivatedCollectionsCopy = res.deactivated.map((c)=>{
            if(deactivatedCountInfo.hasOwnProperty(c.id)){
                c.urlsCount = deactivatedCountInfo[c.id]
            }
            return c
        });

        setDeactivateCollections(deactivatedCollectionsCopy)
        // Process untracked API data
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

        const untrackedCollections = Object.entries(uningestedApiCountInfo || {})
            .filter(([_, count]) => count > 0)
            .map(([collectionId, untrackedCount]) => {
                const collection = collectionMap.get(parseInt(collectionId));
                return collection ? {
                    id: collection.id,
                    displayName: collection.displayName,
                    displayNameComp: collection.displayNameComp,
                    urlsCount: untrackedCount,
                    rowStatus: 'critical',
                    disableClick: true,
                    deactivated: false,
                    collapsibleRow: untrackedApiDataMap[collection.id] ?
                        transform.getUntrackedApisCollapsibleRow(untrackedApiDataMap[collection.id]) : null
                } : null;
            })
            .filter(Boolean);
        
        // Make the heavy API call asynchronous to prevent blocking rendering
        

        setHasUsageEndpoints(hasUserEndpoints)
        res['Untracked'] = untrackedCollections
        
        setData(res);
        setEnvTypeMap(envTypeObj);
        setAllCollections(apiCollectionsResp.apiCollections || []);
        
        // Fetch endpoints count and sensitive info asynchronously
        Promise.all([
            dashboardApi.fetchEndpointsCount(0, 0),
            shouldCallHeavyApis ? api.getSensitiveInfoForCollections() : Promise.resolve(null)
        ]).then(([endpointsResponse, sensitiveResponse]) => {
            // Update endpoints count
            if (endpointsResponse) {
                setTotalAPIs(endpointsResponse.newCount);
            }
            
            // Update sensitive info if available
            if (sensitiveResponse) {
                const newSensitiveInfo = {
                    sensitiveUrls: sensitiveResponse.sensitiveUrlsInResponse,
                    sensitiveInfoMap: sensitiveResponse.sensitiveSubtypesInCollection
                };
                
                // Update the store with new sensitive info
                setLastFetchedSensitiveResp(newSensitiveInfo);
                
                // Re-calculate data with new sensitive info
                const updatedDataObj = convertToNewData(
                    finalArr,
                    newSensitiveInfo.sensitiveInfoMap || {},
                    severityInfoMap,
                    coverageMap,
                    trafficInfoMap,
                    riskScoreMap,
                    false
                );
                
                setNormalData(updatedDataObj.normal);
                
                // Re-categorize and update the prettified data
                if (updatedDataObj.prettify) {
                    const { categorized: updatedCategorized } = categorizeCollections(updatedDataObj.prettify);
                    
                    // Update deactivated collections with counts
                    const updatedDeactivatedCollections = updatedCategorized.deactivated.map((c) => {
                        if(deactivatedCountInfo.hasOwnProperty(c.id)){
                            c.urlsCount = deactivatedCountInfo[c.id]
                        }
                        return c
                    });
                    
                    updatedCategorized.deactivated = updatedDeactivatedCollections;
                    updatedCategorized['Untracked'] = untrackedCollections;
                    
                    setData(updatedCategorized);
                    
                    // Update summary with new sensitive endpoints count
                    const updatedSummary = transform.getSummaryData(updatedDataObj.normal);
                    updatedSummary.totalCriticalEndpoints = riskScoreObj.criticalUrls;
                    updatedSummary.totalSensitiveEndpoints = newSensitiveInfo.sensitiveUrls;
                    setSummaryData(updatedSummary);
                }
            }
        }).catch(error => {
            console.error("Error fetching endpoints count or sensitive info:", error);
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
        } catch (error) {
            console.error("Error in fetchData:", error);
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
        fetchData()
        resetFunc()    
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
        fetchData()
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
            let headerTextToValueMap = Object.fromEntries(headers.map(x => [x.text, x.isText === CellType.TEXT ? x.value : x.textValue]).filter(x => x[0]?.length > 0));
            let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
            data['all'].forEach(i => {
                if(selectedResources.length === 0 || selectedResourcesSet.has(i.id)){
                    csv += Object.values(headerTextToValueMap).map(h => (i[h] || "-")).join(",") + "\r\n"
                }
            })
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
                activator={<div onClick={() => setPopover(!popover)}>Set ENV type</div>}
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
            func.setToast(true, false, "ENV type updated successfully")
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
            data: transform.formatNumberWithCommas(summaryData.totalCriticalEndpoints),
        },
        {
            title: mapLabel("Tested APIs (Coverage)", getDashboardCategory()),
            data: coverage
        },
        {
            title: mapLabel("Sensitive in response APIs", getDashboardCategory()),
            data: transform.formatNumberWithCommas(summaryData.totalSensitiveEndpoints),
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

    const tableComponent = (
        centerView === CenterViewType.Tree ?
        <TreeViewTable
            collectionsArr={normalData.filter((x) => (!x?.deactivated && x?.type !== "API_GROUP"))}
            sortOptions={sortOptions}
            resourceName={resourceName}
            tableHeaders={headers.filter((x) => x.shouldMerge !== undefined)}
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
            headers={headers}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            mode={IndexFiltersMode.Default}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            tableTabs={tableTabs}
            onSelect={handleSelectedTab}
            selected={selected}
            csvFileName={"Inventory"}
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
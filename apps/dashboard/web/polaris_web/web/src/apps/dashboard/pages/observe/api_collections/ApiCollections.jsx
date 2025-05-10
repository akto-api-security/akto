import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, IndexFiltersMode, Box, Badge, Popover, ActionList, ResourceItem, Avatar,  HorizontalStack, Icon, TextField, Tooltip} from "@shopify/polaris"
import { HideMinor, ViewMinor,FileMinor, FileFilledMinor } from '@shopify/polaris-icons';
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
import { on } from "stream";
  
const CenterViewType = {
    Table: 0,
    Tree: 1,
    Graph: 2
  }


const headers = [
    {
        title: "API collection name",
        text: "API collection name",
        value: "displayNameComp",
        filterKey: "displayName",
        textValue: 'displayName',
        showFilter: true
    },
    {
        title: "Total endpoints",
        text: "Total endpoints",
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

function convertToCollectionData(c) {
    return {
        ...c,
        detected: func.prettifyEpoch(c.startTs),
        icon: CircleTickMajor,
        nextUrl: "/dashboard/observe/inventory/"+ c.id
    }    
}

const convertToNewData = (collectionsArr, sensitiveInfoMap, severityInfoMap, coverageMap, trafficInfoMap, riskScoreMap, isLoading) => {

    const newData = collectionsArr.map((c) => {
        if(c.deactivated){
            c.rowStatus = 'critical'
            c.disableClick = true
        }
        return{
            ...c,
            displayNameComp: (<Box maxWidth="20vw"><TooltipText tooltip={c.displayName} text={c.displayName} textProps={{fontWeight: 'medium'}}/></Box>),
            testedEndpoints: c.urlsCount === 0 ? 0 : (coverageMap[c.id] ? coverageMap[c.id] : 0),
            sensitiveInRespTypes: sensitiveInfoMap[c.id] ? sensitiveInfoMap[c.id] : [],
            severityInfo: severityInfoMap[c.id] ? severityInfoMap[c.id] : {},
            detected: func.prettifyEpoch(trafficInfoMap[c.id] || 0),
            detectedTimestamp: c.urlsCount === 0 ? 0 : (trafficInfoMap[c.id] || 0),
            riskScore: c.urlsCount === 0 ? 0 : (riskScoreMap[c.id] ? riskScoreMap[c.id] : 0),
            discovered: func.prettifyEpoch(c.startTs || 0),
            descriptionComp: (<Box maxWidth="300px"><TooltipText tooltip={c.description} text={c.description}/></Box>),
        }
    })

    const prettifyData = transform.prettifyCollectionsData(newData, isLoading)
    return { prettify: prettifyData, normal: newData }
}

function ApiCollections() {
    const userRole = window.USER_ROLE

    const navigate = useNavigate();
    const [data, setData] = useState({'all': [], 'hostname':[], 'groups': [], 'custom': [], 'deactivated': []})
    const [active, setActive] = useState(false);
    const [loading, setLoading] = useState(false)
          
    const [summaryData, setSummaryData] = useState({totalEndpoints:0 , totalTestedEndpoints: 0, totalSensitiveEndpoints: 0, totalCriticalEndpoints: 0})
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
    const [textFieldActive, setTextFieldActive] = useState(false);
    const [customEnv,setCustomEnv] = useState('')

    // const dummyData = dummyJson;

    const definedTableTabs = ['All', 'Hostname', 'Groups', 'Custom', 'Deactivated']

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

        // first api call to get only collections name and collection id
        setLoading(true)
        const apiCollectionsResp = await api.getAllCollectionsBasic();
        setLoading(false)
        let hasUserEndpoints = await api.getUserEndpoints()
        setHasUsageEndpoints(hasUserEndpoints)
        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        let dataObj = {}
        dataObj = convertToNewData(tmp, {}, {}, {}, {}, {}, true);
        let res = {}
        res.all = dataObj.prettify
        res.hostname = dataObj.prettify.filter((c) => c.hostName !== null && c.hostName !== undefined && !c.deactivated)
        const allGroups = dataObj.prettify.filter((c) => c.type === "API_GROUP" && !c.deactivated);
        res.groups = allGroups;
        res.custom = res.all.filter(x => !res.hostname.includes(x) && !x.deactivated && !res.groups.includes(x));
        setData(res);
        if (res.hostname.length === 0 && (tableSelectedTab === undefined || tableSelectedTab.length === 0)) {
            setTimeout(() => {
                setSelectedTab("custom");
                setSelected(3);
            },[100])
        }

        let envTypeObj = {}
        tmp.forEach((c) => {
            envTypeObj[c.id] = c.envType
        })
        setEnvTypeMap(envTypeObj)
        setAllCollections(apiCollectionsResp.apiCollections.filter(x => x?.deactivated !== true) || [])

        const shouldCallHeavyApis = (func.timeNow() - lastFetchedInfo.lastRiskScoreInfo) >= (5 * 60)
        // const shouldCallHeavyApis = false;

        // fire all the other apis in parallel

        let apiPromises = [
            api.getCoverageInfoForCollections(),
            api.getLastTrafficSeen(),
            collectionApi.fetchCountForHostnameDeactivatedCollections(),
            dashboardApi.fetchEndpointsCount(0, 0)
        ];
        if(shouldCallHeavyApis){
            apiPromises = [
                ...apiPromises,
                ...[api.getRiskScoreInfo(), api.getSensitiveInfoForCollections(), api.getSeverityInfoForCollections()]
            ]
        }

        if(userRole === 'ADMIN') {
            apiPromises = [
                ...apiPromises,
                ...[api.getAllUsersCollections(), settingRequests.getTeamData()]
            ]
        }

        let results = await Promise.allSettled(apiPromises);
        let coverageInfo = results[0].status === 'fulfilled' ? results[0].value : {};
        // let coverageInfo = dummyData.coverageMap
        let trafficInfo = results[1].status === 'fulfilled' ? results[1].value : {};
        let deactivatedCountInfo = results[2].status === 'fulfilled' ? results[2].value : {};
        let fetchEndpointsCountResp = results[3].status === 'fulfilled' ? results[3].value : {}

        let riskScoreObj = lastFetchedResp
        let sensitiveInfo = lastFetchedSensitiveResp
        let severityObj = lastFetchedSeverityResp
        if (fetchEndpointsCountResp && fetchEndpointsCountResp.newCount) {
            setTotalAPIs(fetchEndpointsCountResp.newCount)
        }

        if(shouldCallHeavyApis){
            if(results[4]?.status === "fulfilled"){
                const res = results[4].value
                riskScoreObj = {
                    criticalUrls: res.criticalEndpointsCount,
                    riskScoreMap: res.riskScoreOfCollectionsMap
                }
            }

            if(results[5]?.status === "fulfilled"){
                const res = results[5].value
                sensitiveInfo ={ 
                    sensitiveUrls: res.sensitiveUrlsInResponse,
                    sensitiveInfoMap: res.sensitiveSubtypesInCollection
                }
            }

            if(results[6]?.status === "fulfilled"){
                const res = results[6].value
                severityObj = res
            }

            // update the store which has the cached response
            setLastFetchedInfo({lastRiskScoreInfo: func.timeNow(), lastSensitiveInfo: func.timeNow()})
            setLastFetchedResp(riskScoreObj)
            setLastFetchedSeverityResp(severityObj)
            setLastFetchedSensitiveResp(sensitiveInfo)

        }

        let usersCollectionList = []
        let userList = []

        const index = !shouldCallHeavyApis ? 4 : 7

        if(userRole === 'ADMIN') {
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
        }

        setUsersCollection(usersCollectionList)
        setTeamData(userList)

        setHasUsageEndpoints(hasUserEndpoints)
        setCoverageMap(coverageInfo)

        dataObj = convertToNewData(tmp, sensitiveInfo.sensitiveInfoMap, severityObj, coverageInfo, trafficInfo, riskScoreObj?.riskScoreMap, false);
        setNormalData(dataObj.normal)

        // Separate active and deactivated collections
        const deactivatedCollectionsCopy = dataObj.prettify.filter(c => c.deactivated).map((c)=>{
            if(deactivatedCountInfo.hasOwnProperty(c.id)){
                c.urlsCount = deactivatedCountInfo[c.id]
            }
            return c
        });
        setDeactivateCollections(JSON.parse(JSON.stringify(deactivatedCollectionsCopy)));
        
        // Calculate summary data only for active collections
        const summary = transform.getSummaryData(dataObj.normal)
        summary.totalCriticalEndpoints = riskScoreObj.criticalUrls;
        summary.totalSensitiveEndpoints = sensitiveInfo.sensitiveUrls
        setSummaryData(summary)

        setCollectionsMap(func.mapCollectionIdToName(tmp.filter(x => !x?.deactivated)))
        const allHostNameMap = func.mapCollectionIdToHostName(tmp.filter(x => !x?.deactivated))
        setHostNameMap(allHostNameMap)
        
        tmp = {}
        tmp.all = dataObj.prettify
        tmp.hostname = dataObj.prettify.filter((c) => c.hostName !== null && c.hostName !== undefined && !c.deactivated)
        const allGroupsForTmp = dataObj.prettify.filter((c) => c.type === "API_GROUP" && !c.deactivated);
        tmp.groups = allGroupsForTmp;
        tmp.custom = tmp.all.filter(x => !tmp.hostname.includes(x) && !x.deactivated && !tmp.groups.includes(x));
        tmp.deactivated = deactivatedCollectionsCopy
        setData(tmp);
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
    async function handleCollectionsAction(collectionIdList, apiFunction, toastContent){
        const collectionIdListObj = collectionIdList.map(collectionId => ({ id: collectionId.toString() }))
        await apiFunction(collectionIdListObj).then(() => {
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
                content: `Remove collection${func.addPlurality(selectedResources.length)}`,
                onAction: () => handleCollectionsAction(selectedResources.filter(v => !defaultApiGroups.includes(v)), api.deleteMultipleCollections, "deleted")
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
                onClose={() => setPopover(false)}
                active={popover}
                autofocusTarget="first-node"
            >
                <Popover.Pane>
                    {textFieldActive ? 
                    <Box padding={"1"}>
                        <TextField onChange={setCustomEnv} value={customEnv} connectedRight={(
                            <Tooltip content="Save your Custom env type" dismissOnMouseOut>
                                <Button onClick={() => {
                                    resetResourcesSelected();
                                    updateEnvType(selectedResources, customEnv);
                                    setTextFieldActive(false);
                                }} plain icon={FileFilledMinor}/>
                            </Tooltip>
                        )}/>
                    </Box>
                        :<ActionList
                        actionRole="menuitem"
                        items={[
                            {content: 'Staging', onAction: () => updateEnvType(selectedResources, "STAGING")},
                            {content: 'Production', onAction: () => updateEnvType(selectedResources, "PRODUCTION")},
                            {content: 'Reset', onAction: () => updateEnvType(selectedResources, null)},
                            {content: 'Add Custom', onAction: () => setTextFieldActive(!textFieldActive)}
                        ]}
                    
                    />}
                </Popover.Pane>
            </Popover>
        )

        const toggleEnvType = {
            content: toggleTypeContent
        }

        const bulkActionsOptions = [...actions];
        if(selectedTab !== 'groups') {
            bulkActionsOptions.push(toggleEnvType)
        }
        return bulkActionsOptions
    }
    const updateData = (dataMap) => {
        let copyObj = data;
        Object.keys(copyObj).forEach((key) => {
            data[key].length > 0 && data[key].forEach((c) => {
                c['envType'] = dataMap[c.id]
                c['envTypeComp'] = dataMap[c.id] ? <Badge size="small" status="info">{dataMap[c.id]}</Badge> : null
            })
        })
        setData(copyObj)
        setRefreshData(!refreshData)
    }

    const updateEnvType = (apiCollectionIds,type) => {
        let copyObj = JSON.parse(JSON.stringify(envTypeMap))
        apiCollectionIds.forEach(id => copyObj[id] = type)
        api.updateEnvTypeOfCollection(type,apiCollectionIds).then((resp) => {
            func.setToast(true, false, "ENV type updated successfully")
            setEnvTypeMap(copyObj)
            updateData(copyObj)
        })
        resetResourcesSelected();

    }

    const modalComponent = <CreateNewCollectionModal
        key="modal"
        active={active}
        setActive={setActive}
        createCollectionModalActivatorRef={createCollectionModalActivatorRef}
        fetchData={fetchData}
    />

    let coverage = '0%';
    if(summaryData.totalEndpoints !== 0){
        if(summaryData.totalEndpoints < summaryData.totalTestedEndpoints){
            coverage = '100%'
        }else{
            coverage = Math.ceil((summaryData.totalTestedEndpoints * 100) / summaryData.totalEndpoints) + '%'
        }
    }

      const summaryItems = [
        {
            title: "Total APIs",
            data: transform.formatNumberWithCommas(totalAPIs),
        },
        {
            title: "Critical APIs",
            data: transform.formatNumberWithCommas(summaryData.totalCriticalEndpoints),
        },
        {
            title: "Tested APIs (Coverage)",
            data: coverage
        },
        {
            title: "Sensitive in response APIs",
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

    return(
        <PageWithMultipleCards
            title={
                <TitleWithInfo 
                    tooltipContent={"Akto automatically groups similar APIs into meaningful collections based on their subdomain names. "}
                    titleText={"API collections"} 
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
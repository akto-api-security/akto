import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, IndexFiltersMode, Box, Badge, Popover, ActionList, Link, Tooltip, Modal, Checkbox, LegacyCard, ResourceList, ResourceItem, Avatar, Filters, Card } from "@shopify/polaris"
import api from "../api"
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

const headers = [
    {
        title: "API collection name",
        text: "API collection name",
        value: "displayNameComp",
        filterKey:"displayName",
        textValue: 'displayName',
        showFilter:true
    },
    {
        title: "Total endpoints",
        text: "Total endpoints",
        value: "endpoints",
        isText: CellType.TEXT,
        sortActive: true
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Risk score of collection is maximum risk score of the endpoints inside this collection</Text>} title="Risk score" />,
        value: 'riskScoreComp',
        textValue: 'riskScore',
        text: 'Risk Score',
        sortActive: true
    },
    {   
        title: 'Test coverage',
        text: 'Test coverage', 
        value: 'coverage',
        isText: CellType.TEXT,
        tooltipContent: (<Text variant="bodySm">Percentage of endpoints tested successfully in the collection</Text>)
    },
    {
        title: 'Issues', 
        text: 'Issues', 
        value: 'issuesArr',
        textValue: 'issuesArrVal',
        tooltipContent: (<Text variant="bodySm">Severity and count of issues present in the collection</Text>)
    },
    {   
        title: 'Sensitive data' , 
        text: 'Sensitive data' , 
        value: 'sensitiveSubTypes',
        textValue: 'sensitiveSubTypesVal',
        tooltipContent: (<Text variant="bodySm">Types of data type present in response of endpoint inside the collection</Text>)
    },
    {
        text: 'Collection type',
        title: 'Collection type',
        value: 'envTypeComp',
        filterKey: "envType",
        showFilter: true,
        textValue: 'envType',
        tooltipContent: (<Text variant="bodySm">Environment type for an API collection, Staging or Production </Text>)
    },
    {   
        title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time an endpoint within collection was either discovered for the first time or seen again</Text>} title="Last traffic seen" />, 
        text: 'Last traffic seen', 
        value: 'lastTraffic',
        isText: CellType.TEXT,
        sortActive: true
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Time when collection was created</Text>} title="Discovered" />,
        text: 'Discovered',
        value: 'discovered',
        isText: CellType.TEXT,
        sortActive: true,
    }
]

const sortOptions = [
    { label: 'Name', value: 'displayName asc', directionLabel: 'A-Z', sortKey: 'displayName' },
    { label: 'Name', value: 'displayName desc', directionLabel: 'Z-A', sortKey: 'displayName' },
    { label: 'Activity', value: 'deactivatedScore asc', directionLabel: 'Active', sortKey: 'deactivatedRiskScore' },
    { label: 'Activity', value: 'deactivatedScore desc', directionLabel: 'Inactive', sortKey: 'activatedRiskScore' },
    { label: 'Risk Score', value: 'score asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: 3 },
    { label: 'Risk Score', value: 'score desc', directionLabel: 'Low risk', sortKey: 'riskScore' , columnIndex: 3},
    { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: 9 },
    { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs' , columnIndex: 9},
    { label: 'Endpoints', value: 'endpoints asc', directionLabel: 'More', sortKey: 'endpoints', columnIndex: 2 },
    { label: 'Endpoints', value: 'endpoints desc', directionLabel: 'Less', sortKey: 'endpoints' , columnIndex: 2},
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
        endpoints: c["urlsCount"] || 0,
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
            testedEndpoints: coverageMap[c.id] ? coverageMap[c.id] : 0,
            sensitiveInRespTypes: sensitiveInfoMap[c.id] ? sensitiveInfoMap[c.id] : [],
            severityInfo: severityInfoMap[c.id] ? severityInfoMap[c.id] : {},
            detected: func.prettifyEpoch(trafficInfoMap[c.id] || 0),
            detectedTimestamp : trafficInfoMap[c.id] || 0,
            riskScore: riskScoreMap[c.id] ? riskScoreMap[c.id] : 0,
            discovered: func.prettifyEpoch(c.startTs || 0),
        }
    })

    const prettifyData = transform.prettifyCollectionsData(newData, isLoading)
    return { prettify: prettifyData, normal: newData }
}

function ApiCollections() {
    const userRole = window.USER_ROLE

    const [data, setData] = useState({'all':[]})
    const [active, setActive] = useState(false);
    const [loading, setLoading] = useState(false)
    const [selectedTab, setSelectedTab] = useState("all")
    const [selected, setSelected] = useState(0)
    const [summaryData, setSummaryData] = useState({totalEndpoints:0 , totalTestedEndpoints: 0, totalSensitiveEndpoints: 0, totalCriticalEndpoints: 0})
    const [hasUsageEndpoints, setHasUsageEndpoints] = useState(false)
    const [envTypeMap, setEnvTypeMap] = useState({})
    const [refreshData, setRefreshData] = useState(false)
    const [popover,setPopover] = useState(false)
    const [teamData, setTeamData] = useState([])
    const [usersCollection, setUsersCollection] = useState([])
    const [selectedItems, setSelectedItems] = useState([])

    const definedTableTabs = ['All', 'Hostname', 'Groups', 'Custom']

    const { tabsInfo } = useTable()
    const tableCountObj = func.getTabsCount(definedTableTabs, data)
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

    const allCollections = PersistStore(state => state.allCollections)
    const setAllCollections = PersistStore(state => state.setAllCollections)
    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const setHostNameMap = PersistStore(state => state.setHostNameMap)

    const setCoverageMap = PersistStore(state => state.setCoverageMap)

    const lastFetchedInfo = PersistStore.getState().lastFetchedInfo
    const lastFetchedResp = PersistStore.getState().lastFetchedResp
    const lastFetchedSeverityResp = PersistStore.getState().lastFetchedSeverityResp
    const lastFetchedSensitiveResp = PersistStore.getState().lastFetchedSensitiveResp
    const setLastFetchedInfo = PersistStore.getState().setLastFetchedInfo
    const setLastFetchedResp = PersistStore.getState().setLastFetchedResp
    const setLastFetchedSeverityResp = PersistStore.getState().setLastFetchedSeverityResp
    const setLastFetchedSensitiveResp = PersistStore.getState().setLastFetchedSensitiveResp

    // as riskScore cron runs every 5 min, we will cache the data and refresh in 5 mins
    // similarly call sensitive and severityInfo

    async function fetchData() {

        // first api call to get only collections name and collection id
        setLoading(true)
        let apiCollectionsResp = await api.getAllCollectionsBasic();
        setLoading(false)
        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        let dataObj = {}
        dataObj = convertToNewData(tmp, {}, {}, {}, {}, {}, true);
        let res = {}
        res.all = dataObj.prettify
        res.hostname = dataObj.prettify.filter((c) => c.hostName !== null && c.hostName !== undefined)
        res.groups = dataObj.prettify.filter((c) => c.type === "API_GROUP")
        res.custom = res.all.filter(x => !res.hostname.includes(x) && !res.groups.includes(x));
        setData(res);

        const shouldCallHeavyApis = (func.timeNow() - lastFetchedInfo.lastRiskScoreInfo) >= (5 * 60)

        // fire all the other apis in parallel

        let apiPromises = [
            api.getAllCollections(),
            api.getCoverageInfoForCollections(),
            api.getLastTrafficSeen(),
            api.getUserEndpoints(),
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

        apiCollectionsResp = results[0].status === 'fulfilled' ? results[0].value : {};
        let coverageInfo = results[1].status === 'fulfilled' ? results[1].value : {};
        let trafficInfo = results[2].status === 'fulfilled' ? results[2].value : {};
        let hasUserEndpoints = results[3].status === 'fulfilled' ? results[3].value : true;

        let riskScoreObj = lastFetchedResp
        let sensitiveInfo = lastFetchedSensitiveResp
        let severityObj = lastFetchedSeverityResp

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
            }
        }

        setUsersCollection(usersCollectionList)
        setTeamData(userList)

        setHasUsageEndpoints(hasUserEndpoints)
        setCoverageMap(coverageInfo)

        tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        let envTypeObj = {}
        tmp.forEach((c) => {
            envTypeObj[c.id] = c.envType
        })
        setEnvTypeMap(envTypeObj)

        dataObj = convertToNewData(tmp, sensitiveInfo.sensitiveInfoMap, severityObj, coverageInfo, trafficInfo, riskScoreObj?.riskScoreMap, false);

        const summary = transform.getSummaryData(dataObj.normal)
        summary.totalCriticalEndpoints = riskScoreObj.criticalUrls;
        summary.totalSensitiveEndpoints = sensitiveInfo.sensitiveUrls
        setSummaryData(summary)

        setAllCollections(apiCollectionsResp.apiCollections || [])
        setCollectionsMap(func.mapCollectionIdToName(tmp))
        const allHostNameMap = func.mapCollectionIdToHostName(tmp)
        setHostNameMap(allHostNameMap)
        
        tmp = {}
        tmp.all = dataObj.prettify
        tmp.hostname = dataObj.prettify.filter((c) => c.hostName !== null && c.hostName !== undefined)
        tmp.groups = dataObj.prettify.filter((c) => c.type === "API_GROUP")
        tmp.custom = tmp.all.filter(x => !tmp.hostname.includes(x) && !tmp.groups.includes(x));

        setData(tmp);
    }

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2)
    }

    useEffect(() => {
        fetchData()
        resetFunc()    
    }, [])
    const createCollectionModalActivatorRef = useRef();

    async function handleCollectionsAction(collectionIdList, apiFunction, toastContent){
        const collectionIdListObj = collectionIdList.map(collectionId => ({ id: collectionId.toString() }))
        await apiFunction(collectionIdListObj)
        fetchData()
        func.setToast(true, false, `${collectionIdList.length} API collection${func.addPlurality(collectionIdList.length)} ${toastContent} successfully`)
    }
    async function handleShareCollectionsAction(collectionIdList, userIdList, apiFunction){
        const collectionIdSet = new Set(collectionIdList);

        for(const userId of userIdList) {
            const userCollections = usersCollection[userId] || [];
            userCollections.forEach(collectionId => collectionIdSet.add(collectionId));
        }

        const collectionIdListObj = Array.from(collectionIdSet);

        await apiFunction(collectionIdListObj, userIdList)
        func.setToast(true, false, `${userIdList.length} Member${func.addPlurality(userIdList.length)}'s collection${func.addPlurality(collectionIdList.length)} has been updated successfully`)
    }

    const exportCsv = () =>{
        const csvFileName = definedTableTabs[selected] + " Collections.csv"
        if (!loading) {
            let headerTextToValueMap = Object.fromEntries(headers.map(x => [x.text, x.isText === CellType.TEXT ? x.value : x.textValue]).filter(x => x[0]?.length > 0));
            let csv = Object.keys(headerTextToValueMap).join(",") + "\r\n"
            data[selectedTab].forEach(i => {
                csv += Object.values(headerTextToValueMap).map(h => (i[h] || "-")).join(",") + "\r\n"
            })
            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, csvFileName) ;
            func.setToast(true, false,"CSV exported successfully")
        }
    }

    const promotedBulkActions = (selectedResources) => {
        let actions = [
            {
                content: `Remove collection${func.addPlurality(selectedResources.length)}`,
                onAction: () => handleCollectionsAction(selectedResources, api.deleteMultipleCollections, "deleted")
            },
            {
                content: 'Export as CSV',
                onAction: () => exportCsv()
            }
        ];

        const deactivated = allCollections.filter(x => { return x.deactivated }).map(x => x.id);
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

        if(userRole === 'ADMIN') {
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
                    <ActionList
                        actionRole="menuitem"
                        items={[
                            {content: 'Staging', onAction: () => updateEnvType(selectedResources, "STAGING")},
                            {content: 'Production', onAction: () => updateEnvType(selectedResources, "PRODUCTION")},
                            {content: 'Reset', onAction: () => updateEnvType(selectedResources, null)},
                        ]}
                    />
                </Popover.Pane>
            </Popover>
        )

        const toggleEnvType = {
            content: toggleTypeContent
        }

        return [...actions, toggleEnvType];
    }
    const updateData = (dataMap) => {
        let copyObj = data;
        Object.keys(copyObj).forEach((key) => {
            data[key].length > 0 && data[key].forEach((c) => {
                c['envType'] = dataMap[c.id]
                c['envTypeComp'] = dataMap[c.id] ? <Badge size="small" status="info">{func.toSentenceCase(dataMap[c.id])}</Badge> : null
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
            data: transform.formatNumberWithCommas(summaryData.totalEndpoints),
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


    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex)
    }

    const tableComponent = (
        <GithubSimpleTable
            key={refreshData}
            pageLimit={100}
            data={data[selectedTab]} 
            sortOptions={sortOptions} 
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
        />
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
            primaryAction={<Button id={"create-new-collection-popup"} primary secondaryActions onClick={showCreateNewCollectionPopup}>Create new collection</Button>}
            isFirstPage={true}
            components={components}
        />
    )
}

export default ApiCollections 
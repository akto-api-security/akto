import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, Modal, TextField, IndexFiltersMode, Card, HorizontalStack, VerticalStack, Box, HorizontalGrid } from "@shopify/polaris"
import api from "../api"
import { useEffect,useState, useCallback, useRef } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { CircleTickMajor } from '@shopify/polaris-icons';
import ObserveStore from "../observeStore"
import PersistStore from "../../../../main/PersistStore"
import transform from "../transform"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import { CellType } from "../../../components/tables/rows/GithubRow"

const headers = [
    {
        title: "API collection name",
        text: "API collection name",
        value: "displayName",
        showFilter:true,
    },
    {
        title: "Total endpoints",
        text: "Total endpoints",
        value: "endpoints",
        type: CellType.TEXT,
    },
    {
        title: 'Risk score',
        value: 'riskScoreComp',
    },
    {
        title: "Discovered",
        text: "Discovered",
        value: "detected",
        type: CellType.TEXT,
    },
    {   
        title: 'Test coverage',
        text: 'Test coverage', 
        value: 'coverage',
        type: CellType.TEXT,
    },
    {
        title: 'Issues', 
        text: 'Issues', 
        value: 'issuesArr',
    },
    {   
        title: 'Sensitive data' , 
        text: 'Sensitive data' , 
        value: 'sensitiveSubTypes',
    },
    {   
        title: 'Last traffic seen', 
        text: 'Last traffic seen', 
        value: 'lastTraffic',
        type: CellType.TEXT,
    }
]

const sortOptions = [
    { label: 'Risk Score', value: 'score asc', directionLabel: 'Risky first', sortKey: 'riskScore' },
    { label: 'Risk Score', value: 'score desc', directionLabel: 'Stable first', sortKey: 'riskScore' },
    { label: 'Discovered', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'startTs' },
    { label: 'Discovered', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'startTs' },
    { label: 'Endpoints', value: 'endpoints asc', directionLabel: 'More', sortKey: 'endpoints' },
    { label: 'Endpoints', value: 'endpoints desc', directionLabel: 'Less', sortKey: 'endpoints' },
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

const convertToNewData = (collectionsArr, sensitiveInfoArr, severityInfoMap, coverageMap, trafficInfoMap, riskScoreMap) => {
    const sensitiveInfoMap = {} 
    sensitiveInfoArr.forEach((curr) =>{
        const { apiCollectionId, ...obj } = curr
        sensitiveInfoMap[apiCollectionId] = obj
    })

    const newData = collectionsArr.map((c) => {
        return{
            ...c,
            testedEndpoints: coverageMap[c.id] ? coverageMap[c.id] : 0,
            sensitiveInRespCount: sensitiveInfoMap[c.id] ? sensitiveInfoMap[c.id]['sensitiveUrlsInResponse'] : 0,
            sensitiveInRespTypes: sensitiveInfoMap[c.id] ? sensitiveInfoMap[c.id]['sensitiveSubtypesInResponse'] : [],
            severityInfo: severityInfoMap[c.id] ? severityInfoMap[c.id] : {},
            detected: func.prettifyEpoch(trafficInfoMap[c.id] || 0),
            riskScore: riskScoreMap[c.id] ? riskScoreMap[c.id] : 0
        }
    })

    const prettifyData = transform.prettifyCollectionsData(newData)
    return { prettify: prettifyData, normal: newData }
}

function ApiCollections() {

    const [data, setData] = useState({'All':[]})
    const [active, setActive] = useState(false);
    const [newCollectionName, setNewCollectionName] = useState('');
    const [loading, setLoading] = useState(false)
    const [selectedTab, setSelectedTab] = useState("All")
    const [selected, setSelected] = useState(0)
    const [summaryData, setSummaryData] = useState({totalEndpoints:0 , totalTestedEndpoints: 0, totalSensitiveEndpoints: 0, totalCriticalEndpoints: 0})
    const handleNewCollectionNameChange = 
        useCallback(
            (newValue) => setNewCollectionName(newValue),
        []);
    
    const tableTabs = [
        {
            content: 'All',
            badge: data["All"]?.length?.toString(),
            onAction: () => { setSelectedTab('All') },
            id: 'All',
        },
        {
            content: 'Hostname',
            badge: data["Hostname"]?.length?.toString(),
            onAction: () => { setSelectedTab('Hostname') },
            id: 'Hostname',
        },
        {
            content: 'Groups',
            badge: data["Groups"]?.length?.toString(),
            onAction: () => { setSelectedTab('Groups') },
            id: 'Groups',
        },
        {
            content: 'Custom',
            badge: data["Custom"]?.length?.toString(),
            onAction: () => { setSelectedTab('Custom') },
            id: 'Custom',
        }
    ]

    const setInventoryFlyout = ObserveStore(state => state.setInventoryFlyout)
    const setFilteredItems = ObserveStore(state => state.setFilteredItems) 
    const setSamples = ObserveStore(state => state.setSamples)
    const setSelectedUrl = ObserveStore(state => state.setSelectedUrl)

    const lastFetchedInfo = PersistStore(state => state.lastFetchedInfo)
    const lastFetchedResp = PersistStore(state => state.lastFetchedResp)
    const lastFetchedSeverityResp = PersistStore(state => state.lastFetchedSeverityResp)
    const lastCalledSensitiveInfo = PersistStore(state => state.lastCalledSensitiveInfo)
    const lastFetchedSensitiveResp = PersistStore(state => state.lastFetchedSensitiveResp)
    const setLastFetchedInfo = PersistStore(state => state.setLastFetchedInfo)
    const setLastFetchedResp = PersistStore(state => state.setLastFetchedResp)
    const setLastFetchedSeverityResp = PersistStore(state => state.setLastFetchedSeverityResp)
    const setLastCalledSensitiveInfo = PersistStore(state => state.setLastCalledSensitiveInfo)
    const setLastFetchedSensitiveResp = PersistStore(state => state.setLastFetchedSensitiveResp)

    const resetFunc = () => {
        setInventoryFlyout(false)
        setFilteredItems([])
        setSamples("")
        setSelectedUrl({})
    }

    const showCreateNewCollectionPopup = () => {
        setActive(true)
    }

    const setAllCollections = PersistStore(state => state.setAllCollections)
    const setCollectionsMap = PersistStore(state => state.setCollectionsMap)
    const setHostNameMap = PersistStore(state => state.setHostNameMap)

    const createNewCollection = async () => {
        let newColl = await api.createCollection(newCollectionName)
        setNewCollectionName('')
        //setData([convertToCollectionData(newColl.apiCollections[0]), ...data])
        fetchData()
        setActive(false)
        func.setToast(true, false, "API collection created successfully")
    }

    async function fetchRiskScoreInfo(){
        let tempRiskScoreObj = lastFetchedResp
        let tempSeverityObj = lastFetchedSeverityResp
        await api.lastUpdatedInfo().then(async(resp) => {
            if(resp.lastUpdatedIssues > lastFetchedInfo.lastRiskScoreInfo || resp.lastUpdatedSensitiveMap > lastFetchedInfo.lastSensitiveInfo){
                await api.getRiskScoreInfo().then((res) =>{
                    const newObj = {
                        criticalUrls: res.criticalEndpointsCount,
                        riskScoreMap: res.riskScoreOfCollectionsMap, 
                    }
                    tempRiskScoreObj = JSON.parse(JSON.stringify(newObj));
                    setLastFetchedResp(newObj);
                })
            }
            if(resp.lastUpdatedIssues > lastFetchedInfo.lastRiskScoreInfo){
                await api.getSeverityInfoForCollections().then((resp) => {
                    tempSeverityObj = JSON.parse(JSON.stringify(resp))
                    setLastFetchedSeverityResp(resp)
                })
            }
            setLastFetchedInfo({
                lastRiskScoreInfo: func.timeNow() > resp.lastUpdatedIssues ? func.timeNow() : resp.lastUpdatedIssues,
                lastSensitiveInfo: func.timeNow() > resp.lastUpdatedSensitiveMap ? func.timeNow() : resp.lastUpdatedSensitiveMap,
            })
        })
        let finalObj = {
            riskScoreObj: tempRiskScoreObj,
            severityObj: tempSeverityObj
        }
        return finalObj
    }
    
    async function fetchSensitiveInfo(){
        let tempSensitveArr = lastFetchedSensitiveResp
        if((func.timeNow() - (5 * 60)) >= lastCalledSensitiveInfo){
            await api.getSensitiveInfoForCollections().then((resp) => {
                tempSensitveArr = JSON.parse(JSON.stringify(resp))
                setLastCalledSensitiveInfo(func.timeNow())
                setLastFetchedSensitiveResp(resp)
            })
        }
        return tempSensitveArr 
    }

    async function fetchData() {
        setLoading(true)
        let apiPromises = [
            api.getAllCollections(),
            api.getCoverageInfoForCollections(),
            api.getLastTrafficSeen()
        ];
        
        let results = await Promise.allSettled(apiPromises);

        let apiCollectionsResp = results[0].status === 'fulfilled' ? results[0].value : {};
        let coverageInfo = results[1].status === 'fulfilled' ? results[1].value : {};
        let trafficInfo = results[2].status === 'fulfilled' ? results[2].value : {};

        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)

        const issuesObj = await fetchRiskScoreInfo();
        const severityObj = issuesObj.severityObj;
        const riskScoreObj = issuesObj.riskScoreObj;
        const sensitveInfoArr = await fetchSensitiveInfo();
        setLoading(false)

        const dataObj = convertToNewData(tmp, sensitveInfoArr, severityObj, coverageInfo, trafficInfo, riskScoreObj?.riskScoreMap);

        const summary = transform.getSummaryData(dataObj.normal)
        summary.totalCriticalEndpoints = riskScoreObj.criticalUrls;
        setSummaryData(summary)

        setAllCollections(apiCollectionsResp.apiCollections || [])
        setCollectionsMap(func.mapCollectionIdToName(tmp))
        const allHostNameMap = func.mapCollectionIdToHostName(tmp)
        setHostNameMap(allHostNameMap)
        
        tmp = {}
        tmp.All = dataObj.prettify
        tmp.Hostname = dataObj.prettify.filter((c) => c.type === "TRAFFIC")
        tmp.Groups = dataObj.prettify.filter((c) => c.type === "API_GROUP")
        tmp.Custom = dataObj.prettify.filter((c) => (c.type === "CUSTOM" || c.type === "OTHER_SOURCES"))

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

    async function handleRemoveCollections(collectionIdList) {
        const collectionIdListObj = collectionIdList.map(collectionId => ({ id: collectionId.toString() }))
        const response = await api.deleteMultipleCollections(collectionIdListObj)
        fetchData()
        func.setToast(true, false, `${collectionIdList.length} API collection${collectionIdList.length > 1 ? "s" : ""} deleted successfully`)
    }

    const promotedBulkActions = (selectedResources) => [
        {
          content: 'Remove collections',
          onAction: () => handleRemoveCollections(selectedResources)
        },
      ];


      const summaryItems = [
        {
            title: "Total APIs",
            data: summaryData.totalEndpoints,
        },
        {
            title: "Critical APIs",
            data: summaryData.totalCriticalEndpoints,
        },
        {
            title: "Tested APIs (Coverage)",
            data: Math.ceil((summaryData.totalTestedEndpoints * 100) / summaryData.totalEndpoints) + '%'
        },
        {
            title: "Sensitive in response APIs",
            data: summaryData.totalSensitiveEndpoints,
        }
    ]

    const summaryCard = (
        <Card padding={0} key="info">
            <Box padding={2} paddingInlineStart={4} paddingInlineEnd={4}>
                <HorizontalGrid columns={4} gap={4}>
                    {summaryItems.map((item, index) => (
                        <Box borderInlineEndWidth={index < 3 ? "1" : ""} key={index} paddingBlockStart={1} paddingBlockEnd={1} borderColor="border-subdued">
                            <VerticalStack gap="1">
                                <Text color="subdued" variant="headingXs">
                                    {item.title}
                                </Text>
                                <Text variant="bodyMd" fontWeight="semibold">
                                    {item.data}
                                </Text>
                            </VerticalStack>
                        </Box>
                    ))}
                </HorizontalGrid>
            </Box>
        </Card>
    )

    const modalComponent = (
        <Modal
            key="modal"
            activator={createCollectionModalActivatorRef}
            open={active}
            onClose={() => setActive(false)}
            title="New collection"
            primaryAction={{
            id:"create-new-collection",
            content: 'Create',
            onAction: createNewCollection,
            }}
        >
            <Modal.Section>

            <TextField
                id={"new-collection-input"}
                label="Name"
                helpText="Enter name for the new collection"
                value={newCollectionName}
                onChange={handleNewCollectionNameChange}
                autoComplete="off"
                maxLength="24"
                suffix={(
                    <Text>{newCollectionName.length}/24</Text>
                )}
                autoFocus
            />


            </Modal.Section>
        </Modal>
    )

    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex)
    }

    const tableComponent = (
        <GithubSimpleTable
            key="table"
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
        />
    )

    const components = loading ? [<SpinnerCentered key={"loading"}/>]: [modalComponent, tableComponent]

    return(
        <PageWithMultipleCards
        title={
                <Text variant='headingLg' truncate>
            {
                "API Collections"
            }
        </Text>
            }
            primaryAction={<Button id={"create-new-collection-popup"} primary secondaryActions onClick={showCreateNewCollectionPopup}>Create new collection</Button>}
            isFirstPage={true}
            components={components}
        />
    )
}

export default ApiCollections 
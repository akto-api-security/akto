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

const headers = [
    {
        title: "API collection name",
        text: "API collection name",
        value: "displayName",
        showFilter:true,
        itemCell: 1,
    },
    {
        title: "Total endpoints",
        text: "Total endpoints",
        value: "endpoints",
        itemCell: 2,
        isText: true,
    },
    {   
        title: 'Test coverage',
        text: 'Test coverage', 
        value: 'coverage',
        itemCell: 4,
        isText: true,
    },
    {
        title: 'Issues', 
        text: 'Issues', 
        value: 'issuesArr',
        itemCell: 5,
    },
    {   
        title: 'Sensitive data' , 
        text: 'Sensitive data' , 
        value: 'sensitiveSubTypes',
        itemCell: 7,
    },
    {   
        title: 'Last traffic seen', 
        text: 'Last traffic seen', 
        value: 'lastTraffic',
        itemCell: 8,
        isText: true,
    }
]

const sortOptions = [
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

const convertToNewData = (collectionsArr, sensitiveInfoArr, severityInfoMap, coverageMap, trafficInfoMap) => {
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
        }
    })

    const prettifyData = transform.prettifyCollectionsData(newData)
    return { prettify: prettifyData, normal: newData }
}

function ApiCollections() {

    const [data, setData] = useState([])
    const [active, setActive] = useState(false);
    const [newCollectionName, setNewCollectionName] = useState('');
    const [loading, setLoading] = useState(false)
    const [summaryData, setSummaryData] = useState({totalEndpoints:0 , totalTestedEndpoints: 0, totalSensitiveEndpoints: 0, totalCriticalEndpoints: 0})
    const handleNewCollectionNameChange = 
        useCallback(
            (newValue) => setNewCollectionName(newValue),
        []);
    
    
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

    async function fetchData() {
        setLoading(true)
        let [apiCollectionsResp, sensitiveInfoResp, coverageInfoResp, severityInfoResp, trafficInfoResp] = await Promise.all([
                api.getAllCollections(), api.getSensitiveInfoForCollections(), api.getCoverageInfoForCollections(), api.getSeverityInfoForCollections(), api.getLastTrafficSeen()
            ])
        setLoading(false)
        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        const dataObj = convertToNewData(tmp, sensitiveInfoResp.sensitiveInfoInApiCollections, severityInfoResp.severityInfo, coverageInfoResp.testedEndpointsMaps, trafficInfoResp.lastTrafficSeenMap);

        const summary = transform.getSummaryData(dataObj.normal)
        setSummaryData(summary)

        setAllCollections(apiCollectionsResp.apiCollections || [])
        setCollectionsMap(func.mapCollectionIdToName(tmp))
        const allHostNameMap = func.mapCollectionIdToHostName(tmp)
        setHostNameMap(allHostNameMap)
        
        setData(dataObj.prettify)
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
        // {
        //     title: "Critical APIs",
        //     data: summaryData.totalCriticalEndpoints,
        // },
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
                <HorizontalGrid columns={3} gap={4}>
                    {summaryItems.map((item, index) => (
                        <Box borderInlineEndWidth={index < 2 ? "1" : ""} key={index} paddingBlockStart={1} paddingBlockEnd={1} borderColor="border-subdued">
                            <VerticalStack gap="1">
                                <Text color="subdued" variant="bodySm">
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

    const tableComponent = (
        <GithubSimpleTable
            key="table"
            pageLimit={100}
            data={data} 
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
        />
    )

    const components = loading ? [<SpinnerCentered key={"loading"}/>]: [summaryCard, modalComponent, tableComponent]

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

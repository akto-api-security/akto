import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, Modal, TextField, IndexFiltersMode } from "@shopify/polaris"
import api from "../api"
import { useEffect,useState, useCallback, useRef } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { CircleTickMajor } from '@shopify/polaris-icons';
import ObserveStore from "../observeStore"
import PersistStore from "../../../../main/PersistStore"
import transform from "../transform"

const headers = [
    {
        title: "API collection name",
        value: "displayName",
        showFilter:true,
        itemCell: 1,
        isText: true,
    },
    {
        title: "Total endpoints",
        value: "endpoints",
        itemCell: 2,
        isText: true,
    },
    {
        title: 'Risk score', 
        value: 'riskScore',
        itemCell: 3,
    },
    {   
        title: 'Test coverage', 
        value: 'coverage',
        itemCell: 4,
        isText: true,
    },
    {
        title: 'Issues', 
        value: 'issuesArr',
        itemCell: 5,
    },
    {   
        title: 'Sensitive data' , 
        value: 'sensitiveSubTypes',
        itemCell: 7,
        isText: true,
    },
    {   
        title: 'Last traffic seen', 
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
        detected: "Last seen " + func.prettifyEpoch(c.startTs),
        icon: CircleTickMajor,
        nextUrl: "/dashboard/observe/inventory/"+ c.id
    }    
}

const convertToNewData = (collectionsArr, sensitiveInfoArr, severityInfoMap, coverageMap) => {
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
            severityInfo: severityInfoMap[c.id] ? severityInfoMap[c.id] : {}
        }
    })

    const prettifyData = transform.prettifyCollectionsData(newData)
    return prettifyData
}

function ApiCollections() {

    const [data, setData] = useState([])
    const [active, setActive] = useState(false);
    const [newCollectionName, setNewCollectionName] = useState('');
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

    const createNewCollection = async () => {
        let newColl = await api.createCollection(newCollectionName)
        setNewCollectionName('')
        //setData([convertToCollectionData(newColl.apiCollections[0]), ...data])
        fetchData()
        setActive(false)
        func.setToast(true, false, "API collection created successfully")
    }

    async function fetchData() {
        let [apiCollectionsResp, sensitiveInfoResp, coverageInfoResp, severityInfoResp] = await Promise.all([
                api.getAllCollections(), api.getSensitiveInfoForCollections(), api.getCoverageInfoForCollections(), api.getSeverityInfoForCollections()
            ])

        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        const completeData = convertToNewData(tmp, sensitiveInfoResp.sensitiveInfoInApiCollections, severityInfoResp.severityInfo, coverageInfoResp.testedEndpointsMaps)
        setAllCollections(apiCollectionsResp.apiCollections || [])
        setCollectionsMap(func.mapCollectionIdToName(tmp))
        setData(completeData)
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
            components={[
               
                (<Modal
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
                </Modal>)
                ,               
                (<GithubSimpleTable
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
                />)
            ]}
        />
    )
}

export default ApiCollections 

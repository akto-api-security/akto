import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, Modal, TextField, IndexFiltersMode, Card, VerticalStack, Box, HorizontalGrid } from "@shopify/polaris"
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
        value: "displayNameComp",
        filterKey:"displayName",
        showFilter:true,
    },
    {
        title: "Total endpoints",
        text: "Total endpoints",
        value: "endpoints",
        type: CellType.TEXT,
    },
    {
        title: "Discovered",
        text: "Discovered",
        value: "detected",
        type: CellType.TEXT,
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

const convertToNewData = (collectionsArr) => {

    const newData = collectionsArr.map((c) => {
        return{
            ...c
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
        let apiPromises = [
            api.getAllCollections()
        ];
        
        let results = await Promise.allSettled(apiPromises);

        let apiCollectionsResp = results[0].status === 'fulfilled' ? results[0].value : {};

        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)

        setLoading(false)

        const dataObj = convertToNewData(tmp);

        setAllCollections(apiCollectionsResp.apiCollections || [])
        setCollectionsMap(func.mapCollectionId(tmp))
        const allHostNameMap = func.mapCollectionIdToHostName(tmp)
        setHostNameMap(allHostNameMap)
        
        tmp = {}
        tmp.All = dataObj.prettify
        tmp.Hostname = dataObj.prettify.filter((c) => c.type === "TRAFFIC")
        tmp.Groups = dataObj.prettify.filter((c) => c.type === "API_GROUP")
        tmp.Custom = dataObj.prettify.filter((c) => c.type === "CUSTOM")

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
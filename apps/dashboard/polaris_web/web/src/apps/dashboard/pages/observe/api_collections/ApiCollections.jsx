import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, Modal, TextField } from "@shopify/polaris"
import api from "../api"
import { useEffect,useState, useCallback, useRef } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {
    ClockMinor,
    CircleTickMinor
  } from '@shopify/polaris-icons';

import { useNavigate } from "react-router-dom"
import ObserveStore from "../observeStore"

const headers = [
    {
        text: "API collection name",
        value: "displayName",
        showFilter:true,
        itemOrder: 1,
    },
    {
        text: "Endpoints",
        value: "endpoints",
        itemCell: 2,
    },
    {
        text: "Discovered",
        value: "detected",
        icon: ClockMinor,
        itemOrder: 3
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
        icon: CircleTickMinor,
        nextUrl: "/dashboard/observe/inventory/"+ c.id
    }    
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

    const createNewCollection = async () => {
        let newColl = await api.createCollection(newCollectionName)
        setNewCollectionName('')
        //setData([convertToCollectionData(newColl.apiCollections[0]), ...data])
        fetchData()
        setActive(false)
        func.setToast(true, false, "API collection created successfully")
    }

    async function fetchData() {
        let apiCollectionsResp = await api.getAllCollections()

        let tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
        setData(tmp)
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
                />)
            ]}
        />
    )
}

export default ApiCollections 

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

const headers = [
    {
        text: "",
        value: "icon",
        itemOrder: 0,
    },
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
    { label: 'Endpoints', value: 'endpoints asc', directionLabel: 'More', sortKey: 'endpoints' },
    { label: 'Endpoints', value: 'endpoints desc', directionLabel: 'Less', sortKey: 'endpoints' },
    { label: 'Discovered', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'startTs' },
    { label: 'Discovered', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'startTs' }
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
    
    
    const navigate = useNavigate()

    const showCreateNewCollectionPopup = () => {
        setActive(true)
    }

    const createNewCollection = async () => {
        let newColl = await api.createCollection(newCollectionName)
        setNewCollectionName('')
        setData([convertToCollectionData(newColl.apiCollections[0]), ...data])
        setActive(false)

    }

    useEffect(() => {
        let tmp=[]
        async function fetchData() {
            let apiCollectionsResp = await api.getAllCollections()

            tmp = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData)
            setData(tmp)
        }

        fetchData()    
    }, [])

    const createCollectionModalActivatorRef = useRef();

    return(
        <PageWithMultipleCards
        title={
                <Text variant='headingLg' truncate>
            {
                "API Collections"
            }
        </Text>
            }
            primaryAction={<Button secondaryActions onClick={showCreateNewCollectionPopup}>Create new collection</Button>}
            isFirstPage={true}
            components={[
               
                (<Modal
                    key="modal"
                    activator={createCollectionModalActivatorRef}
                    open={active}
                    onClose={() => setActive(false)}
                    title="New collection"
                    primaryAction={{
                    content: 'Create',
                    onAction: createNewCollection,
                    }}
                >
                    <Modal.Section>

                    <TextField
                        label="Enter name for the new collection"
                        value={newCollectionName}
                        onChange={handleNewCollectionNameChange}
                        autoComplete="off"
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
                disambiguateLabel={()=>{}} 
                headers={headers}
                />)
            ]}
        />
    )
}

export default ApiCollections 

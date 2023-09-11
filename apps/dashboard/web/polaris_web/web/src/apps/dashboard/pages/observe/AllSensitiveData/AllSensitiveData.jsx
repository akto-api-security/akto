import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button, Modal } from "@shopify/polaris"
import api from "../api"
import { useEffect,useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {
    CircleCancelMinor,
    CircleTickMinor
  } from '@shopify/polaris-icons';

import { useNavigate } from "react-router-dom"
import dashboardFunc from "../../transform"
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout"

const headers = [
    {
        text: "",
        value: "icon",
        itemOrder: 0,
    },
    {
        text: "Data type",
        value: "subType",
        showFilter:true,
        itemOrder: 1,
    },
    {
        text: "Custom type",
        value: "isCustomType",
        showFilter:true,
        itemOrder: 2,
    },
    {
        text: "API response",
        value: "response",
        itemCell: 2,
    },
    {
        text: "API request",
        value: "request",
        itemCell: 2,
    },
    {
        text:"Sensitive count",
        value: "sensitiveCount"
    }
] 

const sortOptions = [
    { label: 'Sensitive data', value: 'sensitiveCount asc', directionLabel: 'More exposure', sortKey: 'sensitiveCount' },
    { label: 'Sensitive data', value: 'sensitiveCount desc', directionLabel: 'Less exposure', sortKey: 'sensitiveCount' },
    { label: 'Data type', value: 'subType asc', directionLabel: 'A-Z', sortKey: 'subType' },
    { label: 'Data type', value: 'subType desc', directionLabel: 'Z-A', sortKey: 'subType' },
  ];

const resourceName = {
    singular: 'sensitive data type',
    plural: 'sensitive data types',
  };

function AllSensitiveData() {

    const [data, setData] = useState([])
    const [mapData, setMapData] = useState({})
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const navigate = useNavigate()

    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => navigate("/dashboard/observe/data-types", {state: {name: item.subType, dataObj: mapData[item.subType], type: item.isCustomType ? 'Custom' : 'Akto'}}),
            }]
        }]
    }

    function disambiguateLabel(key, value) {
        switch(key){
            case "subType": 
                return func.convertToDisambiguateLabelObj(value, null, 4)
            default:
                return value
        }
    }

    const handleRedirect = () => {
        navigate("/dashboard/observe/data-types")
    }
    
    useEffect(() => {
        let tmp=[]
        async function fetchData(){
            let mapDataToKey = {}
            await api.fetchDataTypes().then((res) => {
                res.dataTypes.aktoDataTypes.forEach((type) => {
                    mapDataToKey[type.name] = type
                    tmp.push({
                        subType:type.name,
                        request:0,
                        response:0,
                        id:type.name,
                        nextUrl:type.name,
                        icon: CircleTickMinor,
                        iconColor: "success",
                        iconTooltip: "Active",
                        sensitiveCount:0
                    })
                })
                res.dataTypes.customDataTypes.forEach((type) => {
                    mapDataToKey[type.name] = type
                    tmp.push({
                        subType:type.name,
                        isCustomType:['Custom'],
                        request:0,
                        response:0,
                        id:type.name,
                        nextUrl:type.name,
                        icon: type.active ? CircleTickMinor : CircleCancelMinor,
                        iconColor: type.active ? "success" : "critical",
                        iconTooltip: type.active ? "Active" : "Inactive",
                        sensitiveCount:0
                    })
                })
                setMapData(mapDataToKey)
            })
            await api.fetchSubTypeCountMap(0, func.timeNow()).then((res) => {
                let count = res.response.subTypeCountMap;
                Object.keys(count.REQUEST).map((key) => {
                    tmp.forEach((obj) => {
                        if(obj.subType==key){
                            obj.request=count.REQUEST[key]
                            obj.sensitiveCount=obj.request
                        }
                    })
                })
                Object.keys(count.RESPONSE).map((key) => {
                    tmp.forEach((obj) => {
                        if(obj.subType==key){
                            obj.response=count.RESPONSE[key]
                            obj.sensitiveCount=(obj.response*100000)
                        }
                    })
                })
                setData(tmp);
            })
        }
        fetchData();
    }, [])

    function displayGPT(){
        setIsGptScreenActive(true)
        let requestObj = {key: "DATA_TYPES"}
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }
    
    return (
        <PageWithMultipleCards
        title={
                <Text variant='headingLg' truncate>
            {
                "Sensitive data exposure"
            }
        </Text>
            }
            primaryAction={<Button id={"all-data-types"} primary onClick={handleRedirect}>Create custom data types</Button>}
            secondaryActions={<Button onClick={displayGPT}>Ask AktoGPT</Button>}
            isFirstPage={true}
            components={[
                <GithubSimpleTable
                key="table"
                data={data} 
                sortOptions={sortOptions} 
                resourceName={resourceName} 
                filters={[]}
                disambiguateLabel={disambiguateLabel} 
                headers={headers}
                hasRowActions={true}
                getActions={getActions}
                getStatus={func.getTestResultStatus}
                increasedHeight={true}
                />,
                <Modal key="modal" large open={isGptScreenActive} onClose={()=> setIsGptScreenActive(false)} title="Akto GPT">
                    <Modal.Section flush>
                        <AktoGptLayout prompts={prompts} closeModal={()=> setIsGptScreenActive(false)} />
                    </Modal.Section>
                </Modal>
            ]}
        />

    )
}

export default AllSensitiveData
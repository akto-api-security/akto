import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import { Text, Button } from "@shopify/polaris"
import api from "../api"
import { useEffect,useState } from "react"
import func from "@/util/func"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {
    CircleCancelMinor,
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
        text: "Data type",
        value: "subType",
        showFilter:true,
        itemOrder: 1,
    },
    {
        text: "Custom type",
        value: "isCustomType",
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
] 

const sortOptions = [
    { label: 'Data type', value: 'subType asc', directionLabel: 'A-Z', sortKey: 'subType' },
    { label: 'Data type', value: 'subType desc', directionLabel: 'Z-A', sortKey: 'subType' },
  ];

const resourceName = {
    singular: 'Sensitive data type',
    plural: 'Sensitive data types',
  };

function AllSensitiveData(){

    const [data, setData] = useState([])
    const [mapData, setMapData] = useState({})
    const navigate = useNavigate()

    const getActions = (item) => {
        return [{
            items: [{
                content: 'Edit',
                onAction: () => navigate("/dashboard/observe/data-types", {state: {name: item.subType, dataObj: mapData[item.subType]}}),
            }]
        }]
    }

    const handleRedirect = () => {
        navigate("/dashboard/observe/data-types", {state: {name: "", dataObj: {}}})
    }
    
    useEffect(() => {
        let tmp=[]
        async function fetchData(){
            let dataTypeMap={}
            let mapDataToKey = {}
            await api.fetchDataTypes().then((res) => {
                res.dataTypes.aktoDataTypes.forEach((type) => {
                    mapDataToKey[type.name] = type
                    dataTypeMap[type.name]={active:true}
                })
                res.dataTypes.customDataTypes.forEach((type) => {
                    mapDataToKey[type.name] = type
                    dataTypeMap[type.name]={active:type.active, custom:true}
                })
            })
            setMapData(mapDataToKey)
            api.fetchSubTypeCountMap(0, func.timeNow()).then((res) => {
                let count = res.response.subTypeCountMap;
                Object.keys(count.REQUEST).map((key) => {
                    tmp.push({
                        subType:key,
                        request:count.REQUEST[key],
                        response:0,
                    })
                })
                Object.keys(count.RESPONSE).map((key) => {
                    let data = tmp.filter((data) => {
                        return data.subType==key
                    })
                    tmp = tmp.filter((data) => {
                        return data.subType!=key
                    }) 
                    if(data.length==0){
                        data={
                            subType:key,
                            request:0,
                            response:count.RESPONSE[key],
                        }
                    } else {
                        data = data[0]
                        data.response=count.RESPONSE[key]
                    }
                    tmp.push(data);
                })
                tmp.forEach((data, index) => {
                    tmp[index]["hexId"] = data.subType
                    tmp[index]["nextUrl"] = data.subType
                    if(dataTypeMap[data.subType].custom){
                        tmp[index]['isCustomType']= [{confidence : 'Custom'}]
                    }
                    tmp[index]['icon'] = dataTypeMap[data.subType].active ? CircleTickMinor : CircleCancelMinor
                })
                setData(tmp);
            })
        }
        fetchData();
    }, [])
    
    return (
        <PageWithMultipleCards
        title={
                <Text variant='headingLg' truncate>
            {
                "Sensitive data exposure"
            }
        </Text>
            }
            primaryAction={<Button primary onClick={handleRedirect}>Create custom data types</Button>}
            components={[
                <GithubSimpleTable
                key="table"
                data={data} 
                sortOptions={sortOptions} 
                resourceName={resourceName} 
                filters={[]}
                disambiguateLabel={()=>{}} 
                headers={headers}
                hasRowActions={true}
                getActions={getActions}
                
                />
            ]}
        />

    )
}

export default AllSensitiveData
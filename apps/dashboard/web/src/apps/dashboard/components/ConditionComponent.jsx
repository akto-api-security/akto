import { ButtonGroup, HorizontalStack, TextField } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import Dropdown from './layouts/Dropdown';
import DropdownSearch from './shared/DropdownSearch';
import Store from '../store';
import func from "@/util/func"
import api from '../pages/testing/api';

function ConditionComponent(props) {

    const { condition, setConditions, index, param, selectOptions } = props
    useEffect(()=>{
        fetchApiEndpoints(condition)
    },[condition])
    const allCollections = Store(state => state.allCollections);
    const allCollectionsOptions = allCollections.map(collection => {
        return {
            label: collection.displayName,
            value: collection.id
        }
    })
    const getApiEndpointsOptions = (data) => {
        return data.map(apiEndpoint => {
            let str = func.toMethodUrlString(apiEndpoint);
            return {
                id: str,
                label: str,
                value: str
            }
        })
    }

    function getCollectionId(field) {
        if (field == undefined)
            return undefined;
        let value = field.value;
        if (value == undefined || Object.keys(value) == undefined || Object.keys(value)[0] == undefined)
            return undefined;

        return Object.keys(value)[0];
    }

    async function fetchApiEndpoints (value) {
        let collectionId = getCollectionId(value);
        if(collectionId==undefined)
            return []
        const apiEndpointsResponse = await api.fetchCollectionWiseApiEndpoints(collectionId)
        if (apiEndpointsResponse) {
            setApiEndpoints((prev) => {
                if(prev.apiCollectionId == collectionId){
                    return prev;
                }
                return {apiCollectionId:collectionId, endpoints:getApiEndpointsOptions(apiEndpointsResponse.listOfEndpointsInCollection)}
            })
        }
    }
    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)
    const [apiEndpoints, setApiEndpoints] = useState({})

    const handleChange = (value) => {
        setConditions((prev) => {
            prev[index].value = value;
            return [...prev];
        })
    };

    const orAndConditions = [
        {
            label: 'OR',
            value: 'OR',
        },
        {
            label: 'AND',
            value: 'AND'
        }
    ]

    const getOption = (type) => {
        const option = selectOptions.filter((item) => {
            return item.value == type
        })[0]
        return option;
    }
    const getConditions = (type) => {
        const option = getOption(type)
        if (option.operators) {
            return option.operators
        }
        return orAndConditions;
    }

    const handleRegexSelected = (type) => {
        setConditions((prev) => {
            if(getOption(type).type == "MAP"){
                if(getOption(prev[index].type).type==undefined){
                    prev[index].value={}
                }
            } else {
                prev[index].value=""
            }
            prev[index].type = type;
            prev[index].operator = getConditions(type)[0].label;
            return [...prev];
        })
    }

    const handleCollectionSelected = (collectionId) => {
        let value = {};
        value[collectionId] = []
        setConditions((prev) => {
            prev[index].value = value;
            return [...prev];
        })
        fetchApiEndpoints(condition)
    }

    const handleEndpointsSelected = (apiEndpoints, field) => {
        let value = field.value;
        let collectionId = getCollectionId(field);
        value[collectionId]=apiEndpoints;
        setConditions((prev) => {
            prev[index].value = value;
            return [...prev];
        })
    }

    function getEndpointCount(field){
        let collectionId = getCollectionId(field);
        if(collectionId==undefined)
            return undefined
        return `${field.value[collectionId].length} endpoint${field.value[collectionId].length==1 ? "":"s"} selected`;
    }

    function getEndpoints(field){
        let collectionId = getCollectionId(field);
        if(collectionId==undefined)
            return [];
        return field.value[collectionId].map((obj)=> {return func.toMethodUrlString(obj)})
    }

    const prefixLeft = (field) => (
        <div style={{ display: "flex", gap: "4px" }}>
            <div style={{ flexGrow: "1" }}>
                <TextField value={param}/>
            </div>
            <div style={{ flexGrow: "1" }}>
                <Dropdown menuItems={selectOptions} initial={field.type} selected={(type) => handleRegexSelected(type)} />
            </div>
        </div>
    )

    const collectionComponent = (field) => {
        return (
            <div style={{display:"flex", gap:"4px"}}>
                <div style={{flexGrow:"1"}}>
                <DropdownSearch
                    placeholder="Select API collection"
                    optionsList={allCollectionsOptions}
                    setSelected={(collectionId) => handleCollectionSelected(collectionId)}
                    preSelected={[Number(getCollectionId(field))]}
                    value={mapCollectionIdToName[getCollectionId(field)]}
                />
                </div>
                <div style={{flexGrow:"1"}}>
                <DropdownSearch
                    disabled={apiEndpoints?.endpoints == undefined || apiEndpoints.endpoints.length === 0}
                    placeholder="Select API endpoint"
                    optionsList={apiEndpoints?.endpoints == undefined || typeof apiEndpoints.then == 'function' ? [] : 
                                    apiEndpoints.endpoints}
                    setSelected={(apiEndpoints) => {handleEndpointsSelected(apiEndpoints.map((obj) => {
                         return func.toMethodUrlObject(obj) }), field) }}
                    preSelected={getEndpoints(field)}
                    itemName={"endpoint"}
                    value={getEndpointCount(field)}
                    allowMultiple
                />
                </div>
            </div>
        )
    }

    return (
        <div style={{ display: "flex", gap:"4px" }}>
            <div style={{ flex: "2" }}>
                {prefixLeft(condition)}
            </div>
            <div style={{ flex: "3" }}>
                {
                    getOption(condition.type).type == "MAP" ?
                        collectionComponent(condition) :
                        <TextField
                            value={condition.value}
                            onChange={(newValue) => handleChange(newValue)}
                        />
                }
            </div>
        </div>
    )
}

export default ConditionComponent
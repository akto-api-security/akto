import { TextField } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import Dropdown from './layouts/Dropdown';
import DropdownSearch from './shared/DropdownSearch';
import func from "@/util/func"
import api from '../pages/testing/api';
import PersistStore from '../../main/PersistStore';

function ConditionComponent(props) {

    const { id, condition, index, param, selectOptions, dispatch } = props

    useEffect(()=>{
        if(condition?.type !== 'CONTAINS') {
            fetchApiEndpoints(condition)
        }
    },[condition])
    const allCollections = PersistStore(state => state.allCollections);
    const activatedCollections = allCollections.filter(collection => collection.deactivated === false)
    const allCollectionsOptions = [
        {
            title: `Search from ${activatedCollections.length} Collection${func.addPlurality(activatedCollections.length)} (type more to refine results)`,
            options: activatedCollections.map(collection => ({
                label: collection.displayName,
                value: collection.id
            }))
        }
    ]
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

    const handleTextChange = (value) => {
        dispatch({ type: "update", index: index, obj: { value: value } })
    };

    const handleTypeSelected = (type) => {
        dispatch({ type: "update", index: index, obj: { type: type } })
    }

    const handleCollectionSelected = (collectionId) => {
        dispatch({ type: "update", index: index, obj: { value: { [collectionId]: [] } } })
    }

    const handleEndpointsSelected = (apiEndpoints, field) => {
        let collectionId = getCollectionId(field);
        if (collectionId) {
            dispatch({ type: "update", index: index, obj: { value: { [collectionId]: apiEndpoints } } })
        }
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
                <Dropdown menuItems={selectOptions} initial={field.type} selected={(type) => handleTypeSelected(type)} />
            </div>
        </div>
    )

    const collectionComponent = (field) => {
        return (
            <div style={{display:"flex", gap:"4px"}}>
                <div style={{flexGrow:"1"}}>
                <DropdownSearch
                    id={`${id}-api-collection-${index}`}
                    placeholder="Select API collection"
                    optionsList={allCollectionsOptions}
                    setSelected={(collectionId) => handleCollectionSelected(collectionId)}
                    preSelected={[Number(getCollectionId(field))]}
                    value={mapCollectionIdToName[getCollectionId(field)]}
                    isNested={true}
                    dynamicTitle={true}
                />
                </div>
                <div style={{flexGrow:"1"}}>
                <DropdownSearch
                    id={`${id}-api-endpoint-${index}`}
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

    const component = (condition) => {
        let type = func.getOption(selectOptions, condition.type).type;

        switch (type) {
            case "MAP": return collectionComponent(condition);
            case "NUMBER": return <TextField id={`${id}-param-text-${index}`} disabled/>;
            default:
                return <TextField
                id={`${id}-param-text-${index}`}
                value={condition.value}
                onChange={(newValue) => handleTextChange(newValue)}
            />
        }
    }

    return (
        <div style={{ display: "flex", gap:"4px" }}>
            <div style={{ flex: "2" }}>
                {prefixLeft(condition)}
            </div>
            <div style={{ flex: "3" }}>
                {component(condition)}
            </div>
        </div>
    )
}

export default ConditionComponent
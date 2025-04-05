import { Button } from '@shopify/polaris';
import { DeleteMinor } from "@shopify/polaris-icons"
import React, { useState, useEffect } from 'react'
import DropdownSearch from './shared/DropdownSearch';
import func from "@/util/func"
import PersistStore from '../../main/PersistStore';
import api from '../pages/testing/api';
import Dropdown from './layouts/Dropdown';

const HTTP_METHODS = [
    {'label': 'GET', 'value': 'GET'},
    {'label': 'POST', 'value': 'POST'},
    {'label': 'PUT', 'value': 'PUT'},
    {'label': 'DELETE', 'value': 'DELETE'},
    {'label': 'HEAD', 'value': 'HEAD'},
    {'label': 'OPTIONS', 'value': 'OPTIONS'},
    {'label': 'TRACE', 'value': 'TRACE'},
    {'label': 'PATCH', 'value': 'PATCH'},
    {'label': 'OTHER', 'value': 'OTHER'},
    {'label': 'TRACK', 'value': 'TRACK'}
]

function CollectionComponent(props) {

    const { condition, index, dispatch, operatorComponent } = props
    const [apiEndpoints, setApiEndpoints] = useState({})

    useEffect(() => {
        fetchApiEndpoints(condition.data)
    }, [condition])

    const allCollections = PersistStore(state => state.allCollections);
    const allCollectionsOptions = allCollections.filter(x => x.type !== "API_GROUP")
        .map(collection => {
            return {
                label: collection.displayName,
                value: collection.id
            }
        })

    const handleCollectionSelected = (collectionId) => {
        dispatch({ type: "overwrite", index: index, key: "data", obj: { [collectionId]: [] } })
    }

    function getCollectionId(data) {
        if (data == undefined || Object.keys(data) == undefined || Object.keys(data)[0] == undefined)
            return undefined;

        if( condition.type != "CUSTOM" ){
            return undefined;
        }

        return Object.keys(data)[0];
    }

    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)

    const handleEndpointsSelected = (apiEndpoints, data) => {
        let collectionId = getCollectionId(data);
        if (collectionId) {
            dispatch({ type: "overwrite", index: index, key: "data", obj: { [collectionId]: apiEndpoints } })
        }
    }

    function getEndpointCount(data) {
        let collectionId = getCollectionId(data);
        if (collectionId == undefined)
            return undefined
        return `${data[collectionId].length} endpoint${data[collectionId].length == 1 ? "" : "s"} selected`;
    }

    function getEndpoints(data) {
        let collectionId = getCollectionId(data);
        if (collectionId == undefined)
            return [];
        return data[collectionId].map((obj) => { return func.toMethodUrlString(obj) })
    }

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

    async function fetchApiEndpoints(value) {
        let collectionId = getCollectionId(value);
        if (collectionId == undefined)
            return []
        const apiEndpointsResponse = await api.fetchCollectionWiseApiEndpoints(collectionId)
        if (apiEndpointsResponse) {
            setApiEndpoints((prev) => {
                if (prev.apiCollectionId == collectionId) {
                    return prev;
                }
                return { apiCollectionId: collectionId, endpoints: getApiEndpointsOptions(apiEndpointsResponse.listOfEndpointsInCollection) }
            })
        }
    }

    function collectionComponent(condition, index) {
        return <div style={{ display: "flex", gap: "4px", flexGrow: "1" }}>
            <div style={{ flex: "3" }}>
                <DropdownSearch
                    id={`api-collection-${index}`}
                    placeholder="Select API collection"
                    optionsList={allCollectionsOptions}
                    setSelected={(collectionId) => handleCollectionSelected(collectionId)}
                    preSelected={[Number(getCollectionId(condition.data))]}
                    value={mapCollectionIdToName[getCollectionId(condition.data)]}
                />
            </div>
            <div style={{ flex: "5" }}>
                <DropdownSearch
                    id={`api-endpoint-${index}`}
                    disabled={apiEndpoints?.endpoints == undefined || apiEndpoints.endpoints.length === 0}
                    placeholder="Select API endpoint"
                    optionsList={apiEndpoints?.endpoints == undefined || typeof apiEndpoints.then == 'function' ? [] :
                        apiEndpoints.endpoints}
                    setSelected={(apiEndpoints) => {
                        handleEndpointsSelected(apiEndpoints.map((obj) => {
                            return func.toMethodUrlObject(obj)
                        }), condition.data)
                    }}
                    preSelected={getEndpoints(condition.data)}
                    itemName={"endpoint"}
                    value={getEndpointCount(condition.data)}
                    allowMultiple
                />
            </div>
        </div>
    }

    function getDefaultValues(type){
        switch(type){
            case "CUSTOM":
                return {}
            case "METHOD":
                return {method:"GET"}
        }
    }

    const prefixLeft = (condition, index) => (
        <Dropdown
            key={`condition-type-${index}`}
            menuItems={[{
                label: 'Api list',
                value: 'CUSTOM',
            },
            {
                label: 'Method',
                value: 'METHOD'
            }]}
            initial={condition.type}
            selected={(value) => {
                dispatch({ type: "overwrite", index: index, key: "data", obj: getDefaultValues(value) })
                dispatch({ type: "updateKey", index: index, key: "type", obj: value })
            }} />
    )

    const component = (condition, index) => {
        switch (condition.type) {
            case "CUSTOM":
                return collectionComponent(condition, index)

            case "METHOD":
                return <>
                    <Dropdown
                        id={`METHOD-${index}`}
                        key={`METHOD-${index}`}
                        menuItems={HTTP_METHODS}
                        initial={condition?.data?.method || "GET"}
                        selected={(value) => {
                            dispatch({ type: "update", index: index, key: "data", obj: { "method": value } })
                        }} />
                </>;
            default: break;
        }
    }

    const handleDelete = (index) => {
        dispatch({ type: "delete", index: index })
    };

    return (
        <div style={{ display: "flex", gap: "4px" }}>
            {operatorComponent}
            {prefixLeft(condition, index)}
            {component(condition, index)}
            <Button icon={DeleteMinor} onClick={() => handleDelete(index)} />
        </div>
    )

}

export default CollectionComponent;
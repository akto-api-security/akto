import React, { useState, useEffect } from 'react'
import DropdownSearch from './shared/DropdownSearch';
import func from "@/util/func"
import PersistStore from '../../main/PersistStore';
import api from '../pages/testing/api';
import Dropdown from './layouts/Dropdown';
import { TextField, Button, Icon } from '@shopify/polaris';
import { DeleteMinor, HashtagMinor } from "@shopify/polaris-icons"
import SingleDate from './layouts/SingleDate';
import DateRangeFilter from './layouts/DateRangeFilter';
import values from "@/util/values";

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

    const { condition, index, dispatch } = props
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

        if( condition.type != "API_LIST" ){
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
        return <div style={{ display: "flex", gap: "4px" }}>
            <div style={{ flexGrow: "1" }}>
                <DropdownSearch
                    id={`api-collection-${index}`}
                    placeholder="Select API collection"
                    optionsList={allCollectionsOptions}
                    setSelected={(collectionId) => handleCollectionSelected(collectionId)}
                    preSelected={[Number(getCollectionId(condition.data))]}
                    value={mapCollectionIdToName[getCollectionId(condition.data)]}
                />
            </div>
            <div style={{ flexGrow: "1" }}>
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
            case "API_LIST":
                return {}
            case "METHOD":
                return {method:"GET"}
            case "PARAM":
                return {paramLocation: "HEADER_REQUEST"}
            case "TIMESTAMP":
                return {key: "lastSeen", type:"START", startTimestamp: new Date() }
        }
    }

    const prefixLeft = (condition, index) => (
        <Dropdown
            key={`condition-type-${index}`}
            menuItems={[{
                label: 'Api list',
                value: 'API_LIST',
            },
            {
                label: 'Method',
                value: 'METHOD'
            },
            {
                label: 'Param',
                value: 'PARAM'
            },
            {
                label: 'Timestamp',
                value: 'TIMESTAMP'
            }]}
            initial={condition.type}
            selected={(value) => {
                dispatch({ type: "overwrite", index: index, key: "data", obj: getDefaultValues(value) })
                dispatch({ type: "updateKey", index: index, key: "type", obj: value })
            }} />
    )

    const timestampComponent = (condition, index) => {
        
        switch(condition.data.type){
            case "START":
                return  <SingleDate
                id={`TIMESTAMP-START-${index}`}
                key={`TIMESTAMP-START-${index}`}
                data = {condition?.data?.startTimestamp}
                dispatch = {dispatch}
                dataKey={"startTimestamp"}
                index = {index}
            />
            case "END":
                return <SingleDate
                id={`TIMESTAMP-END-${index}`}
                key={`TIMESTAMP-END-${index}`}
                data = {condition?.data?.endTimestamp}
                dispatch = {dispatch}
                dataKey={"endTimestamp"}
                index = {index}
            />
            case "BETWEEN":
                return <div style={{width: "fit-content"}}>
                    <DateRangeFilter 
                id={`TIMESTAMP-BETWEEN-${index}`}
                key={`TIMESTAMP-BETWEEN-${index}`}
                initialDispatch = {condition?.data?.period} 
                dispatch={(dateObj) => dispatch({type: "update", index: index, key: "data", 
                obj: { period: dateObj.period, title: dateObj.title, alias: dateObj.alias } })}
                />
                </div>
            case "PERIOD":
                return  <TextField
                id={`TIMESTAMP-PERIOD-${index}`}
                key={`TIMESTAMP-PERIOD-${index}`}
                prefix={<Icon source={HashtagMinor} color="subdued" />}
                placeholder={"No. of days"}
                type="number"
                min={1}
                value={condition?.data?.periodInSeconds != undefined ? condition?.data?.periodInSeconds/86400 : undefined}
                onChange={(value) => {
                    dispatch({ type: "update", index: index, key: "data", obj: { "periodInSeconds": Number(value)*86400 } })
                }}
            />
            default: break; 
        }

    }

    const component = (condition, index) => {
        switch (condition.type) {
            case "API_LIST":
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
            case "TIMESTAMP":
                return <>
                    <Dropdown
                        id={`TIMESTAMP-${index}`}
                        key={`TIMESTAMP-${index}`}
                        menuItems={[{
                            label: 'Last seen',
                            value: 'lastSeen'
                        },{
                            label: 'Discovered',
                            value: 'timestamp',
                        }]}
                        initial={condition?.data?.key || "Last seen"}
                        selected={(value) => {
                            dispatch({ type: "update", index: index, key: "data", obj: { "key": value } })
                        }} />
                    <Dropdown
                        id={`TIMESTAMP-TYPE-${index}`}
                        key={`TIMESTAMP-TYPE-${index}`}
                        menuItems={[{
                            label: 'Start time',
                            value: 'START',
                        },
                        {
                            label: 'End time',
                            value: 'END'
                        },
                        {
                            label: 'Start and end time',
                            value: 'BETWEEN'
                        },{
                            label: 'Last X days',
                            value: 'PERIOD'
                        }]}
                        initial={condition?.data?.type}
                        selected={(value) => {
                            dispatch({ type: "update", index: index, key: "data", obj: { "type": value } })
                            switch(value){
                                case "START":
                                    dispatch({ type: "update", index: index, key: "data", obj: { "startTimestamp": new Date() } })
                                    break;
                                case "END":
                                    dispatch({ type: "update", index: index, key: "data", obj: { "endTimestamp": new Date() } })
                                    break;
                                case "BETWEEN":
                                    dispatch({ type: "update", index: index, key: "data", obj: { "period": values.ranges[3] } })
                                    break;
                                case "PERIOD":
                                    dispatch({ type: "update", index: index, key: "data", obj: { "periodInSeconds": 86400*7 } })
                                    break;
                                default: break;
                            }
                        }} />
                    {timestampComponent(condition, index)}
                </>

            case "PARAM":
                return <>
                    <Dropdown
                        id={`PARAM-LOCATION-${index}`}
                        key={`PARAM-LOCATION-${index}`}
                        menuItems={[{
                            label: 'In request header',
                            value: 'HEADER_REQUEST',
                        },
                        {
                            label: 'In response header',
                            value: 'HEADER_RESPONSE'
                        },
                        {
                            label: 'In request payload',
                            value: 'PAYLOAD_REQUEST'
                        },
                        {
                            label: 'In response payload',
                            value: 'PAYLOAD_RESPONSE'
                        }]}
                        initial={condition?.data?.paramLocation}
                        selected={(value) => {
                            dispatch({ type: "update", index: index, key: "data", obj: { "paramLocation": value } })
                        }} />
                    <TextField
                        id={`PARAM-PARAM-${index}`}
                        key={`PARAM-PARAM-${index}`}
                        placeholder={"param"}
                        value={condition?.data?.param}
                        onChange={(value) => {
                            dispatch({ type: "update", index: index, key: "data", obj: { "param": value } })
                        }}
                    />
                    <TextField
                        id={`PARAM-VALUE-${index}`}
                        key={`PARAM-VALUE-${index}`}
                        placeholder={"value"}
                        value={condition?.data?.value}
                        onChange={(value) => {
                            dispatch({ type: "update", index: index, key: "data", obj: { "value": value } })
                        }}
                    />
                </>
            default: break;
        }
    }

    const handleDelete = (index) => {
        dispatch({ type: "delete", index: index })
    };

    return (
        <div style={{ display: "flex", gap: "4px" }}>
            {prefixLeft(condition, index)}
            {component(condition, index)}
            <Button icon={DeleteMinor} onClick={() => handleDelete(index)} />
        </div>
    )

}

export default CollectionComponent;
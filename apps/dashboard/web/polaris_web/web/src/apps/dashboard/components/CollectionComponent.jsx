import { Button, TextField } from '@shopify/polaris';
import { DeleteMinor } from "@shopify/polaris-icons"
import React, { useState, useEffect } from 'react'
import DropdownSearch from './shared/DropdownSearch';
import func from "@/util/func"
import PersistStore from '../../main/PersistStore';
import api from '../pages/testing/api';
import Dropdown from './layouts/Dropdown';
import { labelMap } from '../../main/labelHelperMap';
import {mapLabel} from '../../main/labelHelper';

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
    const initialRegexText = (condition && condition?.type === 'REGEX') ? (condition?.data?.regex || '') : ''
    const initialHostRegexText = (condition && condition?.type === 'HOST_REGEX') ? (condition?.data?.host_regex || '') : ''
    const initialTagsText = (condition && condition?.type === 'TAGS') ? (condition?.data?.query || '') : ''
    const [regexText, setRegexText] = useState(initialRegexText)
    const [hostRegexText, setHostRegexText] = useState(initialHostRegexText)
    const [tagsText, setTagsText] = useState(initialTagsText)
    const dashboardCategory = PersistStore(state => state.dashboardCategory)

    useEffect(() => {
        fetchApiEndpoints(condition.data)
    }, [condition])

    const allCollections = PersistStore(state => state.allCollections);
    const activatedCollections = allCollections.filter(collection => collection.deactivated === false)
    const allCollectionsOptions = activatedCollections.filter(x => x.type !== "API_GROUP")
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
            let strLabel = func.toMethodUrlString({...apiEndpoint, shouldParse: true});
            let strValue = func.toMethodUrlString({...apiEndpoint, shouldParse: false});

            return {
                id: strValue,
                label: strLabel,
                value: strValue
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
                    placeholder={`Select ${labelMap[dashboardCategory]["API endpoint"]}`}
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
            case "REGEX":
                return {}
            case "HOST_REGEX":
                return {}
            case "TAGS":
                return {}
            default:
                return {}
        }
    }

    const prefixLeft = (condition, index) => (
        <Dropdown
            key={`condition-type-${index}`}
            menuItems={[{
                label: mapLabel(dashboardCategory, 'Api') + ' list',
                value: 'CUSTOM',
            },
            {
                label: 'Method',
                value: 'METHOD'
            },
            {
                label: 'Path matches regex',
                value: 'REGEX'
            },
            {
                label: 'Host name matches regex',
                value: 'HOST_REGEX'
            },
            {
                label: 'Tags',
                value: 'TAGS'
            }
        ]}
            initial={condition.type}
            selected={(value) => {
                dispatch({ type: "overwrite", index: index, key: "data", obj: getDefaultValues(value) })
                dispatch({ type: "updateKey", index: index, key: "type", obj: value })
            }} />
    )

    const handleRegexText = (val) => {
        setRegexText(val)
        dispatch({ type: "overwrite", index: index, key: "data", obj: {"regex":val } })
    }

    const handleHostRegexText = (val) => {
        setHostRegexText(val)
        dispatch({ type: "overwrite", index: index, key: "data", obj: {"host_regex":val } })
    }

    const handleTagsText = (val) => {
        setTagsText(val)
        dispatch({ type: "overwrite", index: index, key: "data", obj: {"query":val } })
    }

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
            case "REGEX":
                return(
                    <TextField onChange={(val) => handleRegexText(val)} value={regexText} />
                )
            case "HOST_REGEX":
                return(
                    <TextField onChange={(val) => handleHostRegexText(val)} value={hostRegexText} />
                )
            case "TAGS":
                return(
                    <TextField onChange={(val) => handleTagsText(val)} value={tagsText} />
                )
            default:
                break;
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
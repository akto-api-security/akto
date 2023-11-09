import React, { useState, useEffect } from 'react'
import DropdownSearch from './shared/DropdownSearch';
import func from "@/util/func"
import PersistStore from '../../main/PersistStore';
import api from '../pages/testing/api';

function CollectionComponent(props) {

    const { data, index, dispatch } = props
    const [apiEndpoints, setApiEndpoints] = useState({})

    useEffect(() => {
        fetchApiEndpoints(data)
    }, [data])

    const allCollections = PersistStore(state => state.allCollections);
    const allCollectionsOptions = allCollections.filter(x => x.type !== "API_GROUP")
        .map(collection => {
            return {
                label: collection.displayName,
                value: collection.id
            }
        })

    const handleCollectionSelected = (collectionId) => {
        dispatch({ type: "update", index: index, obj: { [collectionId]: [] } })
    }

    function getCollectionId(data) {
        if (data == undefined || Object.keys(data) == undefined || Object.keys(data)[0] == undefined)
            return undefined;

        return Object.keys(data)[0];
    }

    const mapCollectionIdToName = func.mapCollectionIdToName(allCollections)

    const handleEndpointsSelected = (apiEndpoints, data) => {
        let collectionId = getCollectionId(data);
        if (collectionId) {
            dispatch({ type: "update", index: index, obj: { [collectionId]: apiEndpoints } })
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

    return (
        <div style={{ display: "flex", gap: "4px" }}>
            <div style={{ flexGrow: "1" }}>
                <DropdownSearch
                    id={`api-collection-${index}`}
                    placeholder="Select API collection"
                    optionsList={allCollectionsOptions}
                    setSelected={(collectionId) => handleCollectionSelected(collectionId)}
                    preSelected={[Number(getCollectionId(data))]}
                    value={mapCollectionIdToName[getCollectionId(data)]}
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
                        }), data)
                    }}
                    preSelected={getEndpoints(data)}
                    itemName={"endpoint"}
                    value={getEndpointCount(data)}
                    allowMultiple
                />
            </div>
        </div>
    )

}

export default CollectionComponent;
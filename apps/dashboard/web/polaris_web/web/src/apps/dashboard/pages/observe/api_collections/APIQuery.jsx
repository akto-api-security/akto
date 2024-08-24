import CollectionComponent from "../../../components/CollectionComponent"
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";
import { VerticalStack, Card, Button, HorizontalStack } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import React, { useState, useReducer, useCallback } from 'react'
import { produce } from "immer"
import ApiEndpoints from "./ApiEndpoints";
import api from "../api"


function APIQuery() {
    const emptyCondition = { data: {}, operator: "AND", type: "CUSTOM" };
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => conditionsReducer(draft, action)), []);
    const [endpointListFromConditions, setEndpointListFromConditions] = useState({})
    const [sensitiveParams, setSensitiveParams] = useState({})


    function conditionsReducer(draft, action) {
        switch (action.type) {
            case "add": draft.push(action.obj); break;
            case "overwrite": draft[action.index][action.key] = {};
            case "update": draft[action.index][action.key] = { ...draft[action.index][action.key], ...action.obj }; break;
            case "updateKey": draft[action.index] = { ...draft[action.index], [action.key]: action.obj }; break;
            case "delete": return draft.filter((item, index) => index !== action.index);
            case "clear": return [];
            default: break;
        }
    }

    const handleAddField = () => {
        dispatchConditions({ type: "add", obj: emptyCondition })
    };

    const handleClearFunction = () => {
        dispatchConditions({ type: "clear" })
        exploreEndpoints()
    }
    function prepareData() {
        let dt = []
        conditions.forEach(condition => {
            if (condition.type == "CUSTOM") {
                let apiList = []
                let collectionId = Object.keys(condition.data)[0]
                if (collectionId != undefined && condition.data[collectionId].length > 0) {
                    condition.data[collectionId].forEach(x =>
                        apiList.push({
                            apiCollectionId: parseInt(collectionId),
                            url: x.url,
                            method: x.method
                        }))
                }
                dt.push({ type: condition.type, operator: condition.operator, data: { apiList: apiList } })
            } else {
                dt.push(condition)
            }
        })
        return dt;
    }

    const exploreEndpoints = async () => {
        let dt = prepareData();
        if (dt.length > 0) {
            let endpointListFromConditions = await api.getEndpointsListFromConditions(dt);
            let sensitiveParams = await api.loadSensitiveParameters(-1);
            if (endpointListFromConditions || sensitiveParams) {
                setEndpointListFromConditions(endpointListFromConditions)
                setSensitiveParams(sensitiveParams)
            }
        } else {
            setEndpointListFromConditions({})
            setSensitiveParams({})
        }
    }

    console.log("endpointListFromConditions", endpointListFromConditions)
    console.log("Object.keys(endpointListFromConditions) > 0", Object.keys(endpointListFromConditions) > 0)
    const components = [<VerticalStack gap="6" key="conditions-component">
        <Card>
            <VerticalStack gap="4">
                {
                    conditions.length > 0 && conditions.map((condition, index) => (
                        <CollectionComponent
                            condition={condition}
                            index={index}
                            dispatch={dispatchConditions}
                            operatorComponent={<OperatorDropdown
                                items={[{
                                    label: 'OR',
                                    value: 'OR',
                                },
                                {
                                    label: 'AND',
                                    value: 'AND'
                                }]}
                                label={condition.operator}
                                selected={(value) => {
                                    dispatchConditions({ type: "updateKey", index: index, key: "operator", obj: value })
                                }} />}
                        />
                    ))
                }
                <HorizontalStack gap={4} align="start">
                    <Button onClick={() => handleAddField()}>Add condition</Button>
                    {
                        conditions.length > 0 ? <Button plain destructive onClick={handleClearFunction}>Clear all</Button> : null
                    }
                </HorizontalStack>
                <HorizontalStack gap={4} align="end">
                    <Button onClick={() => exploreEndpoints()}>Explore endpoints</Button>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    </VerticalStack>,
    <ApiEndpoints key="endpoint-table"
        endpointListFromConditions={endpointListFromConditions}
        sensitiveParamsForQuery={sensitiveParams}
        isQueryPage={true}
    ></ApiEndpoints>
    ]
    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Via explore mode, you can explore api's and create a collection"}
                    titleText={"Explore Mode"}
                />
            }
            primaryAction={<Button id={"explore-mode-query-page"} primary secondaryActions onClick={exploreEndpoints}>Save as collection</Button>}
            isFirstPage={false}
            components={components}
        />
    )
}

export default APIQuery
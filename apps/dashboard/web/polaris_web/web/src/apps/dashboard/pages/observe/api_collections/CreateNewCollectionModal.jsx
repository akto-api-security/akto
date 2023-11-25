import { Text, Modal, TextField, VerticalStack, HorizontalStack, Button, Card } from "@shopify/polaris"
import api from "../api"
import func from "@/util/func"
import CollectionComponent from "../../../components/CollectionComponent";
import React, { useState, useReducer, useCallback } from 'react'
import { produce } from "immer"
import Dropdown from "../../../components/layouts/Dropdown";

function CreateNewCollectionModal(props) {

    const { active, setActive, createCollectionModalActivatorRef, fetchData } = props;

    const [newCollectionName, setNewCollectionName] = useState('');
    const [showApiSelector, setShowApiSelector] = useState(false);

    function prepareData(){
        let dt = []
            conditions.forEach(condition => {
                if (condition.type == "API_LIST") {
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
                } else if (condition.type == "TIMESTAMP") {
                    switch (condition.data.type) {
                        case "START":
                            dt.push({
                                type: condition.type, operator: condition.operator,
                                data: { key: condition.data.key, 
                                    startTimestamp: parseInt(condition.data.startTimestamp.valueOf() / 1000),
                                    endTimestamp: 0,
                                    periodInSeconds: 0
                                }
                            })
                            break;
                        case "END":
                            dt.push({
                                type: condition.type, operator: condition.operator,
                                data: { key: condition.data.key, 
                                    endTimestamp: parseInt(condition.data.endTimestamp.valueOf() / 1000),
                                    startTimestamp: 0,
                                    periodInSeconds: 0 
                                }
                            })
                            break;
                        case "BETWEEN":
                            dt.push({
                                type: condition.type, operator: condition.operator,
                                data: {
                                    key: condition.data.key,
                                    startTimestamp: parseInt(condition.data.period.period.since.valueOf() / 1000),
                                    endTimestamp: parseInt(condition.data.period.period.until.valueOf() / 1000),
                                    periodInSeconds: 0
                                }
                            })
                            break;
                        case "PERIOD":
                            dt.push({
                                type: condition.type, operator: condition.operator,
                                data: { key: condition.data.key, 
                                    endTimestamp: 0,
                                    startTimestamp: 0,
                                    periodInSeconds: condition.data.periodInSeconds 
                                }
                            })
                        default: break;
                    }
                } else if(condition.type == "PARAM") {
                    let isHeader = false;
                    let isRequest = false;
                    if(condition.data.paramLocation.startsWith("HEADER")){
                        isHeader = true;
                    }
                    if(condition.data.paramLocation.endsWith("REQUEST")){
                        isRequest = true;
                    }
                    dt.push({ type: condition.type, operator: condition.operator, 
                        data: { value: condition.data.value, param: condition.data.param,
                            isHeader: isHeader, isRequest: isRequest } })
                } else {
                    dt.push(condition)
                }
            })
        return dt;
    }

    const createNewCollection = async () => {

        if (showApiSelector) {
            let dt = prepareData();
            await api.createCustomCollection(newCollectionName, dt);
        } else {
            await api.createCollection(newCollectionName)
            setNewCollectionName('')
        }
        fetchData()
        setActive(false)
        func.setToast(true, false, "API collection created successfully")
    }

    const handleNewCollectionNameChange =
        useCallback(
            (newValue) => setNewCollectionName(newValue),
            []);

    const emptyCondition = { data: {}, operator: "AND", type: "API_LIST" };
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => conditionsReducer(draft, action)), [emptyCondition]);

    const handleAddField = () => {
        dispatchConditions({ type: "add", obj: emptyCondition })
    };

    function conditionsReducer(draft, action) {
        switch (action.type) {
            case "add": draft.push(action.obj); break;
            case "update": draft[action.index][action.key] = { ...draft[action.index][action.key], ...action.obj }; break;
            case "overwrite": draft[action.index][action.key] = { ...action.obj }; break;
            case "updateKey": draft[action.index] = { ...draft[action.index], [action.key]: action.obj }; break;
            case "delete": return draft.filter((item, index) => index !== action.index);
            case "clear": return [];
            default: break;
        }
    }

    const [apiCount, setApiCount] = useState({});

    const VerifyConditions = async () => {
        let dt = prepareData();
            console.log(dt);
        let res = await api.getEndpointsFromConditions(dt);
        if(res){
            setApiCount(
                {conditions: conditions, apiCount: res.apiCount}
            )
        }
    }

    const ApiCountComponent = () => {
        if(func.deepComparison(apiCount.conditions, conditions)){
            return <Text>
                {`${apiCount.apiCount} endpoints selected with the above condition`}
            </Text>
        } else {
            return <></>
        }
    }

    return (<Modal
        large
        key="modal"
        activator={createCollectionModalActivatorRef}
        open={active}
        onClose={() => setActive(false)}
        title="New collection"
        primaryAction={{
            id: "create-new-collection",
            content: 'Create',
            onAction: createNewCollection,
        }}
        secondaryActions={showApiSelector ? [{
            id: "verify-new-collection",
                content: 'Verify',
                onAction: VerifyConditions,
        }] : []}
    >
        <Modal.Section>
            <VerticalStack gap={3}>
                <TextField
                    id={"new-collection-input"}
                    label="Name"
                    value={newCollectionName}
                    onChange={handleNewCollectionNameChange}
                    autoComplete="off"
                    maxLength="24"
                    suffix={(
                        <Text>{newCollectionName.length}/24</Text>
                    )}
                    autoFocus
                />
                <span>
                    <Button plain onClick={() => setShowApiSelector(!showApiSelector)}>
                        {showApiSelector ? "Create empty collection" : "Add endpoints"}
                    </Button>
                </span>
                {
                    ApiCountComponent()
                }
                {
                    showApiSelector ? <Card background="bg-subdued">
                        <VerticalStack gap={2}>
                            {
                                conditions.length > 0 && conditions.map((condition, index) => (
                                    <div style={{ display: "flex", gap: "4px" }} key={index}>

                                        <div style={{ flex: "1" }}>
                                            <Dropdown
                                                menuItems={[{
                                                    label: 'OR',
                                                    value: 'OR',
                                                },
                                                {
                                                    label: 'AND',
                                                    value: 'AND'
                                                }]}
                                                initial={condition.operator}
                                                selected={(value) => {
                                                    dispatchConditions({ type: "updateKey", index: index, key: "operator", obj: value })
                                                }} />
                                        </div>
                                        <div style={{ flex: "6" }}>
                                            <CollectionComponent
                                                condition={condition}
                                                index={index}
                                                dispatch={dispatchConditions}
                                            />
                                        </div>
                                    </div>
                                ))
                            }
                        <HorizontalStack gap={4} align="start">
                            <Button onClick={() => handleAddField()}>Add condition</Button>
                            <Button plain destructive onClick={() => dispatchConditions({ type: "clear" })}>Clear all</Button>
                        </HorizontalStack>
                        </VerticalStack>
                    </Card> : null
                }
            </VerticalStack>
        </Modal.Section>

    </Modal>)

}

export default CreateNewCollectionModal
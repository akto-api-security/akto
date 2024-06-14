import AktoButton from './../../../components/shared/AktoButton';
import { Text, Modal, TextField, VerticalStack, HorizontalStack, Button, Card } from "@shopify/polaris"
import api from "../api"
import func from "@/util/func"
import CollectionComponent from "../../../components/CollectionComponent";
import React, { useState, useReducer, useCallback } from 'react'
import { produce } from "immer"
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";

function CreateNewCollectionModal(props) {

    const { active, setActive, createCollectionModalActivatorRef, fetchData } = props;

    const [newCollectionName, setNewCollectionName] = useState('');
    const [showApiSelector, setShowApiSelector] = useState(false);

    function prepareData(){
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

    const createNewCollection = async () => {
        if(newCollectionName.length === 0){
            func.setToast(true,true, "Collection name cannot be empty")
            return
        }
        if (showApiSelector) {
            let dt = prepareData();
            await api.createCustomCollection(newCollectionName, dt);
        } else {
            await api.createCollection(newCollectionName)
            setNewCollectionName('')
        }
        fetchData()
        setActive(false)
        func.setToast(true, false, <div data-testid="collection_creation_message">{"API collection created successfully"}</div>)

    }

    const handleNewCollectionNameChange =
        useCallback(
            (newValue) => setNewCollectionName(newValue),
            []);

    const emptyCondition = { data: {}, operator: "AND", type: "CUSTOM" };
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => conditionsReducer(draft, action)), [emptyCondition]);

    const handleAddField = () => {
        dispatchConditions({ type: "add", obj: emptyCondition })
    };

    function conditionsReducer(draft, action) {
        switch (action.type) {
            case "add": draft.push(action.obj); break;
            case "overwrite": draft[action.index][action.key] = { };
            case "update": draft[action.index][action.key] = { ...draft[action.index][action.key], ...action.obj }; break;
            case "updateKey": draft[action.index] = { ...draft[action.index], [action.key]: action.obj }; break;
            case "delete": return draft.filter((item, index) => index !== action.index);
            case "clear": return [];
            default: break;
        }
    }

    const [apiCount, setApiCount] = useState({});

    const VerifyConditions = async () => {
        let dt = prepareData();
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
                    {...newCollectionName.length === 0 ? {error: "Collection name cannot be empty"} : {}}
                />
                <span>
                    <AktoButton plain onClick={() => setShowApiSelector(!showApiSelector)}>
                        {showApiSelector ? "Create empty collection" : "Add endpoints"}
                    </AktoButton>
                </span>
                {
                    ApiCountComponent()
                }
                {
                    showApiSelector ? <Card background="bg-subdued">
                        <VerticalStack gap={2}>
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
                            <AktoButton onClick={() => handleAddField()}>Add condition</AktoButton>
                            <AktoButton plain destructive onClick={() => dispatchConditions({ type: "clear" })}>Clear all</AktoButton>
                        </HorizontalStack>
                        </VerticalStack>
                    </Card> : null
                }
            </VerticalStack>
        </Modal.Section>

    </Modal>)

}

export default CreateNewCollectionModal
import { Text, Modal, TextField, VerticalStack, HorizontalStack, Button, Card } from "@shopify/polaris"
import api from "../api"
import func from "@/util/func"
import CollectionComponent from "../../../components/CollectionComponent";
import React, { useState, useReducer, useCallback, useMemo } from 'react'
import { produce } from "immer"
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";

function CreateNewCollectionModal(props) {

    const { active, setActive, createCollectionModalActivatorRef, fetchData } = props;

    const [newCollectionName, setNewCollectionName] = useState('');
    const [showApiSelector, setShowApiSelector] = useState(false);

    const isCreateButtonDisabled = useMemo(() => {
        return newCollectionName.trim().length === 0;
    }, [newCollectionName]);

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
        fetchData({ current: true }, true) // Force refresh after creating new collection
        setActive(false)
        func.setToast(true, false, <div data-testid="collection_creation_message">{"API collection created successfully"}</div>)

    }

    const handleNewCollectionNameChange =
        useCallback(
            (newValue) => setNewCollectionName(newValue),
            []);

    const emptyCondition = { data: {}, operator: "AND", type: "CUSTOM" };
    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => func.conditionsReducer(draft, action)), [emptyCondition]);

    const handleAddField = () => {
        dispatchConditions({ type: "add", obj: emptyCondition })
    };

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
            disabled: isCreateButtonDisabled
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
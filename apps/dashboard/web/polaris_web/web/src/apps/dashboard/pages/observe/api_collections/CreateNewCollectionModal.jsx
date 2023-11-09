import { Text, Modal, TextField, Link, VerticalStack, HorizontalStack, Button } from "@shopify/polaris"
import api from "../api"
import func from "@/util/func"
import CollectionComponent from "../../../components/CollectionComponent";
import React, { useState, useReducer, useCallback } from 'react'
import { produce } from "immer"
import { DeleteMinor } from "@shopify/polaris-icons"

function CreateNewCollectionModal(props) {

    const { active, setActive, createCollectionModalActivatorRef, fetchData } = props;

    const [newCollectionName, setNewCollectionName] = useState('');
    const [showApiSelector, setShowApiSelector] = useState(false);

    const createNewCollection = async () => {

        if (showApiSelector) {
            let apiList = []
            conditions.forEach(condition => {
                let collectionId = Object.keys(condition)[0]
                if (collectionId != undefined && condition[collectionId].length > 0) {
                    condition[collectionId].forEach(x =>
                        apiList.push({
                            apiCollectionId: Number(collectionId),
                            url: x.url,
                            method: x.method
                        }))
                }
            })
            await api.addApisToCustomCollection(apiList, newCollectionName);
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

    const [conditions, dispatchConditions] = useReducer(produce((draft, action) => conditionsReducer(draft, action)), [{}]);

    const handleDelete = (index) => {
        dispatchConditions({ type: "delete", index: index })
    };

    const handleAddField = () => {
        dispatchConditions({ type: "add", obj: {} })
    };

    function conditionsReducer(draft, action) {
        switch (action.type) {
            case "add": draft.push(action.obj); break;
            case "update": draft[action.index] = { ...draft[action.index], ...action.obj }; break;
            case "delete": return draft.filter((item, index) => index !== action.index);
            case "clear": return [{}];
            default: break;
        }
    }

    return (<Modal
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
            </VerticalStack>
        </Modal.Section>
        {
            showApiSelector ?
                <Modal.Section>
                    <VerticalStack gap={2}>
                        {
                            conditions.length > 0 && conditions.map((condition, index) => (
                                <div style={{ display: "flex", gap: "4px" }} key={index}>
                                    <div style={{ flex: "4" }}>
                                        <CollectionComponent
                                            data={condition}
                                            index={index}
                                            dispatch={dispatchConditions}
                                        />
                                    </div>
                                    <Button icon={DeleteMinor} onClick={() => handleDelete(index)} />
                                </div>
                            ))
                        }
                    </VerticalStack>
                    <br />
                    <HorizontalStack gap={4} align="start">
                        <Button onClick={handleAddField}>Add condition</Button>
                        <Button plain destructive onClick={() => dispatchConditions({ type: "clear" })}>Clear all</Button>
                    </HorizontalStack>
                </Modal.Section> : null
        }

    </Modal>)

}

export default CreateNewCollectionModal
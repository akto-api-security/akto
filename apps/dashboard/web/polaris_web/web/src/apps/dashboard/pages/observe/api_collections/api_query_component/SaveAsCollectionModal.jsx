import { VerticalStack, Text, Modal, TextField } from "@shopify/polaris";
import React, { useState } from 'react'


function SaveAsCollectionModal(props) {
    
    const {createNewCollection, active, setActive, initialCollectionName, loading} = props
    const [newCollectionName, setNewCollectionName] = useState(initialCollectionName || '');
    return (
        <Modal
            large
            key="modal"
            open={active}
            onClose={() => setActive(false)}
            title="New collection"
            primaryAction={{
                id: "create-new-collection",
                content: 'Create',
                onAction: () => createNewCollection(newCollectionName),
                loading: loading || false
            }}
        >
            <Modal.Section>
                <VerticalStack gap={3}>
                    <TextField
                        id={"new-collection-input"}
                        label="Name"
                        value={newCollectionName}
                        onChange={(val) => setNewCollectionName(val)}
                        autoComplete="off"
                        maxLength="24"
                        suffix={(
                            <Text>{newCollectionName.length}/24</Text>
                        )}
                        autoFocus
                        {...newCollectionName.length === 0 ? { error: "Collection name cannot be empty" } : {}}
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default SaveAsCollectionModal
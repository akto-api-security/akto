import { Modal, TextField } from '@shopify/polaris';
import React from 'react'

const APICollectionDescriptionModal = ({ showDescriptionModal, setShowDescriptionModal, title, handleSaveDescription, description, setDescription, placeholder }) => {
    return (
        <Modal
            open={showDescriptionModal}
            onClose={() => setShowDescriptionModal(false)}
            title={title}
            primaryAction={{
                content: 'Save',
                onAction: handleSaveDescription
            }}
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: () => setShowDescriptionModal(false)
                }
            ]}
        >
            <Modal.Section>
                <TextField
                    label="Description"
                    value={description || ""}
                    onChange={(value) => {
                        setDescription(value);
                    }}
                    multiline={4}
                    autoComplete="off"
                    maxLength={64}
                    placeholder={placeholder}
                    helpText={`${(description || "").length}/64 characters`}
                />
            </Modal.Section>
        </Modal>
    )
}

export default APICollectionDescriptionModal
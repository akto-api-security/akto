import { Modal, Text } from '@shopify/polaris'
import React from 'react'

function DeleteModal({showDeleteModal, setShowDeleteModal, SsoType, onAction}) {

    const deleteText = "Are you sure you want to remove " + SsoType + "SSO Integration? This might take away access from existing Akto users. This action cannot be undone."
    return (
        <Modal
            open={showDeleteModal}
            onClose={() => setShowDeleteModal(false)}
            title="Are you sure?"
            primaryAction={{
                content: 'Delete ' + SsoType + ' SSO',
                onAction: onAction
            }}
        >
            <Modal.Section>
                <Text variant="bodyMd">{deleteText}</Text>
            </Modal.Section>
        </Modal>
    )
}

export default DeleteModal
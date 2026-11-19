import { Modal, Text } from '@shopify/polaris'
import React from 'react'
import { whenAllowed } from '@/util/permissions'

function DeleteModal({showDeleteModal, setShowDeleteModal, SsoType, onAction, allowed = true}) {

    const deleteText = "Are you sure you want to remove " + SsoType + "SSO Integration? This might take away access from existing Akto users. This action cannot be undone."
    return (
        <Modal
            open={showDeleteModal}
            onClose={() => setShowDeleteModal(false)}
            title="Are you sure?"
            primaryAction={{
                content: 'Delete ' + SsoType + ' SSO',
                onAction: onAction,
                ...whenAllowed(allowed)
            }}
        >
            <Modal.Section>
                <Text variant="bodyMd">{deleteText}</Text>
            </Modal.Section>
        </Modal>
    )
}

export default DeleteModal
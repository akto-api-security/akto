import React from 'react'
import { Modal, Text } from '@shopify/polaris';
import Store from '../../store';

function ConfirmationModal(props) {

    const confirmationModalConfig = Store(state => state.confirmationModalConfig)
    const setConfirmationModalConfig = Store(state => state.setConfirmationModalConfig)

    const { modalContent, primaryActionContent, primaryAction } = props

    function closeModal(){
        setConfirmationModalConfig({
            show: false,
            modalContent: "",
            primaryActionContent: "",
            primaryAction: {}
        })
    }

    return (
        <Modal
            open={confirmationModalConfig.show}
            onClose={() => closeModal()}
            title="Are you sure ?"
            primaryAction={{
                content: primaryActionContent,
                onAction: () => {
                    primaryAction()
                    closeModal()
                },
                destructive: /delete/i.test(String(primaryActionContent))
            }}
        >
            <Modal.Section>
                <Text>{modalContent}</Text>
            </Modal.Section>
        </Modal>
    )
}

export default ConfirmationModal
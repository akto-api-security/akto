import React from 'react'
import { Modal, Text } from '@shopify/polaris';
import Store from '../../store';

function ConfirmationModal(props) {

    const confirmationModalConfig = Store(state => state.confirmationModalConfig)
    const setConfirmationModalConfig = Store(state => state.setConfirmationModalConfig)

    const {modalTitle, modalContent, primaryActionContent, primaryAction } = props

    function closeModal(){
        setConfirmationModalConfig({
            show: false,
            modalContent: "",
            primaryActionContent: "",
            primaryAction: {}
        })
    }

    return (
        <div className="confirmation-model">
            <Modal
                open={confirmationModalConfig.show}
                onClose={() => closeModal()}
                title={(modalTitle==="")? "Are you sure?" : modalTitle}
                primaryAction={{
                    destructive:(primaryActionContent === "Remove collection")?true:false,
                    content: primaryActionContent,
                    onAction: () => {
                        primaryAction()
                        closeModal()
                    }
                }}
            >
                <Modal.Section>
                    <Text>{modalContent}</Text>
                </Modal.Section>
            </Modal>
        </div>
    )
}

export default ConfirmationModal
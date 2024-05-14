import { Modal, Text } from '@shopify/polaris';
import transform from '../transform';
import React, { useEffect, useState }  from 'react'
import TestingStore from '../testingStore';

function ReRunModal({refreshSummaries, selectedTestRun}) {
    const [localModal, setLocalModal] = useState(false)
    const rerunModal = TestingStore(state => state.rerunModal)
    const setRerunModal = TestingStore(state => state.setRerunModal)

    useEffect(() => {
        setLocalModal(rerunModal || false)
    },[rerunModal])

    const handleClose = () => {
        setLocalModal(false)
        setRerunModal(false)
    }
    
    return (
        <Modal
            open={localModal}
            onClose={handleClose}
            title={"Re-run test"}
            primaryAction={{
                content: "Re-run test",
                onAction: () => {transform.rerunTest(selectedTestRun.id, refreshSummaries) ; handleClose()}
            }}
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: handleClose,
                },
            ]}
        >
            <Modal.Section>
                <Text variant="bodyMd">By clicking Re-run button, the previous the test run summary will be override.<br/>Are you sure you want to re-run test?</Text>
          </Modal.Section>
        </Modal>
    )
}

export default ReRunModal
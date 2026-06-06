import { Modal, Text } from '@shopify/polaris';
import transform from '../transform';
import React, { useEffect, useState }  from 'react'
import TestingStore from '../testingStore';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

function ReRunModal({refreshSummaries, selectedTestRun, shouldRefresh}) {
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
            title={"Re-run " + mapLabel("test", getDashboardCategory())}
            primaryAction={{
                content: "Re-run " + mapLabel("test", getDashboardCategory()),
                onAction: () => {transform.rerunTest(selectedTestRun.id, refreshSummaries, shouldRefresh) ; handleClose()}
            }}
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: handleClose,
                },
            ]}
        >
            <Modal.Section>
                <Text variant="bodyMd">By clicking 'Re-run test' button, the previous test run summary will be overridden.<br/>Are you sure you want to re-run test?</Text>
          </Modal.Section>
        </Modal>
    )
}

export default ReRunModal
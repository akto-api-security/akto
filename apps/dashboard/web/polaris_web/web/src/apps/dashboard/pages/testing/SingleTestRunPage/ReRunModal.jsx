import { Modal, Text } from '@shopify/polaris';
import { transform } from 'lodash';
import React, { useState } from 'react'

function ReRunModal({selectedTestRun, refreshSummaries}) {
    const [rerunModal, setRerunModal] = useState(false)
    return (
        <Modal
            open={rerunModal}
            onClose={setRerunModal(false)}
            title={"Re-run test"}
            primaryAction={{
                content: "Re-run test",
                onAction: () => {transform.rerunTest(selectedTestRun.id, refreshSummaries) ; setRerunModal(false)}
            }}
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: setRerunModal(false),
                },
            ]}
        >
          <Text variant="bodyMd">By clicking Re-run button, the previous the test run summary will be override.<br/>Are you sure you want to re-run test?</Text>
        </Modal>
    )
}

export default ReRunModal
import { Modal } from '@shopify/polaris'
import React, { useState } from 'react'
import { useAgentsStore } from '../../agents.store'

function SourceCodeAnalyserCTA() {
    const { finalCTAShow, setFinalCTAShow } = useAgentsStore()
    const { setCurrentAgent} = useAgentsStore()
    return (
       <Modal
            title={"Save the apis with schema into a collection"}
            open={finalCTAShow}
            onClose={() => setFinalCTAShow(false)}
            primaryAction={{
                content: 'Save apis',
                onAction: () => {} /* setCurrentAgent as source code agent here */
            }}
            secondaryActions={[{
                content: 'Cancel',
                onAction: () => setFinalCTAShow(false)
            }]}
        >
            <Modal.Section>
                <p>
                    You need to have APIs from Source code analyzer agent on this directory first. Please get the APIs from the agent.
                </p>
            </Modal.Section>
        </Modal>
    )
}

export default SourceCodeAnalyserCTA
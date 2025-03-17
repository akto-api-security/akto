import { Modal } from '@shopify/polaris'
import React, { useState } from 'react'
import { useAgentsStore } from '../../agents.store'

function APISRequiredCTA() {
    const [ show, setShow] = useState<boolean>(true)
    const { setCurrentAgent} = useAgentsStore()
    return (
       <Modal
            title={"APIs required"}
            open={show}
            onClose={() => setShow(false)}
            primaryAction={{
                content: 'Get APIs',
                onAction: () => {setShow(false); setCurrentAgent(null)} /* setCurrentAgent as source code agent here */
            }}
            secondaryActions={[{
                content: 'Cancel',
                onAction: () => setShow(false)
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

export default APISRequiredCTA
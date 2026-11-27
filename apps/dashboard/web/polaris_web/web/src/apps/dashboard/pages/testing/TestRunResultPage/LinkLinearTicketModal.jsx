import { Modal, Text, TextField } from '@shopify/polaris'
import React, { useState } from 'react'

const LinkLinearTicketModal = ({ activator, modalActive, setModalActive, handleLinkAction }) => {

    const [ticketRef, setTicketRef] = useState('')

    const closeModal = () => {
        setTicketRef('')
        setModalActive(false)
    }

    return (
        <Modal
            activator={activator}
            open={modalActive}
            onClose={closeModal}
            size="small"
            title={<Text variant="headingMd">Link existing Linear ticket</Text>}
            primaryAction={{
                content: 'Link ticket',
                onAction: () => {
                    handleLinkAction(ticketRef.trim())
                    closeModal()
                },
                disabled: ticketRef.trim().length === 0
            }}
        >
            <Modal.Section>
                <TextField
                    label="Linear ticket URL or key"
                    placeholder="https://linear.app/workspace/issue/ENG-123 or ENG-123"
                    value={ticketRef}
                    onChange={setTicketRef}
                    autoComplete="off"
                    helpText="The ticket must belong to a team added in the Linear integration."
                />
            </Modal.Section>
        </Modal>
    )
}

export default LinkLinearTicketModal

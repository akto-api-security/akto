import { LegacyStack, Modal, Text, TextField } from '@shopify/polaris'
import React, { useState } from 'react'
import func from '@/util/func'
import homeRequests from "../pages/home/api"

const WelcomeBackDetailsModal = ({ isAdmin }) => {
    const [modalToggle, setModalToggle] = useState(window.SHOULD_ASK_WELCOME_DETAILS === 'true')
    
    const [username, setUsername] = useState("")
    const [organization, setOrganization] = useState("")

    const handleWelcomeBackDetails = async () => {
        if(!isAdmin && organization.length > 0) return
        if(!username || (isAdmin && !organization)) {
            func.setToast(true, true, "Please enter all details.")
            return
        }

        const email = window.USER_NAME

        await homeRequests.updateUsernameAndOrganization(email ,username, organization)

        setModalToggle(false)
    }

    return (
        <Modal
            size="small"
            open={modalToggle}
            title="Welcome back"
            onClose={() => {func.setToast(true, false, "You cannot skip this process.")}}
            primaryAction={{
                content: 'Save',
                onAction: handleWelcomeBackDetails
            }}
        >
            <Modal.Section>
                <LegacyStack vertical>
                    <Text variant="bodyMd" color="subdued">
                        Tell us more about yourself.
                    </Text>

                    <TextField
                        label="Name"
                        value={username}
                        placeholder="Joe doe"
                        onChange={setUsername}
                        autoComplete="off"
                    />

                    <TextField
                        label="Organization"
                        disabled={!isAdmin}
                        value={organization}
                        placeholder="Acme Inc"
                        onChange={setOrganization}
                        autoComplete="off"
                    />
                </LegacyStack>
            </Modal.Section>
        </Modal>
    )
}

export default WelcomeBackDetailsModal
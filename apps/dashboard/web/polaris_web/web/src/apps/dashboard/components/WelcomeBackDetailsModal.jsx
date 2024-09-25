import { Modal, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import func from '@/util/func'
import homeRequests from "../pages/home/api"

const WelcomeBackDetailsModal = ({ isAdmin }) => {
    const [modalToggle, setModalToggle] = useState(true)

    const [username, setUsername] = useState(window.USER_FULL_NAME)
    const [organization, setOrganization] = useState(window.ORGANIZATION_NAME)

    const handleWelcomeBackDetails = async () => {
        if(username.length > 24) {
            func.setToast(true, true, "Username can't be longer than 24 characters")
            return
        }
        if(organization.length > 24) {
            func.setToast(true, true, "Organization name can't be longer than 24 characters")
            return
        }

        const nameReg = /^[\w\s-]{1,}$/;
        const orgReg = /^[\w\s.&-]{1,}$/;
        if(!nameReg.test(username.trim()) || (isAdmin && !orgReg.test(organization.trim()))) {
            func.setToast(true, true, "Please enter valid details.")
            return
        }

        const email = window.USER_NAME

        homeRequests.updateUsernameAndOrganization(email ,username, organization).then((resp) => {
            try {
                setModalToggle(false)
            } catch (error) {
                func.setToast(true, true, "Invalid username or organization name")
            }
        })
    }

    return (
        <Modal
            size="small"
            open={modalToggle}
            title={<div className='welcome-back-modal-title'>Welcome back</div>}
            primaryAction={{
                content: 'Save',
                onAction: () => handleWelcomeBackDetails()
            }}
        >
            <Modal.Section>
                <VerticalStack gap={4}>
                    <Text variant="bodyMd" color="subdued">
                        Tell us more about yourself.
                    </Text>

                    <TextField
                        label="Name"
                        value={username}
                        disabled={window.USER_FULL_NAME.length !== 0}
                        placeholder="Joe doe"
                        onChange={setUsername}
                        autoComplete="off"
                    />

                    {
                        isAdmin && <TextField
                            label="Organization name"
                            disabled={!isAdmin || window.ORGANIZATION_NAME.length !== 0}
                            value={organization}
                            placeholder="Acme Inc"
                            onChange={setOrganization}
                            autoComplete="off"
                        />
                    }
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default WelcomeBackDetailsModal
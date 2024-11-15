import { Modal, Text, TextField, BlockStack } from '@shopify/polaris'
import React, { useState } from 'react'
import func from '@/util/func'
import homeRequests from "../pages/home/api"

const WelcomeBackDetailsModal = ({ isAdmin }) => {
    const [modalToggle, setModalToggle] = useState(true)

    const [username, setUsername] = useState(window.USER_FULL_NAME || "")
    const [organization, setOrganization] = useState(window.ORGANIZATION_NAME || "")

    const handleWelcomeBackDetails = async () => {

        const nameReg = /^[\w\s-]{1,}$/;
        const orgReg = /^[\w\s.&-]{1,}$/;
        if(!nameReg.test(username.trim()) || (isAdmin && !orgReg.test(organization.trim()))) {
            func.setToast(true, true, "Please enter valid details.")
            return
        }

        homeRequests.updateUsernameAndOrganization(username, organization).then((resp) => {
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
                <BlockStack gap={4}>
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
                        maxLength="24"
                        suffix={(
                            <Text>{username.length}/24</Text>
                        )}
                    />
                    {
                        isAdmin && <TextField
                            label="Organization name"
                            disabled={!isAdmin || window.ORGANIZATION_NAME.length !== 0}
                            value={organization}
                            placeholder="Acme Inc"
                            onChange={setOrganization}
                            autoComplete="off"
                            maxLength={"24"}
                            suffix={(
                                <Text>{organization.length}/24</Text>
                            )}
                        />
                    }
                </BlockStack>
            </Modal.Section>
        </Modal>
    );
}

export default WelcomeBackDetailsModal
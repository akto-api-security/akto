import { Modal, Text, TextField } from "@shopify/polaris"
import { useState, useRef } from "react"
import func from "@/util/func"
import Store from "../../../store"
import settingRequests from "../api"

const InviteUserModal = ({ inviteUser, setInviteUser, toggleInviteUserModal }) => {
    const setToastConfig = Store(state => state.setToastConfig)
    const ref = useRef(null)
    const [inviteEmail, setInviteEmail] = useState()

    const handleSendInvitation = async () => {
        setInviteUser(previousState => ({
            ...previousState,
            state: "loading",
            email: inviteEmail
        }
        ))

        const spec = {
            inviteeName: "there",
            inviteeEmail: inviteEmail,
            websiteHostName: window.location.origin
        }

        const inviteUsersResponse = await settingRequests.inviteUsers(spec)

        setInviteUser(previousState => ({
            ...previousState,
            state: "success",
            inviteLink: inviteUsersResponse.finalInviteCode
        }
        ))

        setToastConfig({
            isActive: true,
            isError: false,
            message: "User invitation sent successfully"
        })

        setInviteEmail("")
    }

    const handleCopyInvitation = () => {
        func.copyToClipboard(inviteUser.inviteLink, ref, "Invitation link copied to clipboard")
    }

    if (inviteUser.state !== "success") {
        return (
            <Modal
                small
                open={inviteUser.isActive}
                onClose={toggleInviteUserModal}
                title="Add team member"
                primaryAction={{
                    loading: inviteUser.state === "loading",
                    content: 'Send invitation',
                    onAction: handleSendInvitation,
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: toggleInviteUserModal,
                    },
                ]}
            >
                <Modal.Section>
                    <TextField
                        label="Account email"
                        value={inviteEmail}
                        placeholder="name@workemail.com"
                        onChange={(email) => setInviteEmail(email)}
                        autoComplete="off"
                    />
                    <Text variant="bodyMd" color="subdued">
                        We'll use this address if we need to contact you about your account.
                    </Text>

                </Modal.Section>
            </Modal>
        )
    } else {
        return (
            <Modal
                small
                open={inviteUser.isActive}
                onClose={toggleInviteUserModal}
                title="Add team member"
                primaryAction={{
                    content: 'Copy invitation',
                    onAction: handleCopyInvitation,
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: toggleInviteUserModal,
                    },
                ]}
            >

                <Modal.Section>
                    <TextField
                        label="Invite link"
                        disabled={true}
                        value={inviteUser.inviteLink}
                    />
                     <Text variant="bodyMd" color="subdued">
                        Alternatively, you can copy the invite link and share it with your invitee directly.
                    </Text>
                    <div ref={ref} />
                </Modal.Section>
            </Modal>
        )
    }
}

export default InviteUserModal
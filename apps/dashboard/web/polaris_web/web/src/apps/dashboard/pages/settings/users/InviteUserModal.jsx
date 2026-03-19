import { Modal, Text, TextField, Box, Checkbox } from "@shopify/polaris"
import { useState, useRef, useCallback, useEffect } from "react"
import func from "@/util/func"
import Store from "../../../store"
import settingRequests from "../api"
import Dropdown from "../../../components/layouts/Dropdown"

const PRODUCT_SCOPES = [
    { label: 'API Security', value: 'API' },
    { label: 'Akto ARGUS', value: 'AGENTIC' },
    { label: 'Akto ATLAS', value: 'ENDPOINT' },
    { label: 'DAST', value: 'DAST' },
];

const InviteUserModal = ({ inviteUser, setInviteUser, toggleInviteUserModal, roleHierarchy, rolesOptions, defaultInviteRole}) => {

    const setToastConfig = Store(state => state.setToastConfig)
    const ref = useRef(null)
    const [inviteEmail, setInviteEmail] = useState()
    const [inviteRole, setInviteRole] = useState(defaultInviteRole)
    const [selectedProductScopes, setSelectedProductScopes] = useState(["API"])

    useEffect(() => {
        setInviteRole(defaultInviteRole)
        setSelectedProductScopes(["API"])
    }, [defaultInviteRole])

    const handleRoleSelectChange = useCallback(
        (value) => {
            setInviteRole(value)
        },
        [],
    );

    const handleProductScopeToggle = (scope) => {
        setSelectedProductScopes(prevScopes => {
            const newScopes = prevScopes.includes(scope)
                ? prevScopes.filter(s => s !== scope)
                : [...prevScopes, scope]
            return newScopes.length > 0 ? newScopes : ["API"]
        })
    }

    const handleSendInvitation = async () => {
        setInviteUser(previousState => ({
            ...previousState,
            state: "loading",
            email: inviteEmail
        }
        ))

        const spec = {
            inviteeName: "there",
            inviteeEmail: inviteEmail?.toLowerCase(),
            websiteHostName: window.location.origin,
            inviteeRole: inviteRole,
            productScopes: selectedProductScopes
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
        setInviteRole(defaultInviteRole)
        setSelectedProductScopes(["API"])
    }

    const handleCopyInvitation = () => {
        func.copyToClipboard(inviteUser.inviteLink, ref, "Invitation link copied to clipboard")
    }

    const filteredRoleOptions = rolesOptions[0].items.map((c) => {
        return{
            label: c?.content,
            value: c?.role,
        }
    }).filter((c) => roleHierarchy.includes(c.value))
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

                    <Dropdown
                        id={"inviteRoleSelection"}
                        selected={handleRoleSelectChange}
                        menuItems={filteredRoleOptions}
                        initial={inviteRole} />

                    <Text variant="bodyMd" color="subdued" as="p" style={{ marginTop: "20px" }}>
                        Product Scopes
                    </Text>
                    <Box padding="400">
                        {PRODUCT_SCOPES.map((scope) => (
                            <Box key={scope.value} padding="200">
                                <Checkbox
                                    label={scope.label}
                                    checked={selectedProductScopes.includes(scope.value)}
                                    onChange={() => handleProductScopeToggle(scope.value)}
                                />
                            </Box>
                        ))}
                    </Box>
                    <Text variant="bodySm" color="subdued">
                        Select which product scopes this user should have access to. If none selected, defaults to API Security.
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
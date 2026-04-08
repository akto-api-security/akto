import { Modal, Text, TextField, Box, Checkbox, HorizontalStack } from "@shopify/polaris"
import { useState, useRef, useCallback, useEffect, useMemo } from "react"
import func from "@/util/func"
import Store from "../../../store"
import settingRequests from "../api"
import Dropdown from "../../../components/layouts/Dropdown"

/**
 * Gets available product scopes based on user's feature access.
 * Maps feature flags to product scopes:
 * - API Security: always available (default)
 * - Akto ARGUS: requires SECURITY_TYPE_AGENTIC feature
 * - Akto ATLAS: requires ENDPOINT_SECURITY feature
 * - DAST: requires AKTO_DAST feature
 */
const getAvailableProductScopes = () => {
    const { agenticSecurityGranted, endpointSecurityGranted, dastGranted } = func.getStiggFeatureGrants()

    const scopes = [
        { label: 'API Security', value: 'API' } // Always available
    ]

    // Add scopes based on feature access
    if (agenticSecurityGranted) {
        scopes.push({ label: 'Akto ARGUS', value: 'AGENTIC' })
    }

    if (endpointSecurityGranted) {
        scopes.push({ label: 'Akto ATLAS', value: 'ENDPOINT' })
    }

    if (dastGranted) {
        scopes.push({ label: 'DAST', value: 'DAST' })
    }

    return scopes
}

const InviteUserModal = ({ inviteUser, setInviteUser, toggleInviteUserModal, roleHierarchy, rolesOptions, defaultInviteRole}) => {

    // Get available scopes based on user's feature access
    const availableScopes = useMemo(() => getAvailableProductScopes(), [])

    const setToastConfig = Store(state => state.setToastConfig)
    const ref = useRef(null)
    const [inviteEmail, setInviteEmail] = useState()
    const [scopeRoleMapping, setScopeRoleMapping] = useState({})

    useEffect(() => {
        // Reset when modal opens/modal state changes
        if (inviteUser.isActive) {
            setScopeRoleMapping({})
        }
    }, [inviteUser.isActive])

    const handleScopeToggle = (scope) => {
        setScopeRoleMapping(prevMapping => {
            const newMapping = { ...prevMapping }
            if (newMapping[scope]) {
                delete newMapping[scope]
            } else {
                newMapping[scope] = defaultInviteRole
            }
            return newMapping
        })
    }

    const handleScopeRoleChange = (scope, role) => {
        setScopeRoleMapping(prevMapping => {
            const updated = {
                ...prevMapping,
                [scope]: role
            }
            return updated
        })
    }

    const handleSendInvitation = async () => {
        // Ensure we have at least one scope selected with a role
        const selectedScopes = Object.keys(scopeRoleMapping || {})

        if (selectedScopes.length === 0) {
            setToastConfig({
                isActive: true,
                isError: true,
                message: "Please select at least one product scope and assign a role"
            })
            return
        }

        // Verify all selected scopes have roles
        const allHaveRoles = selectedScopes.every(scope => scopeRoleMapping[scope] && scopeRoleMapping[scope].trim())
        if (!allHaveRoles) {
            setToastConfig({
                isActive: true,
                isError: true,
                message: "Please assign a role to each selected scope"
            })
            return
        }

        if (!inviteEmail) {
            setToastConfig({
                isActive: true,
                isError: true,
                message: "Please enter an email address"
            })
            return
        }

        setInviteUser(previousState => ({
            ...previousState,
            state: "loading",
            email: inviteEmail
        }))

        const spec = {
            inviteeName: "there",
            inviteeEmail: inviteEmail.toLowerCase(),
            websiteHostName: window.location.origin,
            scopeRoleMapping: scopeRoleMapping
        }

        try {
            const inviteUsersResponse = await settingRequests.inviteUsers(spec)

            setInviteUser(previousState => ({
                ...previousState,
                state: "success",
                inviteLink: inviteUsersResponse.finalInviteCode
            }))

            setToastConfig({
                isActive: true,
                isError: false,
                message: "User invitation sent successfully"
            })

            setInviteEmail("")
            setScopeRoleMapping({})
        } catch (error) {
            setInviteUser(previousState => ({
                ...previousState,
                state: "initial",
                email: inviteEmail
            }))

            setToastConfig({
                isActive: true,
                isError: true,
                message: error.response?.data?.actionErrors?.[0] || "Failed to send invitation"
            })
            throw error
        }
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

                    <Text variant="bodyMd" color="subdued" as="p" style={{ marginTop: "20px" }}>
                        Product Scope Access
                    </Text>
                    <Text variant="bodySm" color="subdued" as="p" style={{ marginBottom: "15px" }}>
                        Select product scopes and assign a role for each. Different roles can be assigned to different scopes.
                    </Text>
                    <Box padding="400">
                        {availableScopes.map((scope) => (
                            <Box
                                key={scope.value}
                                style={{
                                    marginBottom: "12px",
                                    borderBottom: "1px solid #e5e5e5",
                                    paddingBottom: "12px",
                                    display: "flex",
                                    alignItems: "center",
                                    gap: "16px"
                                }}
                            >
                                <Checkbox
                                    label=""
                                    checked={Object.keys(scopeRoleMapping).includes(scope.value)}
                                    onChange={() => handleScopeToggle(scope.value)}
                                />
                                <Text variant="bodyMd" style={{ minWidth: "120px", flexShrink: 0 }}>
                                    {scope.label}
                                </Text>
                                {Object.keys(scopeRoleMapping).includes(scope.value) && (
                                    <Box style={{ marginLeft: "80px" }}>
                                        <Dropdown
                                            id={`role-${scope.value}`}
                                            selected={(value) => handleScopeRoleChange(scope.value, value)}
                                            menuItems={filteredRoleOptions}
                                            initial={scopeRoleMapping[scope.value]}
                                        />
                                    </Box>
                                )}
                            </Box>
                        ))}
                    </Box>

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
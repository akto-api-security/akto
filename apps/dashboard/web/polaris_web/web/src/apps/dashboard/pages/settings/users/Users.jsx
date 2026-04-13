import {  Avatar, Banner, Box, Button, HorizontalStack, Icon, LegacyCard, Link, Page, ResourceItem, ResourceList, Text, Modal, TextField, Checkbox, VerticalStack } from "@shopify/polaris"
import { DeleteMajor, PasskeyMajor } from "@shopify/polaris-icons"
import { useEffect, useState, useRef, useMemo } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import InviteUserModal from "./InviteUserModal";
import Dropdown from "../../../components/layouts/Dropdown";
import PersistStore from "../../../../main/PersistStore";
import SearchableResourceList from "../../../components/shared/SearchableResourceList";
import ResourceListModal from "../../../components/shared/ResourceListModal";
import observeApi from "../../observe/api";
import { usersCollectionRenderItem } from "../rbac/utils";

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

const Users = () => {
    // Get available scopes based on user's feature access
    const PRODUCT_SCOPES = useMemo(() => getAvailableProductScopes(), [])
    const username = window.USER_NAME
    const userRole = window.USER_ROLE

    const [inviteUser, setInviteUser] = useState({
        isActive: false,
        state: "initial", // initial, loading, success
        email: "",
        inviteLink: "",
    })

    const [loading, setLoading] = useState(false)
    const [users, setUsers] = useState([])
    const [usersCollection, setUsersCollection] = useState([])
    const [roleHierarchy, setRoleHierarchy] = useState([])
    const [allCollections, setAllCollections] = useState([])
    let rbacAccess = func.checkForRbacFeatureBasic();
    let rbacAccessAdvanced =  func.checkForRbacFeature()

    const collectionsMap = PersistStore(state => state.collectionsMap)

    const [selectedItems, setSelectedItems] = useState({})

    const handleSelectedItems = (id, items) => {
        setSelectedItems(prevSelectedItems => ({
            ...prevSelectedItems,
            [id]: items
        }));
    }
    const [passwordResetState, setPasswordResetState] = useState({
        passwordResetLogin: "",
        confirmPasswordResetActive: false,
        passwordResetLinkActive: false,
        passwordResetLink: ""
    })

    const setPasswordResetStateHelper = (field, value) => {
        setPasswordResetState(prevState => ({
            ...prevState,
            [field]: value
        }))
    }

    const [editScopeRoleModal, setEditScopeRoleModal] = useState({
        isActive: false,
        userId: null,
        email: "",
        name: "",
        currentRole: "",
        currentScopeRoleMapping: {},
        editingScopeRoleMapping: {},
        isSimpleRole: false // true if user only has simple role, false if has scopeRoleMapping
    })

    const ref = useRef(null)

    const resetPassword = async () => {
        await settingRequests.resetUserPassword(passwordResetState.passwordResetLogin).then((resetPasswordLink) => {
            setPasswordResetStateHelper("passwordResetLinkActive", true)
            setPasswordResetStateHelper("passwordResetLink", resetPasswordLink)
        })
    }

    const closePasswordResetToggle = () => {
        setPasswordResetStateHelper("passwordResetLinkActive", false)
        setPasswordResetStateHelper("confirmPasswordResetActive", false)
        setPasswordResetStateHelper("passwordResetLink", "")
    }

    const handleCopyPasswordResetLink = () => {
        func.copyToClipboard(passwordResetState.passwordResetLink, ref, "Password reset link copied to clipboard")
    }

    const [customRoles, setCustomRoles] = useState([])
    const [defaultInviteRole, setDefaultInviteRole] = useState('MEMBER')

    let paidFeatureRoleOptions =  rbacAccess ? [
        {
            content: 'Developer',
            role: 'DEVELOPER',
        },
        {
            content: 'Guest',
            role: 'GUEST',
        },
        {
            content: 'Threat Engineer',
            role: 'THREAT_ENGINEER',
        },
        {
            content: 'Threat Viewer',
            role: 'THREAT_VIEWER',
        }, ...customRoles
    ] : []

    const websiteHostName = window.location.origin
    const notOnPremHostnames = ["app.akto.io", "localhost", "127.0.0.1", "[::1]"]
    const isOnPrem = websiteHostName && !notOnPremHostnames.includes(window.location.hostname)

    let rolesOptions = [
        {
            items: [
            {
                content: 'Admin',
                role: 'ADMIN',
            },
            {
                content: 'Member',
                role: 'MEMBER',
            }, ...paidFeatureRoleOptions,
            {
                content: 'No Access',
                role: 'NO_ACCESS',
            }]
        },
        {
            items: [
                isOnPrem && {
                    destructive: false,
                    content: 'Reset Password',
                    role: 'RESET_PASSWORD',
                    icon: PasskeyMajor
                },
                {
                    destructive: true,
                    content: 'Remove',
                    role: 'REMOVE',
                    icon: DeleteMajor
                }
            ]
        }
    ]

    const getRoleHierarchy = async() => {
        let roleHierarchyResp = await settingRequests.getRoleHierarchy()
        if(roleHierarchyResp.includes("MEMBER")){
            roleHierarchyResp.push("SECURITY ENGINEER")
        }
        if(window.USER_ROLE === 'ADMIN'){
            roleHierarchyResp.push('REMOVE')
            roleHierarchyResp.push('RESET_PASSWORD')
        }

        const customRolesResponse = await settingRequests.getCustomRoles()
        if(customRolesResponse.roles){
            setCustomRoles(customRolesResponse.roles.map(x => {

                if(roleHierarchyResp.includes(x.baseRole)){
                    roleHierarchyResp.push(x.name)
                }
                if(x.defaultInviteRole){
                    setDefaultInviteRole(x.name)
                }

                return {
                    content: x.name,
                    role: x.name
                }
            }))
        }

        setRoleHierarchy(roleHierarchyResp)

    }

    useEffect(() => {
        if(userRole !== 'GUEST') {
            getTeamData();
        }
        getRoleHierarchy()

        setAllCollections(Object.entries(collectionsMap).map(([id, collectionName]) => ({
            id: parseInt(id, 10),
            collectionName
        })));
    }, [])

    const getRoleDisplayName = (role) => {
        for(let section of rolesOptions) {
            for(let item of section.items) {
                if(item.role === role) {
                    return item.content;
                }
            }
        }
        return role;
    }

    const getTeamData = async () => {
        setLoading(true);
        const usersResponse = await settingRequests.getTeamData()
        if(userRole === 'ADMIN') {
            const usersCollectionList = await observeApi.getAllUsersCollections()
            setUsersCollection(usersCollectionList)
        }
        setUsers(usersResponse)
        setLoading(false)
    };

    const isLocalDeploy = func.checkLocal();

    const toggleInviteUserModal = () => {
        setInviteUser({
            isActive: !inviteUser.isActive,
            state: "initial",
            email: "",
            inviteLink: ""
        })
    }

    const handleRemoveUser = async (login) => {
        await settingRequests.removeUser(login)
        func.setToast(true, false, "User removed successfully")
    }

    const getRoleDisplayForSidebar = (scopeRoleMapping, oldRole) => {
        // If scopeRoleMapping exists, show all scopes with their roles (same as left side)
        if (scopeRoleMapping && Object.keys(scopeRoleMapping).length > 0) {
            const rolesWithScopes = []
            Object.entries(scopeRoleMapping).forEach(([scope, role]) => {
                // Skip NO_ACCESS roles
                if (role !== 'NO_ACCESS') {
                    const scopeLabel = PRODUCT_SCOPES.find(s => s.value === scope)?.label || scope
                    rolesWithScopes.push(`${getRoleDisplayName(role)} (${scopeLabel})`)
                }
            })
            // If all are NO_ACCESS, show first one
            if (rolesWithScopes.length === 0) {
                const firstScope = Object.keys(scopeRoleMapping)[0]
                const firstRole = scopeRoleMapping[firstScope]
                const scopeLabel = PRODUCT_SCOPES.find(s => s.value === firstScope)?.label || firstScope
                return `${getRoleDisplayName(firstRole)} (${scopeLabel})`
            }
            return rolesWithScopes.join(', ')
        }
        // Fallback to old role
        return getRoleDisplayName(oldRole)
    }

    const openEditScopeRoleModal = (userId, email, name, currentRole, currentScopeRoleMapping) => {
        const isSimpleRole = !currentScopeRoleMapping || Object.keys(currentScopeRoleMapping).length === 0
        setEditScopeRoleModal({
            isActive: true,
            userId,
            email,
            name,
            currentRole,
            currentScopeRoleMapping: currentScopeRoleMapping || {},
            editingScopeRoleMapping: currentScopeRoleMapping ? { ...currentScopeRoleMapping } : {},
            isSimpleRole
        })
    }

    const closeEditScopeRoleModal = () => {
        setEditScopeRoleModal({
            isActive: false,
            userId: null,
            email: "",
            name: "",
            currentRole: "",
            currentScopeRoleMapping: {},
            editingScopeRoleMapping: {},
            isSimpleRole: false
        })
    }

    const handleScopesToggleInModal = (scope) => {
        setEditScopeRoleModal(prev => {
            const newMapping = { ...prev.editingScopeRoleMapping }
            if (newMapping[scope]) {
                delete newMapping[scope]
            } else {
                // Add scope with current role or default role
                newMapping[scope] = prev.currentRole || "MEMBER"
            }
            return { ...prev, editingScopeRoleMapping: newMapping }
        })
    }

    const handleScopeRoleChangeInModal = (scope, role) => {
        setEditScopeRoleModal(prev => ({
            ...prev,
            editingScopeRoleMapping: {
                ...prev.editingScopeRoleMapping,
                [scope]: role
            }
        }))
    }

    const saveEditedScopeRoleMapping = async () => {
        const { email, editingScopeRoleMapping } = editScopeRoleModal

        try {
            // Call backend to update scope-role mapping
            await settingRequests.updateUserScopeRoleMapping(email, editingScopeRoleMapping)

            // Update UI
            const scopes = Object.keys(editingScopeRoleMapping)
            setUsers(users.map(user =>
                user.login === email
                    ? { ...user, scopeRoleMapping: editingScopeRoleMapping, productScopes: scopes }
                    : user
            ))
            func.setToast(true, false, "User access updated successfully")
            closeEditScopeRoleModal()
        } catch (error) {
            func.setToast(true, true, "Failed to update user access")
            console.error(error)
        }
    }

   
    const getUserApiCollectionIds = (userId) => {
        return usersCollection[userId] || [];
    };

    const handleRemoveInvitations = async (data) => {
        await settingRequests.removeInvitation(data.login)
        func.setToast(true, false, "Invitation removed successfully")
        await getTeamData();
    }

    return (
        <Page
            title="Users"
            primaryAction={{
                content: 'Invite user',
                onAction: () => toggleInviteUserModal(),
                'disabled': (isLocalDeploy || userRole === 'GUEST' || userRole === 'DEVELOPER' || window.INVITE_DISABLED_FOR_SSO)
            }}
            divider
        >
            {isLocalDeploy &&
                <Banner
                    title="Invite new members"
                    action={{
                        content: 'Go to docs',
                        url: 'https://docs.akto.io/getting-started/quick-start-with-akto-cloud',
                        target: "_blank"
                    }}
                    status="info"
                >
                    <p>Inviting team members is disabled in local. Collaborate with your team by using Akto cloud or AWS/GCP deploy.</p>
                </Banner>
            }
            <br />
            
            <Banner>
                <Text variant="headingMd">Role permissions</Text>
                <Text variant="bodyMd">Each role has different permissions. <Link url="https://docs.akto.io/" target="_blank">Learn more</Link></Text>
            </Banner>

            {userRole !== 'GUEST' && <div style={{ paddingTop: "20px" }}>
                <LegacyCard>
                    <ResourceList
                        resourceName={{ singular: 'user', plural: 'users' }}
                        items={users}
                        renderItem={(item) => {
                            const { id, name, login, role } = item;
                            const initials = func.initials(login)
                            const media = <Avatar user size="medium" name={login} initials={initials} />

                            const updateUsersCollection = async () => {
                                const collectionIdList = selectedItems[id];
                                const userCollectionMap = {
                                    [id]: collectionIdList
                                };
                                await observeApi.updateUserCollections(userCollectionMap)
                                func.setToast(true, false, `User's ${selectedItems[id].length} collection${func.addPlurality(selectedItems[id].length)} have been updated!`)
                                await getTeamData()
                            }

                            const userCollectionsHandler = () => {
                                updateUsersCollection()
                                return true
                            }

                            const handleSelectedItemsChange = (items) => {
                                handleSelectedItems(id, items)
                            }

                            const userCollectionsModalComp = (
                                <Box>
                                    <SearchableResourceList
                                        resourceName={'collection'}
                                        items={allCollections}
                                        renderItem={usersCollectionRenderItem}
                                        isFilterControlEnabale={userRole === 'ADMIN'}
                                        selectable={userRole === 'ADMIN'}
                                        onSelectedItemsChange={handleSelectedItemsChange}
                                        alreadySelectedItems={getUserApiCollectionIds(id)}
                                    />
                                </Box>
                            )

                            const shortcutActions = (username !== login && roleHierarchy.includes(role.toUpperCase())) ?
                                [
                                    {
                                        content: (
                                            <HorizontalStack gap={4}>
                                                { (role === 'ADMIN' || userRole !== 'ADMIN' || !rbacAccessAdvanced) ? undefined :
                                                    <ResourceListModal
                                                        title={"Collection list"}
                                                        activatorPlaceaholder={`${(usersCollection[id] || []).length} collections accessible`}
                                                        isColoredActivator={true}
                                                        component={userCollectionsModalComp}
                                                        primaryAction={userCollectionsHandler}
                                                    />
                                                }

                                                <Button
                                                    onClick={() => openEditScopeRoleModal(id, login, name, role, item?.scopeRoleMapping)}
                                                >
                                                    Edit Access
                                                </Button>
                                            </HorizontalStack>
                                        )
                                    }
                                ] : item?.isInvitation ? [
                                    {
                                        content: (
                                            <HorizontalStack gap={4}>
                                                <Text color="subdued">{func.toSentenceCase(getRoleDisplayForSidebar(item?.scopeRoleMapping, role))}</Text>
                                                <div onClick={() => handleRemoveInvitations(item)}><Icon source={DeleteMajor}/></div>
                                            </HorizontalStack>
                                        )
                                    }
                                ] : [
                                    {
                                        content: <Text color="subdued">{func.toSentenceCase(getRoleDisplayForSidebar(item?.scopeRoleMapping, role))}</Text>,
                                        url: '#',
                                    }
                                ]

                            // Display current configuration
                            const currentConfigDisplay = item?.scopeRoleMapping && Object.keys(item.scopeRoleMapping).length > 0
                                ? (
                                    <Box>
                                        <Text variant="bodySm" color="subdued">
                                            {item?.isInvitation ? "Invitation sent for " : "Scope-based access:"}
                                        </Text>
                                        <Box paddingBlockStart="100">
                                            {Object.entries(item.scopeRoleMapping).map(([scope, roleValue]) => {
                                                const scopeLabel = PRODUCT_SCOPES.find(s => s.value === scope)?.label || scope
                                                // Skip NO_ACCESS roles for display purposes
                                                if (roleValue === 'NO_ACCESS') return null;
                                                return (
                                                    <Text key={scope} variant="bodySm">
                                                        {getRoleDisplayName(roleValue)} ({scopeLabel}){item?.isInvitation && Object.entries(item.scopeRoleMapping).filter(([,r]) => r !== 'NO_ACCESS').length > 1 ? ',' : ''}
                                                    </Text>
                                                )
                                            })}
                                        </Box>
                                    </Box>
                                )
                                : (
                                    <Text variant="bodySm" color="subdued">
                                        Role: {getRoleDisplayName(role)}
                                    </Text>
                                )

                            return (
                                <ResourceItem
                                    id={id}
                                    media={media}
                                    shortcutActions={shortcutActions}
                                    persistActions
                                >
                                    <Text variant="bodyMd" fontWeight="bold" as="h3">
                                        {name}
                                    </Text>
                                    <Text variant="bodyMd">
                                        {login}
                                    </Text>
                                    <Box paddingBlockStart="100">
                                        {currentConfigDisplay}
                                    </Box>
                                </ResourceItem>
                            );
                        }}
                        headerContent={`Showing ${users.length} team member${users.length > 1 ? 's': ''}`}
                        showHeader
                        loading={loading}
                    />
                </LegacyCard>
                <InviteUserModal
                    inviteUser={inviteUser}
                    setInviteUser={setInviteUser}
                    toggleInviteUserModal={toggleInviteUserModal}
                    roleHierarchy={roleHierarchy}
                    rolesOptions={rolesOptions}
                    defaultInviteRole={defaultInviteRole}
                />

                {/* Edit Scope-Role Mapping Modal */}
                <Modal
                    open={editScopeRoleModal.isActive}
                    onClose={closeEditScopeRoleModal}
                    title={`Edit access for ${editScopeRoleModal.name}`}
                    primaryAction={{
                        loading: false,
                        content: 'Save',
                        onAction: saveEditedScopeRoleMapping,
                    }}
                    secondaryActions={[
                        {
                            content: 'Remove User',
                            destructive: true,
                            onAction: async () => {
                                await handleRemoveUser(editScopeRoleModal.email)
                                closeEditScopeRoleModal()
                            },
                        },
                        {
                            content: 'Cancel',
                            onAction: closeEditScopeRoleModal,
                        },
                    ]}
                >
                    <Modal.Section>
                        {/* Current Configuration Display */}
                        <Box paddingBlockEnd="400" borderBottomWidth="1" borderColor="border">
                            <Text variant="headingSm" as="h3">Current Configuration</Text>
                            <Box paddingBlockStart="200">
                                {editScopeRoleModal.isSimpleRole
                                    ? <Text variant="bodySm">{getRoleDisplayName(editScopeRoleModal.currentRole)}</Text>
                                    : Object.entries(editScopeRoleModal.currentScopeRoleMapping).length > 0
                                    ? (
                                        Object.entries(editScopeRoleModal.currentScopeRoleMapping).map(([scope, role]) => {
                                            const scopeLabel = PRODUCT_SCOPES.find(s => s.value === scope)?.label || scope
                                            return (
                                                <Text key={scope} variant="bodySm">
                                                    {getRoleDisplayName(role)} for {scopeLabel}
                                                </Text>
                                            )
                                        })
                                    )
                                    : <Text variant="bodySm">No access configured</Text>
                                }
                            </Box>
                        </Box>

                        {/* Edit Configuration */}
                        <Box paddingBlockStart="400">
                            <Text variant="headingSm" as="h3">Configure Access by Scope</Text>
                            <Text variant="bodySm" color="subdued" as="p" style={{ marginBottom: "20px", marginTop: "10px" }}>
                                Select scopes and assign a role for each. Unselected scopes will have no access.
                            </Text>

                            <Box padding="400">
                                {PRODUCT_SCOPES.map((scope) => {
                                    const isSelected = scope.value in editScopeRoleModal.editingScopeRoleMapping
                                    const selectedRole = editScopeRoleModal.editingScopeRoleMapping[scope.value]

                                    return (
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
                                                checked={isSelected}
                                                onChange={() => handleScopesToggleInModal(scope.value)}
                                            />
                                            <Text variant="bodyMd" style={{ minWidth: "120px", flexShrink: 0 }}>
                                                {scope.label}
                                            </Text>
                                            {isSelected && (
                                                <Box style={{ marginLeft: "80px" }}>
                                                    <Dropdown
                                                        id={`edit-role-${scope.value}`}
                                                        selected={(value) => handleScopeRoleChangeInModal(scope.value, value)}
                                                        menuItems={rolesOptions[0]?.items?.map((role) => ({
                                                            label: role.content,
                                                            value: role.role
                                                        })) || []}
                                                        initial={selectedRole || "MEMBER"}
                                                    />
                                                </Box>
                                            )}
                                        </Box>
                                    )
                                })}
                            </Box>
                        </Box>
                    </Modal.Section>
                </Modal>

                <Modal
                    small
                    open={passwordResetState.confirmPasswordResetActive}
                    onClose={() => setPasswordResetStateHelper("confirmPasswordResetActive", false)}
                    title="Password Reset"
                    primaryAction={{
                        content: 'Generate',
                        onAction: resetPassword,
                    }}
                    secondaryActions={[
                        {
                        content: 'Cancel',
                        onAction: () => setPasswordResetStateHelper("confirmPasswordResetActive", false),
                        },
                    ]}
                >
                    <Modal.Section>
                        <Text>Are you sure you want to generate a link to reset the password for <b>{passwordResetState.passwordResetLogin}</b>?</Text>
                    </Modal.Section>
                </Modal>

                <Modal
                    small
                    open={passwordResetState.passwordResetLinkActive}
                    onClose={closePasswordResetToggle}
                    title="Password Reset"
                    primaryAction={{
                        content: 'Copy link',
                        onAction: handleCopyPasswordResetLink,
                    }}
                    secondaryActions={[
                        {
                        content: 'Cancel',
                        onAction: closePasswordResetToggle,
                        },
                    ]}
                >
                    <Modal.Section>
                        <TextField
                            label="Password reset link"
                            disabled={true}
                            value={passwordResetState.passwordResetLink}
                        />
                        <div ref={ref} />
                    </Modal.Section>
                </Modal>
            </div>}

        </Page>

    )
}

export default Users
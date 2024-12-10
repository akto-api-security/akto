import { ActionList, Avatar, Banner, Box, Button, Icon, LegacyCard, Link, Modal, Page, Popover, ResourceItem, ResourceList, Text, TextField } from "@shopify/polaris"
import { DeleteIcon, CheckIcon, PasskeyIcon } from "@shopify/polaris-icons";
import { useEffect, useRef, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import InviteUserModal from "./InviteUserModal";

const Users = () => {
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
    const [roleHierarchy, setRoleHierarchy] = useState([])
    const rbacAccess = func.checkForRbacFeature();

    const [roleSelectionPopup, setRoleSelectionPopup] = useState({})

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

    let paidFeatureRoleOptions =  rbacAccess ? [
        {
            content: 'Developer',
            role: 'DEVELOPER',
        },
        {
            content: 'Guest',
            role: 'GUEST',
        }
    ] : []

    const websiteHostName = window.location.origin
    const notOnPremHostnames = ["app.akto.io", "localhost", "127.0.0.1", "[::1]"]
    const isOnPrem = websiteHostName && !notOnPremHostnames.includes(window.location.hostname)

    const rolesOptions = [
        {
            items: [
            {
                content: 'Admin',
                role: 'ADMIN',
            },
            {
                content: 'Security Engineer',
                role: 'MEMBER',
            }, ...paidFeatureRoleOptions]
        },
        {
            items: [
                isOnPrem && {
                    destructive: false,
                    content: 'Reset Password',
                    role: 'RESET_PASSWORD',
                    icon: PasskeyIcon
                },
                {
                    destructive: true,
                    content: 'Remove',
                    role: 'REMOVE',
                    icon: DeleteIcon
                }
            ]
        }
    ]

    const getRoleHierarchy = async() => {
        let roleHierarchyResp = await settingRequests.getRoleHierarchy(window.USER_ROLE)
        if(roleHierarchyResp.includes("MEMBER")){
            roleHierarchyResp.push("SECURITY ENGINEER")
        }
        if(window.USER_ROLE === 'ADMIN'){
            roleHierarchyResp.push('REMOVE')
            roleHierarchyResp.push('RESET_PASSWORD')
        }
        setRoleHierarchy(roleHierarchyResp)
        
    }

    useEffect(() => {
        if(userRole !== 'GUEST') {
            getTeamData();
        }
        getRoleHierarchy()
    }, [])

    const handleRoleSelectChange = async (id, newRole, login) => {
        if(newRole === 'REMOVE') {
            await handleRemoveUser(login)
            toggleRoleSelectionPopup(id)
            setUsers(users.filter(user => user.login !== login))
            return
        }

        if(newRole === 'RESET_PASSWORD') {
            setPasswordResetStateHelper("confirmPasswordResetActive", true)
            setPasswordResetStateHelper("passwordResetLogin", login)
            toggleRoleSelectionPopup(id)
            return
        }

        // Call Update Role API
        setUsers(users.map(user => user.login === login ? { ...user, role: newRole } : user))
        setRoleSelectionPopup(prevState => ({ ...prevState, [login]: false }))
        await updateUserRole(login, newRole)

        toggleRoleSelectionPopup(id)
    }

    const toggleRoleSelectionPopup = (id) => {
        setRoleSelectionPopup(prevState => ({
            ...prevState,
            [id]: !prevState[id]
        }));
    }

    const getRolesOptionsWithTick = (currentRole) => {
        const tempArr =  rolesOptions.map(section => ({
            ...section,
            items: section.items.filter((c) => roleHierarchy.includes(c.role)).map(item => ({
                ...item,
                prefix: item.role === "REMOVE"?  <Box><Icon source={DeleteIcon}/></Box> : item.role === "RESET_PASSWORD" ? <Box><Icon source={PasskeyIcon}/></Box> : item.role === currentRole ? <Box><Icon source={CheckIcon}/></Box> : <div style={{padding: "10px"}}/>
            }))
        }));
        return tempArr
    }

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
        setUsers(usersResponse.users)
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

    const updateUserRole = async (login,roleVal) => {
        await settingRequests.makeAdmin(login, roleVal)
        func.setToast(true, false, "Role updated for " + login + " successfully")
    }

    return (
        <Page
            title="Users"
            primaryAction={{
                content: 'Invite user',
                onAction: () => toggleInviteUserModal(),
                'disabled': (isLocalDeploy || userRole === 'GUEST')
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
                    tone="info"
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
                            const media = <Avatar user size="md" name={login} initials={initials} />
                            const shortcutActions = (username !== login && roleHierarchy.includes(role.toUpperCase())) ? 
                                [
                                    {
                                        content: <Popover
                                                    active={roleSelectionPopup[id]}
                                                    onClose={() => toggleRoleSelectionPopup(id)}
                                                    activator={<Button disclosure onClick={() => toggleRoleSelectionPopup(id)}>{getRoleDisplayName(role)}</Button>}
                                                 >
                                                    <ActionList
                                                        actionRole="menuitem"
                                                        sections={getRolesOptionsWithTick(role).map(section => ({
                                                            ...section,
                                                            items: section.items.map(item => ({
                                                                ...item,
                                                                onAction: () => handleRoleSelectChange(id, item.role, login)
                                                            }))
                                                        }))}
                                                    />
                                                 </Popover>
                                    }
                                ] : [
                                    {
                                        content: <Text tone="subdued">{func.toSentenceCase(getRoleDisplayName(role))}</Text>,
                                        url: '#',
                                    }
                                ]

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
                />
                <Modal
                    size="small"
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
                    size="small"
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
    );
}

export default Users
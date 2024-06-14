import AktoButton from './../../../components/shared/AktoButton';
import { ActionList, Avatar, Banner, Button, LegacyCard, Link, Page, Popover, ResourceItem, ResourceList, Text } from "@shopify/polaris"
import { DeleteMajor, TickMinor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import InviteUserModal from "./InviteUserModal";
import Store from "../../../store";
import PersistStore from '../../../../main/PersistStore';

const Users = () => {
    const username = Store(state => state.username)
    const userRole = PersistStore(state => state.userRole)

    const [inviteUser, setInviteUser] = useState({
        isActive: false,
        state: "initial", // initial, loading, success
        email: "",
        inviteLink: "",
    })

    const [loading, setLoading] = useState(false)
    const [users, setUsers] = useState([])

    const [roleSelectionPopup, setRoleSelectionPopup] = useState({})

    const rolesOptions = [
        {
            items: [
            {
                content: 'Admin',
                role: 'ADMIN',
                icon: <div style={{padding: "10px"}}/>
            },
            {
                content: 'Security Engineer',
                role: 'MEMBER',
                icon: <div style={{padding: "10px"}}/>
            },
            {
                content: 'Developer',
                role: 'DEVELOPER',
                icon: <div style={{padding: "10px"}}/>
            },
            {
                content: 'Guest',
                role: 'GUEST',
                icon: <div style={{padding: "10px"}}/>
            }]
        },
        {
            items: [{
                destructive: true,
                content: 'Remove',
                role: 'REMOVE',
                icon: DeleteMajor
            }]
        }
    ]

    const handleRoleSelectChange = async (id, newRole, login) => {
        if(newRole === 'REMOVE') {
            // await handleRemoveUser(login)
            console.log("removing user ", login)
            toggleRoleSelectionPopup(id)
            return
        }

        // Call Update Role API
        setUsers(users.map(user => user.login === login ? { ...user, role: newRole } : user))
        setRoleSelectionPopup(prevState => ({ ...prevState, [login]: false }))
        console.log(newRole, login)

        toggleRoleSelectionPopup(id)
    }

    const toggleRoleSelectionPopup = (id) => {
        setRoleSelectionPopup(prevState => ({
            ...prevState,
            [id]: !prevState[id]
        }));
    }

    const getRolesOptionsWithTick = (currentRole) => {
        return rolesOptions.map(section => ({
            ...section,
            items: section.items.map(item => ({
                ...item,
                icon: item.role === currentRole ? TickMinor : item.icon
            }))
        }));
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

    useEffect(() => {
        getTeamData();
    }, [])

    const isLocalDeploy = false;
    const currentUser = users.find(user => user.login === username)

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

    const handleMakeAdmin = async (login) => {
        await settingRequests.makeAdmin(login)
        func.setToast(true, false, "User " + login + " made admin successfully")
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
                    status="info"
                >
                    <p>Inviting team members is disabled in local. Collaborate with your team by using Akto cloud or AWS/GCP deploy.</p>
                </Banner>
            }
            <br />
            
            <Banner>
                <Text variant="headingMd">Role permissions</Text>
                <Text variant="bodyMd">Each role have different permissions. <Link url="https://docs.akto.io/" target="_blank">Learn more</Link></Text>
            </Banner>

            <div style={{ paddingTop: "20px" }}>
                <LegacyCard>
                    <ResourceList
                        resourceName={{ singular: 'user', plural: 'users' }}
                        items={users}
                        renderItem={(item) => {
                            const { id, name, login, role } = item;
                            const initials = func.initials(login)
                            const media = <Avatar user size="medium" name={login} initials={initials} />
                            const shortcutActions = username !== login && role !== currentUser.role && currentUser.role !== "GUEST" ? 
                                [
                                    {
                                        content: <Popover
                                                    active={roleSelectionPopup[id]}
                                                    onClose={() => toggleRoleSelectionPopup(id)}
                                                    activator={<AktoButton disclosure onClick={() => toggleRoleSelectionPopup(id)}>{getRoleDisplayName(role)}</AktoButton>}
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
                                        content: <Text color="subdued">{getRoleDisplayName(role)}</Text>,
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
                />
            </div>

        </Page>

    )
}

export default Users

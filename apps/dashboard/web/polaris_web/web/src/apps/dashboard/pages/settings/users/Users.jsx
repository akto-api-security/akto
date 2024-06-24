import { Avatar, Banner, Button, Card, LegacyCard, Modal, Page, ResourceItem, ResourceList, Scrollable, Text, TextContainer, TextField } from "@shopify/polaris"
import { useCallback, useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import InviteUserModal from "./InviteUserModal";
import Store from "../../../store";

const Users = () => {
    const username = Store(state => state.username)

    const [inviteUser, setInviteUser] = useState({
        isActive: false,
        state: "initial", // initial, loading, success
        email: "",
        inviteLink: "",
    })

    const [loading, setLoading] = useState(false)
    const [users, setUsers] = useState([])

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

    let isAdmin = false
    if (currentUser) {
        isAdmin = currentUser.role === "ADMIN"
    } 

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
                disabled: isLocalDeploy
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
            <Text variant="headingMd">Team details</Text>
            <Text variant="bodyMd">Find and manage your team permissions here</Text>
            <div style={{ paddingTop: "20px" }}>
                <LegacyCard>
                    <ResourceList
                        resourceName={{ singular: 'user', plural: 'users' }}
                        items={users}
                        renderItem={(item) => {
                            const { id, login, role } = item;

                            const initials = func.initials(login)
                            const media = <Avatar user size="medium" name={login} initials={initials} />
                            const shortcutActions = username !== login && isAdmin  ? 
                                [
                                    {
                                        content: 'Remove user',
                                        onAction: () => {handleRemoveUser(login)},
                                    },
                                    ( role.toUpperCase() === "MEMBER" ) &&
                                    {
                                        content: 'Make admin',
                                        onAction: () => {handleMakeAdmin(login)},
                                    }
                                ] : []

                            return (
                                <ResourceItem
                                    id={id}
                                    media={media}
                                    shortcutActions={shortcutActions}
                                    persistActions
                                >
                                    <Text variant="bodyMd" fontWeight="bold" as="h3">
                                        {login}
                                    </Text>
                                    <Text variant="bodyMd">
                                        {role}
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

import { Avatar, Banner, Button, Card, LegacyCard, Modal, Page, ResourceItem, ResourceList, Scrollable, Text, TextContainer, TextField } from "@shopify/polaris"
import { useCallback, useEffect, useState } from "react";
import settingRequests from "../api";
import func from "../../../../../util/func";

const Users = () => {
    const [users, setUsers] = useState([])

    const isLocalDeploy = false;
    //const isLocalDeploy = window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy'

    const [inviteUserModalActive, setInviteUserModalActive] = useState(false)
    const toggleInviteUserModal = () => setInviteUserModalActive(!inviteUserModalActive)

    const [inviteEmail, setInviteEmail] = useState()
    const [inviteSuccess, setInviteSuccess] = useState({
        status: "", // loading, success


    })

    useEffect(() => {
        const getTeamData = async () => {
            const usersResponse = await settingRequests.getTeamData()
            setUsers(usersResponse.users)
        };

        getTeamData();
    }, [])

    const handleSendInvitation = async () => {
        const spec = {
            inviteeName: "there",
            inviteeEmail: inviteEmail,
            websiteHostName: window.location.origin
        }
        const inviteUsersResponse = await settingRequests.inviteUsers(spec)
        setInviteUserSuccess({

        })
        console.log(inviteUsersResponse)
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
                        url: 'https://docs.akto.io/getting-started/quick-start-with-akto-cloud' ,
                        target: "_blank"
                    }}
                    status="info"
                >
                    <p>Inviting team members is disabled in local. Collaborate with your team by using Akto cloud or AWS/GCP deploy.</p>
                </Banner>
            }
            <br/>   
            <Text variant="headingMd">Team details</Text>
            <Text variant="bodyMd">Find and manage your team permissions here</Text>
            <div style={{ paddingTop: "5vh" }}>
                <LegacyCard>
                    <ResourceList
                        resourceName={{ singular: 'user', plural: 'users' }}
                        items={users}
                        renderItem={(item) => {
                            const { id, login, role } = item;

                            const initials = func.initials(login)
                            const media = <Avatar user size="medium" name={login} initials={initials} />;
                            const shortcutActions =
                                [
                                    {
                                        content: 'Remove User',
                                        disabled: true,
                                        onAction: () => { console.log("remove user") }
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
                                        {login}
                                    </Text>
                                    <Text variant="bodyMd"  >
                                        {role}
                                    </Text>
                                </ResourceItem>
                            );
                        }}
                        totalItemsCount={1}
                    />
                </LegacyCard>

                <Modal
                    small
                    open={inviteUserModalActive}
                    onClose={toggleInviteUserModal}
                    title="Add team member"
                    primaryAction={{
                        loading: true,
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
            </div>

        </Page>

    )
}

export default Users

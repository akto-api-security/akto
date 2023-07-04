
import { Avatar, Button, Card, LegacyCard, Modal, Page, ResourceItem, ResourceList, Scrollable, Text, TextContainer } from "@shopify/polaris"
import { useCallback, useEffect, useState } from "react";
import axios from "axios";
import Store from "../../../store";

const Users = () => {
    const accessToken = Store(state => state.accessToken)
    const { users, setUsers } = useState([])

    const [active, setActive] = useState(false)
    const handleChange = () => setActive(!active)

    useEffect(() => {
        const getUsers = async () => {
            try {
                const res = await axios.post("/api/getTeamData", null, {
                    headers: {
                        "Access-Token": accessToken
                    }
                }
                )

                setUsers([])

            } catch (err) {
                console.log(err)
            }
        };

        getUsers();
    }, [])

    const handleSendInvitation = () => {
        return true;
    }

    return (
        <Page
            title="Users"
            primaryAction={{
                content: 'Invite user',
                onAction: () => handleChange()
            }}
            divider
        >
            <Text variant="headingMd">Team details</Text>
            <Text variant="bodyMd">Find and manage your team permissions here</Text>
            <div style={{ paddingTop: "5vh" }}>
                <LegacyCard>
                    <ResourceList
                        resourceName={{ singular: 'user', plural: 'users' }}
                        // items={[
                        //     {
                        //         id: '2',
                        //         name: 'oren@akto.io',
                        //         role: 'Admin',
                        //     },
                        //     {
                        //         id: '3',
                        //         name: 'fenil@akto.io',
                        //         role: 'Member',
                        //     },
                        // ]}
                        items={[
                            {
                                "id": 1677216315,
                                "login": "bhavik@akto.io",
                                "name": "bhavik@akto.io",
                                "role": "ADMIN"
                            },
                            {
                                "id": 1677216393,
                                "login": "avneesh@akto.io",
                                "name": "avneesh@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1677217507,
                                "login": "oren@akto.io",
                                "name": "oren@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1677217514,
                                "login": "aryan@akto.io",
                                "name": "aryan@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1677217740,
                                "login": "ankita@akto.io",
                                "name": "ankita@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1677218601,
                                "login": "fenil@akto.io",
                                "name": "fenil@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1677219446,
                                "login": "shivansh@akto.io",
                                "name": "shivansh@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1677235231,
                                "login": "shivam@akto.io",
                                "name": "shivam@akto.io",
                                "role": "ADMIN"
                            },
                            {
                                "id": 1679993640,
                                "login": "jaydev+1@akto.io",
                                "name": "jaydev+1@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1679993641,
                                "login": "pentest.2user@gmail.com",
                                "name": "pentest.2user@gmail.com",
                                "role": "Member"
                            },
                            {
                                "id": 1681286493,
                                "login": "ankush+bedanta@akto.io",
                                "name": "ankush+bedanta@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1686568633,
                                "login": "anonymoustesteditor@akto.io",
                                "name": "Anonymous User",
                                "role": "Member"
                            },
                            {
                                "id": 1686725167,
                                "login": "mayankesh@akto.io",
                                "name": "mayankesh@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1688381978,
                                "login": "arjun@akto.io",
                                "name": "arjun@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1688394998,
                                "login": "ankush@akto.io",
                                "name": "ankush@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1688398031,
                                "login": "raaga@akto.io",
                                "name": "raaga@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1688398986,
                                "login": "jesse@akto.io",
                                "name": "jesse@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1688428854,
                                "login": "theo@akto.io",
                                "name": "theo@akto.io",
                                "role": "Member"
                            },
                            {
                                "id": 1677216315,
                                "login": "ayush@akto.io",
                                "name": "-",
                                "role": "Invitation sent"
                            },
                            {
                                "id": 1679993641,
                                "login": "jaydev+2@akto.io",
                                "name": "-",
                                "role": "Invitation sent"
                            },
                            {
                                "id": 1679993641,
                                "login": "jaydev+3@akto.io",
                                "name": "-",
                                "role": "Invitation sent"
                            },
                            {
                                "id": 1679993641,
                                "login": "jaydev+3@akto.io",
                                "name": "-",
                                "role": "Invitation sent"
                            },
                            {
                                "id": 1677216315,
                                "login": "raaga@akto.io",
                                "name": "-",
                                "role": "Invitation sent"
                            },
                            {
                                "id": 1677217740,
                                "login": "luke@akto.io",
                                "name": "-",
                                "role": "Invitation sent"
                            }
                        ]
                        }
                        renderItem={(item) => {
                            const { id, name, role } = item;
                            const media = <Avatar user size="medium" name={name} initials="OE" />;
                            const shortcutActions =
                                [
                                    {
                                        content: 'Remove User',
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
                                        {name}
                                    </Text>
                                    <div>{role}</div>
                                </ResourceItem>
                            );
                        }}
                        totalItemsCount={3}
                    />
                </LegacyCard>
            
                <Modal
                    open={active}
                    onClose={handleChange}
                    title="Add team member"
                    primaryAction={{
                        content: 'Send invitation',
                        onAction: handleSendInvitation,
                    }}
                    secondaryActions={[
                        {
                            content: 'Cancel',
                            onAction: handleChange,
                        },
                    ]}
                >
                    <Modal.Section>
                        <Text>Accou</Text>
                    </Modal.Section>
                </Modal>
            </div>

        </Page>

    )
}

export default Users

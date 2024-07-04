import { ActionList, Avatar, Banner, Box, Button, HorizontalStack, Icon, LegacyCard, Link, Modal, Page, Popover, ResourceItem, ResourceList, Text, TextField } from "@shopify/polaris"
import { DeleteMajor, TickMinor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import InviteUserModal from "./InviteUserModal";
import Store from "../../../store";
import PersistStore from "../../../../main/PersistStore";
import SearchableResourceList from "../../../components/shared/SearchableResourceList";
import ResourceListModal from "../../../components/shared/ResourceListModal";
import observeApi from "../../observe/api";

const Users = () => {
    const username = Store(state => state.username)
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
    const stiggFeatures = window.STIGG_FEATURE_WISE_ALLOWED
    let rbacAccess = false;

    const collectionsMap = PersistStore(state => state.collectionsMap)

    const [selectedItems, setSelectedItems] = useState({})

    const handleSelectedItems = (id, items) => {
        setSelectedItems(prevSelectedItems => ({
            ...prevSelectedItems,
            [id]: items
        }));
    }


    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
        rbacAccess = true
    } else if(stiggFeatures && stiggFeatures['RBAC_FEATURE']){
        rbacAccess = stiggFeatures['RBAC_FEATURE'].isGranted
    }

    const [roleSelectionPopup, setRoleSelectionPopup] = useState({})

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
            items: [{
                destructive: true,
                content: 'Remove',
                role: 'REMOVE',
                icon: DeleteMajor
            }]
        }
    ]

    const getRoleHierarchy = async() => {
        let roleHierarchyResp = await settingRequests.getRoleHierarchy(window.USER_ROLE)
        if(window.USER_ROLE === 'ADMIN'){
            roleHierarchyResp.push('REMOVE')
        }
        setRoleHierarchy(roleHierarchyResp)

    }

    useEffect(() => {
        getTeamData();
        getRoleHierarchy()

        setAllCollections(Object.entries(collectionsMap).map(([id, collectionName]) => ({
            id: parseInt(id, 10),
            collectionName
        })));
    }, [])

    const handleRoleSelectChange = async (id, newRole, login) => {
        if(newRole === 'REMOVE') {
            await handleRemoveUser(login)
            toggleRoleSelectionPopup(id)
            setUsers(users.filter(user => user.login !== login))
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
                prefix: item.role === "REMOVE"?  <Box><Icon source={DeleteMajor}/></Box> : item.role === currentRole ? <Box><Icon source={TickMinor}/></Box> : <div style={{padding: "10px"}}/>
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
        if(userRole === 'ADMIN') {
            const usersCollectionList = await settingRequests.getAllUsersCollections()
            setUsersCollection(usersCollectionList)
        }
        setUsers(usersResponse)
        setLoading(false)
    };

    const isLocalDeploy = false;

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
    
    const getUserApiCollectionIds = (userId) => {
        return usersCollection[userId] || [];
    };

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

                            const usersCollectionRenderItem = (item) => {
                                const { id, collectionName } = item;

                                return (
                                    <ResourceItem id={id}>
                                        <Text variant="bodyMd" fontWeight="semibold" as="h3">{collectionName}</Text>
                                    </ResourceItem>
                                );
                            }

                            const updateUsersCollection = async () => {
                                const collectionIdList = selectedItems[id];
                                const collectionIdListObj = collectionIdList.map(collectionId => ({ id: collectionId.toString() }))
                                await observeApi.updateUserCollections(collectionIdListObj, [id])
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
                                                {role === 'ADMIN' || userRole !== 'ADMIN' ? undefined :
                                                    <ResourceListModal
                                                        title={"Collection list"}
                                                        activatorPlaceaholder={`${(usersCollection[id] || []).length} Collections accessible`}
                                                        isColoredActivator={true}
                                                        component={userCollectionsModalComp}
                                                        primaryAction={userCollectionsHandler}
                                                    />
                                                }

                                                <Popover
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
                                            </HorizontalStack>
                                        )
                                    }
                                ] : [
                                    {
                                        content: <Text color="subdued">{func.toSentenceCase(getRoleDisplayName(role))}</Text>,
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
            </div>

        </Page>

    )
}

export default Users
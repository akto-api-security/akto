import { Box, HorizontalStack, LegacyCard, Page, ResourceItem, ResourceList, Text, Modal, TextField, VerticalStack, Checkbox } from "@shopify/polaris"
import { useEffect, useState } from "react";
import func from "@/util/func";
import settingRequests from "../api";
import ResourceListModal from "../../../components/shared/ResourceListModal";
import { usersCollectionRenderItem } from "../rbac/utils";
import PersistStore from "../../../../main/PersistStore";
import SearchableResourceList from "../../../components/shared/SearchableResourceList";
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";

const rolesOptions = [
    {
        label: 'Admin',
        value: 'ADMIN',
    },
    {
        label: 'Security Engineer',
        value: 'MEMBER',
    },
    {
        label: 'Developer',
        value: 'DEVELOPER',
    },
    {
        label: 'Guest',
        value: 'GUEST',
    },
    {
        label: 'Threat Engineer',
        value: 'THREAT_ENGINEER',
    },
    {
        label: 'Threat Viewer',
        value: 'THREAT_VIEWER',
    }]

function getRoleDisplayName(role) {
    for (const item of rolesOptions) {
        if (item.value === role) {
            return item.label
        }
    }
    return role
}

export { rolesOptions, getRoleDisplayName }

// base roles whose static access map already grants threat protection
const THREAT_ENABLED_BASE_ROLES = ['ADMIN', 'THREAT_ENGINEER', 'THREAT_VIEWER']

/*
 * threatProtectionEnabled is null for roles created before the toggle existed, which
 * means "no explicit choice" - those keep whatever the base role grants.
 */
function threatEnabledFor(role) {
    return role?.threatProtectionEnabled ?? THREAT_ENABLED_BASE_ROLES.includes(role?.baseRole)
}

// base roles that already grant threat protection have nothing to toggle
function showThreatToggle(role) {
    return !THREAT_ENABLED_BASE_ROLES.includes(role?.baseRole)
}

/*
 * Only persist a choice while the toggle is shown. Saving one for a base role that
 * already grants threat access would leave a stale override behind if the base role
 * were later changed to one that does not.
 */
function threatValueToSave(role) {
    return showThreatToggle(role) ? threatEnabledFor(role) : null
}

// an empty feature map means a self-hosted deployment, where everything is granted
function isThreatFeatureGranted() {
    const stiggFeatures = window?.STIGG_FEATURE_WISE_ALLOWED
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
        return true
    }
    return stiggFeatures?.THREAT_DETECTION?.isGranted === true
}

const Roles = () => {

    const threatFeatureGranted = isThreatFeatureGranted()

    const userRole = window.USER_ROLE
    const isLocalDeploy = func.checkLocal();
    const [roles, setRoles] = useState([])
    const [tempRoles, setTempRoles] = useState([])
    const [allCollections, setAllCollections] = useState([])
    const [loading, setLoading] = useState(false)
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [createNewRoleModalActive, setCreateNewRoleModalActive] = useState(false)

    const toggleInviteUserModal = () => {
        setCreateNewRoleModalActive(!createNewRoleModalActive)
    }

    const getRoleData = async () => {
        try {
            setLoading(true);
            const roleResponse = await settingRequests.getCustomRoles()
            if (roleResponse && roleResponse.roles) {
                setRoles(roleResponse.roles)
                setTempRoles(roleResponse.roles)
            }
            setLoading(false)
        } catch (error) {
            setLoading(false)
        }
    };

    useEffect(() => {
        if (userRole !== 'GUEST') {
            getRoleData();
        }

    }, [])

    // collectionsMap loads asynchronously, so this cannot be a mount-only effect
    useEffect(() => {
        setAllCollections(Object.entries(collectionsMap).map(([id, collectionName]) => ({
            id: parseInt(id, 10),
            collectionName
        })));
    }, [collectionsMap])

    const getRoleItems = (role, key) => {
        return roles.filter(r => r.name === role)[0][key] || []
    };

    // drop ids for collections that are deleted or deactivated; the picker cannot list them
    const selectable = (ids) => ids.filter((id) => id in collectionsMap);

    const handleSelectedItemsChange = (role, items, key) => {
        setRoles(prevRoles => {
            return prevRoles.map(r => {
                if (r.name === role) {
                    return {
                        ...r,
                        [key]: items
                    }
                }
                return r;
            })
        })
    }

    const updateBaseRole = (role, baseRole) => {
        setRoles(prevRoles => {
            return prevRoles.map(r => {
                if (r.name === role) {
                    return {
                        ...r,
                        baseRole: baseRole
                    }
                }
                return r;
            })
        })
    }

    const updateThreatProtection = (role, value) => {
        setRoles(prevRoles => {
            return prevRoles.map(r => {
                if (r.name === role) {
                    return {
                        ...r,
                        threatProtectionEnabled: value
                    }
                }
                return r;
            })
        })
    }

    const updateDefaultInviteRole = (role, value) => {
        setRoles(prevRoles => {
            return prevRoles.map(r => {
                if (r.name === role) {
                    return {
                        ...r,
                        defaultInviteRole: value
                    }
                }
                return r;
            })
        })
    }

    const handleUpdate = async (role) => {
        const roleData = roles.filter(r => r.name === role)[0]
        await settingRequests.updateCustomRole(roleData.apiCollectionsId, role, roleData.baseRole, roleData.defaultInviteRole, threatValueToSave(roleData))
        await getRoleData();
    }

    const handleClose = () => {
        setRoles(tempRoles)
    }

    const [newRoleName, setNewRoleName] = useState('')

    const handleNewRoleNameUpdate = (val) => {
        setNewRoleName(val)
    }

    const handleCreateNewRole = async () => {
        await settingRequests.createCustomRole([], newRoleName, "GUEST")
        setNewRoleName('')
        toggleInviteUserModal();
        await getRoleData();
    }

    return (
        <Page
            title="Custom roles"
            primaryAction={{
                content: 'Create new role',
                onAction: () => toggleInviteUserModal(),
                'disabled': (isLocalDeploy || userRole !== 'ADMIN')
            }}
            divider
        >
            <Modal
                open={createNewRoleModalActive}
                onClose={toggleInviteUserModal}
                title="Create new role"
                primaryAction={{
                    content: 'Create',
                    onAction: () => { handleCreateNewRole() },
                    'disabled': newRoleName.length === 0
                }}
                secondaryActions={[
                    {
                        content: 'Cancel',
                        onAction: toggleInviteUserModal
                    }
                ]}
            >
                <Box padding={8}>
                    <TextField onChange={val => handleNewRoleNameUpdate(val)} value={newRoleName} />
                </Box>
            </Modal>
            <LegacyCard>
                <ResourceList
                    resourceName={{ singular: 'role', plural: 'roles' }}
                    items={roles}
                    renderItem={(item) => {
                        const { name, baseRole, defaultInviteRole } = item;
                        const shortcutActions = [
                            {
                                content: (
                                    <ResourceListModal
                                        title={`Update ${name} role`}
                                        activatorPlaceaholder={`${selectable(getRoleItems(name, "apiCollectionsId")).length} collections accessible, ${getRoleDisplayName(baseRole)} permissions${defaultInviteRole ? ', Default invite role' : ''}`}
                                        isColoredActivator={true}
                                        component={<VerticalStack gap={4}>
                                            <Box paddingBlockStart={4}>
                                                <HorizontalStack gap={6} align="center" blockAlign="center">
                                                    <OperatorDropdown
                                                        items={rolesOptions}
                                                        label={getRoleDisplayName(baseRole)}
                                                        designer={true}
                                                        selected={(value) => {
                                                            updateBaseRole(name, value)
                                                        }}
                                                    />
                                                    <Checkbox
                                                        label={"Default invite role"}
                                                        checked={defaultInviteRole}
                                                        onChange={(checked) => { updateDefaultInviteRole(name, checked) }}
                                                    />
                                                    {showThreatToggle(item) ? (
                                                        <Checkbox
                                                            label={"Enable threat protection"}
                                                            checked={threatEnabledFor(item)}
                                                            disabled={!threatFeatureGranted}
                                                            onChange={(checked) => { updateThreatProtection(name, checked) }}
                                                        />
                                                    ) : null}
                                                </HorizontalStack>
                                            </Box>
                                            <Box>
                                                <SearchableResourceList
                                                    resourceName={'collection'}
                                                    items={allCollections}
                                                    renderItem={usersCollectionRenderItem}
                                                    isFilterControlEnabale={userRole === 'ADMIN'}
                                                    selectable={userRole === 'ADMIN'}
                                                    onSelectedItemsChange={(items) => handleSelectedItemsChange(name, items, 'apiCollectionsId')}
                                                    alreadySelectedItems={selectable(getRoleItems(name, "apiCollectionsId"))}
                                                />
                                            </Box>
                                        </VerticalStack>}
                                        primaryAction={() => { handleUpdate(name) }}
                                        secondaryAction={() => { handleClose() }}
                                        showDeleteAction={true}
                                        deleteAction={async () => { await settingRequests.deleteCustomRole(name); await getRoleData() }}
                                    />

                                )
                            }
                        ]

                        return (
                            <ResourceItem
                                id={name}
                                shortcutActions={shortcutActions}
                                persistActions
                            >
                                <Text variant="bodyMd" fontWeight="bold" as="h3">
                                    {name}
                                </Text>
                            </ResourceItem>
                        );
                    }}
                    headerContent={`Showing ${roles.length} role${roles.length > 1 ? 's' : ''}`}
                    showHeader
                    loading={loading}
                />
            </LegacyCard>

        </Page>
    )
}

export default Roles;
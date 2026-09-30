import { Box, Button, HorizontalStack, LegacyCard, Page, ResourceItem, ResourceList, Text, Modal, TextField, VerticalStack, Checkbox } from "@shopify/polaris"
import { DeleteMinor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react";
import func from "@/util/func";
import settingRequests from "../api";
import ResourceListModal from "../../../components/shared/ResourceListModal";
import { usersCollectionRenderItem } from "../rbac/utils";
import PersistStore from "../../../../main/PersistStore";
import SearchableResourceList from "../../../components/shared/SearchableResourceList";
import OperatorDropdown from "../../../components/layouts/OperatorDropdown";
import Dropdown from "../../../components/layouts/Dropdown";

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

/*
 * Base roles that decide threat access themselves - admin and the threat roles always
 * have it, guest never does. The backend ignores the toggle for these too, so showing
 * it would imply a control that does not exist.
 */
const FIXED_THREAT_BASE_ROLES = ['ADMIN', 'GUEST', 'THREAT_ENGINEER', 'THREAT_VIEWER']

function showThreatToggle(role) {
    return !FIXED_THREAT_BASE_ROLES.includes(role?.baseRole)
}

function threatEnabledFor(role) {
    return role?.threatProtectionEnabled === true
}

// never persist a choice for a base role that decides on its own
function threatValueToSave(role) {
    return showThreatToggle(role) && threatEnabledFor(role)
}

// an empty feature map means a self-hosted deployment, where everything is granted
function isThreatFeatureGranted() {
    const stiggFeatures = window?.STIGG_FEATURE_WISE_ALLOWED
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
        return true
    }
    return stiggFeatures?.THREAT_DETECTION?.isGranted === true
}

// Permissions an admin can change per custom role; anything not changed keeps the base role's access
const PERMISSION_FEATURES = [
    { feature: 'INVITE_MEMBERS', label: 'Invite users and change their roles' },
    { feature: 'THREAT_PROTECTION', label: 'Threat protection and guardrail activity' },
    { feature: 'THREAT_SETTINGS', label: 'Threat settings and data retention' },
    { feature: 'AI_AGENTS', label: 'AI agents' },
    { feature: 'API_COLLECTIONS', label: 'API collections and inventory' },
    { feature: 'SENSITIVE_DATA', label: 'Sensitive data' },
    { feature: 'SAMPLE_DATA', label: 'Request and response samples' },
    { feature: 'START_TEST_RUN', label: 'Run tests' },
    { feature: 'TEST_RESULTS', label: 'Test results' },
    { feature: 'ISSUES', label: 'Issues' },
    { feature: 'INTEGRATIONS', label: 'Integrations' },
    { feature: 'API_TOKENS', label: 'API tokens' },
]

const ROLE_DEFAULT = 'ROLE_DEFAULT'
const accessOptions = [
    { label: 'Base role default', value: ROLE_DEFAULT },
    { label: 'No access', value: 'NO_ACCESS' },
    { label: 'Read', value: 'READ' },
    { label: 'Read and write', value: 'READ_WRITE' },
]

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

    const updatePermission = (role, feature, access) => {
        setRoles(prevRoles => {
            return prevRoles.map(r => {
                if (r.name === role) {
                    const permissionOverrides = { ...(r.permissionOverrides || {}) }
                    if (access === ROLE_DEFAULT) {
                        delete permissionOverrides[feature]
                    } else {
                        permissionOverrides[feature] = access
                    }
                    return {
                        ...r,
                        permissionOverrides
                    }
                }
                return r;
            })
        })
    }

    const updateCollectionRules = (role, collectionRules) => {
        setRoles(prevRoles => prevRoles.map(r => r.name === role ? { ...r, collectionRules } : r))
    }

    const [newHostPattern, setNewHostPattern] = useState('')
    const [newTag, setNewTag] = useState('')

    const addCollectionRule = (role, currentRules) => {
        const host = newHostPattern.trim()
        const tag = newTag.trim()
        if ((host.length > 0) === (tag.length > 0)) {
            func.setToast(true, true, "Enter either a host pattern or a tag")
            return
        }
        let rule = { hostRegex: host }
        if (tag.length > 0) {
            const [tagKey, ...rest] = tag.split('=')
            rule = { tagKey: tagKey.trim(), tagValue: rest.join('=').trim() }
        }
        updateCollectionRules(role, [...(currentRules || []), rule])
        setNewHostPattern('')
        setNewTag('')
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
        await settingRequests.updateCustomRole(roleData.apiCollectionsId, role, roleData.baseRole, roleData.defaultInviteRole, threatValueToSave(roleData), roleData.permissionOverrides || {}, roleData.collectionRules || [])
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
                                                <VerticalStack gap={2}>
                                                    <Text variant="headingSm" as="h4">Permissions</Text>
                                                    <Text variant="bodySm" color="subdued">Change what this role can do. Anything left at the default keeps the base role's access.</Text>
                                                    {PERMISSION_FEATURES.map(({ feature, label }) => (
                                                        <HorizontalStack key={feature} align="space-between" blockAlign="center" wrap={false} gap={4}>
                                                            <Text variant="bodyMd">{label}</Text>
                                                            <Box minWidth="180px">
                                                                <Dropdown
                                                                    id={`permission-${name}-${feature}`}
                                                                    menuItems={accessOptions}
                                                                    initial={item?.permissionOverrides?.[feature] || ROLE_DEFAULT}
                                                                    selected={(access) => updatePermission(name, feature, access)}
                                                                />
                                                            </Box>
                                                        </HorizontalStack>
                                                    ))}
                                                </VerticalStack>
                                            </Box>
                                            <Box>
                                                <VerticalStack gap={2}>
                                                    <Text variant="headingSm" as="h4">Also include collections matching</Text>
                                                    <Text variant="bodySm" color="subdued">Collections added later that match a rule are included automatically.</Text>
                                                    {(item.collectionRules || []).map((rule, index) => (
                                                        <HorizontalStack key={index} align="space-between" blockAlign="center" wrap={false}>
                                                            <Text variant="bodyMd">{rule.hostRegex ? `Host matches ${rule.hostRegex}` : `Tag ${rule.tagKey} = ${rule.tagValue}`}</Text>
                                                            <Button plain icon={DeleteMinor} accessibilityLabel="Remove rule"
                                                                onClick={() => updateCollectionRules(name, item.collectionRules.filter((_, i) => i !== index))} />
                                                        </HorizontalStack>
                                                    ))}
                                                    <HorizontalStack gap={3} blockAlign="end" wrap={false}>
                                                        <Box width="100%">
                                                            <TextField label="Host pattern (regex)" value={newHostPattern} onChange={setNewHostPattern} placeholder="^team-a-.*" autoComplete="off" />
                                                        </Box>
                                                        <Box width="100%">
                                                            <TextField label="or tag" value={newTag} onChange={setNewTag} placeholder="team=team-a" autoComplete="off" />
                                                        </Box>
                                                        <Button onClick={() => addCollectionRule(name, item.collectionRules)}>Add</Button>
                                                    </HorizontalStack>
                                                </VerticalStack>
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
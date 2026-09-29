import React, { useEffect, useState } from 'react'
import { Box, Button, HorizontalStack, LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor } from '@shopify/polaris-icons';
import func from "@/util/func"
import settingRequests from '../../api';
import { rolesOptions, getRoleDisplayName } from '../../roles/Roles';
import Dropdown from '../../../../components/layouts/Dropdown';

// Maps SSO groups (as sent in the SAML groups claim) to Akto roles, applied on every login.
function SamlGroupRoleMapping({ configType, savedMapping }) {
    const [mapping, setMapping] = useState({})
    const [newGroup, setNewGroup] = useState('')
    const [newRole, setNewRole] = useState('')
    const [customRoleOptions, setCustomRoleOptions] = useState([])
    const [saving, setSaving] = useState(false)

    useEffect(() => {
        setMapping(savedMapping || {})
    }, [savedMapping])

    useEffect(() => {
        settingRequests.getCustomRoles().then((resp) => {
            const roles = resp?.roles || []
            setCustomRoleOptions(roles.map(r => ({ label: r.name, value: r.name })))
        }).catch(() => {})
    }, [])

    const roleOptions = [...rolesOptions, ...customRoleOptions]

    const handleAdd = () => {
        const group = newGroup.trim()
        if (!group) {
            func.setToast(true, true, "Group cannot be empty")
            return
        }
        if (!newRole) {
            func.setToast(true, true, "Select an Akto role")
            return
        }
        if (mapping[group]) {
            func.setToast(true, true, "This group is already mapped. Remove it first to change.")
            return
        }
        setMapping({ ...mapping, [group]: newRole })
        setNewGroup('')
        setNewRole('')
    }

    const handleRemove = (group) => {
        const next = { ...mapping }
        delete next[group]
        setMapping(next)
    }

    const handleSave = async () => {
        setSaving(true)
        try {
            await settingRequests.saveSamlGroupRoleMapping(mapping, configType)
            func.setToast(true, false, "Group mappings saved successfully!")
        } catch (e) {
            // Error toast already shown by the request interceptor
        } finally {
            setSaving(false)
        }
    }

    const roleLabel = (role) => {
        const custom = customRoleOptions.find(r => r.value === role)
        return custom ? custom.label : getRoleDisplayName(role)
    }

    return (
        <LegacyCard title="Group to role mapping">
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <Text variant="bodyMd" color="subdued">
                        Users get the mapped role on every SSO login, for all products. If a user is in several mapped groups,
                        the most privileged role is used. Users in no mapped group keep their current role.
                        Enter the group exactly as your IdP sends it in the groups claim (Azure AD sends group Object IDs by default).
                    </Text>
                    {Object.keys(mapping).length > 0 ? (
                        <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" padding="3">
                            <VerticalStack gap="2">
                                {Object.entries(mapping).map(([group, role]) => (
                                    <HorizontalStack key={group} align="space-between" blockAlign="center" wrap={false}>
                                        <Text variant="bodyMd" breakWord>{group}</Text>
                                        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                            <Text variant="bodyMd" fontWeight="medium">{roleLabel(role)}</Text>
                                            <Button plain icon={DeleteMinor} onClick={() => handleRemove(group)} accessibilityLabel="Remove mapping" />
                                        </HorizontalStack>
                                    </HorizontalStack>
                                ))}
                            </VerticalStack>
                        </Box>
                    ) : (
                        <Text variant="bodySm" color="subdued">No mappings yet.</Text>
                    )}
                    <HorizontalStack gap="3" blockAlign="end" wrap={false}>
                        <Box minWidth="200px" width="100%">
                            <TextField label="Group" value={newGroup} onChange={setNewGroup} placeholder="Group name or Object ID" autoComplete="off" />
                        </Box>
                        <Box minWidth="200px" width="100%">
                            <Dropdown
                                key={`akto-role-${Object.keys(mapping).length}`}
                                id="saml-group-akto-role"
                                label="Akto role"
                                menuItems={roleOptions}
                                initial={newRole}
                                selected={(role) => setNewRole(role || '')}
                            />
                        </Box>
                        <Button onClick={handleAdd}>Add</Button>
                    </HorizontalStack>
                    <HorizontalStack align="end">
                        <Button primary loading={saving} onClick={handleSave}>Save</Button>
                    </HorizontalStack>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default SamlGroupRoleMapping

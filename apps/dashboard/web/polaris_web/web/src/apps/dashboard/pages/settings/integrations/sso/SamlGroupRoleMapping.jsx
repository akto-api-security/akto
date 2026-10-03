import React, { useEffect, useState } from 'react'
import { Badge, Banner, Box, Button, Checkbox, Form, HorizontalStack, LegacyCard, Select, Text, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor } from '@shopify/polaris-icons';
import func from "@/util/func"
import settingRequests from '../../api';
import { rolesOptions, getRoleDisplayName } from '../../roles/roleUtils';
import { usePermissions } from "@/util/permissions"
import AllowedAction from '../../../../components/shared/AllowedAction';

// group names are stored as keys, which can't contain '.' or start with '$'
function groupError(group, mapping) {
    const trimmed = (group || '').trim()
    if (trimmed.length === 0) return 'Enter a group name or Object ID.'
    if (trimmed.includes('.') || trimmed.startsWith('$')) return "Group names can't contain '.' or start with '$'."
    if (mapping[trimmed]) return 'This group is already mapped. Remove it first to change its role.'
    return ''
}

// Maps SSO groups (as sent in the SAML groups claim) to Akto roles, applied on every login.
function SamlGroupRoleMapping({ configType, savedMapping, savedRemoveAccessWithoutGroup }) {
    const { canCall } = usePermissions()
    const [saved, setSaved] = useState({ mapping: {}, removeAccessWithoutGroup: false })
    const [mapping, setMapping] = useState({})
    const [removeAccessWithoutGroup, setRemoveAccessWithoutGroup] = useState(false)
    const [newGroup, setNewGroup] = useState('')
    const [newRole, setNewRole] = useState('')
    const [triedAdd, setTriedAdd] = useState(false)
    const [customRoleOptions, setCustomRoleOptions] = useState([])
    const [saving, setSaving] = useState(false)

    useEffect(() => {
        const initial = { mapping: savedMapping || {}, removeAccessWithoutGroup: savedRemoveAccessWithoutGroup === true }
        setSaved(initial)
        setMapping(initial.mapping)
        setRemoveAccessWithoutGroup(initial.removeAccessWithoutGroup)
    }, [savedMapping, savedRemoveAccessWithoutGroup])

    useEffect(() => {
        settingRequests.getCustomRoles().then((resp) => {
            const roles = resp?.roles || []
            setCustomRoleOptions(roles.map(r => ({ label: r.name, value: r.name })))
        }).catch(() => {})
    }, [])

    const roleOptions = [{ label: 'Pick a role', value: '', disabled: true }, ...rolesOptions, ...customRoleOptions]
    const hasMapping = Object.keys(mapping).length > 0
    const changed = JSON.stringify(mapping) !== JSON.stringify(saved.mapping) || removeAccessWithoutGroup !== saved.removeAccessWithoutGroup
    const addError = groupError(newGroup, mapping) || (newRole ? '' : 'Pick a role.')

    const handleAdd = () => {
        setTriedAdd(true)
        if (addError) return
        setMapping({ ...mapping, [newGroup.trim()]: newRole })
        setNewGroup('')
        setNewRole('')
        setTriedAdd(false)
    }

    const handleRemove = (group) => {
        const next = { ...mapping }
        delete next[group]
        setMapping(next)
        // with no group left, nobody can be "in a mapped group"
        if (Object.keys(next).length === 0) setRemoveAccessWithoutGroup(false)
    }

    const handleSave = async () => {
        setSaving(true)
        try {
            await settingRequests.saveSamlGroupRoleMapping(mapping, configType, removeAccessWithoutGroup && hasMapping)
            setSaved({ mapping, removeAccessWithoutGroup: removeAccessWithoutGroup && hasMapping })
            func.setToast(true, false, "Group mapping saved")
        } catch (e) {
            // Error toast already shown by the request interceptor
        } finally {
            setSaving(false)
        }
    }

    const roleLabel = (role) => {
        if (customRoleOptions.some(r => r.value === role)) return role
        return rolesOptions.some(r => r.value === role) ? getRoleDisplayName(role) : null
    }

    return (
        <LegacyCard title="Group to role mapping">
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <Text variant="bodyMd" color="subdued">
                        Users get the mapped role on every SSO login, for all products. If a user is in several mapped groups,
                        the most privileged role is used. Users in no mapped group keep their current role, unless the option below is on.
                        Enter the group exactly as your IdP sends it in the groups claim (Azure AD sends group Object IDs by default).
                    </Text>
                    {hasMapping ? (
                        <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" padding="3">
                            <VerticalStack gap="2">
                                {Object.entries(mapping).map(([group, role]) => (
                                    <HorizontalStack key={group} align="space-between" blockAlign="center" wrap={false} gap="4">
                                        <Text variant="bodyMd" breakWord>{group}</Text>
                                        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                            {roleLabel(role)
                                                ? <Text variant="bodyMd" fontWeight="medium">{roleLabel(role)}</Text>
                                                : <Badge status="critical">{`${role} (deleted)`}</Badge>}
                                            <Button plain icon={DeleteMinor} onClick={() => handleRemove(group)} accessibilityLabel={`Remove mapping for ${group}`} />
                                        </HorizontalStack>
                                    </HorizontalStack>
                                ))}
                            </VerticalStack>
                        </Box>
                    ) : (
                        <Text variant="bodySm" color="subdued">No groups mapped yet.</Text>
                    )}
                    <Form onSubmit={handleAdd}>
                        <HorizontalStack gap="3" blockAlign="start" wrap={false}>
                            <Box minWidth="200px" width="100%">
                                <TextField label="Group" value={newGroup} onChange={(value) => { setNewGroup(value); setTriedAdd(false) }}
                                    placeholder="Group name or Object ID" autoComplete="off"
                                    error={triedAdd && groupError(newGroup, mapping) ? groupError(newGroup, mapping) : undefined} />
                            </Box>
                            <Box minWidth="200px" width="100%">
                                <Select label="Akto role" options={roleOptions} value={newRole} onChange={setNewRole}
                                    error={triedAdd && !groupError(newGroup, mapping) && !newRole ? 'Pick a role.' : undefined} />
                            </Box>
                            <Box paddingBlockStart="6">
                                <Button submit>Add</Button>
                            </Box>
                        </HorizontalStack>
                    </Form>
                    <Checkbox
                        label="Remove access for users in none of the mapped groups"
                        helpText={hasMapping
                            ? "On each SSO login, a user who is in none of the groups above gets no access in any product, instead of keeping their current role. Admins are never changed."
                            : "Map at least one group first."}
                        checked={removeAccessWithoutGroup}
                        disabled={!hasMapping}
                        onChange={setRemoveAccessWithoutGroup}
                    />
                    {removeAccessWithoutGroup && hasMapping ? (
                        <Banner status="warning">
                            <Text as="p">Check that your IdP sends the groups claim before saving. Users who are in none of these groups lose access at their next login.</Text>
                        </Banner>
                    ) : null}
                    <HorizontalStack align="end" gap="3" blockAlign="center">
                        {changed ? <Text variant="bodySm" color="subdued">Unsaved changes</Text> : null}
                        <AllowedAction allowed={canCall('api/saveSamlGroupRoleMapping')}>
                        <Button primary loading={saving} disabled={!changed} onClick={handleSave}>Save</Button>
                        </AllowedAction>
                    </HorizontalStack>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default SamlGroupRoleMapping

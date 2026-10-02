import { Badge, Banner, Box, EmptyState, Form, FormLayout, HorizontalStack, LegacyCard, Modal, Page, ResourceItem, ResourceList, Select, Text, TextField, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import func from "@/util/func";
import settingRequests from "../api";
import { rolesOptions, getRoleDisplayName, collectionsSummary, usageSummary, roleNameError } from "./roleUtils";

export { rolesOptions, getRoleDisplayName }

export const roleDetailsUrl = (name) => `/dashboard/settings/roles/details?name=${encodeURIComponent(name)}`

/*
 * New role, or a copy of an existing one. A copy keeps everything except the name and "default for invites".
 */
export function CreateRoleModal({ open, onClose, source, existingNames, onCreated }) {
    const [name, setName] = useState('')
    const [baseRole, setBaseRole] = useState('GUEST')
    const [touched, setTouched] = useState(false)
    const [saving, setSaving] = useState(false)

    useEffect(() => {
        if (open) {
            setName(source ? `${source.name}_COPY` : '')
            setBaseRole(source?.baseRole || 'GUEST')
            setTouched(false)
        }
    }, [open, source])

    const error = roleNameError(name, existingNames)

    const create = async () => {
        setTouched(true)
        if (error) return
        setSaving(true)
        try {
            const roleName = name.trim().toUpperCase()
            await settingRequests.createCustomRole(source?.apiCollectionsId || [], roleName, baseRole, false, false, source ? {
                permissionOverrides: source.permissionOverrides || {},
                collectionRules: source.collectionRules || [],
                assignableRoles: source.assignableRoles || [],
                threatProtectionEnabled: source.threatProtectionEnabled === true,
            } : {})
            func.setToast(true, false, source ? `${roleName} created from ${source.name}` : `${roleName} created`)
            onCreated(roleName)
        } catch (e) {
            // the server's message is already shown
        } finally {
            setSaving(false)
        }
    }

    return (
        <Modal
            open={open}
            onClose={onClose}
            title={source ? `Copy ${source.name}` : "Create role"}
            primaryAction={{ content: source ? 'Create copy' : 'Create', onAction: create, loading: saving }}
            secondaryActions={[{ content: 'Cancel', onAction: onClose }]}
        >
            <Modal.Section>
                <Form onSubmit={create}>
                    <FormLayout>
                        <TextField
                            label="Role name"
                            value={name}
                            onChange={(value) => { setName(value); setTouched(true) }}
                            helpText="Letters, numbers, - and _. Saved in capitals."
                            error={touched && error ? error : undefined}
                            autoComplete="off"
                            autoFocus
                        />
                        <Select
                            label="Start from"
                            options={rolesOptions}
                            value={baseRole}
                            onChange={setBaseRole}
                            helpText={source ? "Collections, rules and permission changes are copied too." : "The role gets this built-in role's permissions. You can change them next."}
                        />
                    </FormLayout>
                </Form>
            </Modal.Section>
        </Modal>
    )
}

const Roles = () => {
    const navigate = useNavigate()
    const userRole = window.USER_ROLE
    const isLocalDeploy = func.checkLocal()
    const canEdit = userRole === 'ADMIN' && !isLocalDeploy

    const [roles, setRoles] = useState([])
    const [roleUsage, setRoleUsage] = useState({})
    const [loading, setLoading] = useState(false)
    const [loadFailed, setLoadFailed] = useState(null) // null, or why loading failed
    const [createModal, setCreateModal] = useState({ open: false, source: null })

    const loadRoles = async () => {
        setLoading(true)
        setLoadFailed(null)
        try {
            const response = await settingRequests.getCustomRoles()
            setRoles(response?.roles || [])
            setRoleUsage(response?.roleUsage || {})
        } catch (e) {
            setLoadFailed(e?.response?.status === 403 ? "You don't have access to roles in this product." : "Check your connection and try again.")
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (userRole !== 'GUEST') {
            loadRoles()
        }
    }, [])

    const existingNames = roles.map(r => r.name)

    const createDisabledReason = isLocalDeploy ? "Custom roles aren't available on local deployments."
        : userRole !== 'ADMIN' ? "Only admins can create roles." : null

    const renderItem = (role) => {
        const changes = Object.keys(role.permissionOverrides || {}).length
        return (
            <ResourceItem
                id={role.name}
                onClick={() => navigate(roleDetailsUrl(role.name))}
                accessibilityLabel={`Open ${role.name}`}
                shortcutActions={canEdit ? [{ content: 'Copy', accessibilityLabel: `Copy ${role.name}`, onAction: () => setCreateModal({ open: true, source: role }) }] : []}
                persistActions
            >
                <HorizontalStack align="space-between" blockAlign="center" gap="4">
                    <VerticalStack gap="2">
                        <HorizontalStack gap="2" blockAlign="center">
                            <Text variant="bodyMd" fontWeight="semibold" as="h3">{role.name}</Text>
                            {role.defaultInviteRole ? <Badge status="info">Default for invites</Badge> : null}
                        </HorizontalStack>
                        <HorizontalStack gap="2">
                            <Badge>{`Based on ${getRoleDisplayName(role.baseRole)}`}</Badge>
                            <Badge>{collectionsSummary(role)}</Badge>
                            {changes > 0 ? <Badge status="attention">{`${changes} permission change${changes === 1 ? '' : 's'}`}</Badge> : null}
                        </HorizontalStack>
                    </VerticalStack>
                    <Box paddingInlineEnd="4">
                        <Text variant="bodySm" color="subdued">{usageSummary(roleUsage[role.name])}</Text>
                    </Box>
                </HorizontalStack>
            </ResourceItem>
        )
    }

    const emptyState = (
        <EmptyState
            heading="No custom roles yet"
            action={canEdit ? { content: 'Create role', onAction: () => setCreateModal({ open: true, source: null }) } : undefined}
            image="/public/images/emptystate-files.png"
        >
            <p>Custom roles start from a built-in role. You can change what they can do and limit them to some collections.</p>
        </EmptyState>
    )

    return (
        <Page
            title="Roles"
            subtitle="Custom roles start from a built-in role. Change what they can do and which collections they see."
            primaryAction={{
                content: 'Create role',
                onAction: () => setCreateModal({ open: true, source: null }),
                disabled: createDisabledReason !== null,
                helpText: createDisabledReason || undefined,
            }}
            divider
        >
            <VerticalStack gap="4">
                {loadFailed ? (
                    <Banner status="critical" title="Couldn't load roles" action={{ content: 'Try again', onAction: loadRoles }}>
                        <p>{loadFailed}</p>
                    </Banner>
                ) : null}
                <LegacyCard>
                    <ResourceList
                        resourceName={{ singular: 'role', plural: 'roles' }}
                        items={roles}
                        renderItem={renderItem}
                        loading={loading}
                        emptyState={!loading && !loadFailed ? emptyState : undefined}
                        showHeader={roles.length > 0}
                        headerContent={`${roles.length} role${roles.length === 1 ? '' : 's'}`}
                    />
                </LegacyCard>
            </VerticalStack>
            <CreateRoleModal
                open={createModal.open}
                source={createModal.source}
                existingNames={existingNames}
                onClose={() => setCreateModal({ open: false, source: null })}
                onCreated={(roleName) => {
                    setCreateModal({ open: false, source: null })
                    navigate(roleDetailsUrl(roleName))
                }}
            />
        </Page>
    )
}

export default Roles;

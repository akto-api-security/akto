import { Badge, Banner, Box, Button, Checkbox, EmptyState, Form, HorizontalGrid, HorizontalStack, LegacyCard, Modal, Page, ResourceItem, ResourceList, Select, Spinner, Tag, Text, TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import func from "@/util/func";
import settingRequests from "../api";
import PersistStore from "../../../../main/PersistStore";
import DetailsPage from "../../../components/DetailsPage";
import SearchableResourceList from "../../../components/shared/SearchableResourceList";
import { usersCollectionRenderItem } from "../rbac/utils";
import {
    rolesOptions, getRoleDisplayName, PERMISSION_GROUPS, ROLE_DEFAULT, ACCESS_LABELS,
    editablePermissions, isLimitedToCollections, isGivableByTeamAdmin, describeRule, usageSummary, hostPatternError, tagRuleError
} from "./roleUtils";
import { roleDetailsUrl, CreateRoleModal } from "./Roles";
import { usePermissions } from "@/util/permissions";

const PRODUCT_LABELS = { API: 'API Security', AGENTIC: 'Akto ARGUS', ENDPOINT: 'Akto ATLAS', DAST: 'DAST' }

// an empty feature map means a self-hosted deployment, where everything is granted
function isThreatFeatureGranted() {
    const stiggFeatures = window?.STIGG_FEATURE_WISE_ALLOWED
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
        return true
    }
    return stiggFeatures?.THREAT_DETECTION?.isGranted === true
}

function draftFrom(role) {
    return {
        baseRole: role.baseRole,
        defaultInviteRole: role.defaultInviteRole === true,
        permissionOverrides: editablePermissions(role),
        apiCollectionsId: [...(role.apiCollectionsId || [])].sort((a, b) => a - b),
        collectionRules: [...(role.collectionRules || [])],
        assignableRoles: [...(role.assignableRoles || [])].sort(),
    }
}

const sameDraft = (a, b) => JSON.stringify(a) === JSON.stringify(b)

function RoleDetails() {
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const roleName = searchParams.get('name')
    const { canCall } = usePermissions()
    const canEdit = canCall('api/updateCustomRole') && !func.checkLocal()
    const canDelete = canCall('api/deleteCustomRole')
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const threatGranted = isThreatFeatureGranted()

    const [loading, setLoading] = useState(true)
    const [loadFailed, setLoadFailed] = useState(null) // null, or why loading failed
    const [role, setRole] = useState(null)
    const [allRoles, setAllRoles] = useState([])
    const [usage, setUsage] = useState({})
    const [basePermissions, setBasePermissions] = useState({})
    const [members, setMembers] = useState([])
    const [draft, setDraft] = useState(null)
    const [saving, setSaving] = useState(false)
    const [collectionPicker, setCollectionPicker] = useState({ open: false, selected: [] })
    const [ruleType, setRuleType] = useState('HOST')
    const [ruleValue, setRuleValue] = useState('')
    const [ruleTouched, setRuleTouched] = useState(false)
    const [copyOpen, setCopyOpen] = useState(false)

    const load = async () => {
        setLoading(true)
        setLoadFailed(null)
        try {
            const [rolesResponse, team] = await Promise.all([
                settingRequests.getCustomRoles(),
                settingRequests.getTeamData().catch(() => []),
            ])
            const roles = rolesResponse?.roles || []
            const current = roles.find(r => r.name === roleName) || null
            setAllRoles(roles)
            setRole(current)
            setUsage(rolesResponse?.roleUsage?.[roleName] || {})
            setBasePermissions(rolesResponse?.baseRolePermissions || {})
            setMembers((team || []).filter(user => {
                const mapping = user?.scopeRoleMapping
                return mapping && Object.keys(mapping).length > 0 ? Object.values(mapping).includes(roleName) : user?.role === roleName
            }))
            setDraft(current ? draftFrom(current) : null)
        } catch (e) {
            setLoadFailed(e?.response?.status === 403 ? "You don't have access to this role in this product." : "Check your connection and try again.")
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => { load() }, [roleName])

    // collections the current product shows; ids of other products' collections are kept as they are on save
    const visibleCollectionIds = useMemo(() => new Set(Object.keys(collectionsMap || {}).map(id => parseInt(id, 10))), [collectionsMap])
    const allCollections = useMemo(() => Object.entries(collectionsMap || {}).map(([id, collectionName]) => ({ id: parseInt(id, 10), collectionName })), [collectionsMap])

    const original = useMemo(() => role ? draftFrom(role) : null, [role])
    const changed = draft && original && !sameDraft(draft, original)
    const update = (patch) => setDraft(prev => ({ ...prev, ...patch }))

    if (loading && !draft) {
        return <Page title={roleName || "Role"}><HorizontalStack align="center"><Spinner accessibilityLabel="Loading role" /></HorizontalStack></Page>
    }
    if (loadFailed) {
        return (
            <Page title={roleName || "Role"} backAction={{ content: 'Roles', onAction: () => navigate('/dashboard/settings/roles') }}>
                <Banner status="critical" title="Couldn't load this role" action={{ content: 'Try again', onAction: load }}>
                    <Text as="p">{loadFailed}</Text>
                </Banner>
            </Page>
        )
    }
    if (!role || !draft) {
        return (
            <Page title="Role not found" backAction={{ content: 'Roles', onAction: () => navigate('/dashboard/settings/roles') }}>
                <LegacyCard sectioned>
                    <EmptyState heading="This role doesn't exist anymore" image="/public/images/emptystate-files.png"
                        action={{ content: 'Back to roles', onAction: () => navigate('/dashboard/settings/roles') }}>
                        <Text as="p">It may have been deleted by another admin.</Text>
                    </EmptyState>
                </LegacyCard>
            </Page>
        )
    }

    const baseAccess = (feature) => basePermissions?.[draft.baseRole]?.[feature] || 'READ'
    const effectiveAccess = (feature) => {
        const own = draft.permissionOverrides[feature]
        if (own) return own
        // threat settings follow threat protection unless changed on their own
        if (feature === 'THREAT_SETTINGS' && draft.permissionOverrides.THREAT_PROTECTION) return draft.permissionOverrides.THREAT_PROTECTION
        return baseAccess(feature)
    }
    const setPermission = (feature, access) => {
        const permissionOverrides = { ...draft.permissionOverrides }
        if (access === ROLE_DEFAULT) delete permissionOverrides[feature]
        else permissionOverrides[feature] = access
        update({ permissionOverrides })
    }
    const changeCount = Object.keys(draft.permissionOverrides).length
    const otherDefaultRole = allRoles.find(r => r.name !== role.name && r.defaultInviteRole)
    const draftLimited = isLimitedToCollections(draft)
    const inUse = (usage.users || 0) + (usage.invites || 0) > 0

    const detailsCard = (
        <LegacyCard title="Details" key="details" sectioned>
            <VerticalStack gap="4">
                <HorizontalGrid columns={{ xs: 1, md: 2 }} gap="4">
                    <Select
                        label="Based on"
                        options={rolesOptions}
                        value={draft.baseRole}
                        onChange={(baseRole) => update({ baseRole })}
                        disabled={!canEdit}
                        helpText="The role starts with this built-in role's permissions."
                    />
                    <Box paddingBlockStart="6">
                        <Checkbox
                            label="Default role for new invites"
                            checked={draft.defaultInviteRole}
                            disabled={!canEdit || (!!otherDefaultRole && !draft.defaultInviteRole)}
                            onChange={(defaultInviteRole) => update({ defaultInviteRole })}
                            helpText={otherDefaultRole && !draft.defaultInviteRole
                                ? `${otherDefaultRole.name} is the default now. Turn it off there first.`
                                : "Pre-selected when inviting, and given to people who join from your company's domain."}
                        />
                    </Box>
                </HorizontalGrid>
                {draft.baseRole === 'ADMIN' && draftLimited ? (
                    <Banner status="info">
                        <Text as="p">Users with this role are limited to some collections, so they can't manage users, roles or SSO.</Text>
                    </Banner>
                ) : null}
            </VerticalStack>
        </LegacyCard>
    )

    const accessOptions = (feature) => [
        { label: `Default (${ACCESS_LABELS[baseAccess(feature)] || baseAccess(feature)})`, value: ROLE_DEFAULT },
        { label: ACCESS_LABELS.NO_ACCESS, value: 'NO_ACCESS' },
        { label: ACCESS_LABELS.READ, value: 'READ' },
        { label: ACCESS_LABELS.READ_WRITE, value: 'READ_WRITE' },
    ]
    const permissionsCard = (
        <LegacyCard
            key="permissions"
            title="Permissions"
            actions={canEdit && changeCount > 0 ? [{ content: 'Reset to base role', onAction: () => update({ permissionOverrides: {} }) }] : undefined}
        >
            <LegacyCard.Section>
                <Text color="subdued">
                    {changeCount > 0
                        ? `${changeCount} permission${changeCount === 1 ? '' : 's'} changed from ${getRoleDisplayName(draft.baseRole)}. Everything else is the same.`
                        : `Same as ${getRoleDisplayName(draft.baseRole)}. Change only what this role needs.`}
                </Text>
            </LegacyCard.Section>
            {PERMISSION_GROUPS.filter(group => threatGranted || group.title !== 'Threat protection').map(group => (
                <LegacyCard.Section key={group.title} title={group.title}>
                    <VerticalStack gap="3">
                        {group.features.map(({ feature, label }) => {
                            const isChanged = !!draft.permissionOverrides[feature]
                            const followsThreat = feature === 'THREAT_SETTINGS' && !isChanged && !!draft.permissionOverrides.THREAT_PROTECTION
                            return (
                                <HorizontalStack key={feature} align="space-between" blockAlign="center" gap="4" wrap={false}>
                                    <VerticalStack gap="1">
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <Text>{label}</Text>
                                            {isChanged ? <Badge status="attention">Changed</Badge> : null}
                                        </HorizontalStack>
                                        {followsThreat ? <Text variant="bodySm" color="subdued">{`Follows threat protection: ${ACCESS_LABELS[effectiveAccess(feature)]}`}</Text> : null}
                                    </VerticalStack>
                                    <Box minWidth="220px">
                                        <Select
                                            label={label}
                                            labelHidden
                                            options={accessOptions(feature)}
                                            value={draft.permissionOverrides[feature] || ROLE_DEFAULT}
                                            onChange={(access) => setPermission(feature, access)}
                                            disabled={!canEdit}
                                        />
                                    </Box>
                                </HorizontalStack>
                            )
                        })}
                    </VerticalStack>
                </LegacyCard.Section>
            ))}
        </LegacyCard>
    )

    const visibleSelected = draft.apiCollectionsId.filter(id => visibleCollectionIds.has(id))
    const hiddenSelected = draft.apiCollectionsId.filter(id => !visibleCollectionIds.has(id))
    const ruleError = ruleType === 'HOST' ? hostPatternError(ruleValue) : tagRuleError(ruleValue)
    const addRule = () => {
        setRuleTouched(true)
        if (ruleError) return
        const value = ruleValue.trim()
        let rule = { hostRegex: value }
        if (ruleType === 'TAG') {
            const [tagKey, ...rest] = value.split('=')
            rule = { tagKey: tagKey.trim(), tagValue: rest.join('=').trim() }
        }
        if (draft.collectionRules.some(r => describeRule(r) === describeRule(rule))) {
            func.setToast(true, true, "This rule is already added.")
            return
        }
        update({ collectionRules: [...draft.collectionRules, rule] })
        setRuleValue('')
        setRuleTouched(false)
    }
    const collectionsText = !draftLimited
        ? "Users with this role see all collections."
        : `Users with this role see only ${[
            draft.apiCollectionsId.length > 0 ? `${draft.apiCollectionsId.length} chosen collection${draft.apiCollectionsId.length === 1 ? '' : 's'}` : null,
            draft.collectionRules.length > 0 ? `collections matching ${draft.collectionRules.length} rule${draft.collectionRules.length === 1 ? '' : 's'}` : null,
        ].filter(Boolean).join(' and ')}.`

    const collectionsCard = (
        <LegacyCard title="Collections" key="collections">
            <LegacyCard.Section>
                <Banner status={draftLimited ? "info" : undefined}>
                    <Text as="p">{collectionsText} Leave both empty to give access to all collections.</Text>
                </Banner>
            </LegacyCard.Section>
            <LegacyCard.Section title="Chosen collections">
                <VerticalStack gap="3">
                    {visibleSelected.length > 0 ? (
                        <HorizontalStack gap="2">
                            {visibleSelected.slice(0, 15).map(id => (
                                <Tag key={id} onRemove={canEdit ? () => update({ apiCollectionsId: draft.apiCollectionsId.filter(x => x !== id) }) : undefined}>
                                    {collectionsMap[id]}
                                </Tag>
                            ))}
                            {visibleSelected.length > 15 ? <Text color="subdued">{`and ${visibleSelected.length - 15} more`}</Text> : null}
                        </HorizontalStack>
                    ) : <Text color="subdued">No collections chosen in this product.</Text>}
                    {hiddenSelected.length > 0 ? (
                        <Text variant="bodySm" color="subdued">{`Also ${hiddenSelected.length} collection${hiddenSelected.length === 1 ? '' : 's'} from other products, or deleted. They stay as they are.`}</Text>
                    ) : null}
                    {canEdit ? (
                        <HorizontalStack>
                            <Button onClick={() => setCollectionPicker({ open: true, selected: visibleSelected })}>Choose collections</Button>
                        </HorizontalStack>
                    ) : null}
                </VerticalStack>
            </LegacyCard.Section>
            <LegacyCard.Section title="Also include collections matching">
                <VerticalStack gap="3">
                    <Text variant="bodySm" color="subdued">Collections added later that match a rule are included automatically.</Text>
                    {draft.collectionRules.length > 0 ? (
                        <HorizontalStack gap="2">
                            {draft.collectionRules.map((rule, index) => (
                                <Tag key={describeRule(rule)} onRemove={canEdit ? () => update({ collectionRules: draft.collectionRules.filter((_, i) => i !== index) }) : undefined}>
                                    {describeRule(rule)}
                                </Tag>
                            ))}
                        </HorizontalStack>
                    ) : null}
                    {canEdit ? (
                        <Form onSubmit={addRule}>
                            <HorizontalStack gap="3" blockAlign="start" wrap={false}>
                                <Box minWidth="160px">
                                    <Select
                                        label="Match by"
                                        options={[{ label: 'Host name', value: 'HOST' }, { label: 'Tag', value: 'TAG' }]}
                                        value={ruleType}
                                        onChange={(value) => { setRuleType(value); setRuleTouched(false) }}
                                    />
                                </Box>
                                <Box width="100%">
                                    <TextField
                                        label={ruleType === 'HOST' ? "Host pattern" : "Tag"}
                                        value={ruleValue}
                                        onChange={(value) => { setRuleValue(value); setRuleTouched(true) }}
                                        placeholder={ruleType === 'HOST' ? "^team-a-.*\\.example\\.com$" : "team=team-a"}
                                        helpText={ruleType === 'HOST' ? "A regular expression. Use ^ and $ to match the whole host name." : "key=value, as set on the collection."}
                                        error={ruleTouched && ruleValue.length > 0 && ruleError ? ruleError : undefined}
                                        autoComplete="off"
                                    />
                                </Box>
                                <Box paddingBlockStart="6" minWidth="96px">
                                    <Button submit disabled={ruleValue.trim().length === 0}>Add rule</Button>
                                </Box>
                            </HorizontalStack>
                        </Form>
                    ) : null}
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const givableRoles = allRoles.filter(r => r.name !== role.name && isGivableByTeamAdmin(r))
    const canInvite = effectiveAccess('INVITE_MEMBERS') === 'READ_WRITE'
    const teamAdminCard = (
        <LegacyCard title="Roles this role can give" key="team-admin" sectioned>
            <VerticalStack gap="3">
                <Text color="subdued">
                    For team admins: users of this role who are limited to collections can invite people and change roles only to these roles.
                    Users who see all collections follow the usual role levels instead.
                </Text>
                {!canInvite ? (
                    <Banner status="info">
                        <Text as="p">This role can't invite users. Set "Invite users and change their roles" to Read and write to use this list.</Text>
                    </Banner>
                ) : null}
                {givableRoles.length === 0 ? (
                    <Text color="subdued">No other roles are limited to collections yet. Only those can be given by team admins.</Text>
                ) : (
                    <HorizontalStack gap="4">
                        {givableRoles.map(r => (
                            <Checkbox
                                key={r.name}
                                label={r.name}
                                checked={draft.assignableRoles.includes(r.name)}
                                disabled={!canEdit}
                                onChange={(checked) => update({
                                    assignableRoles: (checked ? [...draft.assignableRoles, r.name] : draft.assignableRoles.filter(x => x !== r.name)).sort()
                                })}
                            />
                        ))}
                    </HorizontalStack>
                )}
            </VerticalStack>
        </LegacyCard>
    )

    const usersCard = (
        <LegacyCard title={`Users with this role (${usageSummary(usage)})`} key="users">
            {members.length === 0 ? (
                <LegacyCard.Section>
                    <Text color="subdued">{(usage.invites || 0) > 0 ? "Only pending invites use this role." : "Nobody has this role yet. Give it from the Users page."}</Text>
                </LegacyCard.Section>
            ) : (
                <ResourceList
                    resourceName={{ singular: 'user', plural: 'users' }}
                    items={members.slice(0, 50)}
                    renderItem={(user) => {
                        const products = Object.entries(user?.scopeRoleMapping || {}).filter(([, r]) => r === roleName).map(([scope]) => PRODUCT_LABELS[scope] || scope)
                        return (
                            <ResourceItem id={String(user.id)} onClick={() => navigate('/dashboard/settings/users')} accessibilityLabel={`Open users`}>
                                <HorizontalStack align="space-between" blockAlign="center">
                                    <VerticalStack gap="1">
                                        <Text fontWeight="semibold">{user.name && user.name !== '-' ? user.name : user.login}</Text>
                                        <Text variant="bodySm" color="subdued">{user.login}</Text>
                                    </VerticalStack>
                                    <HorizontalStack gap="2">{products.map(p => <Badge key={p}>{p}</Badge>)}</HorizontalStack>
                                </HorizontalStack>
                            </ResourceItem>
                        )
                    }}
                />
            )}
        </LegacyCard>
    )

    const save = async () => {
        setSaving(true)
        try {
            const permissionOverrides = { ...draft.permissionOverrides }
            const assignableRoles = draft.assignableRoles.filter(name => givableRoles.some(r => r.name === name))
            // threat access is saved as a permission change; the older checkbox is no longer used
            await settingRequests.updateCustomRole(draft.apiCollectionsId, role.name, draft.baseRole, draft.defaultInviteRole, false,
                permissionOverrides, draft.collectionRules, assignableRoles)
            func.setToast(true, false, "Role saved")
            await load()
        } catch (e) {
            // the server's message is already shown
        } finally {
            setSaving(false)
        }
    }

    const deleteRole = () => {
        func.showConfirmationModal(`Delete ${role.name}? This can't be undone.`, "Delete role", async () => {
            try {
                await settingRequests.deleteCustomRole(role.name)
                func.setToast(true, false, `${role.name} deleted`)
                navigate('/dashboard/settings/roles')
            } catch (e) {
                // the server's message is already shown
            }
        })
    }

    const secondaryActions = canEdit ? (
        <HorizontalStack gap="2">
            <Button onClick={() => setCopyOpen(true)}>Copy</Button>
            {!canDelete ? null : inUse ? (
                <Tooltip content={`Used by ${usageSummary(usage)}. Give them another role first.`}>
                    <Button destructive disabled>Delete</Button>
                </Tooltip>
            ) : <Button destructive outline onClick={deleteRole}>Delete</Button>}
        </HorizontalStack>
    ) : null

    const components = [detailsCard, permissionsCard, collectionsCard, teamAdminCard, usersCard]
    if (!canEdit) {
        components.unshift(
            <Banner key="read-only" status="info"><Text as="p">Only admins of all collections can change roles.</Text></Banner>
        )
    }

    return (
        <>
            <DetailsPage
                pageTitle={role.name}
                backUrl="/dashboard/settings/roles"
                titleMetadata={draft.defaultInviteRole ? <Badge status="info">Default for invites</Badge> : undefined}
                subtitle={`Based on ${getRoleDisplayName(role.baseRole)} · ${usageSummary(usage)}`}
                secondaryActions={secondaryActions}
                saveAction={save}
                discardAction={() => { setDraft(draftFrom(role)); setRuleValue(''); setRuleTouched(false) }}
                isDisabled={() => !changed || !canEdit}
                isSaving={saving}
                components={components}
            />
            <Modal
                open={collectionPicker.open}
                onClose={() => setCollectionPicker({ open: false, selected: [] })}
                title="Choose collections"
                large
                primaryAction={{
                    content: 'Done',
                    onAction: () => {
                        update({ apiCollectionsId: [...hiddenSelected, ...collectionPicker.selected].sort((a, b) => a - b) })
                        setCollectionPicker({ open: false, selected: [] })
                    }
                }}
                secondaryActions={[{ content: 'Cancel', onAction: () => setCollectionPicker({ open: false, selected: [] }) }]}
            >
                <Modal.Section>
                    <SearchableResourceList
                        key={collectionPicker.open ? 'open' : 'closed'}
                        resourceName={'collection'}
                        items={allCollections}
                        renderItem={usersCollectionRenderItem}
                        isFilterControlEnabale={true}
                        selectable={true}
                        onSelectedItemsChange={(selected) => setCollectionPicker(prev => ({ ...prev, selected: (selected || []).map(id => parseInt(id, 10)) }))}
                        alreadySelectedItems={collectionPicker.selected}
                    />
                </Modal.Section>
            </Modal>
            <CreateRoleModal open={copyOpen} source={role} existingNames={allRoles.map(r => r.name)} onClose={() => setCopyOpen(false)}
                onCreated={(name) => { setCopyOpen(false); navigate(roleDetailsUrl(name)) }} />
        </>
    )
}

export default RoleDetails

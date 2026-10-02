import { Avatar, Badge, Banner, HorizontalStack, LegacyCard, Link, Modal, Page, ResourceItem, ResourceList, Text, VerticalStack } from "@shopify/polaris"
import { useEffect, useMemo, useState } from "react";
import settingRequests from "../api";
import func from "@/util/func";
import InviteUserModal from "./InviteUserModal";
import EditAccessModal from "./EditAccessModal";
import PersistStore from "../../../../main/PersistStore";
import { categoryToShortName, getDashboardCategory } from "../../../../main/labelHelper";
import SearchableResourceList from "../../../components/shared/SearchableResourceList";
import observeApi from "../../observe/api";
import { usersCollectionRenderItem } from "../rbac/utils";
import { getRoleDisplayName } from "../roles/roleUtils";

const NO_COLLECTION_ID = -2147483648 // placeholder the server uses for "no collections"; never a real grant

/**
 * Gets available product scopes based on user's feature access.
 * Maps feature flags to product scopes:
 * - API Security: always available (default)
 * - Akto ARGUS: requires SECURITY_TYPE_AGENTIC feature
 * - Akto ATLAS: requires ENDPOINT_SECURITY feature
 * - DAST: requires AKTO_DAST feature
 */
const getAvailableProductScopes = () => {
    const { agenticSecurityGranted, endpointSecurityGranted, dastGranted } = func.getStiggFeatureGrants()

    const scopes = [
        { label: 'API Security', value: 'API' } // Always available
    ]

    // Add scopes based on feature access
    if (agenticSecurityGranted) {
        scopes.push({ label: 'Akto ARGUS', value: 'AGENTIC' })
    }

    if (endpointSecurityGranted) {
        scopes.push({ label: 'Akto ATLAS', value: 'ENDPOINT' })
    }

    if (dastGranted) {
        scopes.push({ label: 'DAST', value: 'DAST' })
    }

    return scopes
}

const BUILT_IN_ROLES = ['ADMIN', 'MEMBER', 'DEVELOPER', 'GUEST', 'THREAT_ENGINEER', 'THREAT_VIEWER', 'NO_ACCESS']
const PAID_ROLES = ['DEVELOPER', 'GUEST', 'THREAT_ENGINEER', 'THREAT_VIEWER']
const roleLabel = (role) => role === 'NO_ACCESS' ? 'No access' : getRoleDisplayName(role === 'SECURITY ENGINEER' ? 'MEMBER' : role)

const Users = () => {
    const PRODUCT_SCOPES = useMemo(() => getAvailableProductScopes(), [])
    const username = window.USER_NAME
    const userRole = window.USER_ROLE
    const isAdmin = userRole === 'ADMIN'
    const isLocalDeploy = func.checkLocal()
    const rbacAccess = func.checkForRbacFeatureBasic()
    const rbacAccessAdvanced = func.checkForRbacFeature()
    const currentProduct = categoryToShortName[getDashboardCategory()] || "API"
    const notOnPremHostnames = ["app.akto.io", "localhost", "127.0.0.1", "[::1]"]
    const isOnPrem = !notOnPremHostnames.includes(window.location.hostname)

    const collectionsMap = PersistStore(state => state.collectionsMap)
    const [loading, setLoading] = useState(false)
    const [loadFailed, setLoadFailed] = useState(null) // null, or why loading failed
    const [users, setUsers] = useState([])
    const [usersCollection, setUsersCollection] = useState({})
    const [allowedRoles, setAllowedRoles] = useState([]) // roles the caller may give, from the server
    const [customRoles, setCustomRoles] = useState([])
    const [defaultInviteRole, setDefaultInviteRole] = useState('MEMBER')
    const [inviteOpen, setInviteOpen] = useState(false)
    const [editing, setEditing] = useState(null)
    const [collectionsFor, setCollectionsFor] = useState(null) // { user, selected }

    const loadRoles = async () => {
        try {
            let hierarchy = await settingRequests.getRoleHierarchy() || []
            // team admins (users limited to collections) can only give the roles their custom role lists
            const assignable = await settingRequests.fetchAssignableRoles().catch(() => ({}))
            const teamAdminRoles = assignable?.assignableRoles
            const rolesResponse = await settingRequests.getCustomRoles().catch(() => ({}))
            const roles = rolesResponse?.roles || []
            setCustomRoles(roles)
            const defaultRole = roles.find(r => r.defaultInviteRole)
            if (defaultRole) setDefaultInviteRole(defaultRole.name)
            if (Array.isArray(teamAdminRoles)) {
                setAllowedRoles([...teamAdminRoles, 'NO_ACCESS'])
            } else {
                const customAllowed = roles.filter(r => hierarchy.includes(r.baseRole)).map(r => r.name)
                setAllowedRoles([...hierarchy, ...customAllowed, 'NO_ACCESS'])
            }
        } catch (e) {
            setAllowedRoles(['NO_ACCESS'])
        }
    }

    const loadUsers = async () => {
        setLoading(true)
        setLoadFailed(null)
        try {
            const team = await settingRequests.getTeamData()
            setUsers(team || [])
            if (isAdmin && rbacAccessAdvanced) {
                const collections = await observeApi.getAllUsersCollections().catch(() => ({}))
                setUsersCollection(collections || {})
            }
        } catch (e) {
            setLoadFailed(e?.response?.status === 403 ? "You don't have access to users in this product." : "Check your connection and try again.")
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        if (userRole !== 'GUEST') {
            loadUsers()
        }
        loadRoles()
    }, [])

    // products the caller can give roles in: admins manage every product, others only products they have a role in
    const manageableScopes = useMemo(() => {
        if (isAdmin) return PRODUCT_SCOPES
        const me = users.find(user => user.login === username)
        const mine = Object.entries(me?.scopeRoleMapping || {}).filter(([, role]) => role !== 'NO_ACCESS').map(([scope]) => scope)
        return mine.length > 0 ? PRODUCT_SCOPES.filter(scope => mine.includes(scope.value)) : PRODUCT_SCOPES
    }, [users, PRODUCT_SCOPES, username, isAdmin])

    const roleOptions = useMemo(() => {
        const builtIn = BUILT_IN_ROLES.filter(role => rbacAccess || !PAID_ROLES.includes(role))
        const all = [...builtIn.filter(r => r !== 'NO_ACCESS'), ...(rbacAccess ? customRoles.map(r => r.name) : []), 'NO_ACCESS']
        return all.filter(role => allowedRoles.includes(role)).map(role => ({ label: roleLabel(role), value: role }))
    }, [allowedRoles, customRoles, rbacAccess])

    const knownRoles = new Set([...BUILT_IN_ROLES, 'SECURITY ENGINEER', ...customRoles.map(r => r.name)])
    const canManageUser = (user) => {
        if (user.login === username || user.isInvitation) return false
        const mapping = user.scopeRoleMapping
        const held = mapping && Object.keys(mapping).length > 0 ? Object.values(mapping) : [user.role]
        // roles that no longer exist give no access, so they can be replaced
        return held.every(role => !role || role === 'NO_ACCESS' || !knownRoles.has(role) || allowedRoles.includes(role === 'SECURITY ENGINEER' ? 'MEMBER' : role))
    }

    const isAdminInCurrentProduct = (user) => {
        const mapping = user?.scopeRoleMapping
        return mapping && Object.keys(mapping).length > 0 ? mapping[currentProduct] === 'ADMIN' : user?.role === 'ADMIN'
    }

    const savedCollections = (user) => (usersCollection[user.id] || []).filter(id => id !== NO_COLLECTION_ID)

    const revokeInvite = (user) => {
        func.showConfirmationModal(`Revoke the invite for ${user.login}? The invite link stops working.`, "Revoke invite", async () => {
            try {
                await settingRequests.removeInvitation(user.login)
                func.setToast(true, false, `Invite for ${user.login} revoked`)
                loadUsers()
            } catch (e) {
                // the server's message is already shown
            }
        })
    }

    const saveCollections = async () => {
        const { user, selected } = collectionsFor
        // collections of other products (not shown here) stay as they are
        const hidden = savedCollections(user).filter(id => !(id in (collectionsMap || {})))
        try {
            await observeApi.updateUserCollections({ [user.id]: [...hidden, ...selected] })
            func.setToast(true, false, `Collections updated for ${user.login}`)
            setCollectionsFor(null)
            loadUsers()
        } catch (e) {
            // the server's message is already shown
        }
    }

    const accessBadges = (user) => {
        const mapping = user.scopeRoleMapping
        if (!mapping || Object.keys(mapping).length === 0) {
            return [<Badge key="all">{`All products: ${roleLabel(user.role)}`}</Badge>]
        }
        const order = (scope) => { const i = PRODUCT_SCOPES.findIndex(s => s.value === scope); return i < 0 ? 99 : i }
        const granted = Object.entries(mapping).filter(([, role]) => role !== 'NO_ACCESS').sort(([a], [b]) => order(a) - order(b))
        if (granted.length === 0) return [<Badge key="none" status="critical">No access</Badge>]
        return granted.map(([scope, role]) => {
            const product = PRODUCT_SCOPES.find(s => s.value === scope)?.label || scope
            const missing = !knownRoles.has(role)
            return <Badge key={scope} status={missing ? "critical" : undefined}>{`${product}: ${missing ? `${role} (deleted)` : roleLabel(role)}`}</Badge>
        })
    }

    const expiryBadge = (user) => {
        if (!user.accessExpiresAt) return null
        const date = new Date(user.accessExpiresAt * 1000)
        return user.accessExpiresAt * 1000 <= Date.now()
            ? <Badge status="critical">Access ended</Badge>
            : <Badge status="attention">{`Access ends ${date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}`}</Badge>
    }

    // an invite carries its issuer's id; issuers may revoke their own invites
    const myId = users.find(user => user.login === username && !user.isInvitation)?.id

    const renderItem = (user) => {
        const { id, name, login } = user
        const isSelf = login === username
        const shortcutActions = []
        if (canManageUser(user)) {
            if (isAdmin && rbacAccessAdvanced && !isAdminInCurrentProduct(user)) {
                const count = savedCollections(user).length
                // the list holds chosen collections only; a role limited by rules still limits the user
                const roleInProduct = (user.scopeRoleMapping && Object.keys(user.scopeRoleMapping).length > 0) ? user.scopeRoleMapping[currentProduct] : user.role
                const roleRules = (customRoles.find(r => r.name === roleInProduct)?.collectionRules || []).length > 0
                shortcutActions.push({
                    content: count > 0 ? `${count} collection${count === 1 ? '' : 's'}${roleRules ? ' + rules' : ''}` : roleRules ? 'Collections by rules' : 'All collections',
                    accessibilityLabel: `Collections for ${login}`,
                    onAction: () => setCollectionsFor({ user, selected: savedCollections(user).filter(cid => cid in (collectionsMap || {})) })
                })
            }
            shortcutActions.push({ content: 'Edit access', accessibilityLabel: `Edit access for ${login}`, onAction: () => setEditing(user) })
        } else if (user.isInvitation && (isAdmin || user.id === myId)) {
            shortcutActions.push({ content: 'Revoke invite', accessibilityLabel: `Revoke invite for ${login}`, onAction: () => revokeInvite(user) })
        }

        return (
            <ResourceItem
                id={`${id}-${login}`}
                media={<Avatar customer size="medium" name={login} initials={func.initials(login)} />}
                shortcutActions={shortcutActions}
                persistActions
                accessibilityLabel={login}
            >
                <VerticalStack gap="1">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="bodyMd" fontWeight="semibold" as="h3">{name && name !== '-' ? name : login}</Text>
                        {isSelf ? <Badge>You</Badge> : null}
                        {user.isInvitation ? <Badge status="attention">Invite pending</Badge> : null}
                        {expiryBadge(user)}
                    </HorizontalStack>
                    <Text variant="bodySm" color="subdued">{login}</Text>
                    {user.isInvitation
                        ? <Text variant="bodySm" color="subdued">{user.role}</Text>
                        : <HorizontalStack gap="1">{accessBadges(user)}</HorizontalStack>}
                </VerticalStack>
            </ResourceItem>
        )
    }

    const inviteDisabledReason = isLocalDeploy ? "Inviting is off on local deployments."
        : (userRole === 'GUEST' || userRole === 'DEVELOPER') ? "Your role can't invite users."
            : window.INVITE_DISABLED_FOR_SSO ? "Users are added through your SSO provider." : null

    return (
        <Page
            title="Users"
            primaryAction={{
                content: 'Invite user',
                onAction: () => setInviteOpen(true),
                disabled: inviteDisabledReason !== null,
                helpText: inviteDisabledReason || undefined,
            }}
            divider
        >
            <VerticalStack gap="4">
                {isLocalDeploy ? (
                    <Banner
                        title="Invite new members"
                        action={{ content: 'Go to docs', url: 'https://docs.akto.io/getting-started/quick-start-with-akto-cloud', target: "_blank" }}
                        status="info"
                    >
                        <p>Inviting team members is disabled in local. Collaborate with your team by using Akto cloud or AWS/GCP deploy.</p>
                    </Banner>
                ) : null}
                <Banner title="Role permissions">
                    <p>Each role has different permissions. <Link url="https://docs.akto.io/" target="_blank">Learn more</Link></p>
                </Banner>
                {loadFailed ? (
                    <Banner status="critical" title="Couldn't load users" action={{ content: 'Try again', onAction: loadUsers }}>
                        <p>{loadFailed}</p>
                    </Banner>
                ) : null}
                {userRole !== 'GUEST' ? (
                    <LegacyCard>
                        <ResourceList
                            resourceName={{ singular: 'user', plural: 'users' }}
                            items={users}
                            renderItem={renderItem}
                            headerContent={`${users.length} team member${users.length === 1 ? '' : 's'}`}
                            showHeader
                            loading={loading}
                        />
                    </LegacyCard>
                ) : null}
            </VerticalStack>

            <InviteUserModal
                open={inviteOpen}
                onClose={() => setInviteOpen(false)}
                productScopes={manageableScopes}
                roleOptions={roleOptions}
                defaultInviteRole={defaultInviteRole}
                currentProduct={currentProduct}
                onInvited={loadUsers}
            />

            <EditAccessModal
                user={editing}
                productScopes={manageableScopes}
                roleOptions={roleOptions}
                defaultRole={defaultInviteRole}
                isAdmin={isAdmin}
                canRemove={isAdmin}
                isOnPrem={isOnPrem}
                onClose={() => setEditing(null)}
                onSaved={() => { setEditing(null); loadUsers() }}
                onRemoved={() => { setEditing(null); loadUsers() }}
            />

            <Modal
                open={!!collectionsFor}
                onClose={() => setCollectionsFor(null)}
                title={collectionsFor ? `Collections for ${collectionsFor.user.login}` : ''}
                large
                primaryAction={{ content: 'Save', onAction: saveCollections }}
                secondaryActions={[{ content: 'Cancel', onAction: () => setCollectionsFor(null) }]}
            >
                <Modal.Section>
                    <VerticalStack gap="3">
                        <Text color="subdued">These are added to the collections the user's role gives. Leave all unticked for no extra collections.</Text>
                        {collectionsFor ? (
                            <SearchableResourceList
                                resourceName={'collection'}
                                items={Object.entries(collectionsMap || {}).map(([cid, collectionName]) => ({ id: parseInt(cid, 10), collectionName }))}
                                renderItem={usersCollectionRenderItem}
                                isFilterControlEnabale={true}
                                selectable={true}
                                onSelectedItemsChange={(selected) => setCollectionsFor(prev => prev ? ({ ...prev, selected: (selected || []).map(cid => parseInt(cid, 10)) }) : prev)}
                                alreadySelectedItems={collectionsFor.selected}
                            />
                        ) : null}
                    </VerticalStack>
                </Modal.Section>
            </Modal>
        </Page>
    )
}

export default Users

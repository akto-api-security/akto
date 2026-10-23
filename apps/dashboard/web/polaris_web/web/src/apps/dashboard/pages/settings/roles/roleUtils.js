// Shared by the roles list, the role editor and the SSO group mappings

const rolesOptions = [
    { label: 'Admin', value: 'ADMIN' },
    { label: 'Security Engineer', value: 'MEMBER' },
    { label: 'Developer', value: 'DEVELOPER' },
    { label: 'Guest', value: 'GUEST' },
    { label: 'Threat Engineer', value: 'THREAT_ENGINEER' },
    { label: 'Threat Viewer', value: 'THREAT_VIEWER' },
]

function getRoleDisplayName(role) {
    const option = rolesOptions.find(item => item.value === role)
    return option ? option.label : role
}

// Permissions an admin can change per custom role; anything not changed keeps the base role's access
const PERMISSION_GROUPS = [
    {
        title: 'People and Settings',
        features: [
            { feature: 'INVITE_MEMBERS', label: 'Invite Users and Change Their Roles', description: "Invite new users and change the roles of existing users. Read lets users view the user list." },
            { feature: 'INTEGRATIONS', label: 'Integrations', description: "Import agents into Akto from other platforms, or export them out. Read and write lets users add, edit and remove these connections." },
            { feature: 'API_TOKENS', label: 'API Tokens', description: "Create and manage the API tokens used to call Akto programmatically. Tokens carry access, so limit this to trusted roles." },
        ]
    },
    {
        title: 'Inventory and Data',
        features: [
            { feature: 'API_COLLECTIONS', label: ' Collections and Inventory', description: "View and manage collections and the discovered inventory, including endpoints and their details." },
            { feature: 'SENSITIVE_DATA', label: 'Sensitive Data', description: "See which sensitive data types (such as emails or tokens) were found in your APIs, and change how they are treated." },
            { feature: 'SAMPLE_DATA', label: 'Request and Response Samples', description: "View the captured request and response samples for endpoints. These can contain real traffic data." },
        ]
    },
    {
        title: 'Testing',
        features: [
            { feature: 'START_TEST_RUN', label: 'Run Tests', description: "Start, schedule and stop security test runs. Read only shows test runs that already exist." },
            { feature: 'TEST_RESULTS', label: 'Test Results', description: "View the results of test runs. Read and write also lets users edit test configuration and delete results." },
            { feature: 'ISSUES', label: 'Issues', description: "View the security issues found by tests. Read and write lets users change an issue's status, such as ignoring or fixing it." },
            { feature: 'TEST_SCRIPTS', label: 'Pre and Post Request Scripts', description: "Add scripts that run before or after test requests, for example to refresh auth tokens. Scripts execute code, so limit this to trusted roles." },
        ]
    },
    {
        title: 'Threat Detection',
        features: [
            { feature: 'THREAT_PROTECTION', label: 'API Threat Activity', description: "View detected activity and blocked requests. Read and write lets users act on them, such as changing event status." },
            { feature: 'THREAT_SETTINGS', label: 'Threat Settings and Data Retention', description: "Change detection settings and how long the data is kept. If not set separately, this follows the access of the activity option above." },
        ]
    },
]

const ROLE_DEFAULT = 'ROLE_DEFAULT'

const ACCESS_LABELS = {
    NO_ACCESS: 'No access',
    READ: 'Read',
    READ_WRITE: 'Read and write',
}

// Base roles that decide threat access on their own; the old threat checkbox never applied to them
const FIXED_THREAT_BASE_ROLES = ['ADMIN', 'GUEST', 'THREAT_ENGINEER', 'THREAT_VIEWER']

/*
 * Permission changes as the editor shows them. Older roles turned threat access on with a separate checkbox;
 * it is shown (and saved) as a threat protection change instead, which gives the same access.
 */
function editablePermissions(role) {
    const overrides = { ...(role?.permissionOverrides || {}) }
    if (role?.threatProtectionEnabled && !FIXED_THREAT_BASE_ROLES.includes(role?.baseRole) && !overrides.THREAT_PROTECTION) {
        overrides.THREAT_PROTECTION = 'READ_WRITE'
    }
    return overrides
}

const isLimitedToCollections = (role) =>
    (role?.apiCollectionsId || []).length > 0 || (role?.collectionRules || []).length > 0

// roles a team admin may give: limited to collections and not based on Admin (the backend enforces the same)
const isGivableByTeamAdmin = (role) => role?.baseRole !== 'ADMIN' && isLimitedToCollections(role)

function describeRule(rule) {
    return rule?.hostRegex ? `Host matches ${rule.hostRegex}` : `Tag ${rule?.tagKey} = ${rule?.tagValue}`
}

function collectionsSummary(role, existingCollectionIds) {
    const collections = (role?.apiCollectionsId || []).filter(id => !existingCollectionIds || existingCollectionIds.has(id)).length
    const rules = (role?.collectionRules || []).length
    if (collections === 0 && rules === 0) return 'All collections'
    const parts = []
    if (collections > 0) parts.push(`${collections} collection${collections === 1 ? '' : 's'}`)
    if (rules > 0) parts.push(`${rules} rule${rules === 1 ? '' : 's'}`)
    return parts.join(' + ')
}

function usageSummary(usage) {
    const users = usage?.users || 0
    const invites = usage?.invites || 0
    if (users === 0 && invites === 0) return 'Not used yet'
    const parts = []
    if (users > 0) parts.push(`${users} user${users === 1 ? '' : 's'}`)
    if (invites > 0) parts.push(`${invites} pending invite${invites === 1 ? '' : 's'}`)
    return parts.join(', ')
}

// same rules as the server: letters, numbers, - and _, up to 50 characters, not a built-in role name
function roleNameError(name, existingNames) {
    const trimmed = (name || '').trim()
    if (trimmed.length === 0) return 'Enter a role name.'
    if (trimmed.length > 50) return 'Use 50 characters or fewer.'
    if (!/^[A-Za-z0-9_-]+$/.test(trimmed)) return 'Use only letters, numbers, - and _.'
    const upper = trimmed.toUpperCase()
    if (['ADMIN', 'MEMBER', 'DEVELOPER', 'GUEST', 'THREAT_ENGINEER', 'THREAT_VIEWER', 'NO_ACCESS'].includes(upper)) {
        return `${upper} is a built-in role name.`
    }
    if ((existingNames || []).includes(upper)) return `A role named ${upper} already exists.`
    return ''
}

// a host pattern must be a valid regular expression (Mongo uses the same syntax for these)
function hostPatternError(pattern) {
    const trimmed = (pattern || '').trim()
    if (trimmed.length === 0) return 'Enter a host pattern.'
    if (trimmed.length > 200) return 'Use 200 characters or fewer.'
    try {
        new RegExp(trimmed)
    } catch (e) {
        return 'This is not a valid pattern. Check brackets and special characters.'
    }
    return ''
}

function tagRuleError(tag) {
    const [key, ...rest] = (tag || '').split('=')
    if (!key || !key.trim() || rest.join('=').trim().length === 0) return 'Use key=value, for example team=team-a.'
    return ''
}

export {
    rolesOptions, getRoleDisplayName, PERMISSION_GROUPS, ROLE_DEFAULT, ACCESS_LABELS, FIXED_THREAT_BASE_ROLES,
    editablePermissions, isLimitedToCollections, isGivableByTeamAdmin, describeRule, collectionsSummary, usageSummary,
    roleNameError, hostPatternError, tagRuleError
}

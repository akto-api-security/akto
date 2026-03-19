/** Built-in Akto RBAC roles (ADMIN, MEMBER, DEVELOPER, GUEST). */
export const BASE_ROLE_OPTIONS = [
    { label: 'Admin', value: 'ADMIN' },
    { label: 'Security Engineer', value: 'MEMBER' },
    { label: 'Developer', value: 'DEVELOPER' },
    { label: 'Guest', value: 'GUEST' },
]

export function getBaseRoleDisplayName(role) {
    for (const item of BASE_ROLE_OPTIONS) {
        if (item.value === role) {
            return item.label
        }
    }
    return role
}

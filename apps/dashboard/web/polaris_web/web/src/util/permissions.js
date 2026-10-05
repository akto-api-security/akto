import { useMemo } from "react"
import { create } from "zustand"
import request, { setDeniedActionCheck } from "./request"

/*
 * What the signed-in user can do in the current product, from the server's own role checks (api/fetchUserPermissions).
 * Pages use it to hide what the user can't open and to disable what they can't change, instead of failing after a click.
 * Until it has loaded (or if it fails) everything counts as allowed: the server still refuses what isn't, as before.
 */
const PermissionStore = create((set) => ({
    loaded: false,
    featureAccess: {},
    denied: new Set(),
    // the user has no role in this product at all: every page says so instead of loading
    noProductAccess: false,
    setPermissions: (featureAccess, deniedActions, noProductAccess = false) => set({
        loaded: true,
        featureAccess: featureAccess || {},
        denied: new Set(deniedActions || []),
        noProductAccess,
    }),
}))

// the request layer doesn't send calls the server would refuse (no request, no error toast)
setDeniedActionCheck((action) => PermissionStore.getState().denied.has(action))

export const NO_PERMISSION_REASON = "Your role can't do this. Ask an admin if you need it."

let loading = null
export function loadPermissions() {
    if (!loading) {
        loading = request({ url: '/api/fetchUserPermissions', method: 'post', data: {}, suppress403Toast: true })
            .then((resp) => PermissionStore.getState().setPermissions(resp?.featureAccess, resp?.deniedActions))
            .catch((error) => {
                const noProductAccess = error?.response?.status === 403 && error?.response?.headers?.['x-no-access-error'] === 'true'
                // any other failure: nothing is hidden, the server still refuses as before
                PermissionStore.getState().setPermissions({}, [], noProductAccess)
            })
    }
    return loading
}

// "/api/addSplunkIntegration", "api/addSplunkIntegration" and "addSplunkIntegration" all name the same action
const actionName = (action) => {
    const name = String(action || '').replace(/^\//, '')
    return name.startsWith('api/') ? name : `api/${name}`
}

const accessIn = (state, feature) => state.featureAccess[feature] || 'READ_WRITE'

function checks(state) {
    const denied = (action) => state.noProductAccess || state.denied.has(actionName(action))
    return {
        loaded: state.loaded,
        noProductAccess: state.noProductAccess,
        canCall: (action) => !denied(action),
        canCallAll: (...actions) => actions.every(action => !denied(action)),
        canRead: (feature) => !state.noProductAccess && accessIn(state, feature) !== 'NO_ACCESS',
        canWrite: (feature) => !state.noProductAccess && accessIn(state, feature) === 'READ_WRITE',
        canOpen: (path) => { const action = pageRequires(path); return !state.noProductAccess && (!action || !denied(action)) },
    }
}

/** Permissions for components; re-renders when they load. */
export function usePermissions() {
    const state = PermissionStore(s => s)
    // the same functions until the permissions change, so they can be effect and memo dependencies
    return useMemo(() => checks(state), [state])
}

/** Same checks outside components (e.g. building menus or nav items). */
export const permissions = {
    canCall: (action) => checks(PermissionStore.getState()).canCall(action),
    canCallAll: (...actions) => checks(PermissionStore.getState()).canCallAll(...actions),
    canRead: (feature) => checks(PermissionStore.getState()).canRead(feature),
    canWrite: (feature) => checks(PermissionStore.getState()).canWrite(feature),
    canOpen: (path) => checks(PermissionStore.getState()).canOpen(path),
}

/*
 * The action each page loads first. A page whose action the user can't call is left out of the navigation and shows
 * a "no access" page if opened by URL, instead of loading and failing. The longest matching path wins.
 */
const PAGE_REQUIRES = {
    '/dashboard/observe/inventory': 'api/getAllCollectionsBasic',
    '/dashboard/observe/sensitive': 'api/fetchDataTypes',
    '/dashboard/observe/audit': 'api/fetchAuditData',
    '/dashboard/observe/llm-observability': 'api/fetchLLMSessions',
    '/dashboard/observe/endpoint-shield': 'api/fetchEndpointShieldAgents',
    '/dashboard/nhi/identities': 'api/fetchAllNhiIdentities',
    '/dashboard/nhi/violations': 'api/fetchAllNhiViolations',
    '/dashboard/nhi/policies': 'api/fetchNhiPolicies',
    '/dashboard/testing': 'api/retrieveAllCollectionTests',
    '/dashboard/testing/roles': 'api/fetchTestRoles',
    '/dashboard/testing/user-config': 'api/fetchAuthMechanismData',
    '/dashboard/testing/test-suite': 'api/fetchAllTestSuites',
    '/dashboard/reports/issues': 'api/fetchAllIssues',
    '/dashboard/reports/threat': 'api/fetchThreatComplianceInfos',
    '/dashboard/protection/threat-dashboard': 'api/fetchThreatCategoryCount',
    '/dashboard/protection/threat-actor': 'api/fetchThreatActors',
    '/dashboard/protection/threat-activity': 'api/fetchSuspectSampleData',
    '/dashboard/protection/threat-api': 'api/fetchThreatApis',
    '/dashboard/protection/threat-policy': 'api/fetchThreatComplianceInfos',
    '/dashboard/guardrails/activity': 'api/fetchSuspectSampleData',
    '/dashboard/guardrails/policies': 'api/fetchGuardrailPolicies',
    '/dashboard/guardrails/misconfigurations': 'api/fetchConfigFieldPolicies',
    '/dashboard/settings/users': 'api/getTeamData',
    '/dashboard/settings/threat-configuration': 'api/fetchThreatConfiguration',
    '/dashboard/settings/undo-demerge-apis': 'api/getDeMergedApis',
    '/dashboard/settings/browser-extension': 'api/fetchBrowserExtensionConfigs',
    '/dashboard/settings/logs': 'api/fetchLogsFromDb',
    '/dashboard/settings/module-info': 'api/fetchModuleInfo',
    '/dashboard/settings/job-info': 'api/fetchAccountJobs',
    '/dashboard/settings/metrics': 'api/allMetricsDescription',
    '/dashboard/settings/auth-types': 'api/fetchCustomAuthTypes',
    '/dashboard/settings/default-payloads': 'api/fetchAllDefaultPayloads',
    '/dashboard/settings/advanced-filters': 'api/fetchAdvancedFiltersForTraffic',
    '/dashboard/settings/proxy-patterns': 'api/fetchAdminSettings',
    '/dashboard/settings/allowed-hosts': 'api/fetchAdminSettings',
    '/dashboard/settings/endpoint-shield': 'api/fetchEndpointShieldSettings',
    '/dashboard/settings/remote-commands': 'api/fetchEndpointRemoteCommandList',
    '/dashboard/settings/file-inspection': 'api/fetchFileInspectionRules',
    '/dashboard/settings/audit-logs': 'api/fetchApiAuditLogsFromDb',
    '/dashboard/settings/self-hosted': 'api/getCustomerStiggDetails',
    '/dashboard/settings/integrations/ci-cd': 'api/fetchApiTokens',
    '/dashboard/settings/integrations/akto_apis': 'api/fetchApiTokens',
    '/dashboard/settings/integrations/burp': 'api/fetchApiTokens',
    '/dashboard/settings/integrations/okta_sso': 'api/fetchOktaSso',
    '/dashboard/settings/integrations/azure_sso': 'api/fetchSAMLSso',
    '/dashboard/settings/integrations/google_workspace_sso': 'api/fetchSAMLSso',
    '/dashboard/settings/integrations/github_sso': 'api/fetchGithubSso',
    '/dashboard/settings/integrations/github_app': 'api/fetchGithubAppId',
    '/dashboard/settings/integrations/mcp_registry': 'api/fetchMcpRegistries',
    '/dashboard/settings/integrations/jira': 'api/fetchIntegration',
    '/dashboard/settings/integrations/splunk': 'api/fetchSplunkIntegration',
    '/dashboard/settings/integrations/datadog': 'api/fetchDatadogIntegration',
    '/dashboard/settings/integrations/aws_waf': 'api/fetchAwsWafIntegration',
    '/dashboard/settings/integrations/cloudflare_waf': 'api/fetchCloudflareWafIntegration',
    '/dashboard/settings/integrations/postman': 'api/getPostmanCredential',
    '/dashboard/settings/integrations/webhooks': 'api/fetchCustomWebhooks',
}

/** The action a page (by its path) needs, or undefined when it has none to check. */
export function pageRequires(path) {
    const normalized = String(path || '').toLowerCase().replace(/\/+$/, '')
    let best
    for (const prefix of Object.keys(PAGE_REQUIRES)) {
        const p = prefix.toLowerCase()
        if ((normalized === p || normalized.startsWith(p + '/')) && (!best || prefix.length > best.length)) {
            best = prefix
        }
    }
    return best ? PAGE_REQUIRES[best] : undefined
}

/*
 * Props for an action the user may see but not use: disabled, with the reason as help text (Page actions) or a tooltip.
 * Spread into a Page primaryAction/secondaryAction object, e.g. { content: 'Add', onAction, ...whenAllowed(canCall('api/x')) }.
 */
export function whenAllowed(allowed) {
    return allowed ? {} : { disabled: true, helpText: NO_PERMISSION_REASON }
}

/*
 * Applies `requires` tags to Polaris action objects (page actions, bulk actions, menu items, and groups of them):
 * an action tagged with an action name (or a list of them) that the user can't call is disabled, with the reason as
 * help text where Polaris shows it. The tag is removed so it never reaches the DOM.
 * e.g. promotedBulkActions = withPermissions([{ content: 'Delete', onAction, requires: 'api/deleteMultipleCollections' }])
 */
export function withPermissions(actions) {
    const state = PermissionStore.getState()
    const allowed = (requires) => {
        const names = Array.isArray(requires) ? requires : [requires]
        return names.every(name => !state.denied.has(actionName(name)))
    }
    const apply = (item) => {
        if (Array.isArray(item)) return item.map(apply)
        if (!item || typeof item !== 'object' || item.$$typeof) return item
        const { requires, ...rest } = item
        const result = { ...rest }
        for (const key of ['actions', 'items', 'sections']) {
            if (Array.isArray(result[key])) result[key] = result[key].map(apply)
        }
        if (requires && !allowed(requires)) {
            result.disabled = true
            result.helpText = NO_PERMISSION_REASON
        }
        return result
    }
    return apply(actions)
}

export default PermissionStore

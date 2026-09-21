import PersistStore from "@/apps/main/PersistStore"
import LocalStore from "@/apps/main/LocalStorageStore"

// Two dashboard pages ignore query params entirely and read prior state from a store instead —
// getting either of these wrong looks exactly like "the palette did nothing". Both writes are
// verified against the actual reader:
//
// - Issues: ChartypeComponent.jsx (pages/testing/TestRunsPage/ChartypeComponent.jsx) writes this
//   exact filter shape into PersistStore.filtersMap under TWO keys before navigating — the
//   trailing double slash on the second key is real, not a typo (GithubServerTable.js derives
//   its key from `pathname + "/" + hash`).
// - Guardrail violations: InsightDetailView.jsx documents that ViolationsPage redirects to a
//   legacy layout, dropping the whole query string, unless
//   LocalStore.setGuardrailViolationsNewLayout(true) was called first.
function applyIssuesSeverityFilter(severity) {
    const filterObj = [{ key: "severity", label: severity, value: [severity] }]
    const current = PersistStore.getState().filtersMap || {}
    PersistStore.getState().setFiltersMap({
        ...current,
        "/dashboard/issues/#open": { filters: filterObj, sort: [] },
        "/dashboard/issues//#open": { filters: filterObj, sort: [] },
    })
}

function ensureGuardrailViolationsNewLayout() {
    LocalStore.getState().setGuardrailViolationsNewLayout(true)
}

// Runs whatever side effect a resolved option/intent carries, BEFORE navigate() is called —
// order matters, since both writes above are read on the target page's mount.
export function applyNavigationSideEffects(option) {
    if (!option) return
    if (option.sideEffect?.kind === "ISSUES_SEVERITY_FILTER") {
        applyIssuesSeverityFilter(option.sideEffect.severity)
    }
    if (option.route === "/dashboard/guardrails/violations") {
        ensureGuardrailViolationsNewLayout()
    }
}

export const MAX_RECENT_PROMPTS = 5

export const SUGGESTED_PROMPTS = [
    "unauthenticated APIs with PII",
    "criticals older than 30 days",
    "collections never tested",
]

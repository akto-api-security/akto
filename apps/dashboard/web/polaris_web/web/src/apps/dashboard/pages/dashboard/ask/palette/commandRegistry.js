// The palette's declarative route registry — plain data, no React, no closures. This is the
// source of truth LeftNav.js can't give us: it has the right labels and RBAC gating but as 792
// lines of imperative JSX with paths buried inside onClick closures, and Store.allRoutes (App.js)
// is populated but keys on React component names ("ApiCollections"), not anything a person would
// type, and isn't RBAC-filtered. This file DOES duplicate knowledge LeftNav already holds — the
// mitigation is treating this as the new source of truth and migrating LeftNav onto it later, as
// a separate change, not bundling a 792-line-nav refactor into this overlay.
//
// Every route below is verified against apps/dashboard/web/polaris_web/web/src/apps/main/App.js
// (or, for the InsightRoutes-shared ones, against com.akto.service.insights.InsightRoutes, whose
// own comment already carries that guarantee: "every one is a real path in App.js"). Never add a
// route here you have not verified.
//
// `gate` is a FUNCTION evaluated at resolve time, never a captured boolean — role/entitlement can
// change between palette opens without a page reload (e.g. after a category switch).
import func from "@/util/func"

export const COMMANDS = [
    { id: "inventory", label: "API inventory", section: "Observe",
        route: "/dashboard/observe/inventory", params: {},
        keywords: ["apis", "endpoints", "inventory", "collections", "discovery"], gate: () => true },
    { id: "sensitive_data", label: "Sensitive data", section: "Observe",
        route: "/dashboard/observe/sensitive", params: {},
        keywords: ["pii", "sensitive", "data types"], gate: () => true },
    { id: "api_changes", label: "API changes", section: "Observe",
        route: "/dashboard/observe/changes", params: {},
        keywords: ["changes", "new apis", "recent"], gate: () => true },
    { id: "issues", label: "Issues", section: "Protect",
        route: "/dashboard/issues", params: {},
        keywords: ["vulnerabilities", "vulns", "findings", "criticals", "bugs"], gate: () => true },
    { id: "testing", label: "Testing", section: "Protect",
        route: "/dashboard/testing", params: {},
        keywords: ["test runs", "scans", "run a test"], gate: () => true },
    { id: "agentic_assets", label: "Agentic assets", section: "Observe",
        route: "/dashboard/observe/agentic-assets", params: {},
        keywords: ["mcp", "agents", "ai assets", "llm"],
        gate: () => func.checkForFeatureSaas("SECURITY_TYPE_AGENTIC") },
    { id: "users_and_devices", label: "Users and devices", section: "Observe",
        route: "/dashboard/observe/users-and-devices", params: {},
        keywords: ["users", "devices", "who is using"],
        gate: () => func.checkForFeatureSaas("SECURITY_TYPE_AGENTIC") },
    { id: "llm_observability", label: "LLM observability", section: "Observe",
        route: "/dashboard/observe/llm-observability", params: {},
        keywords: ["llm", "tokens", "model usage"],
        gate: () => func.checkForFeatureSaas("SECURITY_TYPE_AGENTIC") },
    { id: "audit", label: "Audit log", section: "Observe",
        route: "/dashboard/observe/audit", params: {},
        keywords: ["audit", "mcp servers", "log"],
        gate: () => func.checkForFeatureSaas("SECURITY_TYPE_AGENTIC") },
    { id: "endpoint_shield", label: "Endpoint shield", section: "Observe",
        route: "/dashboard/observe/endpoint-shield", params: {},
        keywords: ["endpoint shield", "endpoint"],
        gate: () => func.checkForFeatureSaas("ENDPOINT_SECURITY") },
    { id: "guardrail_policies", label: "Guardrail policies", section: "Guardrails",
        route: "/dashboard/guardrails/policies", params: {},
        keywords: ["guardrails", "policies"], gate: () => true },
    { id: "guardrail_violations", label: "Guardrail violations", section: "Guardrails",
        route: "/dashboard/guardrails/violations", params: {},
        keywords: ["violations", "guardrail hits"], gate: () => true },
    { id: "guardrail_activity", label: "Guardrail activity", section: "Guardrails",
        route: "/dashboard/guardrails/activity", params: {},
        keywords: ["guardrail activity"], gate: () => true },
    { id: "ask_akto_chat", label: "Ask Akto (full chat)", section: "Ask",
        route: "/dashboard/ask-ai", params: {},
        keywords: ["chat", "ask akto", "full page"], gate: () => true },
    { id: "quick_start", label: "Quick start", section: "Settings",
        route: "/dashboard/quick-start", params: {},
        keywords: ["onboarding", "connect a source", "setup"], gate: () => true },
]

// Tier 1 — deterministic regex -> route + params, tried BEFORE fuzzy matching. Order matters:
// first match wins. `build` receives the regex match and returns {label, route, params?,
// sideEffect?}. sideEffect is for targets that ignore query params entirely (see
// resolveCommand.js's applyNavigationSideEffects) — Issues and Guardrail Violations both do.
function cap(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1).toLowerCase() : s }

export const INTENTS = [
    {
        id: "issues_by_severity",
        pattern: /\b(critical|high|medium|low)\b.*\b(issues?|findings?|vulns?|vulnerabilities)\b/i,
        build: (m) => ({
            label: `${cap(m[1])} issues`,
            route: "/dashboard/issues",
            params: {},
            // Issues does NOT read a ?filters= query param — ChartypeComponent.jsx writes this
            // exact shape into PersistStore.filtersMap (under two keys — see
            // paletteHelpers.applyNavigationSideEffects) before navigating, and that's what the
            // Issues page actually reads on mount. Getting this wrong looks exactly like "the
            // palette did nothing".
            sideEffect: { kind: "ISSUES_SEVERITY_FILTER", severity: m[1].toUpperCase() },
        }),
        gate: () => true,
    },
    {
        id: "run_a_test",
        pattern: /\b(run|start)\b.*\btest\b/i,
        build: () => ({ label: "Start a test run", route: "/dashboard/testing", params: {} }),
        gate: () => true,
    },
    {
        id: "unauthenticated_apis",
        pattern: /\bunauth\w*\b/i,
        build: () => ({ label: "Unauthenticated APIs", route: "/dashboard/observe/inventory", params: {} }),
        gate: () => true,
    },
]

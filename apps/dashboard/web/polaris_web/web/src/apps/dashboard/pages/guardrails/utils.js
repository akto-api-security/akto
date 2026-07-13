export const SEVERITY = {
    CRITICAL: { label: "Critical", value: "CRITICAL" },
    HIGH: { label: "High", value: "HIGH" },
    MEDIUM: { label: "Medium", value: "MEDIUM" },
    LOW: { label: "Low", value: "LOW" },
};

export const SEVERITY_OPTIONS = Object.values(SEVERITY);

/** Rule behaviour (block / warn / alert / approval); stored as policy-wide `behaviour`. */
export const GUARDRAIL_BEHAVIOUR = {
    BLOCK: "block",
    WARN: "warn",
    ALERT: "alert",
    APPROVAL: "approval",
};

// Display label only — the stored `value` stays "approval" (backend + guardrails-service depend on it).
export const GUARDRAIL_BEHAVIOUR_OPTIONS = [
    { label: "Block", value: GUARDRAIL_BEHAVIOUR.BLOCK },
    { label: "Alert", value: GUARDRAIL_BEHAVIOUR.ALERT },
    { label: "Human Approval", value: GUARDRAIL_BEHAVIOUR.APPROVAL },
];

export const GUARDRAIL_BEHAVIOUR_TOOLTIP_LINES = [
    "Block: Stop the content when this rule matches.",
    "Alert: Raise an alert for review without blocking.",
    "Human Approval: Hold the content until a reviewer approves or rejects it.",
];

export const normalizeBehaviourValue = (raw) => {
    const v = (raw || "").toString().toLowerCase().trim();
    if (v === GUARDRAIL_BEHAVIOUR.WARN) {
        return GUARDRAIL_BEHAVIOUR.WARN;
    }
    if (v === GUARDRAIL_BEHAVIOUR.ALERT) {
        return GUARDRAIL_BEHAVIOUR.ALERT;
    }
    if (v === GUARDRAIL_BEHAVIOUR.APPROVAL) {
        return GUARDRAIL_BEHAVIOUR.APPROVAL;
    }
    if (v === GUARDRAIL_BEHAVIOUR.BLOCK) {
        return GUARDRAIL_BEHAVIOUR.BLOCK;
    }
    return GUARDRAIL_BEHAVIOUR.BLOCK;
};

export const resolveStoredPolicyBehaviour = (policy) => {
    if (policy?.behaviour != null && String(policy.behaviour).trim() !== "") {
        return normalizeBehaviourValue(policy.behaviour);
    }
    return GUARDRAIL_BEHAVIOUR.BLOCK;
};

/**
 * Whether a single approvedServers entry is still active (not expired).
 * ALWAYS -> always; DURATION -> now < expiredAt; COUNT -> expiredAfter > 0.
 */
const isApprovedServerActive = (entry, nowSeconds) => {
    const mode = String(entry?.mode || "").toUpperCase();
    if (mode === "ALWAYS") return true;
    if (mode === "DURATION") return Number(entry?.expiredAt || 0) > nowSeconds;
    if (mode === "COUNT") return Number(entry?.expiredAfter || 0) > 0;
    return false;
};

/**
 * Build { policyName -> [active approved serverId, ...] } from fetched guardrail policies.
 * Only currently-valid (non-expired) entries are included.
 */
export const buildApprovedByPolicy = (policies) => {
    const nowSeconds = Math.floor(Date.now() / 1000);
    const map = {};
    (policies || []).forEach((p) => {
        if (!p?.name) return;
        const active = (p.approvedServers || [])
            .filter((entry) => isApprovedServerActive(entry, nowSeconds))
            .map((entry) => entry.serverId);
        if (active.length > 0) map[p.name] = active;
    });
    return map;
};

/** True if serverId is currently approved for policyName (AND of policy + server). */
export const isServerApproved = (approvedByPolicy, policyName, serverId) =>
    !!(approvedByPolicy && policyName && serverId && (approvedByPolicy[policyName] || []).includes(serverId));

/**
 * Helper function to transform frontend field names to backend DTO field names
 * Transforms: piiFilters -> piiTypes, contentFilters -> contentFiltering
 *
 * @param {Object} policyData - Policy data with frontend field names
 * @returns {Object} Policy data with backend field names
 */
export const transformPolicyForBackend = (policyData) => {
    return {
        ...policyData,
        piiTypes: policyData.piiFilters || [],
        contentFiltering: policyData.contentFilters || {}
    };
};

/** Defaults `minMatchCount` to 1 when loading policies for edit. */
export const normalizePiiTypesFromPolicy = (policy) =>
    (policy?.piiTypes || []).map((p) => ({
        ...p,
        behavior: typeof p.behavior === "string" ? p.behavior.toLowerCase() : "block",
        domainCount: p.domainCount ?? 0,
        minMatchCount:
            p.minMatchCount != null && Number(p.minMatchCount) >= 1 ? Number(p.minMatchCount) : 1
    }));

export const SEVERITY = {
    CRITICAL: { label: "Critical", value: "CRITICAL" },
    HIGH: { label: "High", value: "HIGH" },
    MEDIUM: { label: "Medium", value: "MEDIUM" },
    LOW: { label: "Low", value: "LOW" },
};

export const SEVERITY_OPTIONS = Object.values(SEVERITY);

/** Rule behaviour (block / warn / alert); stored as policy-wide `behaviour`. */
export const GUARDRAIL_BEHAVIOUR = {
    BLOCK: "block",
    WARN: "warn",
    ALERT: "alert",
};

export const GUARDRAIL_BEHAVIOUR_OPTIONS = [
    { label: "Block", value: GUARDRAIL_BEHAVIOUR.BLOCK },
    { label: "Warn", value: GUARDRAIL_BEHAVIOUR.WARN },
    { label: "Alert", value: GUARDRAIL_BEHAVIOUR.ALERT },
];

export const GUARDRAIL_BEHAVIOUR_TOOLTIP_LINES = [
    "Block: Stop the content when this rule matches.",
    "Warn: Warn the user. User can then bypass the guardrail after this nudge.",
    "Alert: Raise an alert for review without blocking.",
];

export const normalizeBehaviourValue = (raw) => {
    const v = (raw || "").toString().toLowerCase().trim();
    if (v === GUARDRAIL_BEHAVIOUR.WARN) {
        return GUARDRAIL_BEHAVIOUR.WARN;
    }
    if (v === GUARDRAIL_BEHAVIOUR.ALERT) {
        return GUARDRAIL_BEHAVIOUR.ALERT;
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

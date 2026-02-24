export const SEVERITY = {
    CRITICAL: { label: "Critical", value: "CRITICAL" },
    HIGH: { label: "High", value: "HIGH" },
    MEDIUM: { label: "Medium", value: "MEDIUM" },
    LOW: { label: "Low", value: "LOW" },
};

export const SEVERITY_OPTIONS = Object.values(SEVERITY);

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

export const SEVERITY_OPTIONS = [
    { label: "Critical", value: "CRITICAL" },
    { label: "High", value: "HIGH" },
    { label: "Medium", value: "MEDIUM" },
    { label: "Low", value: "LOW" },
];

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

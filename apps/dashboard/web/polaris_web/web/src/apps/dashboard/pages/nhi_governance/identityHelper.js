/**
 * Helper utilities for handling identity data in violations
 * Assumes all identities are in new format: { id: ObjectId, identityName: "name" }
 */

/**
 * Extract identity name from identity object
 * @param {object} identity - Identity object with structure: { id: ObjectId, identityName: "name" }
 * @returns {string} The identity name
 */
export function extractIdentityName(identity) {
    if (!identity || typeof identity !== "object") {
        return "Unknown";
    }
    return identity.identityName || "Unknown";
}

/**
 * Extract identity ID from identity object
 * @param {object} identity - Identity object with structure: { id: ObjectId, identityName: "name" }
 * @returns {ObjectId|null} The identity ID
 */
export function extractIdentityId(identity) {
    if (!identity || typeof identity !== "object") {
        return null;
    }
    return identity.id || null;
}

/**
 * Get the first identity name from identities array
 * @param {array} identities - Array of identity objects
 * @returns {string} The first identity name or "Unknown"
 */
export function getFirstIdentityName(identities) {
    if (!identities || !Array.isArray(identities) || identities.length === 0) {
        return "Unknown";
    }
    return extractIdentityName(identities[0]);
}

/**
 * Get the first identity ID from identities array
 * @param {array} identities - Array of identity objects
 * @returns {ObjectId|null} The first identity ID
 */
export function getFirstIdentityId(identities) {
    if (!identities || !Array.isArray(identities) || identities.length === 0) {
        return null;
    }
    return extractIdentityId(identities[0]);
}

/**
 * Check if violation includes a specific identity name
 * @param {array} identities - Array of identity objects
 * @param {string} identityName - Name to search for
 * @returns {boolean} True if identity is included
 */
export function violationIncludesIdentity(identities, identityName) {
    if (!identities || !Array.isArray(identities)) {
        return false;
    }
    return identities.some((identity) => extractIdentityName(identity) === identityName);
}

/**
 * Get all identity names from identities array
 * @param {array} identities - Array of identity objects
 * @returns {array} Array of identity name strings
 */
export function getAllIdentityNames(identities) {
    if (!identities || !Array.isArray(identities)) {
        return [];
    }
    return identities
        .map((identity) => extractIdentityName(identity))
        .filter((name) => name !== "Unknown");
}

/**
 * Get all identity IDs from identities array
 * @param {array} identities - Array of identity objects
 * @returns {array} Array of identity IDs
 */
export function getAllIdentityIds(identities) {
    if (!identities || !Array.isArray(identities)) {
        return [];
    }
    return identities
        .map((identity) => extractIdentityId(identity))
        .filter((id) => id !== null);
}

export default {
    extractIdentityName,
    extractIdentityId,
    getFirstIdentityName,
    getFirstIdentityId,
    violationIncludesIdentity,
    getAllIdentityNames,
    getAllIdentityIds,
};

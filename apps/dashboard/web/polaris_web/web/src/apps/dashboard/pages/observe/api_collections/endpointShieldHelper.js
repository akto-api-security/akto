import settingRequests from "../../settings/api";

// Shared constants for endpoint shield functionality
const MODULE_TYPE = {
    MCP_ENDPOINT_SHIELD: 'MCP_ENDPOINT_SHIELD'
};
const DEFAULT_VALUE = '-';

/**
 * Fetches endpoint shield module info and builds username map from additionalData.
 * moduleInfo already contains mcpServers with collectionName under additionalData,
 * so no per-agent API calls are needed.
 * @returns {Promise<Object>} - Map of collection name (lowercase) to username
 */
const fetchEndpointShieldUsernameMap = async () => {
    const usernameMap = {};

    try {
        const response = await settingRequests.fetchModuleInfo({ moduleType: MODULE_TYPE.MCP_ENDPOINT_SHIELD });
        const moduleInfos = response?.moduleInfos || [];

        moduleInfos.forEach((module) => {
            const username = module.additionalData?.username || DEFAULT_VALUE;
            const deviceId = module.name;

            if (!username || username === DEFAULT_VALUE) return;

            // Index by deviceId for fallback matching
            if (deviceId) {
                usernameMap[`__deviceId__${deviceId.toLowerCase()}`] = username;
            }

            // additionalData.mcpServers is a map of serverName -> { collectionName, ... }
            const mcpServers = module.additionalData?.mcpServers || {};
            Object.values(mcpServers).forEach((server) => {
                if (server.collectionName) {
                    usernameMap[server.collectionName.toLowerCase()] = username;
                }
            });
        });

        return usernameMap;
    } catch (e) {
        return {};
    }
};

/**
 * Gets username for a collection from the username map
 * Collection name format: <device-id>.<source-id>.<service-name>
 * where <device-id> is also called endpoint-id
 * 
 * Tries multiple matching strategies:
 * 1. Full displayName match (exact collectionName from endpoint shield)
 * 2. Full name match
 * 3. By deviceId/endpointId directly (first part of collection name)
 * 4. Endpoint shield format: deviceId.serviceName (skipping source-id)
 * 
 * @param {Object} collection - Collection object with displayName and/or name
 * @param {Object} usernameMap - Map of collection name to username
 * @returns {string} - Username or "-" if not found
 */
const getUsernameForCollection = (collection, usernameMap) => {
    if (!usernameMap || Object.keys(usernameMap).length === 0 || !collection) return DEFAULT_VALUE;
    
    const displayName = collection.displayName?.toLowerCase();
    const name = collection.name?.toLowerCase();
    
    // Strategy 1: Full displayName match (this is what endpoint shield stores as collectionName)
    if (displayName && usernameMap[displayName]) {
        return usernameMap[displayName];
    }
    
    // Strategy 2: Full name match
    if (name && usernameMap[name]) {
        return usernameMap[name];
    }
    
    // Extract deviceId (endpoint-id) from collection name
    // Format: <device-id>.<source-id>.<service-name>
    const collectionName = displayName || name;
    if (collectionName) {
        const parts = collectionName.split('.');
        if (parts.length >= 1) {
            const deviceId = parts[0];  // First part is device-id (endpoint-id)
            
            // Strategy 3: Try by deviceId/endpointId directly
            const deviceIdKey = `__deviceId__${deviceId}`;
            if (usernameMap[deviceIdKey]) {
                return usernameMap[deviceIdKey];
            }
            
            // Strategy 4: Try deviceId.serviceName format (skipping source-id)
            if (parts.length >= 3) {
                const serviceName = parts.slice(2).join('.');
                const endpointShieldKey = `${deviceId}.${serviceName}`;
                if (usernameMap[endpointShieldKey]) {
                    return usernameMap[endpointShieldKey];
                }
            }
        }
    }
    
    return DEFAULT_VALUE;
};

export {
    fetchEndpointShieldUsernameMap,
    getUsernameForCollection,
    MODULE_TYPE,
    DEFAULT_VALUE
};

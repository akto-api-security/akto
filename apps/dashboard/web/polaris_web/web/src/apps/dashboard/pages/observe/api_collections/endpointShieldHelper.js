import settingRequests from "../../settings/api";

// Shared constants for endpoint shield functionality
const MODULE_TYPE = {
    MCP_ENDPOINT_SHIELD: 'MCP_ENDPOINT_SHIELD'
};
const DEFAULT_VALUE = '-';

const USERNAME_TAG_KEYS = new Set([
    'username',
    'user',
    'useremail',
    'employee',
    'employeeemail',
    'employeemail',
]);

const normalizeKey = (keyName) =>
    typeof keyName === 'string' ? keyName.toLowerCase().replace(/\s/g, '') : '';

const findUsernameFromEnvTypeTags = (envType) => {
    if (!Array.isArray(envType)) return null;
    for (const tag of envType) {
        if (!tag?.keyName || !tag.value) continue;
        if (USERNAME_TAG_KEYS.has(normalizeKey(tag.keyName))) {
            const v = String(tag.value).trim();
            if (v) return v;
        }
    }
    return null;
};

const resolveModuleUsername = (module) => {
    const ad = module?.additionalData || {};
    const candidates = [ad.username, ad.userName, ad.user, ad.email].filter(
        (v) => typeof v === 'string' && v.trim().length > 0 && v.trim() !== DEFAULT_VALUE
    );
    return candidates.length > 0 ? candidates[0].trim() : null;
};

const registerDeviceKeys = (usernameMap, username, rawIds) => {
    rawIds.filter(Boolean).forEach((id) => {
        const k = String(id).toLowerCase();
        if (k) {
            usernameMap[`__deviceId__${k}`] = username;
        }
    });
};

const buildUsernameMapFromModuleInfos = (moduleInfos = []) => {
    const usernameMap = {};
    moduleInfos.forEach((module) => {
        const username = resolveModuleUsername(module);
        if (!username) return;

        const ad = module.additionalData || {};
        registerDeviceKeys(usernameMap, username, [
            module.name,
            ad.deviceId,
            ad.endpointId,
        ]);

        const mcpServers = ad.mcpServers || {};
        Object.values(mcpServers).forEach((server) => {
            if (server.collectionName) {
                usernameMap[server.collectionName.toLowerCase()] = username;
            }
        });
    });
    return usernameMap;
};

/**
 * UserAnalysisData keys from Endpoint Shield: serviceId = module id, deviceId = module name
 * (matches ES / UserAnalysisCron and collection hostnames <deviceId>.<source>.<service>).
 */
const buildUserAnalysisKeysByDeviceId = (moduleInfos = []) => {
    const byDeviceId = new Map();
    moduleInfos.forEach((module) => {
        const serviceId = module.id != null ? String(module.id) : (module._id != null ? String(module._id) : null);
        const deviceId = module.name;
        if (!serviceId || !deviceId) return;
        const entry = { serviceId, deviceId };
        byDeviceId.set(deviceId, entry);
        byDeviceId.set(String(deviceId).toLowerCase(), entry);
    });
    return byDeviceId;
};

/**
 * Fetches endpoint shield module info and builds username map from additionalData.
 * @returns {Promise<Object>}
 */
const fetchEndpointShieldUsernameMap = async () => {
    try {
        const response = await settingRequests.fetchModuleInfo({ moduleType: MODULE_TYPE.MCP_ENDPOINT_SHIELD });
        return buildUsernameMapFromModuleInfos(response?.moduleInfos || []);
    } catch (e) {
        return {};
    }
};

const fetchEndpointShieldUserMetadata = async () => {
    try {
        const [moduleResp, agenticUsersResp] = await Promise.all([
            settingRequests.fetchModuleInfo({ moduleType: MODULE_TYPE.MCP_ENDPOINT_SHIELD }),
            settingRequests.fetchAgenticUsers().catch(() => ({ agenticUsers: [] })),
        ]);

        const moduleInfos = moduleResp?.moduleInfos || [];
        const usernameMap = buildUsernameMapFromModuleInfos(moduleInfos);
        const userAnalysisKeysByDeviceId = buildUserAnalysisKeysByDeviceId(moduleInfos);

        const userMetadataMap = {};
        const agenticUsers = agenticUsersResp?.agenticUsers || [];
        agenticUsers.forEach((u) => {
            if (!u?.userName) return;
            userMetadataMap[u.userName] = {
                team: u.teamName || '',
                userRole: u.userRole || '',
                userEmail: u.userEmail || '',
            };
        });

        return { usernameMap, userMetadataMap, userAnalysisKeysByDeviceId };
    } catch (e) {
        return { usernameMap: {}, userMetadataMap: {}, userAnalysisKeysByDeviceId: new Map() };
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

    if (displayName && usernameMap[displayName]) {
        return usernameMap[displayName];
    }

    if (name && usernameMap[name]) {
        return usernameMap[name];
    }

    const collectionName = displayName || name;
    if (collectionName) {
        const parts = collectionName.split('.');
        if (parts.length >= 1) {
            const deviceId = parts[0];

            const deviceIdKey = `__deviceId__${String(deviceId).toLowerCase()}`;
            if (usernameMap[deviceIdKey]) {
                return usernameMap[deviceIdKey];
            }

            if (parts.length >= 3) {
                const serviceName = parts.slice(2).join('.');
                const endpointShieldKey = `${String(deviceId)}.${serviceName}`.toLowerCase();
                if (usernameMap[endpointShieldKey]) {
                    return usernameMap[endpointShieldKey];
                }
            }
        }
    }

    return DEFAULT_VALUE;
};

/**
 * Username from Endpoint Shield map, else from envType tags (e.g. username=) for local / mixed setups.
 */
const getResolvedUsernameForCollection = (collection, usernameMap) => {
    const fromShield = getUsernameForCollection(collection, usernameMap);
    if (fromShield !== DEFAULT_VALUE) return fromShield;
    const fromTags = findUsernameFromEnvTypeTags(collection.envType);
    return fromTags || DEFAULT_VALUE;
};

export {
    fetchEndpointShieldUsernameMap,
    fetchEndpointShieldUserMetadata,
    buildUserAnalysisKeysByDeviceId,
    getUsernameForCollection,
    getResolvedUsernameForCollection,
    MODULE_TYPE,
    DEFAULT_VALUE
};

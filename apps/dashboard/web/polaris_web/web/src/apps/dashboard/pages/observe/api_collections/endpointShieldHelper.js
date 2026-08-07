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

        // Server sends just the collection-name list (endpointShieldHelper's only use of the
        // mcpServers sub-object) instead of the full per-server clientType/url/updatedTs data —
        // see ModuleInfoAction.fetchEndpointShieldUserMetadata.
        (ad.mcpServerCollectionNames || []).forEach((name) => {
            if (name) usernameMap[name.toLowerCase()] = username;
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
        const response = await settingRequests.fetchEndpointShieldUserMetadata();
        return buildUsernameMapFromModuleInfos(response?.moduleInfos || []);
    } catch (e) {
        return {};
    }
};

// The Agentic-assets / Users-and-devices / Device-endpoints pages redirect-chain into each other on
// mount (agenticNewLayout), so each landing fires this twice+ (each = fetchEndpointShieldUserMetadata +
// fetchAgenticUsers). Collapse that burst with an in-flight promise dedup + a short TTL cache. The TTL is
// small enough that a genuine re-navigation after a few seconds still refetches.
let _shieldMetaCache = { ts: 0, data: null };
let _shieldMetaInflight = null;
const SHIELD_META_TTL_MS = 3000;

const fetchEndpointShieldUserMetadata = async () => {
    const now = Date.now();
    if (_shieldMetaCache.data && now - _shieldMetaCache.ts <= SHIELD_META_TTL_MS) return _shieldMetaCache.data;
    if (_shieldMetaInflight) return _shieldMetaInflight;

    _shieldMetaInflight = (async () => {
        try {
            const [moduleResp, agenticUsersResp] = await Promise.all([
                settingRequests.fetchEndpointShieldUserMetadata(),
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
                    teamSource: u.teamSource || 'sso',
                    roleSource: u.roleSource || 'sso',
                };
            });

            const result = { usernameMap, userMetadataMap, userAnalysisKeysByDeviceId, moduleInfos };
            _shieldMetaCache = { ts: Date.now(), data: result };
            return result;
        } catch (e) {
            // eslint-disable-next-line no-console
            console.error("fetchEndpointShieldUserMetadata failed:", e);
            return { usernameMap: {}, userMetadataMap: {}, userAnalysisKeysByDeviceId: new Map(), moduleInfos: [] };
        } finally {
            _shieldMetaInflight = null;
        }
    })();
    return _shieldMetaInflight;
};

const PLACEHOLDER_ACTORS = new Set(['', '-', '127.0.0.1', '0.0.0.0']);

const coworkActorFallback = (collection, actor) => {
    const host = (collection?.displayName || collection?.name || '').toLowerCase();
    if (!host.includes('cowork')) return null;
    const a = typeof actor === 'string' ? actor.trim() : '';
    if (!a || PLACEHOLDER_ACTORS.has(a)) return null;
    return a;
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
 * 5. Claude Cowork hosts: optional actor (user.email) when shield map misses
 *
 * @param {Object} collection - Collection object with displayName and/or name
 * @param {Object} usernameMap - Map of collection name to username
 * @param {string} [actor] - Optional threat-event actor (e.g. user.email for Cowork)
 * @returns {string} - Username or "-" if not found
 */
// Cheap presence check — avoids Object.keys(usernameMap) allocating/copying every key just to test
// emptiness. Matters here because this runs once per collection (thousands of calls per page).
const hasAnyKey = (obj) => {
    for (const _ in obj) return true;
    return false;
};

const getUsernameForCollection = (collection, usernameMap, actor) => {
    if (!usernameMap || !collection || !hasAnyKey(usernameMap)) return DEFAULT_VALUE;

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

    return coworkActorFallback(collection, actor) || DEFAULT_VALUE;
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

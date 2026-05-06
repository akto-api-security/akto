import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import { 
    ASSET_TAG_KEYS,
    ROW_TYPES,
    TYPE_TAG_KEYS,
    SKILL_TAG_KEY,
    CLIENT_TYPES,
    formatDisplayName,
    getTypeFromTags,
    findAssetTag,
    findTypeTag,
    getAgentTypeFromValue,
    hasPersonalAccountTag,
    hasLocalMcpServerTag,
} from "./mcpClientHelper";
import func from "@/util/func";
import { getResolvedUsernameForCollection, DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";

// Table constants
export const PAGE_LIMIT = 100;

// Route constants
export const INVENTORY_PATH = '/dashboard/observe/inventory';
export const INVENTORY_FILTER_KEY = '/dashboard/observe/inventory/';
export const AGENTIC_ASSETS_PATH = '/dashboard/observe/agentic-assets';
export const USERS_AND_DEVICES_PATH = '/dashboard/observe/users-and-devices';
export const AGENTIC_OBSERVE_BACK_PATHS = [AGENTIC_ASSETS_PATH, USERS_AND_DEVICES_PATH];
export const ASSET_TAG_KEY_VALUES = Object.values(ASSET_TAG_KEYS);

// Row ID prefixes for grouped data
const ROW_ID_PREFIX = { AGENT: 'agent', SERVICE: 'service', SKILL: 'skill' };

// Table headers with all columns
export const getHeaders = (options = {}) => {
    const primaryTitle = options.primaryColumnTitle ?? "Agentic asset";
    const primaryText = options.primaryColumnText ?? primaryTitle;
    const includeIconColumn = options.includeIconColumn !== false;
    const includeUserColumns = options.includeUserColumns === true;
    const endpointsColumnLabel = options.endpointsColumnLabel ?? "Endpoints";
    const endpointsColumnBoxWidth = options.endpointsColumnBoxWidth ?? "80px";
    const headers = [
        { title: "", text: "", value: "iconComp", isText: CellType.TEXT, boxWidth: '24px' },
        { title: primaryTitle, text: primaryText, value: "groupName", filterKey: "groupName", textValue: "groupName", showFilter: true, sortActive: true },
        { title: "Type", text: "Type", value: "clientType", filterKey: "clientType", textValue: "clientType", boxWidth: "220px" },
        { 
            title: endpointsColumnLabel, 
            text: endpointsColumnLabel, 
            value: "endpointsCount", 
            isText: CellType.TEXT, 
            sortActive: true, 
            boxWidth: endpointsColumnBoxWidth,
            mergeType: (a, b) => (a || 0) + (b || 0),
            shouldMerge: true
        },
        { 
            title: "Risk score", 
            text: "Risk score", 
            value: "riskScoreComp", 
            numericValue: "riskScore", 
            textValue: "riskScore", 
            sortActive: true, 
            boxWidth: "80px",
            mergeType: (a, b) => Math.max(a || 0, b || 0),
            shouldMerge: true
        },
        {
            title: "Sensitive data",
            text: "Sensitive data",
            value: "sensitiveSubTypes", 
            numericValue: "sensitiveInRespTypes",
            textValue: "sensitiveSubTypesVal", 
            boxWidth: "160px",
            mergeType: (a, b) => [...new Set([...(a || []), ...(b || [])])],
            shouldMerge: true
        },
        {
            title: "Last traffic seen",
            text: "Last traffic seen",
            value: "lastTraffic",
            numericValue: "detectedTimestamp",
            isText: CellType.TEXT,
            sortActive: true,
            boxWidth: "120px",
            mergeType: (a, b) => Math.max(a || 0, b || 0),
            shouldMerge: true
        },
        ...(includeUserColumns ? [
            {
                title: "Team",
                text: "Team",
                value: "team",
                filterKey: "team",
                textValue: "team",
                isText: CellType.TEXT,
                showFilter: true,
                boxWidth: "120px",
            },
            {
                title: "User role",
                text: "User role",
                value: "userRole",
                filterKey: "userRole",
                textValue: "userRole",
                isText: CellType.TEXT,
                showFilter: true,
                boxWidth: "120px",
            },
        ] : []),
    ];
    if (!includeIconColumn) {
        return headers.filter((h) => h.value !== "iconComp");
    }
    return headers;
};

export const sortOptions = [
    { label: "Name", value: "groupName asc", directionLabel: "A-Z", sortKey: "groupName", columnIndex: 2 },
    { label: "Name", value: "groupName desc", directionLabel: "Z-A", sortKey: "groupName", columnIndex: 2 },
    { label: "Type", value: "clientType asc", directionLabel: "A-Z", sortKey: "clientType", columnIndex: 3 },
    { label: "Type", value: "clientType desc", directionLabel: "Z-A", sortKey: "clientType", columnIndex: 3 },
    { label: "Endpoints", value: "endpointsCount asc", directionLabel: "Lowest", sortKey: "endpointsCount", columnIndex: 4 },
    { label: "Endpoints", value: "endpointsCount desc", directionLabel: "Highest", sortKey: "endpointsCount", columnIndex: 4 },
    { label: "Risk score", value: "riskScore asc", directionLabel: "Lowest", sortKey: "riskScore", columnIndex: 5 },
    { label: "Risk score", value: "riskScore desc", directionLabel: "Highest", sortKey: "riskScore", columnIndex: 5 },
    { label: "Last traffic seen", value: "detectedTimestamp asc", directionLabel: "Oldest", sortKey: "detectedTimestamp", columnIndex: 7 },
    { label: "Last traffic seen", value: "detectedTimestamp desc", directionLabel: "Newest", sortKey: "detectedTimestamp", columnIndex: 7 },
];

/** Same as sortOptions but column indices shifted when the icon column is omitted from headers. */
export const getSortOptionsWithoutIconColumn = (opts = {}) => {
    const endpointsColumnLabel = opts.endpointsColumnLabel ?? "Endpoints";
    return sortOptions.map((o) => ({
        ...o,
        columnIndex: o.columnIndex - 1,
        ...(o.sortKey === "endpointsCount" ? { label: endpointsColumnLabel } : {}),
    }));
};

export const resourceName = { singular: "Agentic asset", plural: "Agentic assets" };

// Extract endpoint ID from hostname format: <endpoint-id>.<source-id>.<service-name>
export const extractEndpointId = (hostName) => {
    if (!hostName) return null;
    const parts = hostName.split('.');
    return parts[0];
};

// Extract service name from hostname format: <endpoint-id>.<source-id>.<service-name>
// Service name can contain dots (e.g., mcp.razorpay.com)
// Skip first 2 parts (endpoint-id, source-id) and join the rest
export const extractServiceName = (hostName) => {
    if (!hostName) return null;
    const parts = hostName.split('.');
    // Need at least 3 parts: endpoint-id, source-id, and at least one part of service-name
    if (parts.length < 3) return hostName;
    // Skip first 2 parts and join the rest as service name
    return parts.slice(2).join('.');
};

// Group collections by agent identification (mcp-client, ai-agent values)
// These are the sources that discovered the services (cursor, litellm, etc.)
// Note: browser-llm-agent is excluded from this grouping
export const groupCollectionsByAgent = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}) => {
    const agents = {};
    
    collections.forEach((c) => {
        if (c.deactivated) return;
        const assetTag = findAssetTag(c.envType);
        if (!assetTag?.value) return; // Skip collections without agent tag
        if (assetTag.keyName === ASSET_TAG_KEYS.BROWSER_LLM_AGENT) return; // Skip browser-llm-agent rows
        
        const key = assetTag.value;
        const hostName = c.hostName || c.displayName || c.name;
        const endpointId = extractEndpointId(hostName);
        
        if (!agents[key]) {
            agents[key] = {
                rowType: ROW_TYPES.AGENT,
                groupName: formatDisplayName(assetTag.value),
                groupKey: key,
                tagKey: assetTag.keyName,
                tagValue: assetTag.value,
                clientType: getAgentTypeFromValue(assetTag.value),
                collections: [],
                firstCollection: null,
                endpointIds: new Set(),
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                hasPersonalAccount: false,
                hasLocalMcpServer: false,
            };
        }

        agents[key].collections.push(c);
        if (!agents[key].firstCollection) agents[key].firstCollection = c;
        if (hasPersonalAccountTag(c.envType)) agents[key].hasPersonalAccount = true;
        if (hasLocalMcpServerTag(c.envType)) agents[key].hasLocalMcpServer = true;
        
        // Track unique endpoint IDs
        if (endpointId) {
            agents[key].endpointIds.add(endpointId);
        }
        
        // Aggregate sensitive types
        const sensitive = sensitiveMap[c.id] || [];
        sensitive.forEach(s => agents[key].sensitiveTypes.add(s));
        
        // Track max traffic timestamp
        const traffic = trafficMap[c.id] || 0;
        if (traffic > agents[key].maxTrafficTimestamp) {
            agents[key].maxTrafficTimestamp = traffic;
        }

        // Track max risk score
        const riskScore = riskScoreMap[c.id] || 0;
        if (riskScore > agents[key].maxRiskScore) {
            agents[key].maxRiskScore = riskScore;
        }
    });
    
    return Object.values(agents).map(g => ({
        ...g,
        id: `${ROW_ID_PREFIX.AGENT}-${g.groupKey}`,
        endpointsCount: g.endpointIds.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(' ') || '-',
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore || null,
    }));
};

// Group collections by service name (extracted from hostname)
// Hostname format: <endpoint-id>.<source-id>.<service-name>
// Service name can contain dots (e.g., "mcp.razorpay.com" from "123.456.mcp.razorpay.com")
export const groupCollectionsByService = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}) => {
    const services = {};
    
    collections.forEach((c) => {
        if (c.deactivated) return;
        const typeTag = findTypeTag(c.envType);
        if (!typeTag) return; // Skip collections without type tag
        // For gen-ai, the agent is the service — show only under agent grouping
        if (typeTag.keyName === TYPE_TAG_KEYS.GEN_AI) return;

        const hostName = c.hostName || c.displayName || c.name;
        if (!hostName) return;
        
        const serviceName = extractServiceName(hostName);
        if (!serviceName) return;
        
        const endpointId = extractEndpointId(hostName);
        const key = serviceName;
        
        if (!services[key]) {
            services[key] = {
                rowType: ROW_TYPES.SERVICE,
                groupName: serviceName,
                groupKey: key,
                serviceName: serviceName,
                hostNames: [],
                clientType: getTypeFromTags(c.envType),
                collections: [],
                firstCollection: null,
                endpointIds: new Set(),
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                hasPersonalAccount: false,
                hasLocalMcpServer: false,
            };
        }

        services[key].collections.push(c);
        if (!services[key].hostNames.includes(hostName)) {
            services[key].hostNames.push(hostName);
        }
        if (!services[key].firstCollection) services[key].firstCollection = c;
        if (hasPersonalAccountTag(c.envType)) services[key].hasPersonalAccount = true;
        if (hasLocalMcpServerTag(c.envType)) services[key].hasLocalMcpServer = true;
        
        // Track unique endpoint IDs
        if (endpointId) {
            services[key].endpointIds.add(endpointId);
        }
        
        // Aggregate sensitive types
        const sensitive = sensitiveMap[c.id] || [];
        sensitive.forEach(s => services[key].sensitiveTypes.add(s));
        
        // Track max traffic timestamp
        const traffic = trafficMap[c.id] || 0;
        if (traffic > services[key].maxTrafficTimestamp) {
            services[key].maxTrafficTimestamp = traffic;
        }
        
        // Track max risk score
        const riskScore = riskScoreMap[c.id] || 0;
        if (riskScore > services[key].maxRiskScore) {
            services[key].maxRiskScore = riskScore;
        }
    });
    
    return Object.values(services).map(g => ({
        ...g,
        id: `${ROW_ID_PREFIX.SERVICE}-${g.groupKey}`,
        endpointsCount: g.endpointIds.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(' ') || '-',
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore,
    }));
};

// Group collections by skill tag value — one row per unique skill name
export const groupCollectionsBySkill = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}) => {
    const skills = {};

    collections.forEach((c) => {
        if (c.deactivated) return;
        const skillValues = c.skills || [];
        if (!skillValues || skillValues.length === 0) return;

        const hostName = c.hostName || c.displayName || c.name;
        const endpointId = extractEndpointId(hostName);

        skillValues.forEach((skillValue) => {
            if (!skills[skillValue]) {
                skills[skillValue] = {
                    rowType: ROW_TYPES.SKILL,
                    groupName: skillValue,
                    groupKey: skillValue,
                    clientType: CLIENT_TYPES.SKILL,
                    collections: [],
                    firstCollection: null,
                    hostNames: [],
                    endpointIds: new Set(),
                    sensitiveTypes: new Set(),
                    maxTrafficTimestamp: 0,
                    maxRiskScore: 0,
                    hasPersonalAccount: false,
                };
            }

            skills[skillValue].collections.push(c);
            if (!skills[skillValue].firstCollection) skills[skillValue].firstCollection = c;
            if (hasPersonalAccountTag(c.envType)) skills[skillValue].hasPersonalAccount = true;
            if (hostName && !skills[skillValue].hostNames.includes(hostName)) {
                skills[skillValue].hostNames.push(hostName);
            }
            if (endpointId) skills[skillValue].endpointIds.add(endpointId);

            const sensitive = sensitiveMap[c.id] || [];
            sensitive.forEach(s => skills[skillValue].sensitiveTypes.add(s));

            const traffic = trafficMap[c.id] || 0;
            if (traffic > skills[skillValue].maxTrafficTimestamp) {
                skills[skillValue].maxTrafficTimestamp = traffic;
            }

            const riskScore = riskScoreMap[c.id] || 0;
            if (riskScore > skills[skillValue].maxRiskScore) {
                skills[skillValue].maxRiskScore = riskScore;
            }
        });
    });

    return Object.values(skills).map(g => ({
        ...g,
        id: `${ROW_ID_PREFIX.SKILL}-${g.groupKey}`,
        endpointsCount: g.endpointIds.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(' ') || '-',
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore || null,
    }));
};

const accumulateHostGroupedCollection = (group, c, trafficMap, sensitiveMap, riskScoreMap) => {
    const hostName = c.hostName || c.displayName || c.name;
    if (hostName && !group.hostNames.includes(hostName)) {
        group.hostNames.push(hostName);
    }
    if (!group.firstCollection) {
        group.firstCollection = c;
    }
    const sensitive = sensitiveMap[c.id] || [];
    sensitive.forEach((s) => group.sensitiveTypes.add(s));
    const traffic = trafficMap[c.id] || 0;
    if (traffic > group.maxTrafficTimestamp) {
        group.maxTrafficTimestamp = traffic;
    }
    const riskScore = riskScoreMap[c.id] || 0;
    if (riskScore > group.maxRiskScore) {
        group.maxRiskScore = riskScore;
    }
};

const finalizeHostGroupedRow = (g, idSegment) => {
    const clientTypeStr = g.clientTypes.size > 0 ? [...g.clientTypes].sort().join(", ") : "-";
    return {
        rowType: g.rowType,
        groupName: g.groupName,
        groupKey: g.groupKey,
        hostNames: g.hostNames,
        inventoryScopeLabel: g.groupName,
        collections: g.collections,
        firstCollection: g.firstCollection,
        id: `${ROW_ID_PREFIX.SERVICE}-${idSegment}-${String(g.groupKey).replace(/[^a-zA-Z0-9_.-]/g, "_")}`,
        clientType: clientTypeStr,
        endpointsCount: g.hostNames.length,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(" ") || "-",
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore,
        team: g.team || '',
        userRole: g.userRole || '',
    };
};

/** Group by device id (first segment of agentic collection hostname). Row opens inventory via hostname filter. */
export const groupCollectionsByDevice = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}) => {
    const devices = {};

    collections.forEach((c) => {
        if (c.deactivated) return;
        const hostName = c.hostName || c.displayName || c.name;
        if (!hostName) return;
        const deviceId = extractEndpointId(hostName);
        if (!deviceId) return;

        if (!devices[deviceId]) {
            devices[deviceId] = {
                rowType: ROW_TYPES.SERVICE,
                groupName: deviceId,
                groupKey: deviceId,
                hostNames: [],
                clientTypes: new Set(),
                collections: [],
                firstCollection: null,
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
            };
        }
        const g = devices[deviceId];
        g.collections.push(c);
        g.clientTypes.add(getTypeFromTags(c.envType));
        accumulateHostGroupedCollection(g, c, trafficMap, sensitiveMap, riskScoreMap);
    });

    return Object.values(devices).map((g) => finalizeHostGroupedRow(g, "device"));
};

/** Group by Endpoint Shield username. Row opens inventory via hostname filter. Skips collections without a resolved username. */
export const groupCollectionsByUser = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}, usernameMap = {}, userMetadataMap = {}) => {
    const users = {};

    collections.forEach((c) => {
        if (c.deactivated) return;
        const username = getResolvedUsernameForCollection(c, usernameMap);
        if (!username || username === DEFAULT_VALUE) return;

        if (!users[username]) {
            const meta = userMetadataMap[username] || {};
            users[username] = {
                rowType: ROW_TYPES.SERVICE,
                groupName: username,
                groupKey: username,
                hostNames: [],
                clientTypes: new Set(),
                collections: [],
                firstCollection: null,
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                team: meta.team || '',
                userRole: meta.userRole || '',
            };
        }
        const g = users[username];
        g.collections.push(c);
        g.clientTypes.add(getTypeFromTags(c.envType));
        accumulateHostGroupedCollection(g, c, trafficMap, sensitiveMap, riskScoreMap);
    });

    return Object.values(users).map((g) => finalizeHostGroupedRow(g, "user"));
};

export const createEnvTypeFilter = (values, negated = false) => ({
    filters: [{ key: 'envType', label: func.convertToDisambiguateLabelObj(values, null, 2), value: { values, negated }, onRemove: () => {} }],
    sort: []
});

export const createHostnameFilter = (hostnames, options = {}) => ({
    filters: [{ key: 'hostName', label: func.convertToDisambiguateLabelObj(hostnames, null, 2), value: { values: hostnames, negated: false }, onRemove: () => {} }],
    sort: [],
    ...(options.inventoryScopeLabel ? { inventoryScopeLabel: options.inventoryScopeLabel } : {}),
});

/** Filter payload for PersistStore when navigating from agentic grouped tables to inventory. */
export const buildAgenticInventoryFilterForRow = (row) => {
    if (row.rowType === ROW_TYPES.AGENT && row.tagKey && row.tagValue) {
        return createEnvTypeFilter([`${row.tagKey}=${row.tagValue}`], false);
    }
    if (row.rowType === ROW_TYPES.SERVICE && row.hostNames?.length > 0) {
        return createHostnameFilter(row.hostNames, row.inventoryScopeLabel ? { inventoryScopeLabel: row.inventoryScopeLabel } : {});
    }
    if (row.rowType === ROW_TYPES.SKILL && row.groupKey) {
        return createEnvTypeFilter([`${SKILL_TAG_KEY}=${row.groupKey}`], false);
    }
    return null;
};

export { ROW_TYPES, SKILL_TAG_KEY } from "./mcpClientHelper";

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
    hasBrowserLlmTag,
    getAgentTypeFromValue,
    hasPersonalAccountTag,
    hasLocalMcpServerTag,
    hasMisconfiguredConfigTag,
    isNotAttachedHostName,
    CLIENT_TAG_ALIASES,
    getClientTagVariants,
    resolveClientKey,
} from "./mcpClientHelper";
export { CLIENT_TAG_ALIASES, getClientTagVariants, resolveClientKey };
import func from "@/util/func";
import {
    getResolvedUsernameForCollection,
    DEFAULT_VALUE,
} from "../api_collections/endpointShieldHelper";

// Table constants
export const PAGE_LIMIT = 100;

export const NEW_LAYOUT_TOOLTIP = "Switch to the new layout for a faster, more focused experience.";

export const SKILL_RISK_CACHE_TTL_MS = 2 * 60 * 1000;
export const AGENTIC_COLLECTIONS_CACHE_TTL_MS = 2 * 60 * 1000;
export const AGENTIC_SENSITIVE_INFO_CACHE_TTL_MS = 2 * 60 * 1000;

// Route constants
export const INVENTORY_PATH = '/dashboard/observe/inventory';
export const INVENTORY_FILTER_KEY = '/dashboard/observe/inventory/';
export const AGENTIC_ASSETS_PATH = '/dashboard/observe/agentic-assets';
export const AGENTIC_ASSETS_LEGACY_PATH = '/dashboard/observe/agentic-assets-legacy';
export const USERS_AND_DEVICES_PATH = '/dashboard/observe/users-and-devices';
export const DEVICE_ENDPOINTS_PATH = '/dashboard/observe/endpoints';
export const AGENTIC_OBSERVE_BACK_PATHS = [
    AGENTIC_ASSETS_PATH,
    AGENTIC_ASSETS_LEGACY_PATH,
    USERS_AND_DEVICES_PATH,
    DEVICE_ENDPOINTS_PATH,
];
export const ASSET_TAG_KEY_VALUES = Object.values(ASSET_TAG_KEYS);

// Row ID prefixes for grouped data
const ROW_ID_PREFIX = { AGENT: 'agent', SERVICE: 'service', SKILL: 'skill', LLM: 'llm' };

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

// Well-known TLD/platform suffixes that are NOT meaningful service names.
// If parts[2] is one of these the hostname is NOT device.agent.service format
// (e.g. vulnerable-mcp-kong.akto.io → slice(2) = "io", meaningless).
const SUFFIX_TLDS = new Set(['io', 'com', 'net', 'org', 'cloud', 'dev', 'app', 'ai', 'co']);

// Extract service name from hostname format: <endpoint-id>.<source-id>.<service-name>
// Service name can contain dots (e.g., mcp.razorpay.com, api.githubcopilot.com).
// Falls back to the full hostname for short hostnames that don't follow the pattern.
export const extractServiceName = (hostName) => {
    if (!hostName) return null;
    const parts = hostName.split('.');
    if (parts.length < 3) return hostName;
    // If the third segment is a bare TLD/platform suffix the hostname is NOT in
    // device.agent.service format — use the full hostname as the service name so it
    // groups as a distinct asset rather than collapsing under the bare TLD.
    if (SUFFIX_TLDS.has(parts[2].toLowerCase())) return hostName;
    return parts.slice(2).join('.');
};

// device+service key from a hostname (first + last segment), ignoring the middle source.
export const deviceServiceKey = (hostName) => {
    if (!hostName) return null;
    const parts = hostName.split(".");
    if (parts.length < 2) return null;
    return parts[0] + " " + parts[parts.length - 1];
};

// Group collections by agent identification (mcp-client, ai-agent values)
// These are the sources that discovered the services (cursor, litellm, etc.)
// Note: browser-llm-agent is excluded from this grouping
export const groupCollectionsByAgent = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}) => {
    const agents = {};

    collections.forEach((c) => {
        if (c.deactivated) return;
        const hostName = c.hostName || c.displayName || c.name;
        if (isNotAttachedHostName(hostName)) return; // Orphan skill bucket — not a real agent
        if (hasBrowserLlmTag(c.envType)) return; // Captured under groupCollectionsByLLM instead
        const assetTag = findAssetTag(c.envType);
        if (!assetTag?.value) return; // Skip collections without agent tag
        if (assetTag.keyName === ASSET_TAG_KEYS.BROWSER_LLM_AGENT) return; // Skip browser-llm-agent rows

        const key = CLIENT_TAG_ALIASES[assetTag.value] ?? assetTag.value;
        const endpointId = extractEndpointId(hostName);
        
        if (!agents[key]) {
            agents[key] = {
                rowType: ROW_TYPES.AGENT,
                groupName: formatDisplayName(key),
                groupKey: key,
                tagKey: assetTag.keyName,
                tagValue: key,
                clientType: getAgentTypeFromValue(key),
                collections: [],
                firstCollection: null,
                endpointIds: new Set(),
                sensitiveTypes: new Set(),
                rawTagValues: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                hasPersonalAccount: false,
                hasLocalMcpServer: false,
                hasMisconfiguredConfig: false,
            };
        }

        agents[key].rawTagValues.add(assetTag.value);
        agents[key].collections.push(c);
        if (!agents[key].firstCollection) agents[key].firstCollection = c;
        if (hasPersonalAccountTag(c.envType)) agents[key].hasPersonalAccount = true;
        if (hasLocalMcpServerTag(c.envType)) agents[key].hasLocalMcpServer = true;
        if (hasMisconfiguredConfigTag(c.envType)) agents[key].hasMisconfiguredConfig = true;
        
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
        rawTagValues: Array.from(g.rawTagValues),
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore || null,
    }));
};

// Group collections by service name (extracted from hostname)
// Hostname format: <endpoint-id>.<source-id>.<service-name>
// Service name can contain dots (e.g., "mcp.razorpay.com" from "123.456.mcp.razorpay.com")

// Tag keys whose presence means the collection already gets its own row in agentGroups.
// browser-llm-agent is deliberately excluded — those rows never appear in agentGroups
// (see the hasBrowserLlmTag skip in groupCollectionsByAgent above), so treating it as an
// "owner" tag here would drop the collection from every group with nothing else to show it.
const AGENT_OWNER_TAG_KEYS = new Set([ASSET_TAG_KEYS.MCP_CLIENT, ASSET_TAG_KEYS.AI_AGENT]);

// Returns true if the collection has an asset-owner tag (Atlas ai-agent/mcp-client)
// or the older connector agent-name tag. Used to exclude agent-owned gen-ai collections
// from service groups and from Argus source-id groups.
const hasAgentOwnerTag = (envType) =>
    (envType || []).some(t => AGENT_OWNER_TAG_KEYS.has(t.keyName) || t.keyName === "agent-name");

export const groupCollectionsByService = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}) => {
    const services = {};

    collections.forEach((c) => {
        if (c.deactivated) return;
        if (isNotAttachedHostName(c.hostName || c.displayName || c.name)) return;
        if (hasBrowserLlmTag(c.envType)) return; // Captured under groupCollectionsByLLM instead
        const typeTag = findTypeTag(c.envType);
        if (!typeTag) return; // Skip collections without type tag
        // gen-ai collections that also have an asset tag (ai-agent/mcp-client) or a connector
        // agent tag (agent-name — older DEFENDER/ENDPOINT connector format) are already
        // captured under agentGroups — skip them here to avoid double-counting.
        // gen-ai collections WITHOUT any such tag are standalone assets that predate the
        // tagging scheme and must still appear.
        if (typeTag.keyName === TYPE_TAG_KEYS.GEN_AI) {
            if (hasAgentOwnerTag(c.envType)) return;
        }

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
                hostNames: new Set(),
                clientType: getTypeFromTags(c.envType),
                collections: [],
                firstCollection: null,
                endpointIds: new Set(),
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                hasPersonalAccount: false,
                hasLocalMcpServer: false,
                hasMisconfiguredConfig: false,
            };
        }

        services[key].collections.push(c);
        services[key].hostNames.add(hostName);
        if (!services[key].firstCollection) services[key].firstCollection = c;
        if (hasPersonalAccountTag(c.envType)) services[key].hasPersonalAccount = true;
        if (hasLocalMcpServerTag(c.envType)) services[key].hasLocalMcpServer = true;
        if (hasMisconfiguredConfigTag(c.envType)) services[key].hasMisconfiguredConfig = true;
        
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
        hostNames: Array.from(g.hostNames),
        endpointsCount: g.endpointIds.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(' ') || '-',
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore,
    }));
};

// Group collections carrying an explicit browser-llm tag (the browser extension detected an
// LLM chat page). Grouped by service name extracted from hostname, same as service groups, but
// membership is decided solely by the browser-llm tag's presence — not by tag order or by
// whichever other type/asset tags (gen-ai, ai-agent, browser-llm-agent) are also attached.
export const groupCollectionsByLLM = (collections, trafficMap = {}, sensitiveMap = {}, riskScoreMap = {}) => {
    const llms = {};

    collections.forEach((c) => {
        if (c.deactivated) return;
        const hostName = c.hostName || c.displayName || c.name;
        if (isNotAttachedHostName(hostName)) return;
        if (!hasBrowserLlmTag(c.envType)) return;
        if (!hostName) return;

        const serviceName = extractServiceName(hostName);
        if (!serviceName) return;

        const endpointId = extractEndpointId(hostName);
        const key = serviceName;

        if (!llms[key]) {
            llms[key] = {
                rowType: ROW_TYPES.SERVICE,
                groupName: serviceName,
                groupKey: key,
                serviceName: serviceName,
                hostNames: new Set(),
                clientType: CLIENT_TYPES.LLM,
                collections: [],
                firstCollection: null,
                endpointIds: new Set(),
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                hasPersonalAccount: false,
                hasLocalMcpServer: false,
                hasMisconfiguredConfig: false,
            };
        }

        llms[key].collections.push(c);
        llms[key].hostNames.add(hostName);
        if (!llms[key].firstCollection) llms[key].firstCollection = c;
        if (hasPersonalAccountTag(c.envType)) llms[key].hasPersonalAccount = true;
        if (hasLocalMcpServerTag(c.envType)) llms[key].hasLocalMcpServer = true;
        if (hasMisconfiguredConfigTag(c.envType)) llms[key].hasMisconfiguredConfig = true;

        if (endpointId) {
            llms[key].endpointIds.add(endpointId);
        }

        const sensitive = sensitiveMap[c.id] || [];
        sensitive.forEach(s => llms[key].sensitiveTypes.add(s));

        const traffic = trafficMap[c.id] || 0;
        if (traffic > llms[key].maxTrafficTimestamp) {
            llms[key].maxTrafficTimestamp = traffic;
        }

        const riskScore = riskScoreMap[c.id] || 0;
        if (riskScore > llms[key].maxRiskScore) {
            llms[key].maxRiskScore = riskScore;
        }
    });

    return Object.values(llms).map(g => ({
        ...g,
        id: `${ROW_ID_PREFIX.LLM}-${g.groupKey}`,
        hostNames: Array.from(g.hostNames),
        endpointsCount: g.endpointIds.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(' ') || '-',
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore,
    }));
};

// Groups Argus gen-ai collections (no asset owner tag) by source-id (hostname parts[1]).
// Option values match what Argus policies store in selectedAgentServers (e.g. "cursor", "claude1").
export const groupArgusAgentsBySourceId = (collections) => {
    const agents = {};
    collections.forEach((c) => {
        if (c.deactivated) return;
        const typeTag = findTypeTag(c.envType);
        if (!typeTag || typeTag.keyName !== TYPE_TAG_KEYS.GEN_AI) return;
        if (hasAgentOwnerTag(c.envType)) return;
        const hostName = c.hostName || c.displayName || c.name;
        if (!hostName) return;
        const parts = hostName.split('.');
        if (parts.length < 3) return;
        const sourceId = parts[1];
        if (!sourceId) return;
        if (!agents[sourceId]) {
            agents[sourceId] = { groupKey: sourceId, collections: [] };
        }
        agents[sourceId].collections.push(c);
    });
    return Object.values(agents);
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
                    hostNames: new Set(),
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
            if (hostName) skills[skillValue].hostNames.add(hostName);
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
        hostNames: Array.from(g.hostNames),
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
    if (hostName) group.hostNames.add(hostName);
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
        hostNames: Array.from(g.hostNames),
        inventoryScopeLabel: g.groupName,
        collections: g.collections,
        firstCollection: g.firstCollection,
        id: `${ROW_ID_PREFIX.SERVICE}-${idSegment}-${String(g.groupKey).replace(/[^a-zA-Z0-9_.-]/g, "_")}`,
        clientType: clientTypeStr,
        endpointsCount: g.hostNames.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(" ") || "-",
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore,
        team: g.team || '',
        userRole: g.userRole || '',
        hasPersonalAccount: g.hasPersonalAccount || false,
        hasLocalMcpServer: g.hasLocalMcpServer || false,
        hasMisconfiguredConfig: g.hasMisconfiguredConfig || false,
        teamSource: g.teamSource || 'sso',
        roleSource: g.roleSource || 'sso',
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
                hostNames: new Set(),
                clientTypes: new Set(),
                collections: [],
                firstCollection: null,
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                hasPersonalAccount: false,
                hasLocalMcpServer: false,
                hasMisconfiguredConfig: false,
            };
        }
        const g = devices[deviceId];
        g.collections.push(c);
        g.clientTypes.add(getTypeFromTags(c.envType));
        if (hasPersonalAccountTag(c.envType)) g.hasPersonalAccount = true;
        if (hasLocalMcpServerTag(c.envType)) g.hasLocalMcpServer = true;
        if (hasMisconfiguredConfigTag(c.envType)) g.hasMisconfiguredConfig = true;
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
                hostNames: new Set(),
                clientTypes: new Set(),
                collections: [],
                firstCollection: null,
                sensitiveTypes: new Set(),
                maxTrafficTimestamp: 0,
                maxRiskScore: 0,
                uniqueSkillNames: new Set(),
                nonSkillCollectionsCount: 0,
                team: meta.team || '',
                userRole: meta.userRole || '',
                teamSource: meta.teamSource || 'sso',
                roleSource: meta.roleSource || 'sso',
            };
        }
        const g = users[username];
        g.collections.push(c);
        const category = getTypeFromTags(c.envType);
        g.clientTypes.add(category);
        if (category !== CLIENT_TYPES.SKILL) {
            g.nonSkillCollectionsCount += 1;
        }
        // Skills bundled inside an AI Agent / MCP Server collection still make the user a Skill
        // owner for the Type column, even when the user has no standalone Skill-type collection.
        if (Array.isArray(c.skills) && c.skills.length > 0) {
            g.clientTypes.add(CLIENT_TYPES.SKILL);
            c.skills.forEach(s => { if (s) g.uniqueSkillNames.add(String(s).toLowerCase()); });
        }
        accumulateHostGroupedCollection(g, c, trafficMap, sensitiveMap, riskScoreMap);
    });

    // Per-user "Agentic assets" count uses the same semantic as the Agentic assets totals page:
    // non-Skill collections + unique skill names (from c.skills[] across all the user's collections).
    // Counting hostNames alone hides skills bundled inside AI Agent / MCP Server collections.
    return Object.values(users).map((g) => {
        const row = finalizeHostGroupedRow(g, "user");
        row.endpointsCount = g.nonSkillCollectionsCount + g.uniqueSkillNames.size;
        row.uniqueSkillNames = g.uniqueSkillNames;
        return row;
    });
};

export const createEnvTypeFilter = (values, negated = false, displayName = null) => ({
    filters: [{ key: 'envType', label: func.convertToDisambiguateLabelObj(values, null, 2), value: { values, negated, displayName }, onRemove: () => {} }],
    sort: []
});

export const createHostnameFilter = (hostnames, options = {}) => ({
    filters: [{ key: 'hostName', label: func.convertToDisambiguateLabelObj(hostnames, null, 2), value: { values: hostnames, negated: false }, onRemove: () => {} }],
    sort: [],
    ...(options.inventoryScopeLabel ? { inventoryScopeLabel: options.inventoryScopeLabel } : {}),
});

/** Filter payload for PersistStore when navigating from agentic grouped tables to inventory. */
export const buildAgenticInventoryFilterForRow = (row) => {
    if (row.rowType === ROW_TYPES.AGENT && row.tagKey) {
        const values = (row.rawTagValues?.length > 0 ? row.rawTagValues : [row.tagValue])
            .map(v => `${row.tagKey}=${v}`);
        return createEnvTypeFilter(values, false, row.groupName);
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

function mergeViolationCounts(target, source) {
    if (!source) return;
    ["critical", "high", "medium", "low"].forEach((k) => {
        target[k] = (target[k] || 0) + (source[k] || 0);
    });
}

function violationsForCollections(collectionIds, violationsByCollectionId) {
    const merged = { critical: 0, high: 0, medium: 0, low: 0 };
    (collectionIds || []).forEach((id) => {
        mergeViolationCounts(merged, violationsByCollectionId[id]);
    });
    const total = merged.critical + merged.high + merged.medium + merged.low;
    return total > 0 ? merged : null;
}

// Collections belong to several overlapping groups at once (a collection with N skill tags is a
// member of N skill groups, plus its owning service/agent/llm group), so buildAgenticAssetsPageData
// processes the same collection many more times than there are collections. deviceId/serviceName/
// resolvedUsername only depend on the collection itself, not on which group is asking — precomputing
// them once per collection (see precomputeCollectionInfo) and passing the lookup map in here turns
// that repeated per-membership cost back into a per-collection one.
function buildTeamGroupsForAsset(group, userMetadataMap, collectionInfoMap) {
    const teamCounts = {};
    const seenDevices = new Set();
    (group.collections || []).forEach((c) => {
        const info = collectionInfoMap.get(c.id);
        if (!info?.deviceId || seenDevices.has(info.deviceId)) return;
        seenDevices.add(info.deviceId);
        const username = info.resolvedUsername;
        if (!username || username === DEFAULT_VALUE) return;
        const team = userMetadataMap[username]?.team;
        if (!team) return;
        teamCounts[team] = (teamCounts[team] || 0) + 1;
    });
    return Object.entries(teamCounts).map(([name, count]) => ({ name, count }));
}

function finalizeDevices(byDevice) {
    // round to 1 dp and null out zero scores (no data)
    return [...byDevice.values()].map(d => ({
        ...d,
        services: Array.from(d.services),
        riskScore: d.riskScore > 0 ? Math.round(d.riskScore * 10) / 10 : null,
        lastSeen: d.lastSeenEpoch > 0 ? func.formatChatTimestamp(d.lastSeenEpoch) : "-",
    }));
}

// One pass over every collection (not every group-membership) to compute the per-collection values
// buildTeamGroupsForAsset/buildDevicesForGroup need — see the comment above buildTeamGroupsForAsset.
function precomputeCollectionInfo(collections, usernameMap) {
    const map = new Map();
    collections.forEach((c) => {
        const hostName = c.hostName || c.displayName || c.name;
        map.set(c.id, {
            deviceId: extractEndpointId(hostName),
            serviceName: extractServiceName(hostName),
            resolvedUsername: getResolvedUsernameForCollection(c, usernameMap),
        });
    });
    return map;
}

function userAnalysisCompositeKey(serviceId, deviceId) {
    return `${serviceId}|${deviceId}`;
}

function putUserAnalysisKey(byKey, serviceId, deviceId, row) {
    if (!serviceId || !deviceId) return;
    byKey.set(userAnalysisCompositeKey(serviceId, deviceId), row);
    byKey.set(userAnalysisCompositeKey(String(serviceId).toLowerCase(), String(deviceId).toLowerCase()), row);
}

export function buildUserAnalysisLookup(userAnalysisList = []) {
    const rows = Array.isArray(userAnalysisList) ? userAnalysisList : [];
    const byKey = new Map();
    rows.forEach((row) => {
        const key = row?.id ?? row?._id;
        const serviceId = key?.serviceId ?? row?.serviceId;
        const deviceId = key?.deviceId ?? row?.deviceId;
        putUserAnalysisKey(byKey, serviceId, deviceId, row);
    });
    return byKey;
}

function analysisKeysForCollection(collection, tagValue, userAnalysisKeysByDeviceId) {
    const hostName = collection.hostName || collection.displayName || collection.name;
    if (!hostName) return [];
    const parts = hostName.split(".");
    const deviceId = parts[0];
    if (!deviceId) return [];
    const keys = [];
    const seen = new Set();
    const addPair = (serviceId, devId) => {
        if (!serviceId || !devId) return;
        const sk = userAnalysisCompositeKey(serviceId, devId);
        if (seen.has(sk)) return;
        seen.add(sk);
        keys.push({ serviceId, deviceId: devId });
    };
    const add = (serviceId) => addPair(serviceId, deviceId);

    const shieldEntry = userAnalysisKeysByDeviceId?.get(deviceId)
        ?? userAnalysisKeysByDeviceId?.get(String(deviceId).toLowerCase());
    if (shieldEntry) {
        addPair(shieldEntry.serviceId, shieldEntry.deviceId);
    }
    if (parts.length >= 3) {
        add(parts.slice(2).join("."));
        if (parts[1]) add(parts[1]);
    } else if (parts.length === 2) {
        add(parts[1]);
    }
    const assetTag = findAssetTag(collection.envType);
    if (assetTag?.value) add(assetTag.value);
    if (tagValue) add(tagValue);
    return keys;
}

function lookupUserAnalysisRow(analysisByKey, serviceId, deviceId) {
    return analysisByKey.get(userAnalysisCompositeKey(serviceId, deviceId))
        ?? analysisByKey.get(userAnalysisCompositeKey(String(serviceId).toLowerCase(), String(deviceId).toLowerCase()));
}

// Violations for one server-computed row (see AgenticObserveAction.fetchAgenticAssetsSummary) —
// exported so AgenticAssetsPage.jsx can attach this per-row, for just the current page, instead of
// the server needing to duplicate the hostCounts-attribution logic.
export function getRowViolations(collectionIds, violationsByCollectionId) {
    return violationsForCollections(collectionIds, violationsByCollectionId);
}

// Team breakdown for a server-computed row's device list. Deliberately lighter than
// buildTeamGroupsForAsset (used by the legacy/agent-group client pipeline): the server's `devices`
// list only carries deviceId (not full collection objects), so username resolution here uses only
// the Endpoint Shield deviceId-keyed lookup tier, not the displayName/name-based fallback tiers
// getResolvedUsernameForCollection also tries. Documented tradeoff, not an oversight.
export function buildTeamGroupsFromDevices(devices, usernameMap, userMetadataMap) {
    const teamCounts = {};
    (devices || []).forEach((d) => {
        const key = `__deviceId__${String(d.deviceId).toLowerCase()}`;
        const username = usernameMap?.[key];
        if (!username || username === DEFAULT_VALUE) return;
        const team = userMetadataMap[username]?.team;
        if (!team) return;
        teamCounts[team] = (teamCounts[team] || 0) + 1;
    });
    return Object.entries(teamCounts).map(([name, count]) => ({ name, count }));
}

// AI-interaction tally for a server-computed row's device list — same tradeoff as
// buildTeamGroupsFromDevices: only the Endpoint Shield deviceId mapping (the primary,
// highest-coverage match tier), not the hostname-parts-based fallback tiers
// analysisKeysForCollection also tries, which need per-collection hostnames the server doesn't send
// back for these rows.
export function computeAiInteractionsFromDevices(devices, analysisByKey, userAnalysisKeysByDeviceId) {
    const seen = new Set();
    let totalInputTokens = 0;
    let totalOutputTokens = 0;
    (devices || []).forEach((d) => {
        const deviceId = d.deviceId;
        const shieldEntry = userAnalysisKeysByDeviceId?.get(deviceId)
            ?? userAnalysisKeysByDeviceId?.get(String(deviceId).toLowerCase());
        if (!shieldEntry) return;
        const sk = userAnalysisCompositeKey(shieldEntry.serviceId, shieldEntry.deviceId);
        if (seen.has(sk)) return;
        seen.add(sk);
        const row = lookupUserAnalysisRow(analysisByKey, shieldEntry.serviceId, shieldEntry.deviceId);
        if (!row) return;
        totalInputTokens += Number(row.totalInputTokens) || 0;
        totalOutputTokens += Number(row.totalOutputTokens) || 0;
    });
    const total = totalInputTokens + totalOutputTokens;
    return total > 0 ? { totalInputTokens, totalOutputTokens, total } : null;
}

// Combines what used to be three separate (group.collections || []).forEach passes —
// buildDevicesForGroup, uniqueSkillNamesForGroup, aggregateAiInteractionsForGroup — into one. Each
// collection can belong to many overlapping groups (a collection with N skill tags is a member of N
// skill groups), so buildAgenticAssetsPageData already visits the same collection far more times than
// there are collections; tripling that with three independent traversals per group multiplied the
// cost further for no reason since none of the three depend on each other's results.
// cachedNonAi carries the devices/skillNames computed on a prior call for this same group (see
// buildAgenticAssetsPageData's groupsCache) — everything except aiInteractions is invariant across
// calls that only differ by analysisByKey, so a cache hit skips the device/skill accumulation
// entirely and just re-walks collections for the (much cheaper) AI-interactions tally.
function buildGroupAggregates(group, riskScoreMap, trafficMap, collectionInfoMap, analysisByKey, userAnalysisKeysByDeviceId, cachedNonAi) {
    const byDevice = cachedNonAi ? null : new Map();
    const skillNames = cachedNonAi ? cachedNonAi.skillNames : new Set();
    const seenAnalysisKeys = new Set();
    let totalInputTokens = 0;
    let totalOutputTokens = 0;

    (group.collections || []).forEach((c) => {
        if (!cachedNonAi) {
            const info = collectionInfoMap.get(c.id);

            (c.skills || []).forEach((s) => { if (s) skillNames.add(s); });

            if (info?.deviceId) {
                const { deviceId, serviceName, resolvedUsername } = info;
                const collRisk = riskScoreMap[c.id] || 0;
                const collTraffic = trafficMap[c.id] || 0;
                if (!byDevice.has(deviceId)) {
                    const username = (resolvedUsername && resolvedUsername !== DEFAULT_VALUE) ? resolvedUsername : "";
                    byDevice.set(deviceId, {
                        deviceId, endpoint: deviceId, username, os: null,
                        riskScore: collRisk, lastSeenEpoch: collTraffic, services: new Set(),
                    });
                }
                const entry = byDevice.get(deviceId);
                // accumulate max risk/traffic across all collections this device appears in
                if (collRisk > (entry.riskScore || 0)) entry.riskScore = collRisk;
                if (collTraffic > (entry.lastSeenEpoch || 0)) entry.lastSeenEpoch = collTraffic;
                if (serviceName) entry.services.add(serviceName);
            }
        }

        const keys = analysisKeysForCollection(c, group.tagValue, userAnalysisKeysByDeviceId);
        keys.forEach(({ serviceId, deviceId }) => {
            const sk = userAnalysisCompositeKey(serviceId, deviceId);
            if (seenAnalysisKeys.has(sk)) return;
            seenAnalysisKeys.add(sk);
            const row = lookupUserAnalysisRow(analysisByKey, serviceId, deviceId);
            if (!row) return;
            totalInputTokens += Number(row.totalInputTokens) || 0;
            totalOutputTokens += Number(row.totalOutputTokens) || 0;
        });
    });

    const total = totalInputTokens + totalOutputTokens;
    return {
        devices: cachedNonAi ? cachedNonAi.devices : finalizeDevices(byDevice),
        skillNames,
        aiInteractions: total > 0 ? { totalInputTokens, totalOutputTokens, total } : null,
    };
}


/**
 * Same grouping as {@link Endpoints} (agentic-assets), shaped for AgenticAssetsPage + flyout.
 */
// Max stretch of synchronous work (ms) before yielding back to the browser's event loop — see the
// chunking comment at the top of the allGroups loop below. Time-based rather than a fixed group count
// because group sizes vary by two orders of magnitude (a handful of collections vs. several thousand),
// so a fixed count either yields too rarely for the biggest groups or needlessly often for the rest.
const MAX_SYNC_MS_PER_BATCH = 16; // ~one frame at 60fps

export async function buildAgenticAssetsPageData(
    collections,
    trafficMap = {},
    riskScoreMap = {},
    sensitiveMap = {},
    {
        usernameMap = {},
        userMetadataMap = {},
        violationsByCollectionId = {},
        analysisByKey = new Map(),
        userAnalysisKeysByDeviceId = new Map(),
        // AgenticAssetsPage calls this twice per mount: once immediately (empty analysisByKey, for
        // first paint) and once more when the account-wide AI-interaction list resolves, just to
        // patch that one field in. analysisByKey is the ONLY thing that differs between those calls —
        // grouping, devices, skill names and violations all come out identical either way. Passing the
        // same (initially empty) object back in as groupsCache across both calls skips redoing that
        // work on the second, "just patch a column" pass instead of rebuilding everything from scratch.
        groupsCache = null,
        // Checked between batches so a component that unmounts mid-build (route change, fast toggle)
        // stops wasted work instead of grinding through the rest of a real-scale account's groups.
        shouldContinue = () => true,
    } = {},
) {
    const reuse = !!groupsCache?.allGroups;
    let allGroups, collectionInfoMap;
    if (reuse) {
        ({ allGroups, collectionInfoMap } = groupsCache);
    } else {
        const agentGroups = groupCollectionsByAgent(collections, trafficMap, sensitiveMap, riskScoreMap);
        const serviceGroups = groupCollectionsByService(collections, trafficMap, sensitiveMap, riskScoreMap);
        const llmGroups = groupCollectionsByLLM(collections, trafficMap, sensitiveMap, riskScoreMap);
        const skillGroups = groupCollectionsBySkill(collections, trafficMap, sensitiveMap, riskScoreMap);
        const agentGroupKeys = new Set(agentGroups.map((a) => a.groupKey));
        const servicesToShow = serviceGroups.filter((s) => !agentGroupKeys.has(s.groupKey));
        allGroups = [...agentGroups, ...servicesToShow, ...llmGroups, ...skillGroups];
        collectionInfoMap = precomputeCollectionInfo(collections, usernameMap);
        if (groupsCache) {
            groupsCache.allGroups = allGroups;
            groupsCache.collectionInfoMap = collectionInfoMap;
            groupsCache.perGroup = new Map();
        }
    }

    const agenticTreeData = [];
    const agenticFlatData = [];
    const assetDevices = {};

    // buildGroupAggregates/buildTeamGroupsForAsset touch every (collection, group-membership) pair —
    // at real scale that's ~1.5-2M pairs, tens of seconds of synchronous work (see the comment on
    // buildGroupAggregates). Running the whole loop in one synchronous stretch blocks the main thread
    // solid for that entire time — the page looks and IS frozen. Yielding via a zero-delay setTimeout
    // whenever a batch has run past MAX_SYNC_MS_PER_BATCH hands control back to the browser between
    // batches, so paint/input/the loading spinner's own animation keep running throughout instead of
    // the tab appearing hung. Doesn't reduce the total time, but changes "frozen for 30s" into
    // "visibly working for 30s". Group sizes range from tens to several thousand collections, so the
    // yield check is time-based, not a fixed group count — a handful of the largest groups would each
    // blow past any fixed count's budget on their own.
    let __batchStart = Date.now();
    for (let __i = 0; __i < allGroups.length; __i++) {
        if (Date.now() - __batchStart > MAX_SYNC_MS_PER_BATCH) {
            await new Promise((resolve) => setTimeout(resolve, 0));
            if (!shouldContinue()) return { agenticTreeData, agenticFlatData, assetDevices };
            __batchStart = Date.now();
        }
        const group = allGroups[__i];
        const collectionIds = (group.collections || []).map((c) => c.id);
        const cachedPerGroup = reuse ? groupsCache.perGroup.get(group.id) : null;
        let violations, groups;
        if (cachedPerGroup) {
            ({ violations, teamGroups: groups } = cachedPerGroup);
        } else {
            // Skills are capability manifest entries — violations belong to the agent/service
            // collection that declares them, not to the skill itself. Suppress to avoid
            // double-counting.
            violations = violationsForCollections(collectionIds, violationsByCollectionId);
            groups = buildTeamGroupsForAsset(group, userMetadataMap, collectionInfoMap);
        }
        const { devices, skillNames, aiInteractions } = buildGroupAggregates(
            group, riskScoreMap, trafficMap, collectionInfoMap, analysisByKey, userAnalysisKeysByDeviceId,
            cachedPerGroup ? { devices: cachedPerGroup.devices, skillNames: cachedPerGroup.skillNames } : null,
        );
        if (groupsCache && !reuse) {
            groupsCache.perGroup.set(group.id, { violations, teamGroups: groups, devices, skillNames });
        }
        const riskScore = group.riskScore ?? group.maxRiskScore ?? null;
        const lastSeen = group.detectedTimestamp || 0;

        const treeRow = {
            path: [group.id],
            name: group.groupName,
            type: group.clientType,
            assetTagValue: group.tagValue,
            riskScore,
            endpointCount: group.endpointsCount,
            deviceCount: group.endpointsCount,
            // Needed to scope the malicious-skill flag to this asset's own collections.
            collectionIds,
            lastSeen: lastSeen > 0 ? func.prettifyEpoch(lastSeen) : "",
            lastSeenEpoch: lastSeen,
            hasPersonalAccount: group.hasPersonalAccount || false,
            hasLocalMcpServer: group.hasLocalMcpServer || false,
            hasMisconfiguredConfig: group.hasMisconfiguredConfig || false,
        };
        if (aiInteractions) {
            treeRow.aiInteractions = aiInteractions.total;
            treeRow.aiInteractionsDetail = {
                totalInputTokens: aiInteractions.totalInputTokens,
                totalOutputTokens: aiInteractions.totalOutputTokens,
            };
        }
        if (violations) treeRow.violations = violations;
        if (groups.length) treeRow.groups = groups;
        if (skillNames.size) treeRow.skillCount = skillNames.size;

        const flatRow = {
            id: group.id,
            name: group.groupName,
            type: group.clientType,
            riskScore,
            collectionIds,
            assetTagValue: group.tagValue,
            deviceCount: group.endpointsCount,
            lastSeen: lastSeen > 0 ? func.prettifyEpoch(lastSeen) : "",
            lastSeenEpoch: lastSeen,
            skillCount: skillNames.size,
            skillNames: skillNames.size ? [...skillNames] : undefined,
            toolCount: 0,
            hasPersonalAccount: group.hasPersonalAccount || false,
            hasLocalMcpServer: group.hasLocalMcpServer || false,
            hasMisconfiguredConfig: group.hasMisconfiguredConfig || false,
        };
        if (violations) flatRow.violations = violations;
        if (groups.length) flatRow.groups = groups;
        if (aiInteractions) {
            flatRow.aiInteractions = aiInteractions.total;
            flatRow.aiInteractionsDetail = {
                totalInputTokens: aiInteractions.totalInputTokens,
                totalOutputTokens: aiInteractions.totalOutputTokens,
            };
        }
        if (group.rowType === ROW_TYPES.AGENT && group.clientType === CLIENT_TYPES.AI_AGENT) {
            const mcpNames = new Set();
            const agentKey = group.groupKey?.toLowerCase();
            (group.collections || []).forEach((c) => {
                // Skip collections ingested via an external connector (e.g. MICROSOFT_DEFENDER /
                // source=DEFENDER) — these represent the agent itself via the connector, not an MCP server.
                const isConnectorIngested = (c.envType || []).some((t) =>
                    t.keyName === "connector" || (t.keyName === "source" && t.value === "DEFENDER")
                );
                if (isConnectorIngested) return;
                const hostName = c.hostName || c.displayName || c.name;
                const serviceName = extractServiceName(hostName);
                if (serviceName && serviceName.toLowerCase() !== agentKey) mcpNames.add(serviceName);
            });
            if (mcpNames.size) flatRow.mcpServers = [...mcpNames];
        }

        agenticTreeData.push(treeRow);
        agenticFlatData.push(flatRow);
        assetDevices[group.id] = devices;
    }

    return { agenticTreeData, agenticFlatData, assetDevices };
}

/**
 * Composite key for collection-scoped malicious-skill lookups.
 * The malicious tag lives on a specific collection's apiInfo, so it must only mark that
 * collection's (i.e. that user/agent/host's) copy of the skill — a same-named skill belonging
 * to a different user must not inherit the flag.
 */
export function skillCollectionKey(collectionId, skillName) {
    return `${collectionId}|${String(skillName).toLowerCase()}`;
}

/** True when any of the given collections carries the malicious tag on one of its own skills. */
export function hasMaliciousSkillInCollections(maliciousSkillKeys, collections) {
    if (!maliciousSkillKeys?.size || !Array.isArray(collections)) return false;
    return collections.some((c) =>
        (Array.isArray(c?.skills) ? c.skills : []).some((s) => maliciousSkillKeys.has(skillCollectionKey(c.id, s)))
    );
}

/**
 * Fetch apiInfos for the given collection IDs, build and cache skillScoreMap + maliciousSkills,
 * then return { skillScoreMap, maliciousSkills }. Uses PersistStore cache; skips fetch if warm.
 * `maliciousSkillKeys` holds `<collectionId>|<skillName>` keys so callers can scope the flag to
 * the collection that actually owns the tagged skill; `maliciousSkills` stays name-only for
 * callers that have no collection context.
 */
export async function fetchAndCacheSkillApiData(collectionIds, { api, PersistStore }) {
    // NOTE: `collectionIds` is accepted for backwards-compatibility but is no longer used — skill/config
    // endpoints only exist on agentic collections, so the backend returns the whole-account skill data in
    // ONE call (api/fetchAgenticSkillData) instead of one fetchApiInfosForCollection per collection (N+1).

    const cached = PersistStore.getState().skillRiskScoreCache;
    const cacheAge = Date.now() - (cached?.ts || 0);

    if (cacheAge <= SKILL_RISK_CACHE_TTL_MS && cached?.ts > 0) {
        return { skillScoreMap: cached.data || {}, maliciousSkills: new Set(cached.maliciousSkills || []), maliciousSkillKeys: new Set(cached.maliciousSkillKeys || []), misconfiguredSkills: new Set(cached.misconfiguredSkills || []), misconfiguredCollectionIds: new Set(cached.misconfiguredCollectionIds || []) };
    }

    let resp;
    try {
        resp = await api.fetchAgenticSkillData();
    } catch {
        resp = {};
    }

    // Server (fetchAgenticSkillData) now returns maliciousSkillKeys pre-aggregated too — collection-
    // scoped `<collectionId>|<skillName>` keys, same format skillCollectionKey produces, so this stays
    // a single query instead of reintroducing the old per-collection fetchApiInfosForCollection N+1.
    const skillScoreMap = resp?.skillScoreMap || {};
    const maliciousSkills = new Set(resp?.maliciousSkills || []);
    const maliciousSkillKeys = new Set(resp?.maliciousSkillKeys || []);
    const misconfiguredSkills = new Set(resp?.misconfiguredSkills || []);
    const misconfiguredCollectionIds = new Set(resp?.misconfiguredCollectionIds || []);

    PersistStore.getState().setSkillRiskScoreCache({ data: skillScoreMap, maliciousSkills: [...maliciousSkills], maliciousSkillKeys: [...maliciousSkillKeys], misconfiguredSkills: [...misconfiguredSkills], misconfiguredCollectionIds: [...misconfiguredCollectionIds], ts: Date.now() });
    return { skillScoreMap, maliciousSkills, maliciousSkillKeys, misconfiguredSkills, misconfiguredCollectionIds };
}

// In-flight dedup on top of the PersistStore TTL cache below — skillRiskScoreCache/guardrailPolicyNames
// only do a TTL check, so two pages mounting in the same tick (e.g. the agenticNewLayout redirect chain)
// both miss a cold cache and both fire; this closes that gap for the heavier collections/traffic/risk
// fetch specifically.
let _agenticCollectionsInflight = null;

/**
 * Fetches + caches {collections, trafficMap, riskScoreMap} — the 3 account-wide, parameterless calls
 * every agentic list page (AgenticAssetsPage, UsersAndDevices, Endpoints, DeviceEndpoints,
 * EndpointPosture) needs on mount. Same PersistStore {data, ts} + TTL convention as
 * skillRiskScoreCache/guardrailPolicyNames, so a page-to-page navigation within the TTL window reuses
 * the same in-memory data instead of re-fetching ~53MB of collections + traffic + risk maps.
 */
export async function fetchAndCacheAgenticCollectionsBundle({ api, PersistStore }) {
    const cached = PersistStore.getState().agenticCollectionsCache;
    const cacheAge = Date.now() - (cached?.ts || 0);
    if (cached?.data && cacheAge <= AGENTIC_COLLECTIONS_CACHE_TTL_MS) {
        return cached.data;
    }
    if (_agenticCollectionsInflight) return _agenticCollectionsInflight;

    _agenticCollectionsInflight = (async () => {
        try {
            const [apiCollectionsResp, trafficInfoResp, riskScoreResp] = await Promise.all([
                api.getAllCollectionsBasic(),
                api.getLastTrafficSeen(),
                api.getRiskScoreInfo(),
            ]);
            const data = {
                collections: apiCollectionsResp?.apiCollections || [],
                trafficMap: trafficInfoResp || {},
                riskScoreMap: riskScoreResp?.riskScoreOfCollectionsMap || {},
            };
            PersistStore.getState().setAgenticCollectionsCache({ data, ts: Date.now() });
            return data;
        } catch (e) {
            // eslint-disable-next-line no-console
            console.error("fetchAndCacheAgenticCollectionsBundle failed:", e);
            return { collections: [], trafficMap: {}, riskScoreMap: {} };
        } finally {
            _agenticCollectionsInflight = null;
        }
    })();
    return _agenticCollectionsInflight;
}

let _agenticSensitiveInfoInflight = null;

/**
 * Fetches + caches sensitiveMap (getSensitiveInfoForCollections). Kept as a SEPARATE cache from the
 * bundle above since only Endpoints.jsx/UsersAndDevices.jsx render a sensitive-subtypes column — the
 * other agentic pages don't need this call at all and shouldn't pay for it via a shared bundle.
 */
export async function fetchAndCacheAgenticSensitiveInfo({ api, PersistStore }) {
    const cached = PersistStore.getState().agenticSensitiveInfoCache;
    const cacheAge = Date.now() - (cached?.ts || 0);
    if (cached?.data && cacheAge <= AGENTIC_SENSITIVE_INFO_CACHE_TTL_MS) {
        return cached.data;
    }
    if (_agenticSensitiveInfoInflight) return _agenticSensitiveInfoInflight;

    _agenticSensitiveInfoInflight = (async () => {
        try {
            const resp = await api.getSensitiveInfoForCollections();
            const data = resp?.sensitiveSubtypesInCollection || {};
            PersistStore.getState().setAgenticSensitiveInfoCache({ data, ts: Date.now() });
            return data;
        } catch {
            return {};
        } finally {
            _agenticSensitiveInfoInflight = null;
        }
    })();
    return _agenticSensitiveInfoInflight;
}

import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import { 
    ASSET_TAG_KEYS, 
    ROW_TYPES,
    TYPE_TAG_KEYS,
    formatDisplayName, 
    getTypeFromTags, 
    findAssetTag,
    findTypeTag,
    getAgentTypeFromValue 
} from "./mcpClientHelper";
import func from "@/util/func";

// Route constants
export const INVENTORY_PATH = '/dashboard/observe/inventory';
export const INVENTORY_FILTER_KEY = '/dashboard/observe/inventory/';
export const ASSET_TAG_KEY_VALUES = Object.values(ASSET_TAG_KEYS);

// Table headers with all columns
export const getHeaders = () => {
    return [
        { title: "", text: "", value: "iconComp", isText: CellType.TEXT, boxWidth: '24px' },
        { title: "Agentic asset", text: "Agentic asset", value: "groupName", filterKey: "groupName", textValue: "groupName", showFilter: true, sortActive: true },
        { title: "Type", text: "Type", value: "clientType", filterKey: "clientType", textValue: "clientType", showFilter: true, sortActive: true, boxWidth: "120px" },
        { 
            title: "Endpoints", 
            text: "Endpoints", 
            value: "endpointsCount", 
            isText: CellType.TEXT, 
            sortActive: true, 
            boxWidth: "80px",
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
    ];
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
export const groupCollectionsByAgent = (collections, trafficMap = {}, sensitiveMap = {}) => {
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
            };
        }
        
        agents[key].collections.push(c);
        if (!agents[key].firstCollection) agents[key].firstCollection = c;
        
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
    });
    
    return Object.values(agents).map(g => ({
        ...g,
        id: `agent-${g.groupKey}`,
        endpointsCount: g.endpointIds.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(' ') || '-',
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: null, // Agents don't have risk score
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
            };
        }
        
        services[key].collections.push(c);
        // Track all hostnames for this service for filtering
        if (!services[key].hostNames.includes(hostName)) {
            services[key].hostNames.push(hostName);
        }
        if (!services[key].firstCollection) services[key].firstCollection = c;
        
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
        id: `service-${g.groupKey}`,
        endpointsCount: g.endpointIds.size,
        sensitiveInRespTypes: Array.from(g.sensitiveTypes),
        sensitiveSubTypesVal: Array.from(g.sensitiveTypes).join(' ') || '-',
        detectedTimestamp: g.maxTrafficTimestamp,
        lastTraffic: func.prettifyEpoch(g.maxTrafficTimestamp),
        riskScore: g.maxRiskScore,
    }));
};

export const createEnvTypeFilter = (values, negated = false) => ({
    filters: [{ key: 'envType', label: func.convertToDisambiguateLabelObj(values, null, 2), value: { values, negated }, onRemove: () => {} }],
    sort: []
});

export const createHostnameFilter = (hostnames) => ({
    filters: [{ key: 'hostName', label: func.convertToDisambiguateLabelObj(hostnames, null, 2), value: { values: hostnames, negated: false }, onRemove: () => {} }],
    sort: []
});

export { ROW_TYPES } from "./mcpClientHelper";

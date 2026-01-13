import func from "@/util/func";
import { formatDisplayName, getTypeFromTags, findAssetTag } from "./mcpClientHelper";
import { UNKNOWN_GROUP } from "./constants";

/**
 * Creates an initial group object for collection grouping
 */
export const createInitialGroup = (groupKey, assetTagKey, assetTagValue, clientType) => ({
    groupName: assetTagValue ? formatDisplayName(assetTagValue) : UNKNOWN_GROUP,
    groupKey,
    tagKey: assetTagKey,
    tagValue: assetTagValue,
    clientType,
    collections: [],
    urlsCount: 0,
    riskScore: 0,
    detectedTimestamp: 0,
    collectionsCount: 0,
    sensitiveInRespTypes: new Set(),
    firstCollection: null,
});

/**
 * Aggregates collection data to a group
 */
export const aggregateCollectionToGroup = (group, collection, riskScoreMap, trafficInfoMap, sensitiveInfoMap) => {
    group.collections.push(collection);
    if (!group.firstCollection) group.firstCollection = collection;
    group.urlsCount += collection.urlsCount || 0;
    group.collectionsCount += 1;
    group.riskScore = Math.max(group.riskScore, riskScoreMap[collection.id] || 0);
    group.detectedTimestamp = Math.max(group.detectedTimestamp, trafficInfoMap[collection.id] || 0);
    (sensitiveInfoMap[collection.id] || []).forEach((type) => group.sensitiveInRespTypes.add(type));
};

/**
 * Groups collections by their asset tags (mcp-client, ai-agent, browser-llm-agent)
 */
export const groupCollectionsByTag = (collections, sensitiveInfoMap, riskScoreMap, trafficInfoMap) => {
    const groups = {};

    collections.forEach((collection) => {
        if (collection.deactivated) return;

        const assetTag = findAssetTag(collection.envType);
        const groupKey = assetTag?.value || UNKNOWN_GROUP;
        const clientType = getTypeFromTags(collection.envType);

        if (!groups[groupKey]) {
            groups[groupKey] = createInitialGroup(groupKey, assetTag?.keyName, assetTag?.value, clientType);
        }

        aggregateCollectionToGroup(groups[groupKey], collection, riskScoreMap, trafficInfoMap, sensitiveInfoMap);
    });

    return Object.values(groups).map((group) => ({
        ...group,
        id: group.groupKey || group.groupName,
        sensitiveInRespTypes: Array.from(group.sensitiveInRespTypes),
        sensitiveSubTypesVal: Array.from(group.sensitiveInRespTypes).join(" ") || "-",
        lastTraffic: func.prettifyEpoch(group.detectedTimestamp),
    }));
};

/**
 * Creates an envType filter object for navigation
 */
export const createEnvTypeFilter = (values, negated = false) => ({
    filters: [{
        key: 'envType',
        label: func.convertToDisambiguateLabelObj(values, null, 2),
        value: { values, negated },
        onRemove: () => {}
    }],
    sort: []
});

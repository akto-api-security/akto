import { Text } from "@shopify/polaris";
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";
import { ASSET_TAG_KEYS, formatDisplayName, getTypeFromTags, findAssetTag } from "./mcpClientHelper";
import func from "@/util/func";

// Route constants
export const UNKNOWN_GROUP = "Unknown";
export const INVENTORY_PATH = '/dashboard/observe/inventory';
export const INVENTORY_FILTER_KEY = '/dashboard/observe/inventory/';
export const ASSET_TAG_KEY_VALUES = Object.values(ASSET_TAG_KEYS);

// Table headers
export const getHeaders = () => {
    const cat = getDashboardCategory();
    return [
        { title: "", text: "", value: "iconComp", isText: CellType.TEXT, boxWidth: '24px' },
        { title: "Agentic asset", text: "Agentic asset", value: "groupName", filterKey: "groupName", textValue: "groupName", showFilter: true },
        { title: "Type", text: "Type", value: "clientType", filterKey: "clientType", textValue: "clientType", showFilter: true, boxWidth: "120px" },
        {
            title: "Endpoints", text: "Endpoints", value: "collectionsCount", isText: CellType.TEXT, sortActive: true, boxWidth: "80px",
            mergeType: (a, b) => (a || 0) + (b || 0),
            shouldMerge: true,
        },
        {
            title: mapLabel("Total endpoints", cat), text: mapLabel("Total endpoints", cat), value: "urlsCount", isText: CellType.TEXT, sortActive: true, boxWidth: "80px", filterKey: "urlsCount", showFilter: true,
            mergeType: (a, b) => (a || 0) + (b || 0),
            shouldMerge: true,
        },
        {
            title: <HeadingWithTooltip content={<Text variant="bodySm">Maximum risk score</Text>} title="Risk score" />, value: "riskScoreComp", textValue: "riskScore", numericValue: "riskScore", text: "Risk Score", sortActive: true, boxWidth: "80px",
            mergeType: (a, b) => Math.max(a || 0, b || 0),
            shouldMerge: true,
        },
        {
            title: "Sensitive data", text: "Sensitive data", value: "sensitiveSubTypes", numericValue: "sensitiveInRespTypes", textValue: "sensitiveSubTypesVal", tooltipContent: <Text variant="bodySm">Types of sensitive data in responses</Text>, boxWidth: "160px",
            mergeType: (a, b) => [...new Set([...(a || []), ...(b || [])])],
            shouldMerge: true,
        },
        {
            title: <HeadingWithTooltip content={<Text variant="bodySm">Last traffic seen</Text>} title="Last traffic seen" />, text: "Last traffic seen", value: "lastTraffic", numericValue: "detectedTimestamp", isText: CellType.TEXT, sortActive: true, boxWidth: "80px",
            mergeType: (a, b) => Math.max(a || 0, b || 0),
            shouldMerge: true,
        },
    ];
};

export const sortOptions = [
    { label: "Endpoints", value: "urlsCount asc", directionLabel: "More", sortKey: "urlsCount", columnIndex: 1 },
    { label: "Endpoints", value: "urlsCount desc", directionLabel: "Less", sortKey: "urlsCount", columnIndex: 1 },
    { label: "Risk Score", value: "score asc", directionLabel: "High risk", sortKey: "riskScore", columnIndex: 2 },
    { label: "Risk Score", value: "score desc", directionLabel: "Low risk", sortKey: "riskScore", columnIndex: 2 },
    { label: "Last traffic seen", value: "detected asc", directionLabel: "Recent first", sortKey: "detectedTimestamp", columnIndex: 5 },
    { label: "Last traffic seen", value: "detected desc", directionLabel: "Oldest first", sortKey: "detectedTimestamp", columnIndex: 5 },
];

export const resourceName = { singular: "Agentic asset", plural: "Agentic assets" };

// Helper to get the merge key from header (similar to transform.js getFinalKey)
const getMergeKey = (h) => h?.numericValue || h?.filterKey || h?.value;

// Grouping utilities (merged from utils.js)
export const groupCollectionsByTag = (collections, sensitiveInfoMap, riskScoreMap, trafficInfoMap) => {
    const headers = getHeaders();
    const mergeableHeaders = headers.filter(h => h.shouldMerge);
    const groups = {};
    
    collections.forEach((c) => {
        if (c.deactivated) return;
        const assetTag = findAssetTag(c.envType);
        const key = assetTag?.value || UNKNOWN_GROUP;
        
        // Prepare collection's mergeable values
        const collectionData = {
            urlsCount: c.urlsCount || 0,
            collectionsCount: 1,
            riskScore: riskScoreMap[c.id] || 0,
            detectedTimestamp: trafficInfoMap[c.id] || 0,
            sensitiveInRespTypes: sensitiveInfoMap[c.id] || [],
        };
        
        if (!groups[key]) {
            groups[key] = {
                groupName: assetTag?.value ? formatDisplayName(assetTag.value) : UNKNOWN_GROUP,
                groupKey: key, tagKey: assetTag?.keyName, tagValue: assetTag?.value,
                clientType: getTypeFromTags(c.envType), collections: [], firstCollection: null,
            };
            // Initialize mergeable fields from first collection
            mergeableHeaders.forEach(h => {
                const mergeKey = getMergeKey(h);
                groups[key][mergeKey] = collectionData[mergeKey];
            });
        } else {
            // Use mergeType functions from headers to combine data
            mergeableHeaders.forEach(h => {
                const mergeKey = getMergeKey(h);
                groups[key][mergeKey] = h.mergeType(groups[key][mergeKey], collectionData[mergeKey]);
            });
        }
        
        const g = groups[key];
        g.collections.push(c);
        if (!g.firstCollection) g.firstCollection = c;
    });
    
    return Object.values(groups).map(g => ({
        ...g, id: g.groupKey || g.groupName,
        sensitiveSubTypesVal: (g.sensitiveInRespTypes || []).join(" ") || "-",
        lastTraffic: func.prettifyEpoch(g.detectedTimestamp),
    }));
};

export const createEnvTypeFilter = (values, negated = false) => ({
    filters: [{ key: 'envType', label: func.convertToDisambiguateLabelObj(values, null, 2), value: { values, negated }, onRemove: () => {} }],
    sort: []
});

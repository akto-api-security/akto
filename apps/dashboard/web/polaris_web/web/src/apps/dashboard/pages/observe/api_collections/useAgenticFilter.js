import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import PersistStore from '../../../../main/PersistStore';
import { formatDisplayName, ASSET_TAG_KEYS } from '../agentic/mcpClientHelper';
import { INVENTORY_FILTER_KEY, ASSET_TAG_KEY_VALUES, SKILL_TAG_KEY, extractServiceName } from '../agentic/constants';

/** Agent tag keys that represent the same agent (gen-ai + mcp-server for one click) */
const AGENT_TAG_KEYS_FOR_FILTER = [ASSET_TAG_KEYS.AI_AGENT, ASSET_TAG_KEYS.MCP_CLIENT];
import transform from '../transform';
import func from '@/util/func';

// Filter type constants for UI display
const FILTER_TYPES = {
    BROWSER_LLM: 'browser-llm',
    AI_AGENT: 'ai-agent',
    MCP_SERVER: 'mcp-server',
    SERVICE: 'service',
    SKILL: 'skill',
};

/**
 * Get formatted envType strings from collection
 * Handles both raw data (objects) and transformed data (strings)
 */
const getFormattedEnvType = (collection) => {
    const envType = collection.envType || [];
    if (envType.length === 0) return [];

    if (typeof envType[0] === 'string') {
        return envType;
    }

    return envType.map(func.formatCollectionType);
};

/**
 * Ensure collection has endpointId, sourceId, serviceName fields
 * (needed for tree view grouping)
 */
const ensureCollectionFields = (collection) => {
    if (collection.endpointId !== undefined && collection.sourceId !== undefined) {
        return collection;
    }

    const splitResult = transform.splitCollectionNameForEndpointSecurity(collection.displayName);
    return {
        ...collection,
        endpointId: splitResult.endpointId || '',
        sourceId: splitResult.sourceId || '',
        serviceName: splitResult.serviceName || '',
        splitApiCollectionName: splitResult.apiCollectionName || collection.displayName
    };
};

/**
 * Parse filter from URL search params
 * URL format: filters=key__value1,value2
 */
const parseFilterFromUrl = (searchParams) => {
    const filtersParam = searchParams.get('filters');
    if (!filtersParam) return { envTypeFilter: null, hostNameFilter: null };

    const decoded = decodeURIComponent(filtersParam);
    const pairs = decoded.split('&');

    let envTypeFilter = null;
    let hostNameFilter = null;

    pairs.forEach(pair => {
        const [key, valuesStr] = pair.split('__');
        if (!valuesStr) return;

        const isNegated = valuesStr.includes('|negated');
        const cleanValuesStr = isNegated ? valuesStr.replace('|negated', '') : valuesStr;
        const values = cleanValuesStr.split(',').filter(v => v !== '');

        if (key === 'envType') {
            envTypeFilter = { key: 'envType', value: { values, negated: isNegated } };
        } else if (key === 'hostName') {
            hostNameFilter = { key: 'hostName', value: { values, negated: isNegated } };
        }
    });

    return { envTypeFilter, hostNameFilter };
};

/**
 * Custom hook to detect envType or hostName filter from Endpoints page navigation
 * and compute filtered summary data for Agentic Collections
 *
 * @param {Array} normalData - The normalized collection data
 * @returns {Object} - { filteredSummaryData, activeFilterTitle, activeFilterType, filteredCollections, activeFilterPlainTitle }
 */
const useAgenticFilter = (normalData) => {
    const [filteredSummaryData, setFilteredSummaryData] = useState(null);
    const [activeFilterTitle, setActiveFilterTitle] = useState(null);
    const [activeFilterType, setActiveFilterType] = useState(null);
    const [filteredCollections, setFilteredCollections] = useState([]);
    const [activeFilterPlainTitle, setActiveFilterPlainTitle] = useState(false);

    const filtersMap = PersistStore(state => state.filtersMap);
    const [searchParams] = useSearchParams();

    useEffect(() => {
        const currentPageFilters = filtersMap[INVENTORY_FILTER_KEY];
        let envTypeFilter = currentPageFilters?.filters?.find(f => f.key === 'envType');
        let hostNameFilter = currentPageFilters?.filters?.find(f => f.key === 'hostName');

        if (!envTypeFilter && !hostNameFilter) {
            const urlFilters = parseFilterFromUrl(searchParams);
            envTypeFilter = urlFilters.envTypeFilter;
            hostNameFilter = urlFilters.hostNameFilter;
        }

        const hasHostnameFilter = Boolean(hostNameFilter?.value?.values?.length > 0);
        const inventoryScopeLabel = currentPageFilters?.inventoryScopeLabel;

        if (normalData.length === 0) {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
            setActiveFilterType(null);
            setFilteredCollections([]);
            setActiveFilterPlainTitle(false);
            return;
        }

        let filterTitle = null;
        let filterType = null;
        let filteredCollections = [];
        let plainTitle = false;

        if (hasHostnameFilter) {
            const hostNames = hostNameFilter.value.values;
            filteredCollections = normalData.filter(collection => hostNames.includes(collection.hostName));

            if (inventoryScopeLabel) {
                filterTitle = inventoryScopeLabel;
                filterType = FILTER_TYPES.AI_AGENT;
                plainTitle = true;
            } else {
                const serviceName = extractServiceName(hostNames[0]);
                filterTitle = serviceName || hostNames[0];

                if (filteredCollections.length > 0) {
                    const firstCollection = filteredCollections[0];
                    const envTypeArr = getFormattedEnvType(firstCollection);
                    if (envTypeArr.some(tag => tag.startsWith('mcp-server='))) {
                        filterType = FILTER_TYPES.MCP_SERVER;
                    } else if (envTypeArr.some(tag => tag.startsWith('gen-ai='))) {
                        filterType = FILTER_TYPES.AI_AGENT;
                    } else if (envTypeArr.some(tag => tag.startsWith('browser-llm='))) {
                        filterType = FILTER_TYPES.BROWSER_LLM;
                    } else {
                        filterType = FILTER_TYPES.SERVICE;
                    }
                }
            }
        } else if (envTypeFilter?.value) {
            const filterValues = envTypeFilter.value.values || [];
            const isNegated = envTypeFilter.value.negated;

            if (!isNegated && filterValues.length === 1) {
                const parts = filterValues[0].split('=');
                if (parts.length === 2) {
                    if (ASSET_TAG_KEY_VALUES.includes(parts[0])) {
                        filterTitle = formatDisplayName(parts[1]);
                        if (parts[0] === ASSET_TAG_KEYS.BROWSER_LLM_AGENT) {
                            filterType = FILTER_TYPES.BROWSER_LLM;
                        } else if (parts[0] === ASSET_TAG_KEYS.AI_AGENT) {
                            filterType = FILTER_TYPES.AI_AGENT;
                        } else if (parts[0] === ASSET_TAG_KEYS.MCP_CLIENT) {
                            filterType = FILTER_TYPES.AI_AGENT;
                        }
                    } else if (parts[0] === SKILL_TAG_KEY) {
                        filterTitle = formatDisplayName(parts[1]);
                        filterType = FILTER_TYPES.SKILL;
                    }
                }
            }

            if (filterTitle && filterValues.length > 0) {
                const eq = filterValues[0].indexOf('=');
                const filterKey = eq >= 0 ? filterValues[0].slice(0, eq) : '';
                const filterTagValue = eq >= 0 ? filterValues[0].slice(eq + 1) : '';
                const matchBothAgentTags = AGENT_TAG_KEYS_FOR_FILTER.includes(filterKey);
                const matchingTags = matchBothAgentTags && filterTagValue
                    ? AGENT_TAG_KEYS_FOR_FILTER.map(k => `${k}=${filterTagValue}`)
                    : [filterValues[0]];
                filteredCollections = normalData.filter(collection => {
                    const envTypeArr = getFormattedEnvType(collection);
                    return envTypeArr.some(tag => tag === filterValues[0] || matchingTags.includes(tag));
                });
            }
        }

        if (filterTitle && filteredCollections.length > 0) {
            setActiveFilterTitle(filterTitle);
            setActiveFilterType(filterType);
            setActiveFilterPlainTitle(plainTitle);

            const transformedCollections = filteredCollections.map(ensureCollectionFields);
            setFilteredCollections(transformedCollections);

            const filteredEndpoints = transformedCollections.reduce((sum, c) => sum + (c.urlsCount || 0), 0);

            const uniqueEndpointIds = new Set();
            const uniqueSourceIds = new Set();
            const uniqueServiceNames = new Set();
            transformedCollections.forEach(c => {
                if (c.endpointId) {
                    uniqueEndpointIds.add(c.endpointId);
                }
                if (c.sourceId) {
                    uniqueSourceIds.add(c.sourceId);
                }
                if (c.serviceName) {
                    uniqueServiceNames.add(c.serviceName);
                }
            });

            setFilteredSummaryData({
                totalEndpoints: filteredEndpoints,
                totalCollections: transformedCollections.length,
                uniqueEndpoints: uniqueEndpointIds.size,
                uniqueSources: uniqueSourceIds.size,
                uniqueResources: uniqueServiceNames.size
            });
        } else {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
            setActiveFilterType(null);
            setFilteredCollections([]);
            setActiveFilterPlainTitle(false);
        }
    }, [filtersMap, normalData, searchParams]);

    return { filteredSummaryData, activeFilterTitle, activeFilterType, filteredCollections, activeFilterPlainTitle };
};

export { FILTER_TYPES };

export default useAgenticFilter;

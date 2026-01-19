import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import PersistStore from '../../../../main/PersistStore';
import { formatDisplayName, ASSET_TAG_KEYS } from '../agentic/mcpClientHelper';
import { INVENTORY_FILTER_KEY, ASSET_TAG_KEY_VALUES, extractServiceName } from '../agentic/constants';
import transform from '../transform';
import func from '@/util/func';

// Filter type constants for UI display
const FILTER_TYPES = {
    BROWSER_LLM: 'browser-llm',
    AI_AGENT: 'ai-agent',
    MCP_SERVER: 'mcp-server',
    SERVICE: 'service'
};

/**
 * Get formatted envType strings from collection
 * Handles both raw data (objects) and transformed data (strings)
 */
const getFormattedEnvType = (collection) => {
    const envType = collection.envType || [];
    if (envType.length === 0) return [];
    
    // Check if already formatted (string) or raw (object)
    if (typeof envType[0] === 'string') {
        return envType;
    }
    
    // Raw data - format it
    return envType.map(func.formatCollectionType);
};

/**
 * Ensure collection has endpointId, sourceId, serviceName fields
 * (needed for tree view grouping)
 */
const ensureCollectionFields = (collection) => {
    // If already has these fields, return as-is
    if (collection.endpointId !== undefined && collection.sourceId !== undefined) {
        return collection;
    }
    
    // Extract from displayName using pattern: <endpoint-id>.<source-id>.<service-name>
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
 * @returns {Object} - { filteredSummaryData, activeFilterTitle, activeFilterType }
 */
const useAgenticFilter = (normalData) => {
    const [filteredSummaryData, setFilteredSummaryData] = useState(null);
    const [activeFilterTitle, setActiveFilterTitle] = useState(null);
    const [activeFilterType, setActiveFilterType] = useState(null);
    const [filteredCollections, setFilteredCollections] = useState([]);
    
    const filtersMap = PersistStore(state => state.filtersMap);
    const [searchParams] = useSearchParams();

    useEffect(() => {
        // Try to get filters from filtersMap first
        const currentPageFilters = filtersMap[INVENTORY_FILTER_KEY];
        let envTypeFilter = currentPageFilters?.filters?.find(f => f.key === 'envType');
        let hostNameFilter = currentPageFilters?.filters?.find(f => f.key === 'hostName');
        
        // If not found in filtersMap, parse from URL (for initial page load)
        if (!envTypeFilter && !hostNameFilter) {
            const urlFilters = parseFilterFromUrl(searchParams);
            envTypeFilter = urlFilters.envTypeFilter;
            hostNameFilter = urlFilters.hostNameFilter;
        }
        
        if (normalData.length === 0) {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
            setActiveFilterType(null);
            return;
        }

        let filterTitle = null;
        let filterType = null;
        let filteredCollections = [];

        // Handle hostname filter (for service rows - may have multiple hostnames)
        if (hostNameFilter?.value?.values?.length > 0) {
            const hostNames = hostNameFilter.value.values;
            // Extract service name from first hostname to show as title
            const serviceName = extractServiceName(hostNames[0]);
            filterTitle = serviceName || hostNames[0];
            filteredCollections = normalData.filter(collection => hostNames.includes(collection.hostName));
            
            // Determine service type from the first collection's tags
            if (filteredCollections.length > 0) {
                const firstCollection = filteredCollections[0];
                // Get formatted envType strings (handles both raw and transformed data)
                const envTypeArr = getFormattedEnvType(firstCollection);
                // Check for type tags in the formatted strings
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
        // Handle envType filter (for agent rows)
        else if (envTypeFilter?.value) {
            const filterValues = envTypeFilter.value.values || [];
            const isNegated = envTypeFilter.value.negated;

            if (!isNegated && filterValues.length === 1) {
                const parts = filterValues[0].split('=');
                if (parts.length === 2 && ASSET_TAG_KEY_VALUES.includes(parts[0])) {
                    filterTitle = formatDisplayName(parts[1]);
                    
                    // Determine filter type from the tag key
                    if (parts[0] === ASSET_TAG_KEYS.BROWSER_LLM_AGENT) {
                        filterType = FILTER_TYPES.BROWSER_LLM;
                    } else if (parts[0] === ASSET_TAG_KEYS.AI_AGENT) {
                        filterType = FILTER_TYPES.AI_AGENT;
                    } else if (parts[0] === ASSET_TAG_KEYS.MCP_CLIENT) {
                        filterType = FILTER_TYPES.AI_AGENT; // mcp-client is treated as AI Agent
                    }
                }
            }

            if (filterTitle) {
                filteredCollections = normalData.filter(collection => {
                    // Get formatted envType strings (handles both raw and transformed data)
                    const envTypeArr = getFormattedEnvType(collection);
                    return envTypeArr.some(tag => tag === filterValues[0]);
                });
            }
        }

        if (filterTitle && filteredCollections.length > 0) {
            setActiveFilterTitle(filterTitle);
            setActiveFilterType(filterType);
            
            // Transform collections to ensure they have the required fields for tree view
            const transformedCollections = filteredCollections.map(ensureCollectionFields);
            setFilteredCollections(transformedCollections);

            const filteredEndpoints = transformedCollections.reduce((sum, c) => sum + (c.urlsCount || 0), 0);
            
            // Count unique values from collection name: <1>.<2>.<3> = <endpoint-id>.<source-id>.<service-name>
            const uniqueEndpointIds = new Set();   // <1> - endpoint-id
            const uniqueSourceIds = new Set();     // <2> - source-id  
            const uniqueServiceNames = new Set();  // <3> - service-name
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
                uniqueEndpoints: uniqueEndpointIds.size,   // count of unique <1> (endpoint-id)
                uniqueSources: uniqueSourceIds.size,       // count of unique <2> (source-id)
                uniqueResources: uniqueServiceNames.size   // count of unique <3> (service-name) for AI Agent
            });
        } else {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
            setActiveFilterType(null);
            setFilteredCollections([]);
        }
    }, [filtersMap, normalData, searchParams]);

    return { filteredSummaryData, activeFilterTitle, activeFilterType, filteredCollections };
};

export { FILTER_TYPES };

export default useAgenticFilter;

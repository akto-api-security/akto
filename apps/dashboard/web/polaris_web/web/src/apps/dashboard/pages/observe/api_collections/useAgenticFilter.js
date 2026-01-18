import { useState, useEffect } from 'react';
import PersistStore from '../../../../main/PersistStore';
import { formatDisplayName, ASSET_TAG_KEYS, TYPE_TAG_KEYS } from '../agentic/mcpClientHelper';
import { INVENTORY_FILTER_KEY, ASSET_TAG_KEY_VALUES, extractServiceName } from '../agentic/constants';

// Filter type constants for UI display
const FILTER_TYPES = {
    BROWSER_LLM: 'browser-llm',
    AI_AGENT: 'ai-agent',
    MCP_SERVER: 'mcp-server',
    SERVICE: 'service'
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
    
    const filtersMap = PersistStore(state => state.filtersMap);

    useEffect(() => {
        const currentPageFilters = filtersMap[INVENTORY_FILTER_KEY];
        const envTypeFilter = currentPageFilters?.filters?.find(f => f.key === 'envType');
        const hostNameFilter = currentPageFilters?.filters?.find(f => f.key === 'hostName');
        
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
                const envTypeArr = firstCollection.envTypeOriginal || [];
                const typeTag = envTypeArr.find(tag => 
                    tag.keyName === TYPE_TAG_KEYS.MCP_SERVER || 
                    tag.keyName === TYPE_TAG_KEYS.GEN_AI || 
                    tag.keyName === TYPE_TAG_KEYS.BROWSER_LLM
                );
                if (typeTag?.keyName === TYPE_TAG_KEYS.MCP_SERVER) {
                    filterType = FILTER_TYPES.MCP_SERVER;
                } else if (typeTag?.keyName === TYPE_TAG_KEYS.GEN_AI) {
                    filterType = FILTER_TYPES.AI_AGENT;
                } else if (typeTag?.keyName === TYPE_TAG_KEYS.BROWSER_LLM) {
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
                    const envTypeArr = collection.envTypeOriginal || collection.envType || [];
                    return envTypeArr.some(tag => {
                        if (typeof tag === 'object' && tag.keyName && tag.value) {
                            return `${tag.keyName}=${tag.value}` === filterValues[0];
                        }
                        return tag === filterValues[0];
                    });
                });
            }
        }

        if (filterTitle && filteredCollections.length > 0) {
            setActiveFilterTitle(filterTitle);
            setActiveFilterType(filterType);

            const filteredEndpoints = filteredCollections.reduce((sum, c) => sum + (c.urlsCount || 0), 0);
            
            // Count unique values from collection name: <1>.<2>.<3> = <endpoint-id>.<source-id>.<service-name>
            const uniqueEndpointIds = new Set();   // <1> - endpoint-id
            const uniqueSourceIds = new Set();     // <2> - source-id  
            const uniqueServiceNames = new Set();  // <3> - service-name
            filteredCollections.forEach(c => {
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
                totalCollections: filteredCollections.length,
                uniqueEndpoints: uniqueEndpointIds.size,   // count of unique <1> (endpoint-id)
                uniqueSources: uniqueSourceIds.size,       // count of unique <2> (source-id)
                uniqueResources: uniqueServiceNames.size   // count of unique <3> (service-name) for AI Agent
            });
        } else {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
            setActiveFilterType(null);
        }
    }, [filtersMap, normalData]);

    return { filteredSummaryData, activeFilterTitle, activeFilterType };
};

export { FILTER_TYPES };

export default useAgenticFilter;

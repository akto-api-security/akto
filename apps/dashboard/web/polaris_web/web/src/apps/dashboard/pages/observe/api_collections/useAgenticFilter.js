import { useState, useEffect } from 'react';
import PersistStore from '../../../../main/PersistStore';
import { ASSET_TAG_KEYS, formatDisplayName } from '../agentic/mcpClientHelper';

const FILTER_PAGE_KEY = '/dashboard/observe/inventory/';

// All asset tag keys that can be used for grouping
const ASSET_TAG_KEY_VALUES = Object.values(ASSET_TAG_KEYS);

/**
 * Custom hook to detect envType filter from Endpoints page navigation
 * and compute filtered summary data for Agentic Collections
 * 
 * @param {Array} normalData - The normalized collection data
 * @returns {Object} - { filteredSummaryData, activeFilterTitle }
 */
const useAgenticFilter = (normalData) => {
    const [filteredSummaryData, setFilteredSummaryData] = useState(null);
    const [activeFilterTitle, setActiveFilterTitle] = useState(null);
    
    const filtersMap = PersistStore(state => state.filtersMap);

    useEffect(() => {
        const currentPageFilters = filtersMap[FILTER_PAGE_KEY];
        const envTypeFilter = currentPageFilters?.filters?.find(f => f.key === 'envType');
        
        if (!envTypeFilter || !envTypeFilter.value || normalData.length === 0) {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
            return;
        }

        const filterValues = envTypeFilter.value.values || [];
        const isNegated = envTypeFilter.value.negated;

        // Determine the title based on filter
        let filterTitle = null;
        if (!isNegated && filterValues.length === 1) {
            // Specific group filter (e.g., mcp-client=SomeValue, ai-agent=SomeValue, browser-llm-agent=SomeValue)
            const parts = filterValues[0].split('=');
            if (parts.length === 2 && ASSET_TAG_KEY_VALUES.includes(parts[0])) {
                // Use formatDisplayName to get proper display name (e.g., "claude-cli" -> "Claude CLI")
                filterTitle = formatDisplayName(parts[1]);
            }
        }
        // Skip handling Unknown case as per requirement

        if (filterTitle) {
            setActiveFilterTitle(filterTitle);

            // Filter collections based on the envType filter
            const filteredCollections = normalData.filter(collection => {
                const envTypeArr = collection.envTypeOriginal || collection.envType || [];
                const hasMatchingTag = envTypeArr.some(tag => {
                    if (typeof tag === 'object' && tag.keyName && tag.value) {
                        return `${tag.keyName}=${tag.value}` === filterValues[0];
                    }
                    return tag === filterValues[0];
                });
                return hasMatchingTag;
            });

            // Compute filtered summary
            const filteredEndpoints = filteredCollections.reduce((sum, c) => sum + (c.urlsCount || 0), 0);

            // Count unique sensitive data types across filtered collections
            const allSensitiveTypes = new Set();
            filteredCollections.forEach(c => {
                if (c.sensitiveInRespTypes && Array.isArray(c.sensitiveInRespTypes)) {
                    c.sensitiveInRespTypes.forEach(type => allSensitiveTypes.add(type));
                }
            });

            setFilteredSummaryData({
                totalEndpoints: filteredEndpoints,
                totalCollections: filteredCollections.length,
                totalSensitiveInResponse: allSensitiveTypes.size
            });
        } else {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
        }
    }, [filtersMap, normalData]);

    return { filteredSummaryData, activeFilterTitle };
};

export default useAgenticFilter;

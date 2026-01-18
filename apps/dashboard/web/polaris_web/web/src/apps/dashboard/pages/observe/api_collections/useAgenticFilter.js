import { useState, useEffect } from 'react';
import PersistStore from '../../../../main/PersistStore';
import { formatDisplayName } from '../agentic/mcpClientHelper';
import { INVENTORY_FILTER_KEY, ASSET_TAG_KEY_VALUES, extractServiceName } from '../agentic/constants';

/**
 * Custom hook to detect envType or hostName filter from Endpoints page navigation
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
        const currentPageFilters = filtersMap[INVENTORY_FILTER_KEY];
        const envTypeFilter = currentPageFilters?.filters?.find(f => f.key === 'envType');
        const hostNameFilter = currentPageFilters?.filters?.find(f => f.key === 'hostName');
        
        if (normalData.length === 0) {
            setFilteredSummaryData(null);
            setActiveFilterTitle(null);
            return;
        }

        let filterTitle = null;
        let filteredCollections = [];

        // Handle hostname filter (for service rows - may have multiple hostnames)
        if (hostNameFilter?.value?.values?.length > 0) {
            const hostNames = hostNameFilter.value.values;
            // Extract service name from first hostname to show as title
            const serviceName = extractServiceName(hostNames[0]);
            filterTitle = serviceName || hostNames[0];
            filteredCollections = normalData.filter(collection => hostNames.includes(collection.hostName));
        }
        // Handle envType filter (for agent rows)
        else if (envTypeFilter?.value) {
            const filterValues = envTypeFilter.value.values || [];
            const isNegated = envTypeFilter.value.negated;

            if (!isNegated && filterValues.length === 1) {
                const parts = filterValues[0].split('=');
                if (parts.length === 2 && ASSET_TAG_KEY_VALUES.includes(parts[0])) {
                    filterTitle = formatDisplayName(parts[1]);
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

            const filteredEndpoints = filteredCollections.reduce((sum, c) => sum + (c.urlsCount || 0), 0);
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

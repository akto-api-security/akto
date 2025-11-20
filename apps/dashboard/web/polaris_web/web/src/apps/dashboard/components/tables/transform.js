import func from "@/util/func";
import PersistStore from "../../../main/PersistStore";

// Cache for filter choices to avoid recomputing on every render
const filterChoicesCache = new Map();

const tableFunc = {
    // NEW: Enhanced version with lazy prettification support
    fetchDataSyncWithLazyPrettify: function (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, setFilters, props){
        // Early return for empty data
        if (!props.data || props.data.length === 0) {
            setFilters([]);
            return {value: [], total: 0, fullDataIds: []};
        }

        // First call the original fetchDataSync to get the paginated data
        const result = this.fetchDataSync(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, setFilters, props);

        // LAZY PRETTIFICATION: If data has a prettifyPageData function, call it on the current page only
        // This allows tables to prettify (create JSX components) only for visible items
        if (props.prettifyPageData && typeof props.prettifyPageData === 'function' && result.value.length > 0) {
            result.value = props.prettifyPageData(result.value);
        }

        return result;
    },

    fetchDataSync: function (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, setFilters, props){
        // Early return for empty data
        if (!props.data || props.data.length === 0) {
            setFilters([]);
            return {value: [], total: 0, fullDataIds: []};
        }
        // Check if data needs lazy transformation (raw data -> plain data)
        const needsLazyTransform = props.data._lazyTransform === true;
        let tempData = props.data;

        if (needsLazyTransform) {
            // Check if already cached (transformed during categorization)
            if (props.data._transformedCache) {
                tempData = props.data._transformedCache;
            } else {
                // Use the transformation function provided by the caller (e.g., ApiCollections)
                // This keeps transform.js generic and allows different tables to provide their own logic
                const transformFunc = props.transformRawData;
                const maps = props.data._transformMaps || {};

                if (!transformFunc || typeof transformFunc !== 'function') {
                    console.error('transformRawData function is required when using lazy transformation');
                    tempData = props.data;
                } else {
                    // Transform raw data to plain data (without JSX) using the provided function
                    tempData = props.data.map(item => transformFunc(item, maps));

                    // Cache the transformed data for future page navigations
                    props.data._transformedCache = tempData;
                }
            }
        }

        // Check if data has an override for actual total (used when data is limited for memory optimization)
        const actualTotal = props.data._actualTotal;

        let localFilters = func.prepareFilters(tempData,props.filters);

        // Create cache key based on data length and headers
        const cacheKey = `${props.data.length}_${props.headers.map(h => h.value).join('_')}`;

        let filtersFromHeaders;
        if (filterChoicesCache.has(cacheKey)) {
          filtersFromHeaders = filterChoicesCache.get(cacheKey);
        } else {
          filtersFromHeaders = props.headers.filter((header) => {
            return header.showFilter
          }).map((header) => {
            let key = header.filterKey || header.value
            let label = header.filterLabel || header.text

            // Use Set for better performance with large datasets
            let uniqueValues = new Set();

            for (let i = 0; i < tempData.length; i++) {
              let value = tempData[i][key];
              if (value instanceof Set) {
                value.forEach(v => uniqueValues.add(v));
              } else if (value instanceof Array) {
                value.forEach(v => uniqueValues.add(v));
              } else if (typeof value !== 'undefined') {
                uniqueValues.add(value);
              }
            }

            // Convert to array and sort only once
            let distinctItems = Array.from(uniqueValues);
            distinctItems.sort();

            let choices = distinctItems.map((item) => ({label: item, value: item}));

            return {
              key: key,
              label: label,
              title: label,
              choices: choices,
            };
          })

          // Cache the result for future calls
          filterChoicesCache.set(cacheKey, filtersFromHeaders);
        }

        localFilters = localFilters.concat(filtersFromHeaders)
        localFilters = localFilters.filter((filter) => filter.choices.length > 0)
        setFilters(localFilters);

        let dataSortKey = props?.sortOptions?.filter(value => {
          return (value.value.startsWith(sortKey))
        }).filter(value => {
          return (value.value.endsWith(sortOrder === -1 ? 'asc' : 'desc'))
        })[0]?.sortKey;

        if(props?.customFilters){
          tempData = props?.modifyData(filters, dataSortKey, sortOrder)
          tempData = tempData.filter((value) => {
            return func.findInObjectValue(value, queryValue.toLowerCase(), ['id', 'time', 'icon', 'order', 'conditions']);
          })
          let page = skip / limit;
          let pageLimit = limit;
          let final2Data = tempData && tempData.length <= pageLimit ? tempData :
          tempData.slice(page * pageLimit, Math.min((page + 1) * pageLimit, tempData.length))

          return {value:final2Data,total:tempData.length, fullDataIds: tempData.map((x) => {return {id: x?.id}})}
        }

        // Optimized filter application - fix the bug where singleFilterData resets to props.data
        const filterKeys = Object.keys(filters || {});
        
        if (filterKeys.length > 0) {
          // Apply all filters in a single pass for better performance
          tempData = tempData.filter((item) => {
            // Check if item passes ALL filters
            for (let filterKey of filterKeys) {
              if (filterKey.includes('dateRange')) {
                const dataKey = filterKey.split('_')[0];
                const startTs = filters[filterKey]?.since ? Date.parse(filters[filterKey].since)/1000 : 0;
                const endTs = filters[filterKey]?.until ? Date.parse(filters[filterKey].until)/1000 : 0;
                
                if (!item[dataKey] || item[dataKey] < startTs || item[dataKey] > endTs) {
                  return false; // Item doesn't pass this filter
                }
              } else {
                // Handle both legacy array format and new {values, negated} format
                let filterValue = filters[filterKey];
                let filterValues = [];
                let isNegated = false;
                
                if (filterValue && typeof filterValue === 'object' && !Array.isArray(filterValue)) {
                  if (filterValue.values !== undefined) {
                    // New format with negation support
                    filterValues = filterValue.values || [];
                    isNegated = filterValue.negated || false;
                  } else if (filterValue.since === undefined) {
                    // Other object format (not dateRange) - try to extract values
                    filterValues = Object.values(filterValue).flat().filter(v => v !== null && v !== undefined);
                    isNegated = false;
                  }
                  // If it's a dateRange object (has since), it's already handled above, so skip
                } else if (Array.isArray(filterValue)) {
                  // Legacy format
                  filterValues = filterValue;
                  isNegated = false;
                } else if (filterValue !== null && filterValue !== undefined) {
                  // Single value
                  filterValues = [filterValue];
                  isNegated = false;
                }
                
                if (filterValues.length > 0) {
                  const itemValue = item[filterKey];
                  let hasMatch = false;
                  
                  if (itemValue instanceof Array) {
                    hasMatch = itemValue.some(v => filterValues.includes(v));
                  } else if (itemValue instanceof Set) {
                    hasMatch = Array.from(itemValue).some(v => filterValues.includes(v));
                  } else {
                    hasMatch = filterValues.includes(itemValue);
                  }
                  
                  // If negated, exclude items that match; otherwise include items that match
                  if (isNegated && hasMatch) {
                    return false; // Negated filter: exclude items that match
                  } else if (!isNegated && !hasMatch) {
                    return false; // Normal filter: exclude items that don't match
                  }
                }
              }
            }
            return true; // Item passes all filters
          });
        }


        // Optimize search query - skip if no query
        if (queryValue && queryValue.length > 0) {
          const lowerQuery = queryValue.toLowerCase();
          tempData = tempData.filter((value) => {
            return func.findInObjectValue(value, lowerQuery, ['id', 'time', 'icon', 'order', 'conditions']);
          });
        }

          // Sort only if we have data and a sort key
          if (tempData.length > 0 && dataSortKey) {
            tempData = func.sortFunc(tempData, dataSortKey, sortOrder, props?.treeView !== undefined ? true : false)
          }

          if(props.getFilteredItems){
            props.getFilteredItems(tempData)
          }

          let finalData = props.useModifiedData ? props.modifyData(tempData, filters || {}) : tempData

          // Optimize pagination calculations
          const totalLength = finalData.length;
          const page = Math.floor(skip / limit);
          const startIndex = page * limit;
          const endIndex = Math.min(startIndex + limit, totalLength);

          // Slice only if necessary
          let final2Data = (totalLength <= limit) ? finalData : finalData.slice(startIndex, endIndex);
          let fullDataIds= finalData.map((x) => ({id: x?.id}));

          // Use actualTotal if provided (for memory-optimized large datasets), otherwise use calculated length
          const reportedTotal = actualTotal !== undefined ? actualTotal : totalLength;

          return {value: final2Data, total: reportedTotal, fullDataIds: fullDataIds}
    },
    mergeFilters(filterArray1, filterArray2, labelFunc, handleRemoveAppliedFilter){
      const combined = [...filterArray1, ...filterArray2];
      const mergedByKey = combined.reduce((acc, {key, value}) => {
        // Normalize value to new format {values, negated}
        let normalizedValue = value;
        if (Array.isArray(value)) {
          normalizedValue = { values: value, negated: false };
        } else if (typeof value === 'object' && value.values === undefined && !value.since) {
          // Legacy object format
          normalizedValue = { values: Object.values(value).flat(), negated: false };
        }
        
        if (acc[key]) {
          if(key.includes('dateRange')){
            acc[key].value = this.mergeTimeRanges(acc[key].value, value)
          }else{
            // Merge values and preserve negation (use the first non-false negation)
            const existingValue = acc[key].value;
            const existingNormalized = Array.isArray(existingValue) 
              ? { values: existingValue, negated: false }
              : (existingValue?.values !== undefined ? existingValue : { values: existingValue || [], negated: false });
            
            const newNormalized = normalizedValue?.values !== undefined 
              ? normalizedValue 
              : { values: normalizedValue || [], negated: false };
            
            acc[key].value = {
              values: [...new Set([...existingNormalized.values, ...newNormalized.values])],
              negated: existingNormalized.negated || newNormalized.negated
            };
          }
        } else {
          if(key.includes('dateRange')){
            acc[key] = {key, value}
          }else{
            acc[key] = { key, value: normalizedValue?.values !== undefined ? normalizedValue : { values: normalizedValue || [], negated: false } };
          } 
        }
        return acc;
      }, {});

      return Object.keys(mergedByKey).map((key) => {
        const obj = mergedByKey[key];
        // Extract values for disambiguateLabel - it expects an array for non-dateRange filters
        // For dateRange, pass the object as-is since disambiguateLabel handles it specially
        const labelValue = key.includes("dateRange")
          ? obj.value
          : (obj.value.values !== undefined ? obj.value.values : (Array.isArray(obj.value) ? obj.value : []));
        let label = labelFunc(obj.key, labelValue);
        
        // Add negation prefix if negated (only for non-dateRange filters)
        if (obj.value.negated && !key.includes("dateRange")) {
          label = `Exclude: ${label}`;
        }
        
        return {
          ...obj,
          label: label,
          onRemove: handleRemoveAppliedFilter
        };
      });
    },
    mergeTimeRanges(obj1, obj2) {
      const sinceEpoch1 = Date.parse(obj1.since);
      const untilEpoch1 = Date.parse(obj1.until);
      const sinceEpoch2 = Date.parse(obj2.since);
      const untilEpoch2 = Date.parse(obj2.until);
      const minSinceEpoch = Math.min(sinceEpoch1, sinceEpoch2);
      const maxUntilEpoch = Math.max(untilEpoch1, untilEpoch2);
      const since = new Date(minSinceEpoch).toISOString();
      const until = new Date(maxUntilEpoch).toISOString();
  
      return { since, until };
  },
  getSortableChoices(sortOptions){
    if(!sortOptions || sortOptions === undefined || sortOptions.length === 0){
      return []
    }

    let sortableColumns = []
    sortOptions.forEach((opt) => {
      sortableColumns.push(opt.sortActive || false)
    })
    return sortableColumns
  },
  getColumnSort(sortSelected, sortOptions){
    if(!sortSelected || sortSelected.length === 0 || !sortOptions || sortOptions === undefined || sortOptions.length === 0){
      return {columnIndex: -1, sortDirection: 'descending'}
    }

    const sortColumn = sortOptions.filter((x) => x.value === sortSelected[0])[0]
    const sortDirection = sortSelected[0].split(" ")[1] === "asc" ? "ascending" : "descending"

    return {
      columnIndex: sortColumn.columnIndex - 1,
      sortDirection: sortDirection
    }
  },
  getInitialSortSelected(sortOptions, filtersMap){
    if(!sortOptions || sortOptions === undefined || sortOptions.length === 0){
      return ['']
    }
    if(!filtersMap || filtersMap?.sort === undefined || filtersMap.sort.length === 0){
      return [sortOptions[0].value]
    }
    return filtersMap.sort
  },
  getPrettifiedFilter(filters){
    let filterStr = "";
    filters.forEach((filter) => {
      if(filterStr.length !== 0){filterStr += "&"}
      // Handle new format with {values, negated} or legacy format
      let valueStr = "";
      if (filter.value && typeof filter.value === 'object' && filter.value.values !== undefined) {
        // New format: encode values and negation
        const values = filter.value.values || [];
        const negated = filter.value.negated ? "1" : "0";
        valueStr = values.join(",") + (negated === "1" ? "|negated" : "");
      } else if (Array.isArray(filter.value)) {
        valueStr = filter.value.join(",");
      } else {
        valueStr = filter.value;
      }
      filterStr += filter.key + "__" + valueStr
    })
    return filterStr
  },

  convertValue(value) {
    const countDecimals = value.split('.').length - 1;
    if(countDecimals > 1){
      return value;
    }
    const intValue = parseInt(value, 10);
    if (!isNaN(intValue)) return intValue;
    return value;
  },

  getFiltersMapFromUrl(searchString, labelFunc, handleRemoveAppliedFilter, pageKey){
    const result = [];
    if(searchString.length === 0){
      return []
    }
    const pairs = searchString.split('&');
  
    pairs.forEach(pair => {
      const [key, valuesStr] = pair.split('__');
      // Check if negation flag is present
      const isNegated = valuesStr.includes('|negated');
      const cleanValuesStr = isNegated ? valuesStr.replace('|negated', '') : valuesStr;
      const valueArray = cleanValuesStr.split(',').map(this.convertValue).filter(v => v !== '');
      
      // Use new format with {values, negated}
      const filterValue = {
        values: valueArray,
        negated: isNegated
      };
      
      // Extract values for disambiguateLabel - it expects an array, not {values, negated}
      const labelValue = filterValue.values || filterValue;
      let label = labelFunc(key, labelValue);
      
      // Add negation prefix if negated
      if (filterValue.negated) {
        label = `Exclude: ${label}`;
      }
      
      result.push({
        key,
        value: filterValue,
        label: label,
        onRemove: handleRemoveAppliedFilter
      });
    });

    const currentFilters = PersistStore.getState().filtersMap
    const setPageFilters = PersistStore.getState().setFiltersMap
    const currentPageFilters = currentFilters?.[pageKey]?.filters || []
    if(!func.deepComparison(currentPageFilters, result)){
      setPageFilters({
        ...currentFilters,
        [pageKey]: {
          sort: currentFilters?.[pageKey]?.sort || [],
          'filters':result
        }
      
      })
      return result
    }
    return []
  }
}

export default tableFunc;
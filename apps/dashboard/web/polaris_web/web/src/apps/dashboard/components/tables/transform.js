import func from "@/util/func";
import PersistStore from "../../../main/PersistStore";

const tableFunc = {
    fetchDataSync: function (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, setFilters, props){
        let localFilters = func.prepareFilters(props.data,props.filters);  
        let filtersFromHeaders = props.headers.filter((header) => {
          return header.showFilter
        }).map((header) => {
          let key = header.filterKey || header.value
          let label = header.filterLabel || header.text
          let allItemValues = []
          props.data && props.data.forEach(i => {
            let value = i[key]
            if (value instanceof Set) {
              allItemValues = allItemValues.concat(...value)
            } else if (value instanceof Array) {
              allItemValues = allItemValues.concat(...value)
            } else if (typeof value !== 'undefined') {
              allItemValues.push(value)
            }
          }
          )
          let distinctItems = [...new Set(allItemValues.sort())]
          let choices = distinctItems.map((item) => {return {label:item, value:item}})
          return (
            {
              key: key,
              label: label,
              title: label,
              choices: choices,
            }
          )
        })
        localFilters = localFilters.concat(filtersFromHeaders)
        localFilters = localFilters.filter((filter) => {return filter.choices.length>0})
        setFilters(localFilters);
          let tempData = props.data;

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

        // actually apply filters for table filter
        let singleFilterData = tempData
        Object.keys(filters || {}).forEach((filterKey)=>{
          singleFilterData = props.data;

          if(filterKey.includes('dateRange')){
            const dataKey = filterKey.split('_')[0];
            const startTs = filters[filterKey]?.since ? Date.parse(filters[filterKey].since)/1000 : 0;
            const endTs = filters[filterKey]?.until ? Date.parse(filters[filterKey].until)/1000 : 0;

            singleFilterData = singleFilterData.filter((value) => {
              if(value[dataKey] && value[dataKey] >= startTs && value[dataKey] <= endTs){
                return true;
              }
              return false;
            })
          }else{
            let filterSet = new Set(filters[filterKey] || []);
            if(filterSet.size!==0){
              singleFilterData = singleFilterData.filter((value) => {
                  return [].concat(value[filterKey]).filter(v => filterSet.has(v)).length > 0
                })
            }
          }
          tempData = tempData.filter(value => singleFilterData.includes(value));
        })


        // used for search query
        tempData = tempData.filter((value) => {
          return func.findInObjectValue(value, queryValue.toLowerCase(), ['id', 'time', 'icon', 'order', 'conditions']);
        })

          tempData = func.sortFunc(tempData, dataSortKey, sortOrder, props?.treeView !== undefined ? true : false)
          if(props.getFilteredItems){
            props.getFilteredItems(tempData)
          }
  
          let finalData = props.useModifiedData ? props.modifyData(tempData,filters || {}) : tempData
          
          let page = skip / limit;
          let pageLimit = limit;
          let final2Data = finalData && finalData.length <= pageLimit ? finalData :
          finalData.slice(page * pageLimit, Math.min((page + 1) * pageLimit, finalData.length))

          return {value:final2Data,total:tempData.length, fullDataIds: finalData.map((x) => {return {id: x?.id}})}
    },
    mergeFilters(filterArray1, filterArray2, labelFunc, handleRemoveAppliedFilter){
      const combined = [...filterArray1, ...filterArray2];
      const mergedByKey = combined.reduce((acc, {key, value}) => {
        if (acc[key]) {
          if(key.includes('dateRange')){
            acc[key].value = this.mergeTimeRanges(acc[key].value, value)
          }else{
            acc[key].value = [...new Set([...value,...acc[key].value ])];
          }
        } else {
          if(key.includes('dateRange')){
            acc[key] = {key, value}
          }else{
            acc[key] = { key, value: [...value] };
          } 
        }
        return acc;
      }, {});

      return Object.keys(mergedByKey).map((key) => {
        const obj = mergedByKey[key]
        return {
          ...obj,
          label: labelFunc(obj.key, obj.value),
          onRemove: handleRemoveAppliedFilter
        }
      })
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
      filterStr += filter.key + "__" + filter.value
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
      const [key, values] = pair.split('__');
      const valueArray = values.split(',').map(this.convertValue);
      result.push({
        key,
        value: valueArray,
        label: labelFunc(key,valueArray),
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
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
          let singleFilterData = tempData
          Object.keys(filters).forEach((filterKey)=>{
            singleFilterData = props.data;
            let filterSet = new Set(filters[filterKey]);
            if(filterSet.size!=0){
              singleFilterData = singleFilterData.filter((value) => {
                  return [].concat(value[filterKey]).filter(v => filterSet.has(v)).length > 0
                })
            }
            tempData = tempData.filter(value => singleFilterData.includes(value));
          })
          tempData = tempData.filter((value) => {
            return func.findInObjectValue(value, queryValue.toLowerCase(), ['id', 'time', 'icon', 'order']);
          })
          let dataSortKey = props?.sortOptions?.filter(value => {
            return (value.value.startsWith(sortKey))
          }).filter(value => {
            return (value.value.endsWith(sortOrder == -1 ? 'asc' : 'desc'))
          })[0]?.sortKey;
  
          tempData = func.sortFunc(tempData, dataSortKey, sortOrder)
          if(props.getFilteredItems){
            props.getFilteredItems(tempData)
          }
  
          let finalData = props.useModifiedData ? props.modifyData(tempData,filters) : tempData
  
          return {value:finalData,total:tempData.length}
    },
    mergeFilters(filterArray1, filterArray2, labelFunc, handleRemoveAppliedFilter){
      const combined = [...filterArray1, ...filterArray2];
      const mergedByKey = combined.reduce((acc, {key, value}) => {
        if (acc[key]) {
          if(key === 'dateRange'){
            acc[key].value = this.mergeTimeRanges(acc[key].value, value)
          }else{
            acc[key].value = [...new Set([...value,...acc[key].value ])];
          }
        } else {
          if(key === 'dateRange'){
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

  getFiltersMapFromUrl(searchString, labelFunc, handleRemoveAppliedFilter, pageKey){
    const result = [];
    if(searchString.length === 0){
      return []
    }
    const pairs = searchString.split('&');
  
    pairs.forEach(pair => {
      const [key, values] = pair.split('__');
      const valueArray = values.split(',');
      result.push({
        key,
        value: valueArray,
        label: labelFunc(key,valueArray),
        onRemove: handleRemoveAppliedFilter
      });
    });

    const currentFilters = PersistStore.getState().filtersMap
    const setPageFilters = PersistStore.getState().setFiltersMap
    const pageFilters = currentFilters?.pageKey?.filters || {}
    if(!func.deepComparison(pageFilters, result)){
      setPageFilters({
        ...pageFilters,
        [pageKey]: {
          sort: pageFilters[pageKey]?.sort || [],
          'filters':result
        }
      
      })
      return result
    }
    return []
  }
}

export default tableFunc;
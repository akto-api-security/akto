import { useState } from "react";
import GithubServerTable from "./GithubServerTable";
import func from "@/util/func";

function GithubSimpleTable(props) {

    const [filters, setFilters] = useState([])
    function fetchDataSync(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
      let localFilters = func.prepareFilters(props.data,props.filters);  
      let filtersFromHeaders = props.headers.filter((header) => {
        return header.showFilter
      }).map((header) => {
        let key = header.filterKey || header.value
        let label = header.filterLabel || header.text
        let allItemValues = []
        props.data.forEach(i => {
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
        })[0]?.sortKey;

        tempData = func.sortFunc(tempData, dataSortKey, sortOrder)
        if(props.getFilteredItems){
          props.getFilteredItems(tempData)
        }

        return {value:tempData,total:tempData.length}
    }
    

    return <GithubServerTable
        key={JSON.stringify(props.data ? props.data : "{}")} // passing any value as a "key" re-renders the component when the value is changed.
        pageLimit={props.pageLimit}
        fetchData={fetchDataSync}
        sortOptions={props.sortOptions} 
        resourceName={props.resourceName} 
        filters={filters}
        disambiguateLabel={props.disambiguateLabel} 
        headers={props.headers}
        getStatus={props.getStatus}
        getActions = {props.getActions}
        hasRowActions={props.hasRowActions}
        loading={props.loading}
        selectable = {props.selectable}
        rowClickable={props.rowClickable}
        promotedBulkActions = {props.promotedBulkActions}
        hideQueryField={props.hideQueryField}
        tabs={props.tabs}
        selected={props.selected}
        onSelect={props.onSelect}
        onRowClick={props.onRowClick}
    />

}

export default GithubSimpleTable
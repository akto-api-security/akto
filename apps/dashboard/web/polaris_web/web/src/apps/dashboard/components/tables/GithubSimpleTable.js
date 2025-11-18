import { useState } from "react";
import GithubServerTable from "./GithubServerTable";
import tableFunc from "./transform";

function GithubSimpleTable(props) {

    const [filters, setFilters] = useState([])

    // OPTIMIZATION: Don't stringify entire dataset - just use data length as key
    // JSON.stringify on 17k items takes ~1000ms and forces full remount
    const tableKey = props.hardCodedKey ? "hardCodedKey" : `table_${props.data?.length || 0}`;
    // console.log("SIMPLE_TABLE_DEBUG: Rendering with key:", tableKey, "data length:", props.data?.length);

    // Use lazy prettification if prettifyPageData function is provided
    // This allows creating JSX components only for visible page (100 items) instead of all data (17k items)
    const fetchFunction = props.prettifyPageData
        ? (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) =>
            tableFunc.fetchDataSyncWithLazyPrettify(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, setFilters, props)
        : (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) =>
            tableFunc.fetchDataSync(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, setFilters, props);

    return <GithubServerTable
        key={tableKey}
        pageLimit={props.pageLimit}
        fetchData={fetchFunction}
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
        increasedHeight = {props.increasedHeight}
        mode={props?.mode}
        headings={props?.headings}
        useNewRow={props?.useNewRow}
        condensedHeight={props?.condensedHeight}
        tableTabs={props?.tableTabs}
        notHighlightOnselected={props.notHighlightOnselected}
        hasZebraStriping={props.hasZebraStriping}
        filterStateUrl={props?.filterStateUrl}
        hidePagination={props?.hidePagination}
        bannerComp={props?.bannerComp}
        csvFileName={props?.csvFileName}
        treeView={props?.treeView}
        customFilters={props?.customFilters}
        showFooter={props?.showFooter}
        setSelectedResourcesForPrimaryAction={props?.setSelectedResourcesForPrimaryAction}
        lastColumnSticky = {props?.lastColumnSticky}
        isMultipleItemsSelected={props?.isMultipleItemsSelected}
        emptyStateMarkup={props?.emptyStateMarkup}
        calendarFilterKeys={props?.calendarFilterKeys}
    />

}

export default GithubSimpleTable
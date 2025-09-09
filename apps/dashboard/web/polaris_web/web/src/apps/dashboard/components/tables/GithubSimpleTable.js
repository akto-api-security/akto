import { useState } from "react";
import GithubServerTable from "./GithubServerTable";
import tableFunc from "./transform";

function GithubSimpleTable(props) {

    const [filters, setFilters] = useState([])
    return <GithubServerTable
        key={props.hardCodedKey ? "hardCodedKey" : JSON.stringify(props.data ? props.data : "{}")} // passing any value as a "key" re-renders the component when the value is changed.
        pageLimit={props.pageLimit}
        fetchData={(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) => tableFunc.fetchDataSync(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue, setFilters, props)}
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
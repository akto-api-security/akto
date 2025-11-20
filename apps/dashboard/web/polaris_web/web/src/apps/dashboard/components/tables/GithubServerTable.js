import {
  IndexTable,
  LegacyCard,
  IndexFilters,
  useSetIndexFiltersMode,
  IndexFiltersMode,
  useIndexResourceState,
  Pagination,
  HorizontalStack,
  Key,
  ChoiceList,
  Tabs,
  Text,
  Link} from '@shopify/polaris';
import { GithubRow} from './rows/GithubRow';
import { useState, useCallback, useEffect, useMemo, useRef, useReducer } from 'react';
import "./style.css"
import transform from '../../pages/observe/transform';
import DropdownSearch from '../shared/DropdownSearch';
import PersistStore from '../../../main/PersistStore';
import tableFunc from './transform';
import useTable from './TableContext';
import { debounce } from 'lodash';

import { useNavigate,  useSearchParams } from 'react-router-dom';
import TableStore from './TableStore';
import func from '../../../../util/func';
import values from "@/util/values"
import { produce } from 'immer';
import DateRangePicker from '../layouts/DateRangePicker';

function GithubServerTable(props) {

  const navigate = useNavigate();

  const [searchParams, setSearchParams] = useSearchParams();
  const updateQueryParams = (key, value) => {
    const newSearchParams = new URLSearchParams(searchParams);
    newSearchParams.set(key, value);
    setSearchParams(newSearchParams);
  };

  const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5])
  const [hideFilter, setHideFilter] = useState(false)

  const filtersMap = PersistStore(state => state.filtersMap)
  const setFiltersMap = PersistStore(state => state.setFiltersMap)
  const tableInitialState = PersistStore(state => state.tableInitialState)
  const setTableInitialState = PersistStore(state => state.setTableInitialState)
  const tableSelectedMap = PersistStore(state => state.tableSelectedTab)
  const setTableSelectedMap = PersistStore(state => state.setTableSelectedTab)
  const currentPageKey = props?.filterStateUrl || (window.location.pathname + "/" +  window.location.hash)
  const pageFiltersMap = filtersMap[currentPageKey];

  const abortControllerRef = useRef(null);

  const { selectedItems, selectItems, applyFilter } = useTable();

  const handleRemoveAppliedFilter = (key) => {
    let temp = appliedFilters
    temp = temp.filter((filter) => {
      return filter.key != key
    })
    props?.appliedFilters?.forEach((defaultAppliedFilter) => {
      if (key === defaultAppliedFilter.key) {
        temp.push(defaultAppliedFilter)
      }
    })
    setAppliedFilters(temp);
    let tempFilters = {}

    let updatedFilters = {...filtersMap}
    updatedFilters[currentPageKey] = {
      'filters': temp,
      'sort': pageFiltersMap?.sort || []
    }
    setFiltersMap(updatedFilters)
  }
  

  const filterMode = (pageFiltersMap?.filters?.length > 0) ? IndexFiltersMode.Filtering : (props?.mode ? props.mode : IndexFiltersMode.Filtering)
  const initialStateFilters = tableFunc.mergeFilters(props.appliedFilters || [], (pageFiltersMap?.filters || []),props.disambiguateLabel, handleRemoveAppliedFilter)
  const { mode, setMode } = useSetIndexFiltersMode(filterMode);
  const [sortSelected, setSortSelected] = useState(tableFunc.getInitialSortSelected(props.sortOptions, pageFiltersMap))
  const [data, setData] = useState([]);
  const [total, setTotal] = useState([]);
  const [page, setPage] = useState(0);
  const pageLimit = props?.pageLimit || 20;
  const [appliedFilters, setAppliedFilters] = useState(initialStateFilters);
  const [queryValue, setQueryValue] = useState('');
  const [fullDataIds, setFullDataIds] = useState([])

  const [sortableColumns, setSortableColumns] = useState([])
  const [activeColumnSort, setActiveColumnSort] = useState({columnIndex: -1, sortDirection: 'descending'})

  let filterOperators = props.headers.reduce((map, e) => { map[e.sortKey || e.filterKey || e.value] = 'OR'; return map }, {})

  useEffect(() => {
    const handleEsc = (event) => {
      if (event.key === "Escape") {
        setMode(IndexFiltersMode.Default)
      }
    }

    window.addEventListener("keydown", handleEsc)
    return () => {
      window.removeEventListener("keydown", handleEsc)
    }
  }, [])

  useEffect(()=> {
    let queryFilters
    if (performance.getEntriesByType('navigation')[0].type === 'reload') {
      queryFilters = []
    }else{
      queryFilters = tableFunc.getFiltersMapFromUrl(decodeURIComponent(searchParams.get("filters") || ""), props?.disambiguateLabel, handleRemoveAppliedFilter, currentPageKey)
    }
    const currentFilters = tableFunc.mergeFilters(queryFilters,initialStateFilters,props?.disambiguateLabel, handleRemoveAppliedFilter)
    setAppliedFilters((prev) => {
      if(func.deepComparison(prev, currentFilters)){
        return prev
      }else{
        return currentFilters;
      }
    })
    setSortSelected(tableFunc.getInitialSortSelected(props.sortOptions, pageFiltersMap))
  },[currentPageKey])

  useEffect(() => {
    const tempFilters = appliedFilters.filter((filter) => !filter?.key?.includes("dateRange"))
    updateQueryParams("filters",tableFunc.getPrettifiedFilter(tempFilters))
  },[appliedFilters])

  async function fetchData(searchVal, tempFilters = []) {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort(); // Cancel the previous request
    }
    abortControllerRef.current = new AbortController();
    const { signal } = abortControllerRef.current;

    let [sortKey, sortOrder] = sortSelected.length == 0 ? ["", ""] : sortSelected[0].split(" ");
    let filters = props.headers.reduce((map, e) => { map[e.filterKey || e.value] = []; return map }, {})
    
    // Optimize: process filters once, reuse logic
    const processFilter = (filter) => {
      const value = filter.value;
      if (value && typeof value === 'object' && value.values !== undefined) {
        // New format - pass as is for client-side, extract values for server-side
        filters[filter.key] = props.supportsNegationFilter ? value : value.values;
      } else if (Array.isArray(value)) {
        // Legacy format - convert only if negation is supported
        filters[filter.key] = props.supportsNegationFilter ? { values: value, negated: false } : value;
      } else {
        filters[filter.key] = value;
      }
    };
    
    if(tempFilters.length === 0){
      appliedFilters.forEach(processFilter);
    }else{
      tempFilters.forEach(processFilter);
    }
  
    let tempData = await props.fetchData(sortKey, sortOrder == 'asc' ? -1 : 1, page * pageLimit, pageLimit, filters, filterOperators, searchVal);
    if(signal.aborted){
      return;
    }
    tempData ? setData([...tempData.value]) : setData([])
    tempData ? setTotal(tempData.total) : setTotal(0)
    tempData ? setFullDataIds(tempData?.fullDataIds) : setFullDataIds([])
    
    applyFilter(tempData.total)
    if (!performance.getEntriesByType('navigation')[0].type === 'reload') {
      setTableInitialState({
        ...tableInitialState,
        [currentPageKey]: tempData.total
      })
    }
  }

  const handleSelectedTab = (x) => {
    const tableTabs = props.tableTabs ? props.tableTabs : props.tabs
    if(tableTabs){
      const id = tableTabs[x]?.id;
      setTableSelectedMap({
        ...tableSelectedMap,
        [window.location.pathname]: id
      })
      const primitivePath = window.location.pathname + window.location?.search
      const newUrl = primitivePath + "#" +  tableTabs[x].id
      navigate(newUrl, { replace: true });
    }
    let val = total
    if(total instanceof Object){
      val = total.length
    }
    applyFilter(val) 
  }

  useEffect(() => {
    handleSelectedTab(props?.selected)
    setActiveColumnSort(tableFunc.getColumnSort(sortSelected, props?.sortOptions))
    fetchData(queryValue);
  }, [sortSelected, appliedFilters, page, pageFiltersMap])

  useEffect(()=> {
    setSortableColumns(tableFunc.getSortableChoices(props?.headers))
  },[props?.headers])

  useEffect(() => {
    if (props?.callFromOutside) {
      fetchData(queryValue);
    }    
  },[props?.callFromOutside])

  const handleSort = (col, dir) => {
    let tempSortSelected = props?.sortOptions.filter(x => x.columnIndex === (col + 1))
    let sortVal = [tempSortSelected[0].value]
    if(dir.includes("desc")){
      setSortSelected([tempSortSelected[1].value])
      sortVal = [tempSortSelected[1].value]
    }else{
      setSortSelected([tempSortSelected[0].value])
    }
    let copyFilters = filtersMap
    copyFilters[currentPageKey] = {
      'filters': pageFiltersMap?.filters || [],
      'sort': sortVal
    }
    setFiltersMap(copyFilters)
  }

  const changeAppliedFilters = useCallback((key, value) => {
    let temp = appliedFilters.filter(filter => filter.key !== key);
    
    // Normalize value to new format {values, negated} - optimize for common cases
    let normalizedValue;
    if (value && typeof value === 'object' && value.values !== undefined) {
      // Already in new format
      normalizedValue = value;
    } else if (Array.isArray(value)) {
      // Legacy array format - most common
      normalizedValue = { values: value, negated: false };
    } else if (typeof value === 'object' && value.since === undefined) {
      // Legacy object format (not dateRange)
      normalizedValue = { values: Object.values(value).flat(), negated: false };
    } else {
      // dateRange or other - keep as is
      normalizedValue = value;
    }
    
    // Only add filter if it has values (or is a dateRange)
    if (normalizedValue.values?.length > 0 || normalizedValue.since || key.includes("dateRange")) {
      // Extract values for disambiguateLabel - it expects an array for non-dateRange filters
      // For dateRange, pass the object as-is since disambiguateLabel handles it specially
      const labelValue = key.includes("dateRange") 
        ? normalizedValue 
        : (normalizedValue.values || []);
      let label = props.disambiguateLabel(key, labelValue);
      
      // Add negation prefix if negated (only for non-dateRange filters)
      if (normalizedValue.negated && !key.includes("dateRange")) {
        label = `Exclude: ${label}`;
      }
      
      temp.push({
        key: key,
        label: label,
        onRemove: handleRemoveAppliedFilter,
        value: normalizedValue
      });
    }
    setPage(0);
    if(!key.includes("dateRange")){
      let tempFilters = {...filtersMap}
      tempFilters[currentPageKey]= {
        filters: temp,
        sort: pageFiltersMap?.sort || []
      }
      setFiltersMap(tempFilters);
    }
    setAppliedFilters(temp);
  }, [appliedFilters, props.disambiguateLabel, handleRemoveAppliedFilter, setFiltersMap, currentPageKey, pageFiltersMap]);

  const debouncedSearch = debounce((searchQuery) => {
      fetchData(searchQuery)
  }, 500);

  const handleFiltersQueryChange = useCallback((val) => {
    setQueryValue(val);
    debouncedSearch(val);
  }, []);

  const handleFiltersQueryClear = useCallback(
    () => {setQueryValue("");
    debouncedSearch("")},
    [],
  );

  const handleFilterStatusChange = (key, value) => {
    // Handle negation filter - value can be array or object with {values, negated}
    let filterValue = value;
    if (typeof value === 'object' && !Array.isArray(value) && value.values !== undefined) {
      // Value is already in the new format {values, negated}
      filterValue = value;
    } else if (Array.isArray(value)) {
      // Legacy format - convert to new format with negated: false
      filterValue = { values: value, negated: false };
    }
    changeAppliedFilters(key, filterValue);
  }

  const handleNegationToggle = (key, negated) => {
    const currentFilter = appliedFilters.find(f => f.key === key);
    if (currentFilter) {
      const currentValue = currentFilter.value;
      const values = Array.isArray(currentValue) ? currentValue : (currentValue?.values || []);
      changeAppliedFilters(key, { values, negated });
    } else {
      changeAppliedFilters(key, { values: [], negated });
    }
  }

  const getSortedChoices = (choices) => {
    return choices.sort((a, b) => (a?.label || a) - (b?.label || b));
  }

  const filters = useMemo(() => {
    return formatFilters(props.filters);
  }, [props.filters, appliedFilters]);

  const handleDateChange = (dateObj, filterKey) => {
    dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})
    let obj = dateObj.period.period;
    handleFilterStatusChange(filterKey + "_dateRange", obj)
  }

  function formatFilters(filters) {
    let formattedFilters = filters
      .map((filter) => {
        const currentFilter = appliedFilters.find((localFilter) => localFilter.key === filter.key);
        const filterValue = currentFilter?.value || filter.selected || [];
        // Normalize filter value to new format {values, negated}
        const normalizedValue = Array.isArray(filterValue) 
          ? { values: filterValue, negated: false }
          : (filterValue?.values !== undefined ? filterValue : { values: filterValue || [], negated: false });
        
        // Add negation choice only if component-level negation is supported (GithubSimpleTable)
        // Individual filters can still opt-out via filter.supportsNegation === false
        const supportsNegation = props.supportsNegationFilter === true && filter.supportsNegation !== false;
        const negationChoices = supportsNegation ? [
          { label: 'Include', value: 'include' },
          { label: 'Exclude', value: 'exclude' }
        ] : [];
        
        return {
          key: filter.key,
          label: filter.label,
          filter: (
            <div>
              {supportsNegation && (
                <ChoiceList
                  title="Filter type"
                  titleHidden
                  choices={negationChoices}
                  selected={normalizedValue.negated ? ['exclude'] : ['include']}
                  onChange={(value) => handleNegationToggle(filter.key, value[0] === 'exclude')}
                  allowMultiple={false}
                />
              )}
              {filter.choices.length < 10 ?
                <ChoiceList
                  title={filter.title}
                  titleHidden
                  choices={getSortedChoices(filter.choices)}
                  selected={normalizedValue.values || []}
                  onChange={(value) => {
                    const newValue = { ...normalizedValue, values: value };
                    handleFilterStatusChange(filter.key, newValue);
                  }}
                  {...(filter.singleSelect ? {} : { allowMultiple: true })}
                />
                :
                <DropdownSearch
                  placeHoder={"Apply filters"}
                  optionsList={getSortedChoices(filter.choices)}
                  setSelected={(value) => {
                    const newValue = { ...normalizedValue, values: value };
                    handleFilterStatusChange(filter.key, newValue);
                  }}
                  {...(filter.singleSelect ? {} : { allowMultiple: true })}
                  allowMultiple
                  preSelected={normalizedValue.values || []}
                />
              }
            </div>
          ),
          // shortcut: true,
          pinned: true
        }
      })
    if(props?.calendarFilterKeys && Object.keys(props?.calendarFilterKeys).length > 0){
      Object.keys(props?.calendarFilterKeys).forEach((key) => {
        formattedFilters.push({
          key: key + "_dateRange",
          label: props?.calendarFilterKeys[key],
          filter: <DateRangePicker
            dispatch={(dateObj) => handleDateChange(dateObj, key)}
            initialDispatch={currDateRange}
            ranges={values.ranges}
            setPopoverState={() => {
                setHideFilter(true)
                setTimeout(() => {
                    setHideFilter(false)
                }, 10)
            }}
          />,
          pinned: true
        })
      })
    }
    return formattedFilters;
  }

  const handleFiltersClearAll = useCallback(() => {
    setFiltersMap({
      ...filtersMap,
      [currentPageKey]:{
        sort: filtersMap[currentPageKey]?.sort || []
      }
    })
    setAppliedFilters([])
  }, [filtersMap, currentPageKey, setFiltersMap]);

  const resourceIDResolver = (data) => {
    return data.id;
  };
  

  const {selectedResources, allResourcesSelected, handleSelectionChange } =
    useIndexResourceState(fullDataIds!== undefined ? fullDataIds : data , {
      resourceIDResolver,
    });

  const customSelectionChange = (selectionType,toggleType, selection) => {
    if(props?.treeView || props?.isMultipleItemsSelected === true){
      let tempItems = selection;
        if(typeof selectItems !== 'object'){
          tempItems = [selection]
        }
      if(selectionType !== "page"){
        let newItems = []
        if(toggleType){
          newItems = [...new Set([...selectedItems, ...tempItems])]
        }else{
          newItems = selectedItems.filter((x) => !tempItems.includes(x))
        }
        selectItems(newItems);
        TableStore.getState().setSelectedItems(newItems)
      }else{
        if(selection === false){
          selectItems([])
          TableStore.getState().setSelectedItems([])
        }else{
        //todo: handle
          if (data) {
            if (toggleType) {
              let allItemsSet = new Set()
              for (let row of data) {
                  if (row?.id instanceof Array) {
                    row?.id.forEach(item => allItemsSet.add(item));
                  }
              }
              selectItems([...allItemsSet])
              TableStore.getState().setSelectedItems([...allItemsSet])
            } else {
              selectItems([])
              TableStore.getState().setSelectedItems([])
            }
          }
        }
      }
    }
    if(selectionType === "page"){
      handleSelectionChange("all",toggleType, selection)
    }else{
      handleSelectionChange(selectionType,toggleType, selection)
    }
    
  }

  const [popoverActive, setPopoverActive] = useState(-1);

  // sending all data in case of simple table because the select-all state is controlled from the data.
  // not doing this affects bulk select functionality.
  // let tmp = data && data.length <= pageLimit ? data :
  //   data.slice(page * pageLimit, Math.min((page + 1) * pageLimit, data.length))

  const rowMarkup = useMemo(() => {
    return data.map(
      (data, index) => (
        <GithubRow
          key={data.id}
          id={data.id}
          dataObj={data}
          index={index}
          getActions={props.getActions}
          getStatus={props.getStatus}
          selectedResources={selectedResources}
          headers={props.headers}
          isRowClickable={props.rowClickable}
          hasRowActions={props.hasRowActions || false}
          page={props.page || 0}
          getNextUrl={props?.getNextUrl}
          onRowClick={props.onRowClick}
          newRow={props?.useNewRow}
          headings={props?.headings}
          notHighlightOnselected={props.notHighlightOnselected}
          popoverActive={popoverActive}
          setPopoverActive={setPopoverActive}
          treeView={props?.treeView}
        />
      ),
    );
  }, [data, selectedResources, props, popoverActive, setPopoverActive]);

  const onPageNext = () => {
    setPage((page) => (page + 1));
  }

  const onPagePrevious = () => {
    setPage((page) => (page - 1));
  }

  const handleTabChange = (x) => {
    props?.onSelect(x); 
    updateQueryParams("filters", tableFunc.getPrettifiedFilter([])) ;
    handleSelectedTab(x)
  }

  let tableHeightClass = props.increasedHeight ? "control-row" : (props.condensedHeight ? "condensed-row" : '') 
  let tableClass = props.useNewRow ? "new-table" : (props.selectable ? "removeHeaderColor" : "hideTableHead")
  const bulkActionResources = selectedItems.length > 0 ? selectedItems : selectedResources
  if (typeof props.setSelectedResourcesForPrimaryAction === 'function') {
    props.setSelectedResourcesForPrimaryAction(bulkActionResources)
  }
  return (
    <div className={tableClass} style={{display: "flex", flexDirection: "column", gap: "20px"}}>
      <LegacyCard>
        {props.tabs && <Tabs tabs={props.tabs} selected={props.selected} onSelect={(x) => handleTabChange(x)}></Tabs>}
        {props.tabs && props.tabs[props.selected].component ? props.tabs[props.selected].component :
          <div>
            <LegacyCard.Section flush>
              <IndexFilters
                sortOptions={props.sortOptions}
                sortSelected={sortSelected}
                queryValue={queryValue}
                queryPlaceholder={`Search in ${transform.formatNumberWithCommas(total)} ${total === 1 ? props.resourceName.singular : props.resourceName.plural}`}
                onQueryChange={handleFiltersQueryChange}
                onQueryClear={handleFiltersQueryClear}
                {...(props.hideQueryField ? { hideQueryField: props.hideQueryField } : {})}
                onSort={setSortSelected}
                cancelAction={{
                  onAction: () => {},
                  disabled: false,
                  loading: false,
                }}
                tabs={props.tableTabs ? props.tableTabs : []}
                canCreateNewView={false}
                filters={filters}
                appliedFilters={appliedFilters}
                onClearAll={handleFiltersClearAll}
                mode={mode}
                setMode={setMode}
                loading={props.loading || false}
                selected={props?.selected}
                onSelect={(x) => handleTabChange(x)}
                hideFilters={hideFilter}
              ></IndexFilters>
              {props?.bannerComp?.selected === props?.selected ? props?.bannerComp?.comp : null}
              <div className={tableHeightClass}>
              <IndexTable
                resourceName={props.resourceName}
                itemCount={data.length}
                selectedItemsCount={
                  allResourcesSelected ? 'All' : bulkActionResources.length
                }
                // condensed
                selectable={props.selectable || false}
                onSelectionChange={customSelectionChange}
                headings={props?.headings ? props.headings :[
                  {
                    id: "data",
                    hidden: true,
                    flush: true
                  }
                ]}
                promotedBulkActions={props.selectable ? props.promotedBulkActions && props.promotedBulkActions(bulkActionResources) : []}
                hasZebraStriping={props.hasZebraStriping || false}
                sortable={sortableColumns}
                sortColumnIndex={activeColumnSort.columnIndex}
                sortDirection={activeColumnSort.sortDirection}
                onSort={handleSort}
                lastColumnSticky={props?.lastColumnSticky || false}
                {...(props?.emptyStateMarkup ? {emptyState:props?.emptyStateMarkup} : {})}
              >
                {rowMarkup}
              </IndexTable>
            </div>
            </LegacyCard.Section>
            {(total !== 0 && !props?.hidePagination) && <LegacyCard.Section>
              <HorizontalStack
                align="center">
                <Pagination
                  label={
                    total == 0 ? 'No data found' :
                        <div data-testid="pagination-label">
                            {`Showing ${transform.formatNumberWithCommas(page * pageLimit + Math.min(1, total))}-${transform.formatNumberWithCommas(Math.min((page + 1) * pageLimit, total))} of ${transform.formatNumberWithCommas(total)}`}
                        </div>
                }
                  hasPrevious={page > 0}
                  previousKeys={[Key.LeftArrow]}
                  onPrevious={onPagePrevious}
                  hasNext={total > (page + 1) * pageLimit}
                  nextKeys={[Key.RightArrow]}
                  onNext={onPageNext}
                />
              </HorizontalStack>
            </LegacyCard.Section>}
          </div>
        }

      </LegacyCard>
      {(props?.showFooter !== false) && <HorizontalStack gap="1" align="center">
        <Text>Stuck? feel free to</Text>
        <Link onClick={() => {
          window?.Intercom("show")
        }}>Contact us</Link>
        <Text>or</Text>
        <Link url="https://akto.io/api-security-demo" target="_blank">Book a call</Link>
      </HorizontalStack>}
    </div>
  );
}

export default GithubServerTable
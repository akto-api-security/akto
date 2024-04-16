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
  Tabs} from '@shopify/polaris';
import {GithubRow} from './rows/GithubRow';
import { useState, useCallback, useEffect } from 'react';
import "./style.css"
import transform from '../../pages/observe/transform';
import DropdownSearch from '../shared/DropdownSearch';
import PersistStore from '../../../main/PersistStore';
import tableFunc from './transform';
import useTable from './TableContext';

function GithubServerTable(props) {

  const filtersMap = PersistStore(state => state.filtersMap)
  const setFiltersMap = PersistStore(state => state.setFiltersMap)
  const tableInitialState = PersistStore(state => state.tableInitialState)
  const setTableInitialState = PersistStore(state => state.setTableInitialState)

  const currentPageKey = window.location.href

  const pageFiltersMap = filtersMap[currentPageKey]
  const initialStateFilters = tableFunc.mergeFilters(props.appliedFilters || [], (pageFiltersMap?.filters || []),props.disambiguateLabel)
  const { mode, setMode } = useSetIndexFiltersMode(props?.mode ? props.mode : IndexFiltersMode.Filtering);
  const [sortSelected, setSortSelected] = useState(tableFunc.getInitialSortSelected(props.sortOptions, pageFiltersMap))
  const [data, setData] = useState([]);
  const [total, setTotal] = useState([]);
  const [page, setPage] = useState(0);
  const pageLimit = props?.pageLimit || 20;
  const [appliedFilters, setAppliedFilters] = useState(initialStateFilters);
  const [queryValue, setQueryValue] = useState('');

  const { applyFilter } = useTable()

  const [sortableColumns, setSortableColumns] = useState([])
  const [activeColumnSort, setActiveColumnSort] = useState({columnIndex: -1, sortDirection: 'descending'})

  let filterOperators = props.headers.reduce((map, e) => { map[e.sortKey || e.value] = 'OR'; return map }, {})

  const handleSelectedTab = (x) => {
    const tableTabs = props.tableTabs ? props.tableTabs : props.tabs
    if(tableTabs){
      const primitivePath = window.location.origin + window.location.pathname
      const newUrl = primitivePath + "#" +  tableTabs[x].id
      window.history.replaceState(null, null, newUrl)
    } 
  }

  useEffect(()=> {
    setAppliedFilters(initialStateFilters)
    setSortSelected(tableFunc.getInitialSortSelected(props.sortOptions, pageFiltersMap))
  },[currentPageKey])

  useEffect(() => {
    let [sortKey, sortOrder] = sortSelected.length == 0 ? ["", ""] : sortSelected[0].split(" ");
    setActiveColumnSort(tableFunc.getColumnSort(sortSelected, props?.sortOptions))
    let filters = props.headers.reduce((map, e) => { map[e.filterKey || e.value] = []; return map }, {})
    appliedFilters.forEach((filter) => {
      filters[filter.key] = filter.value
    })
    async function fetchData() {
      let tempData = await props.fetchData(sortKey, sortOrder == 'asc' ? -1 : 1, page * pageLimit, pageLimit, filters, filterOperators, queryValue);
      tempData ? setData([...tempData.value]) : setData([])
      tempData ? setTotal(tempData.total) : setTotal(0)
      applyFilter(tempData.total)
      
      setTableInitialState({
        ...tableInitialState,
        [currentPageKey]: tempData.total
      })
    }
    handleSelectedTab(props?.selected)
    fetchData();
  }, [sortSelected, appliedFilters, queryValue, page, pageFiltersMap])

  useEffect(()=> {
    setSortableColumns(tableFunc.getSortableChoices(props?.headers))
  },[props?.headers])

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
    let tempFilters = filtersMap
    tempFilters[currentPageKey] = {
      'filters': temp,
      'sort': pageFiltersMap?.sort || []
    }
    setFiltersMap(tempFilters)
  }

  const changeAppliedFilters = (key, value) => {
    let temp = appliedFilters
    temp = temp.filter((filter) => {
      return filter.key !== key
    })
    if (value.length > 0 || Object.keys(value).length > 0) {
      temp.push({
        key: key,
        label: props.disambiguateLabel(key, value),
        onRemove: handleRemoveAppliedFilter,
        value: value
      })
    }
    setPage(0);
    let tempFilters = filtersMap
    tempFilters[currentPageKey] = {
      'filters': temp,
      'sort': pageFiltersMap?.sort || []
    }
    setFiltersMap(tempFilters)
    setAppliedFilters(temp);
  };

  const handleFiltersQueryChange = useCallback(
    (value) => setQueryValue(value),
    [],
  );
  const handleFiltersQueryClear = useCallback(
    () => setQueryValue(""),
    [],
  );

  const handleFilterStatusChange = (key,value) => {
    changeAppliedFilters(key, value);
  }

  const getSortedChoices = (choices) => {
    return choices.sort((a, b) => (a?.label || a) - (b?.label || b));
  }

  let filters = formatFilters(props.filters)

  function formatFilters(filters) {
    return filters 
      .map((filter) => {
        return {
          key: filter.key,
          label: filter.label,
          filter: (
              filter.choices.length < 10 ?       
              <ChoiceList
                title={filter.title}
                titleHidden
                choices={getSortedChoices(filter.choices)}
                selected={
                  appliedFilters.filter((localFilter) => { return localFilter.key == filter.key }).length == 1 ?
                    appliedFilters.filter((localFilter) => { return localFilter.key == filter.key })[0].value : filter.selected || []
                }
                onChange={(value) => handleFilterStatusChange(filter.key,value)}
                {...(filter.singleSelect ? {} : { allowMultiple: true })}
              />
              :
              <DropdownSearch 
                placeHoder={"Apply filters"} 
                optionsList={getSortedChoices(filter.choices)}
                setSelected={(value) => handleFilterStatusChange(filter.key,value)}
                {...(filter.singleSelect ? {} : { allowMultiple: true })}
                allowMultiple
                preSelected={
                  appliedFilters.filter((localFilter) => { return localFilter.key == filter.key }).length == 1 ?
                    appliedFilters.filter((localFilter) => { return localFilter.key == filter.key })[0].value : filter.selected || []
                }
                value={
                  appliedFilters.filter((localFilter) => { return localFilter.key == filter.key }).length == 1 ?
                    appliedFilters.filter((localFilter) => { return localFilter.key == filter.key })[0].value : filter.selected || []
                }
              />
          ),
          // shortcut: true,
          pinned: true
        }
      })
  }

  const handleFiltersClearAll = useCallback(() => {
    setFiltersMap({
      ...filtersMap,
      [currentPageKey]:{
        sort: filtersMap[currentPageKey]?.sort || []
      }
    })
    setAppliedFilters([])
  }, []);

  const resourceIDResolver = (data) => {
    return data.id;
  };

  const { selectedResources, allResourcesSelected, handleSelectionChange } =
    useIndexResourceState(data, {
      resourceIDResolver,
    });

  const [popoverActive, setPopoverActive] = useState(-1);

  // sending all data in case of simple table because the select-all state is controlled from the data.
  // not doing this affects bulk select functionality.
  let tmp = data && data.length <= pageLimit ? data :
    data.slice(page * pageLimit, Math.min((page + 1) * pageLimit, data.length))
  let rowMarkup = tmp
    .map(
      (
        data,
        index,
      ) => (
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
        />
      ),
    );

  const onPageNext = () => {
    setPage((page) => (page + 1));
  }

  const onPagePrevious = () => {
    setPage((page) => (page - 1));
  }

  let tableHeightClass = props.increasedHeight ? "control-row" : (props.condensedHeight ? "condensed-row" : '') 
  let tableClass = props.useNewRow ? "new-table" : (props.selectable ? "removeHeaderColor" : "hideTableHead")
  return (
    <div className={tableClass}>
      <LegacyCard>
        {props.tabs && <Tabs tabs={props.tabs} selected={props.selected} onSelect={(x) => {props?.onSelect(x); handleSelectedTab(x)}}></Tabs>}
        {props.tabs && props.tabs[props.selected].component ? props.tabs[props.selected].component :
          <div>
            <LegacyCard.Section flush>
              <IndexFilters
                sortOptions={props.sortOptions}
                sortSelected={sortSelected}
                queryValue={queryValue}
                queryPlaceholder={`Searching in ${transform.formatNumberWithCommas(total)} ${total == 1 ? props.resourceName.singular : props.resourceName.plural}`}
                onQueryChange={handleFiltersQueryChange}
                onQueryClear={handleFiltersQueryClear}
                {...(props.hideQueryField ? { hideQueryField: props.hideQueryField } : {})}
                onSort={setSortSelected}
                cancelAction={{
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
                onSelect={(x) => {props?.onSelect(x); handleSelectedTab(x)}}
              />
              <div className={tableHeightClass}>
              <IndexTable
                resourceName={props.resourceName}
                itemCount={data.length}
                selectedItemsCount={
                  allResourcesSelected ? 'All' : selectedResources.length
                }
                // condensed
                selectable={props.selectable || false}
                onSelectionChange={handleSelectionChange}
                headings={props?.headings ? props.headings :[
                  {
                    id: "data",
                    hidden: true,
                    flush: true
                  }
                ]}
                bulkActions={props.selectable ? props.bulkActions && props.bulkActions(selectedResources) : []}
                promotedBulkActions={props.selectable ? props.promotedBulkActions && props.promotedBulkActions(selectedResources) : []}
                hasZebraStriping={props.hasZebraStriping || false}
                sortable={sortableColumns}
                sortColumnIndex={activeColumnSort.columnIndex}
                sortDirection={activeColumnSort.sortDirection}
                onSort={handleSort}
              >
                {rowMarkup}
              </IndexTable>
            </div>
            </LegacyCard.Section>
            <LegacyCard.Section>
              <HorizontalStack
                align="center">
                <Pagination
                  label={
                    total == 0 ? 'No data found' :
                      `Showing ${transform.formatNumberWithCommas(page * pageLimit + Math.min(1, total))}-${transform.formatNumberWithCommas(Math.min((page + 1) * pageLimit, total))} of ${transform.formatNumberWithCommas(total)}`
                  }
                  hasPrevious={page > 0}
                  previousKeys={[Key.LeftArrow]}
                  onPrevious={onPagePrevious}
                  hasNext={total > (page + 1) * pageLimit}
                  nextKeys={[Key.RightArrow]}
                  onNext={onPageNext}
                />
              </HorizontalStack>
            </LegacyCard.Section>
          </div>
        }

      </LegacyCard>
    </div>
  );

}

export default GithubServerTable
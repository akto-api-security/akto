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
import { useState, useCallback, useEffect, useReducer } from 'react';
import DateRangePicker from '../layouts/DateRangePicker';
import "./style.css"
import func from '@/util/func';
import { produce } from "immer"
import values from "@/util/values"
import transform from '../../pages/observe/transform';
import SpinnerCentered from '../progress/SpinnerCentered';

function GithubServerTable(props) {

  const { mode, setMode } = useSetIndexFiltersMode(props?.mode ? props.mode : IndexFiltersMode.Filtering);
  const [sortSelected, setSortSelected] = useState(props?.sortOptions?.length > 0 ? [props.sortOptions[0].value] : []);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState([]);
  const [page, setPage] = useState(0);
  const pageLimit = props?.pageLimit || 20;
  const [appliedFilters, setAppliedFilters] = useState(props.appliedFilters || []);
  const [queryValue, setQueryValue] = useState('');
  let filterOperators = props.headers.reduce((map, e) => { map[e.sortKey || e.value] = 'OR'; return map }, {})

  const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[3]);

  useEffect(() => {
    let [sortKey, sortOrder] = sortSelected.length == 0 ? ["", ""] : sortSelected[0].split(" ");
    let filters = props.headers.reduce((map, e) => { map[e.filterKey || e.value] = []; return map }, {})
    appliedFilters.forEach((filter) => {
      filters[filter.key] = filter.value
    })
    async function fetchData() {
      let tempData = await props.fetchData(sortKey, sortOrder == 'asc' ? -1 : 1, page * pageLimit, pageLimit, filters, filterOperators, queryValue);
      tempData ? setData([...tempData.value]) : setData([])
      tempData ? setTotal(tempData.total) : setTotal(0)
    }
    fetchData();
  }, [sortSelected, appliedFilters, queryValue, page])

  const handleRemoveAppliedFilter = (key) => {
    let temp = appliedFilters
    temp = temp.filter((filter) => {
      return filter.key != key
    })
    props?.appliedFilters?.forEach((defaultAppliedFilter) => {
      if (key == defaultAppliedFilter.key) {
        temp.push(defaultAppliedFilter)
      }
    })
    if (key == "dateRange") {
      getDate({ type: "update", period: values.ranges[3] });
    } else {
      setAppliedFilters(temp);
    }
  }

  const changeAppliedFilters = (key, value) => {
    let temp = appliedFilters
    temp = temp.filter((filter) => {
      return filter.key != key
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

  const getDate = (dateObj) => {
    dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})
    let obj = dateObj.period.period;
    handleFilterStatusChange("dateRange",obj)
  }

  let filters = formatFilters(props.filters)
  function formatFilters(filters) {
    return filters
      .map((filter) => {
        return {
          key: filter.key,
          label: filter.label,
          filter: (
            <ChoiceList
              title={filter.title}
              titleHidden
              choices={filter.choices}
              selected={
                appliedFilters.filter((localFilter) => { return localFilter.key == filter.key }).length == 1 ?
                  appliedFilters.filter((localFilter) => { return localFilter.key == filter.key })[0].value : filter.selected || []
              }
              onChange={(value) => handleFilterStatusChange(filter.key,value)}
              {...(filter.singleSelect ? {} : { allowMultiple: true })}
            />
          ),
          // shortcut: true,
          pinned: true
        }
      })
  }
  if (props.calenderFilter) {
    filters.push({
      key: "dateRange",
      label: props.calenderLabel || "Discovered",
      filter:
        (<DateRangePicker ranges={values.ranges}
          initialDispatch = {currDateRange} 
          dispatch={(dateObj) => getDate(dateObj)}
          setPopoverState={() => {}}
        />),
      pinned: true
    })
  }

  const handleFiltersClearAll = useCallback(() => {
    setAppliedFilters(props.appliedFilters || [])
    getDate({ type: "update", period: values.ranges[3] });
  }, []);

  const resourceIDResolver = (data) => {
    return data.id;
  };

  const { selectedResources, allResourcesSelected, handleSelectionChange } =
    useIndexResourceState(data, {
      resourceIDResolver,
    });


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
        {props.tabs && <Tabs tabs={props.tabs} selected={props.selected} onSelect={props.onSelect}></Tabs>}
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
                onSelect={props?.onSelect}
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
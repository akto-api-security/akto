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
  ChoiceList} from '@shopify/polaris';
import GithubRow from './rows/GithubRow';
import { useState, useCallback, useEffect } from 'react';
import DateRangePicker from '../layouts/DateRangePicker';
import values from '../../../../util/values';
import "./style.css"

function GithubServerTable(props) {

  const { mode, setMode } = useSetIndexFiltersMode(IndexFiltersMode.Filtering);
  const [selected, setSelected] = useState(0);
  const [sortSelected, setSortSelected] = useState(props.sortOptions.length!=0 ? [props.sortOptions[0].value] : []);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState([]);
  const [page, setPage] = useState(0);
  const pageLimit = 20;
  const [appliedFilters, setAppliedFilters] = useState(props.appliedFilters || []);
  const [queryValue, setQueryValue] = useState('');
  let filterOperators = props.headers.reduce((map, e) => {map[e.sortKey || e.value] = 'OR'; return map}, {})

  useEffect(() => {
    let [sortKey, sortOrder] = sortSelected.length==0 ? ["",""]: sortSelected[0].split(" ");
    let filters = props.headers.reduce((map, e) => {map[e.filterKey || e.value] = []; return map}, {})
    appliedFilters.forEach((filter) => {
      filters[filter.key]=filter.value
    })
    async function fetchData(){
      let tempData = await props.fetchData(sortKey, sortOrder=='asc' ? -1 : 1, page*pageLimit, pageLimit, filters, filterOperators, queryValue);
      setData([...tempData.value])
      setTotal(tempData.total)
    }
    fetchData();
  }, [sortSelected, appliedFilters, queryValue, page])

  const handleRemoveAppliedFilter = (key) => {
    let temp = appliedFilters
    temp = temp.filter((filter) => {
      return filter.key != key
    })
    props?.appliedFilters?.forEach((defaultAppliedFilter) => {
      if(key == defaultAppliedFilter.key){
        temp.push(defaultAppliedFilter)
      }
    })
    setAppliedFilters(temp);
  }

  const changeAppliedFilters = (key, value) => {
    let temp = appliedFilters
    temp = temp.filter((filter) => {
      return filter.key != key
    })
    if(value.length>0 || Object.keys(value).length>0){
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

  const handleFilterStatusChange = (key) => (value) =>{
    changeAppliedFilters(key, value);
  }

  let filters=formatFilters(props.filters)
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
              onChange={handleFilterStatusChange(filter.key)}
              {...(filter.singleSelect ? {} : {allowMultiple: true})}
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
      label: "Discovered",
      filter:
        (<DateRangePicker ranges={values.ranges}
          getDate={handleFilterStatusChange("dateRange")}
          setPopoverState={() => {}}
          setButtonValue={() => {}}
        />),
      pinned: true
    })
  }

  const handleFiltersClearAll = useCallback(() => {
    setAppliedFilters(props.appliedFilters || [])
  }, []);

  const resourceIDResolver = (data) => {
    return data.id;
  };

  const { selectedResources, allResourcesSelected, handleSelectionChange } =
    useIndexResourceState(data, {
      resourceIDResolver,
    });

  const fun = () => {
    console.log("func", sortSelected)
  }
  
  // sending all data in case of simple table because the select-all state is controlled from the data.
  // not doing this affects bulk select functionality.
  let tmp = data && data.length <= pageLimit ? data : 
  data.slice(page*pageLimit, Math.min((page+1)*pageLimit, data.length))
  let rowMarkup = tmp
  .map(
    (
      data,
      index,
    ) => (
      <GithubRow 
        key={data.id}
        id={data.id}
        data={data} 
        index={index} 
        getActions={props.getActions} 
        selectedResources={selectedResources}
        headers={props.headers}
        hasRowActions={props.hasRowActions || false}
        page={props.page || 0}
        />
    ),
  );

  const onPageNext = () =>{
    setPage((page) => (page+1));
  }

  const onPagePrevious = () =>{
    setPage((page) => (page-1));
  }

  return (
    <div>
      <LegacyCard>
        <LegacyCard.Section flush>
          <IndexFilters
          sortOptions={props.sortOptions}
          sortSelected={sortSelected}
          queryValue={queryValue}
          queryPlaceholder={`Searching in ${total} test run${total==1 ? '':'s'}`}
          onQueryChange={handleFiltersQueryChange}
          onQueryClear={handleFiltersQueryClear}
          {...(props.hideQueryField ? {hideQueryField: props.hideQueryField} : {})}
          onSort={setSortSelected}
          // primaryAction={primaryAction}
          cancelAction={{
            onAction: fun,
            disabled: false,
            loading: false,
          }}
          tabs={[]}
          selected={selected}
          onSelect={setSelected}
          canCreateNewView={false}
          filters={filters}
          appliedFilters={appliedFilters}
          onClearAll={handleFiltersClearAll}
          mode={mode}
          setMode={setMode}
          loading={props.loading || false}
        />
        <IndexTable
          resourceName={props.resourceName}
          itemCount={data.length}
          selectedItemsCount={
            allResourcesSelected ? 'All' : selectedResources.length
          }
          // condensed
          selectable={props.selectable || false}
          onSelectionChange={handleSelectionChange}
          headings={[
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
        </LegacyCard.Section>
        <LegacyCard.Section>
          <HorizontalStack
            align="center">
            <Pagination
              label={
                total == 0 ? 'No data found' :
                  `Showing ${page * pageLimit + Math.min(1, total)}-${Math.min((page + 1) * pageLimit, total)} of ${total}`
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
      </LegacyCard>
    </div>
  );

}

export default GithubServerTable
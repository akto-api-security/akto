import {
  IndexTable,
  LegacyCard,
  IndexFilters,
  useSetIndexFiltersMode,
  IndexFiltersMode,
  useIndexResourceState,
  ChoiceList} from '@shopify/polaris';
import GithubRow from './rows/GithubRow';

import { useState, useCallback, useEffect } from 'react';

function GithubTable(props) {

  const { mode, setMode } = useSetIndexFiltersMode(IndexFiltersMode.Filtering);
  const [selected, setSelected] = useState(0);
  const [sortSelected, setSortSelected] = useState([props.sortOptions[0].value]);
  const [data, setData] = useState(props.data);
  const [appliedFilters, setAppliedFilters] = useState([]);
  const [queryValue, setQueryValue] = useState('');

  let filterObject = props.filters.map((filter) => { return filter.key })
  let obj = {}
  filterObject.forEach((filter) => {
    obj[filter] = []
  })
  const [filterStatus, setFilterStatus] = useState(
    obj
  );

  useEffect(() => {
    let tempData = props.data;
    let singleFilterData = tempData
    appliedFilters.map((filter) => {
      singleFilterData = props.data;
      let filterSet = new Set(filter.value);
      singleFilterData = singleFilterData.filter((value) => {
        return [...value[filter.key]].filter(v => filterSet.has(v)).length > 0
      })
      tempData = tempData.filter(value => singleFilterData.includes(value));
    })

    tempData = tempData.filter((value) => {
      return value.name.toLowerCase().includes(queryValue.toLowerCase());
    })

    let sortKey = props.sortOptions.filter(value => {
      return (value.value === sortSelected[0])
    })[0].sortKey;
    let sortDirection = sortSelected[0].split(" ")[1];
    tempData.sort((a, b) => {
      return (sortDirection == 'asc' ? -1 : 1) * (a[sortKey] - b[sortKey]);
    })
    setData([...tempData])
  }, [sortSelected, appliedFilters, queryValue])

  const changeAppliedFilters = () => {
    // setAppliedFilters([]);
    let temp = []
    let filterKeys = Object.keys(filterStatus);
    filterKeys.forEach((filterKey) => {
      if (!isEmpty(filterStatus[filterKey])) {
        temp.push({
          key: filterKey,
          label: props.disambiguateLabel(filterKey, filterStatus[filterKey]),
          onRemove: handleFilterStatusRemove,
          value: filterStatus[filterKey]
        });
      }
    })
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
  const handleFilterStatusChange = (key) => useCallback(
    (value) => {
      let tempFilter = filterStatus;
      tempFilter[key] = value
      setFilterStatus({ ...tempFilter })
      changeAppliedFilters(tempFilter)
    },
    [],
  );
  const filters = props.filters.map((filter) => {
    return {
      key: filter.key,
      label: filter.label,
      filter: (
        <ChoiceList
          title={filter.title}
          titleHidden
          choices={filter.choices}
          selected={filterStatus[filter.key] || []}
          onChange={handleFilterStatusChange(filter.key)}
          allowMultiple
        />
      ),
      // shortcut: true,
      pinned: true
    }
  })
  const handleFilterStatusRemove = useCallback((...keys) => {
    let tempFilter = filterStatus;
    keys.forEach((key) => {
      if (key in tempFilter) {
        tempFilter[key] = []
      }
    })
    setFilterStatus({ ...tempFilter })
    changeAppliedFilters(tempFilter)
  }, []);

  const handleFiltersClearAll = useCallback(() => {
    let filterKeys = Object.keys(filterStatus);
    handleFilterStatusRemove(...filterKeys);
  }, [
    handleFilterStatusRemove
  ]);

  const { selectedResources, allResourcesSelected, handleSelectionChange } =
    useIndexResourceState(data);


  const fun = () => {
    console.log("func", sortSelected)
  }
  
  let rowMarkup = data.map(
    (
      data,
      index,
    ) => (
      <GithubRow 
        key={data.hexId}
        data={data} 
        index={index} 
        getActions={props.getActions} 
        selectedResources={selectedResources}
        headers={props.headers}/>
    ),
  );

  return (
    <div>
      <LegacyCard>
        <IndexFilters
          sortOptions={props.sortOptions}
          sortSelected={sortSelected}
          queryValue={queryValue}
          queryPlaceholder={`Searching in ${data.length} test runs`}
          onQueryChange={handleFiltersQueryChange}
          onQueryClear={handleFiltersQueryClear}
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
        />
        <IndexTable
          resourceName={props.resourceName}
          itemCount={data.length}
          selectedItemsCount={
            allResourcesSelected ? 'All' : selectedResources.length
          }
          // condensed
          selectable={false}
          onSelectionChange={handleSelectionChange}
          headings={[
            {
              id: "data",
              hidden: true,
              flush: true
            }
          ]}
        >
          {rowMarkup}
        </IndexTable>
      </LegacyCard>
    </div>
  );

  function isEmpty(value) {
    if (Array.isArray(value)) {
      return value.length === 0;
    } else {
      return value === '' || value == null;
    }
  }
}

export default GithubTable
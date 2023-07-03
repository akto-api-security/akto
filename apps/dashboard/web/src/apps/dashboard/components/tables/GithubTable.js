import {
  IndexTable,
  LegacyCard,
  IndexFilters,
  useSetIndexFiltersMode,
  IndexFiltersMode,
  useIndexResourceState,
  Text,
  ChoiceList,
  Badge,
  VerticalStack,
  HorizontalStack,
  ButtonGroup,
  Icon,
  Box
} from '@shopify/polaris';
import {
  HorizontalDotsMinor
} from '@shopify/polaris-icons';

import { useState, useCallback, useEffect } from 'react';

function GithubTable(props) {

  const [selected, setSelected] = useState(0);
  const [sortSelected, setSortSelected] = useState([props.sortOptions[0].value]);
  const [data, setData] = useState(props.testRuns);
  const [dataCopy, setDataCopy] = useState(props.testRuns);

  useEffect(() => {
    let sortKey = props.sortOptions.filter(value => {
      return (value.value === sortSelected[0])
    })[0].sortKey;
    let sortDirection = sortSelected[0].split(" ")[1];
    let tempData = data;
    tempData.sort((a, b) => {
      return (sortDirection == 'asc' ? -1 : 1) * (a[sortKey] - b[sortKey]);
    })
    setData([...tempData])
  }, [sortSelected])

  const { mode, setMode } = useSetIndexFiltersMode(IndexFiltersMode.Filtering);

  const [appliedFilters, setAppliedFilters] = useState([]);
  const [queryValue, setQueryValue] = useState('');
  const handleFiltersQueryChange = useCallback(
    (value) => setQueryValue(value),
    [],
  );
  const handleFiltersQueryClear = useCallback(
    () => setQueryValue(""),
    [],
  );
  useEffect(() => {
    let tempDataCopy = dataCopy;
    let singleFilterData = tempDataCopy
    appliedFilters.map((filter) => {
      singleFilterData = dataCopy;
      let filterSet = new Set(filter.value);
      singleFilterData = singleFilterData.filter((value) => {
        return [...value[filter.key]].filter(v => filterSet.has(v)).length > 0
      })
      tempDataCopy = tempDataCopy.filter(value => singleFilterData.includes(value));
    })
    tempDataCopy = tempDataCopy.filter((value) => {
      return value.name.toLowerCase().includes(queryValue.toLowerCase());
    })
    setData(tempDataCopy);
  }, [appliedFilters, queryValue])

  let filterObject = props.filters.map((filter) => { return filter.key })
  // console.log(filterObject);
  let obj = {}
  filterObject.forEach((filter) => {
    obj[filter] = []
  })
  // console.log(obj)

  const [filterStatus, setFilterStatus] = useState(
    obj
  );

  useEffect(() => {
    setAppliedFilters([]);
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
  }, [filterStatus])

  const handleFilterStatusChange = (key) => useCallback(
    (value) => {
      let tempFilter = filterStatus;
      tempFilter[key] = value
      setFilterStatus({ ...tempFilter })
    },
    [],
  );

  const handleFilterStatusRemove = useCallback((...keys) => {
    let tempFilter = filterStatus;
    keys.forEach((key) => {
      if (key in tempFilter) {
        tempFilter[key] = []
      }
    })
    setFilterStatus({ ...tempFilter })
  }, []);

  const handleFiltersClearAll = useCallback(() => {
    let filterKeys = Object.keys(filterStatus);
    handleFilterStatusRemove(...filterKeys);
  }, [
    handleFilterStatusRemove
  ]);

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
      // onAction: () => { console.log("hello") },
      // shortcut: true,
      pinned: true
    }
  })

  const { selectedResources, allResourcesSelected, handleSelectionChange } =
    useIndexResourceState(data);

  function getStatus(item) {
    switch (item.confidence) {
      case 'High': return 'critical';
      case 'Medium': return 'warning';
      case 'Low': return 'neutral';
    }
  }
  let rowMarkup = data.map(
    (
      data,
      index,
    ) => (

      <IndexTable.Row
        id={data.hexId}
        key={data.hexId}
        selected={selectedResources.includes(data.hexId)}
        position={index}
      >
        <IndexTable.Cell>
          {/* <div style={{ padding: '12px 16px', width: '100%' }}> */}
          <HorizontalStack align='space-between'>
            <HorizontalStack gap="1">
              {/* <VerticalStack align="start" inlineAlign="start" gap="1"> */}
              {/* <HorizontalStack gap="2" align='center'> */}
                <Box padding="1">
                  {
                    props?.headers[0]?.icon &&
                    <Icon source={data[props?.headers[0]?.icon['value']]} color="primary" />
                  }
                </Box>
                {/* </HorizontalStack> */}
              {/* </VerticalStack> */}
              <VerticalStack gap="2">
                <HorizontalStack gap="2" align='start'>
                  <Text as="span" variant="headingMd">
                    {
                      props?.headers[0]?.name &&
                      data[props?.headers[0]?.name['value']]
                    }
                  </Text>
                  {
                    props?.headers[1]?.severityList &&
                      data[props?.headers[1]?.severityList['value']] ? data[props?.headers[1]?.severityList['value']].map((item) =>
                        <Badge key={item.confidence} status={getStatus(item)}>{item.confidence} {item.count}</Badge>) :
                      []}
                </HorizontalStack>
                {/* <div style={{width: 'fit-content'}}> */}
                <HorizontalStack gap='2' align="start" >
                    {/* {
                    props?.headers[2]?.icon &&
                    <Icon source={props?.headers[2]?.icon['value']} color="primary" />
                  } */}
                    {
                      props?.headers[2]?.details &&
                      props?.headers[2]?.details.map((detail) => {
                        return (
                          <ButtonGroup key={detail.value}>
                            <Icon source={detail.icon} color="subdued" />
                            <Text as="span" variant="bodySm" color="subdued">
                              {data[detail.value]}
                            </Text>
                          </ButtonGroup>
                        )
                      })
                    }
                </HorizontalStack>
                {/* </div> */}
              </VerticalStack>
            </HorizontalStack>
            <VerticalStack>
              <Icon source={HorizontalDotsMinor} color="base" />
            </VerticalStack>
          </HorizontalStack>

          {/* ) */}

          {/* }) */}

          {/* } */}

          {/* </div> */}
        </IndexTable.Cell>
      </IndexTable.Row>
    ),
  );

  const fun = () => {
    console.log("func", sortSelected)
  }
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
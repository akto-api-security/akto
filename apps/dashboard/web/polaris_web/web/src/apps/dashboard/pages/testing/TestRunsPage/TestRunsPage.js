import GithubServerTable from "../../../components/tables/GithubServerTable";
import {Text,IndexFiltersMode, LegacyCard, HorizontalStack, Button, Collapsible, HorizontalGrid, Box, Divider} from '@shopify/polaris';
import { ChevronDownMinor , ChevronUpMinor } from '@shopify/polaris-icons';
import api from "../api";
import { useEffect, useReducer, useState } from 'react';
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import func from "@/util/func"
import ChartypeComponent from "./ChartypeComponent";
import { CellType } from "../../../components/tables/rows/GithubRow";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import {produce} from "immer"
import values from "@/util/values";
import {TestrunsBannerComponent} from "./TestrunsBannerComponent";
import useTable from "../../../components/tables/TableContext";
import PersistStore from "../../../../main/PersistStore";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
/*
  {
    text:"", // req. -> The text to be shown wherever the header is being shown
    value:"", // req. -> which key in the data does this header refer to.
    itemOrder:0, // optional -> needed if all headers are shown differently.
    icon:"", // optional -> needed if a header has a custom icon associated with it.
    showFilter:true, // optional, default -> false
    filterKey:"" // optional, default -> value
    filterLabel:"" // optional, default -> title
    filterValues:[], // optional, default to a list of all data in value key.
    // If value is an object then defaults to object of all keys in the value.
    showSort:false, // optional -> if you want to show show sort on this field, defaults to false. 
    sortKey:"", // optional -> using a different key than "value" for sorting.
    sortLabel: "", // optional, defaults to text -> if you want a different sort label than text.
    sortDirectionLabel: { asc:"", desc:"" }, // optional , defaults to new/old if text/sortLabel contains "time", else high/low
  }
*/

let headers = [
  {
    text:"Test name",
    title: 'Test run name',
    value:"testName",
    itemOrder:1,
  },
  {
    text: "Number of tests",
    title: "Number of tests",
    value: "number_of_tests",
    itemOrder: 3,
    type: CellType.TEXT,
    tooltipContent: (<Text variant="bodySm">Count of attempted testing run results</Text>)
  },
  {
    text:"Severity",
    value: 'severity',
    title: 'Issues',
    filterKey:"severityStatus",
    itemOrder:2,
    tooltipContent: (<Text variant="bodySm">Severity and count of issues per test run</Text>)
  },
  {
    text: 'Run time',
    value: 'run_time',
    title: 'Status',
    itemOrder: 3,
    type: CellType.TEXT,
    sortActive: true
  },
  {
    title: '',
    type: CellType.ACTION,
  }
]

const sortOptions = [
  { label: 'Run time', value: 'scheduleTimestamp asc', directionLabel: 'Newest run', sortKey: 'scheduleTimestamp', columnIndex: 4 },
  { label: 'Run time', value: 'scheduleTimestamp desc', directionLabel: 'Oldest run', sortKey: 'scheduleTimestamp', columnIndex: 4 }
];

const resourceName = {
  singular: 'test run',
  plural: 'test runs',
};

let filters = [
  {
    key: 'severity',
    label: 'Severity',
    title: 'Severity',
    choices: [
      { label: "High", value: "HIGH" }, 
      { label: "Medium", value: "MEDIUM" },
      { label: "Low", value: "LOW" }
    ]
  },
  {
    key: 'apiCollectionId',
    label: 'Api collection name',
    title: 'Api collection name',
    choices: [],
},
]

function TestRunsPage() {

  const apiCollectionMap = PersistStore(state => state.collectionsMap)

  function disambiguateLabel(key, value) {
    switch (key) {
      case 'severity':
        return (value).map((val) => `${func.toSentenceCase(val)} severity`).join(', ');
      case "apiCollectionId": 
        return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
      default:
        return value;
    }
  }

  filters = func.getCollectionFilters(filters)

const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[4]);
const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
}

const startTimestamp = getTimeEpoch("since")
const endTimestamp = getTimeEpoch("until") + 86400


const [loading, setLoading] = useState(true);
const [currentTab, setCurrentTab] = useState("one_time");
const [updateTable, setUpdateTable] = useState(false);
const [countMap, setCountMap] = useState({});
const [selected, setSelected] = useState(1);

const [severityCountMap, setSeverityCountMap] = useState({
  HIGH: {text : 0, color: func.getColorForCharts("HIGH")},
  MEDIUM: {text : 0, color: func.getColorForCharts("MEDIUM")},
  LOW: {text : 0, color: func.getColorForCharts("LOW")},
})
const [subCategoryInfo, setSubCategoryInfo] = useState({})
const [collapsible, setCollapsible] = useState(true)
const [hasUserInitiatedTestRuns, setHasUserInitiatedTestRuns] = useState(false)


const refreshSummaries = () =>{
  setTimeout(() => {
    setUpdateTable(!updateTable)
  }, 5000)
}

function processData(testingRuns, latestTestingRunResultSummaries, cicd){
  let testRuns = transform.prepareTestRuns(testingRuns, latestTestingRunResultSummaries, cicd, true);
  const updatedRuns = testRuns.filter((test) => test.orderPriority !== 1)
  if(updatedRuns.length !== testRuns.length){
    refreshSummaries()
  }
  return testRuns;
}

  async function fetchTableData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
    setLoading(true);
    let ret = [];
    let total = 0;
    

    switch (currentTab) {

      case "ci_cd":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "CI_CD",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries, true);
          total = testingRunsCount;
        });
        break;
      case "scheduled":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "RECURRING",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      case "one_time":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "ONE_TIME",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      case "continuous_testing":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "CONTINUOUS_TESTING",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      default:
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, null,queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
    }

    // show the running tests at the top.
    ret = ret.sort((a, b) => (b.orderPriority === 1) ?  1 : ( (a.orderPriority === 1) ? -1 : 0 ));
    // we send the test if any summary of the test is in the filter.
    ret = ret.filter((item) => {
      if(filters.severity && filters.severity.length > 0){
        return item.severityStatus.some(x => filters.severity.includes(x.toUpperCase()))
      } 
      return true
    })

    setLoading(false);
    return { value: ret, total: total };

  }

  const fetchCountsMap = async() => {
    await api.getCountsMap(startTimestamp, endTimestamp).then((resp)=>{
      setCountMap(resp)
    })
  }

  const fetchSummaryInfo = async()=>{
    await api.getSummaryInfo(startTimestamp, endTimestamp).then((resp)=>{
      const severityObj = transform.convertSubIntoSubcategory(resp)
      setSubCategoryInfo(severityObj.subCategoryMap)
      const severityMap = severityObj.countMap;
      let tempMap = JSON.parse(JSON.stringify(severityCountMap))
      Object.keys(tempMap).forEach((key) => {
        tempMap[key].text = severityMap[key]
      })
      setSeverityCountMap(tempMap)
    })
  }

  const definedTableTabs = ['All', 'One time', 'Continuous Testing', 'Scheduled', 'CI/CD']
  const initialCount = [countMap['allTestRuns'], countMap['oneTime'], countMap['continuous'], countMap['scheduled'], countMap['cicd']]

  const { tabsInfo } = useTable()
  const tableCountObj = func.getTabsCount(definedTableTabs, {}, initialCount)
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setCurrentTab, currentTab, tabsInfo)

  const fetchTotalCount = () =>{
    setLoading(true)
    api.getUserTestRuns().then((resp)=> {
      setHasUserInitiatedTestRuns(resp)
    })
    setLoading(false)
    
  }

  useEffect(()=>{
    fetchTotalCount()
    fetchCountsMap()
    fetchSummaryInfo()
  },[currDateRange])

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setTimeout(()=>{
        setLoading(false)
    },200)
}

const iconSource = collapsible ? ChevronUpMinor : ChevronDownMinor
const SummaryCardComponent = () =>{
  let totalVulnerabilities = severityCountMap?.HIGH?.text + severityCountMap?.MEDIUM?.text +  severityCountMap?.LOW?.text 
  return(
    <LegacyCard>
      <LegacyCard.Section title={<Text fontWeight="regular" variant="bodySm" color="subdued">Vulnerabilities</Text>}>
        <HorizontalStack align="space-between">
          <Text fontWeight="semibold" variant="bodyMd">Found {totalVulnerabilities} vulnerabilities in total</Text>
          <Button plain monochrome icon={iconSource} onClick={() => setCollapsible(!collapsible)} />
        </HorizontalStack>
        {totalVulnerabilities > 0 ? 
        <Collapsible open={collapsible} transition={{duration: '500ms', timingFunction: 'ease-in-out'}}>
          <LegacyCard.Subsection>
            <Box paddingBlockStart={3}><Divider/></Box>
            <HorizontalGrid columns={2} gap={6}>
              <ChartypeComponent navUrl={"/dashboard/issues/"} data={subCategoryInfo} title={"Categories"} isNormal={true} boxHeight={'250px'}/>
              <ChartypeComponent data={severityCountMap} reverse={true} title={"Severity"} charTitle={totalVulnerabilities} chartSubtitle={"Total Vulnerabilities"}/>
            </HorizontalGrid>

          </LegacyCard.Subsection>
        </Collapsible>
        : null }
      </LegacyCard.Section>
    </LegacyCard>
  )
}
  const promotedBulkActions = (selectedTestRuns) => { 
    return [
    {
      content: <div data-testid="delete_result_button">{`Delete ${selectedTestRuns.length} test run${selectedTestRuns.length ===1 ? '' : 's'}`}</div>,
      onAction: async() => {
        await api.deleteTestRuns(selectedTestRuns);
        func.setToast(true, false, <div data-testid="delete_success_message">{`${selectedTestRuns.length} test run${selectedTestRuns.length > 1 ? "s" : ""} deleted successfully`}</div>)
        window.location.reload();
      },
    },
  ]};

  const key = currentTab + startTimestamp + endTimestamp;
const coreTable = (
<GithubServerTable
    key={key}
    pageLimit={50}
    fetchData={fetchTableData}
    sortOptions={sortOptions} 
    resourceName={resourceName} 
    filters={filters}
    disambiguateLabel={disambiguateLabel} 
    headers={headers}
    getActions = {(item) => transform.getActions(item)}
    hasRowActions={true}
    loading={loading}
    getStatus={func.getTestResultStatus}
    tableTabs={tableTabs}
    onSelect={handleSelectedTab}
    selected={selected}
    mode={IndexFiltersMode.Default}
    headings={headers}
    useNewRow={true}
    condensedHeight={true}
    promotedBulkActions={promotedBulkActions}
    selectable= {true}
    callFromOutside={updateTable}
  />   
)

const components = !hasUserInitiatedTestRuns ? [<SummaryCardComponent key={"summary"}/>,<TestrunsBannerComponent key={"banner-comp"}/>, coreTable] : [<SummaryCardComponent key={"summary"}/>, coreTable]
  return (
    <PageWithMultipleCards
      title={
        <TitleWithInfo
          titleText={"Test results"}
          tooltipContent={"See testing run results along with compact summary of issues."}
        />
      }
      isFirstPage={true}
      components={components}
      primaryAction={<DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>}
    />
  );
}

export default TestRunsPage
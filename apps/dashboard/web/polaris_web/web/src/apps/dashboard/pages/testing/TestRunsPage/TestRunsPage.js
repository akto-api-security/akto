import GithubServerTable from "../../../components/tables/GithubServerTable";
import {Text,IndexFiltersMode} from '@shopify/polaris';
import api from "../api";
import testingApi from "../../testing/api";
import { useEffect, useReducer, useState } from 'react';
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import func from "@/util/func"
import { CellType } from "../../../components/tables/rows/GithubRow";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import {produce} from "immer"
import values from "@/util/values";
import {TestrunsBannerComponent} from "./TestrunsBannerComponent";
import useTable from "../../../components/tables/TableContext";
import PersistStore from "../../../../main/PersistStore";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";
import SummaryCardComponent from "./SummaryCardComponent";
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

const headers = [
  {
    text:mapLabel("Test", getDashboardCategory()) + " name",
    title: mapLabel("Test", getDashboardCategory()) + " run name",
    value:"testName",
    itemOrder:1,
  },
  {
    text: mapLabel("Number of tests", getDashboardCategory()),
    title: mapLabel("Number of tests", getDashboardCategory()),
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
    text: 'Scan frequency',
    title: 'Scan frequency',
    value: 'scan_frequency',
    type: CellType.TEXT,
  },
  {
    text: 'Total Apis',
    title: mapLabel("Total endpoints", getDashboardCategory()),
    value: 'total_apis',
    type: CellType.TEXT
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
  singular: mapLabel("Test", getDashboardCategory()) + " run",
  plural: mapLabel("Test", getDashboardCategory()) + " runs",
};

let filters = [
  {
    key: 'severity',
    label: 'Severity',
    title: 'Severity',
    choices: [
      { label: "Critical", value: "CRITICAL" },
      { label: "High", value: "HIGH" }, 
      { label: "Medium", value: "MEDIUM" },
      { label: "Low", value: "LOW" }
    ]
  },
  {
    key: 'apiCollectionId',
    label: mapLabel('API', getDashboardCategory()) + ' collection name',
    title: mapLabel('API', getDashboardCategory()) + ' collection name',
    choices: [],
},
]

function TestRunsPage(props) {

  const {showOnlyTable, scopeApiCollectionIds } = props;

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

const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
}

const startTimestamp = getTimeEpoch("since")
const endTimestamp = getTimeEpoch("until") + 86400


const [loading, setLoading] = useState(true);
const [updateTable, setUpdateTable] = useState("");
const [countMap, setCountMap] = useState({});

const definedTableTabs = ['All', 'One time', mapLabel("Continuous Testing", getDashboardCategory()), 'Scheduled', 'CI/CD']
const initialCount = [countMap['allTestRuns'], countMap['oneTime'], countMap['continuous'], countMap['scheduled'], countMap['cicd']]

const { tabsInfo } = useTable()
const tableSelectedTab = PersistStore.getState().tableSelectedTab[window.location.pathname]
const initialSelectedTab = tableSelectedTab || "one_time";
const [currentTab, setCurrentTab] = useState(initialSelectedTab);
let initialTabIdx = func.getTableTabIndexById(1, definedTableTabs, initialSelectedTab)
const [selected, setSelected] = useState(initialTabIdx)

const tableCountObj = func.getTabsCount(definedTableTabs, {}, initialCount)
const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setCurrentTab, currentTab, tabsInfo)

const [severityMap, setSeverityMap] = useState({})
const [subCategoryInfo, setSubCategoryInfo] = useState({})
const [collapsible, setCollapsible] = useState(true)
const [hasUserInitiatedTestRuns, setHasUserInitiatedTestRuns] = useState(false)
const [totalNumberOfTests, setTotalNumberOfTests] = useState(0)

  async function fetchTableData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
    setLoading(true);
    let ret = [];
    let total = 0;
    
    if (showOnlyTable) {
      if(!filters["apiCollectionId"]){
        filters["apiCollectionId"] = scopeApiCollectionIds ? scopeApiCollectionIds : []
      }
    }

    switch (currentTab) {

      case "ci_cd":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "CI_CD",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = transform.processData(testingRuns, latestTestingRunResultSummaries, true);
          total = testingRunsCount;
        });
        break;
      case "scheduled":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "RECURRING",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = transform.processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      case "one_time":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "ONE_TIME",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = transform.processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      case "continuous_testing":
      case "continuous_scanning":
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, "CONTINUOUS_TESTING",queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = transform.processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      default:
        await api.fetchTestingDetails(
          startTimestamp, endTimestamp, sortKey, sortOrder, skip, limit, filters, null,queryValue
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = transform.processData(testingRuns, latestTestingRunResultSummaries);
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

    // Calculate total number of tests
    const sumOfTests = ret.reduce((sum, item) => {
      const numTests = item?.number_of_tests;
      return sum + (typeof numTests === 'number' ? numTests : 0);
    }, 0);
    setTotalNumberOfTests(sumOfTests);

    setLoading(false);
    return { value: ret, total: total };

  }

  const fetchCountsMap = async () => {
    let filters = {}
    if (showOnlyTable) {
      filters["apiCollectionId"] = scopeApiCollectionIds || []
    }
    await api.getCountsMap(startTimestamp, endTimestamp, filters).then((resp) => {
      setCountMap(resp)
    })
  }

  const fetchSummaryInfo = async()=>{
    await api.getSummaryInfo(startTimestamp, endTimestamp).then((resp)=>{
      const severityObj = transform.convertSubIntoSubcategory(resp)
      setSubCategoryInfo(severityObj.subCategoryMap)
    })
    await testingApi.fetchSeverityInfoForIssues({}, [], 0).then(({ severityInfo }) => {
      const countMap = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }

      if (severityInfo && severityInfo != undefined && severityInfo != null && severityInfo instanceof Object) {
          for (const apiCollectionId in severityInfo) {
              let temp = severityInfo[apiCollectionId]
              for (const key in temp) {
                  countMap[key] += temp[key]
              }
          }
      }

      const result = {
          "CRITICAL": {
              "text": countMap.CRITICAL || 0,
              "color": func.getHexColorForSeverity("CRITICAL"),
              "filterKey": "Critical"
          },
          "HIGH": {
              "text": countMap.HIGH || 0,
              "color": func.getHexColorForSeverity("HIGH"),
              "filterKey": "High"
          },
          "MEDIUM": {
              "text": countMap.MEDIUM || 0,
              "color": func.getHexColorForSeverity("MEDIUM"),
              "filterKey": "Medium"
          },
          "LOW": {
              "text": countMap.LOW || 0,
              "color": func.getHexColorForSeverity("LOW"),
              "filterKey": "Low"
          }
      }

      setSeverityMap(result)
    })
  }

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


  const handleTestRunDeletion = async (selectedTestRuns) => {
    await api.deleteTestRuns(selectedTestRuns);
    func.setToast(true, false, <div data-testid="delete_success_message">{`${selectedTestRuns.length} test run${selectedTestRuns.length > 1 ? "s" : ""} deleted successfully`}</div>)
    window.location.reload();
  }
  const promotedBulkActions = (selectedTestRuns) => {
    return [
      {
        content: <div data-testid="delete_result_button">{`Delete ${selectedTestRuns.length} test run${selectedTestRuns.length === 1 ? '' : 's'}`}</div>,
        onAction: () => {
          const deleteConfirmationMessage = `Are you sure, you want to delete test run${func.addPlurality(selectedTestRuns.length)}?`
          func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => handleTestRunDeletion(selectedTestRuns))
        },
      },
    ]
  };

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
    lastColumnSticky={true}
  />   
)

if (showOnlyTable) {
  return coreTable
}

const components = !hasUserInitiatedTestRuns ? [
  <SummaryCardComponent 
    key={"summary"}
    severityMap={severityMap}
    subCategoryInfo={subCategoryInfo}
    collapsible={collapsible}
    setCollapsible={setCollapsible}
    startTimestamp={startTimestamp}
    endTimestamp={endTimestamp}
  />,
  <TestrunsBannerComponent key={"banner-comp"}/>, 
  coreTable
] : [
  <SummaryCardComponent 
    key={"summary"}
    severityMap={severityMap}
    subCategoryInfo={subCategoryInfo}
    collapsible={collapsible}
    setCollapsible={setCollapsible}
    startTimestamp={startTimestamp}
    endTimestamp={endTimestamp}
  />, 
  coreTable
]
  return (
    <PageWithMultipleCards
      title={
        <TitleWithInfo
          titleText={mapLabel('Test results', getDashboardCategory())}
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
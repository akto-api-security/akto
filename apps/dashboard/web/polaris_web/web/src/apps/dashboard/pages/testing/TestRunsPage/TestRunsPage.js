import GithubServerTable from "../../../components/tables/GithubServerTable";
import {
  Text,
  Card} from '@shopify/polaris';
import {
  CircleCancelMajor,
  CalendarMinor,
  ReplayMinor,
  NoteMinor,
  PlayCircleMajor,
  PlayMinor,
  ClockMinor
} from '@shopify/polaris-icons';
import api from "../api";
import { useState } from 'react';
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import func from "@/util/func"
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";

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
    text:"",
    value:"icon",
    itemOrder:0
  },
  {
    text:"Text name",
    value:"name",
    itemOrder:1
  },
  {
    text:"Severity",
    value: 'severity',
    filterKey:"severityStatus",
    itemOrder:2,
  },
  {
    text: "Number of tests",
    value: "number_of_tests_str",
    itemOrder: 3,
    icon: NoteMinor,
  },
  {
    text: 'Run type',
    value: 'run_type',
    itemOrder: 3,
    icon: PlayCircleMajor
  },
  {
    text: 'Run time',
    value: 'run_time',
    itemOrder: 3,
    icon: ClockMinor
  },
]

const sortOptions = [
  { label: 'Run time', value: 'endTimestamp asc', directionLabel: 'Newest run', sortKey: 'endTimestamp' },
  { label: 'Run time', value: 'endTimestamp desc', directionLabel: 'Oldest run', sortKey: 'endTimestamp' }
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
  }
]

function disambiguateLabel(key, value) {
  switch (key) {
    case 'severity':
      return (value).map((val) => `${func.toSentenceCase(val)} severity`).join(', ');
    case "dateRange":
      return value.since.toDateString() + " - " + value.until.toDateString();
    default:
      return value;
  }
}

function TestRunsPage() {

  const stopTest = (hexId) =>{
    api.stopTest(hexId).then((resp) => {
      func.setToast(true, false, "Test run stopped")
    }).catch((resp) => {
      func.setToast(true, true, "Unable to stop test run")
    });
  }

  const rerunTest = (hexId) =>{
    api.rerunTest(hexId).then((resp) => {
      func.setToast(true, false, "Test re-run")
      setTimeout(() => {
        refreshSummaries();
      }, 2000)
    }).catch((resp) => {
      func.setToast(true, true, "Unable to re-run test")
    });
  }

const getActionsList = (hexId) => {
  return [
  {
      content: 'Schedule test',
      icon: CalendarMinor,
      onAction: () => {console.log("schedule test function")},
  },
  {
      content: 'Re-run',
      icon: ReplayMinor,
      onAction: () => {rerunTest(hexId || "")},
  },
  {
      content: 'Add to CI/CD pipeline',
      icon: PlayMinor,
      onAction: () => {window.open('https://docs.akto.io/testing/run-tests-in-cicd', '_blank');},
  },
  {
      content: 'Stop',
      icon: CircleCancelMajor,
      destructive:true,
      onAction: () => {stopTest(hexId || "")},
      disabled: true,
  }
]}

function getActions(item){
  let arr = []
  let section1 = {items:[]}
  let actionsList = getActionsList(item.id);
  if(item['run_type'] === 'One-time'){
    // section1.items.push(actionsList[0])
  }else{
    section1.items.push(actionsList[1])
  }

  if(item['run_type'] === 'CI/CD'){
    // section1.items.push(actionsList[0])
  }else{
    section1.items.push(actionsList[2])
  }
  
  if(item['orderPriority'] === 1 || item['orderPriority'] === 2){
      actionsList[3].disabled = false
  }else{
      actionsList[3].disabled = true
  }

  arr.push(section1)
  let section2 = {items:[]}
  section2.items.push(actionsList[3]);
  arr.push(section2);
  return arr
}

const [loading, setLoading] = useState(true);
const [currentTab, setCurrentTab] = useState("onetime");
const [updateTable, setUpdateTable] = useState(false);

const checkIsTestRunning = (testingRuns) => {
  let val = false
  testingRuns.forEach(element => {
    if(element.orderPriority == 1){
      val = true
    }
  });
  return val ;
}

const refreshSummaries = () =>{
  setTimeout(() => {
    setUpdateTable(!updateTable);
  }, 2000)
}

function processData(testingRuns, latestTestingRunResultSummaries, cicd){
  let testRuns = transform.prepareTestRuns(testingRuns, latestTestingRunResultSummaries, cicd);
  if(checkIsTestRunning(testRuns)){
    refreshSummaries();
  }
  return testRuns;
}

  async function fetchTableData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
    setLoading(true);
    let ret = [];
    let total = 0;
    let now = func.timeNow()
    let dateRange = filters['dateRange'] || false;
    delete filters['dateRange']
    let startTimestamp = 0;
    let endTimestamp = func.timeNow()
    if (dateRange) {
      startTimestamp = Math.floor(Date.parse(dateRange.since) / 1000);
      endTimestamp = Math.floor(Date.parse(dateRange.until) / 1000);
      filters.endTimestamp = [startTimestamp, endTimestamp]
    }

    switch (currentTab) {

      case "cicd":
        await api.fetchTestingDetails({
          startTimestamp: 0, endTimestamp: 0, fetchCicd: true, sortKey, sortOrder, skip, limit, filters
        }).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries, true);
          total = testingRunsCount;
        });
        break;
      case "recurring":
        await api.fetchTestingDetails({
          startTimestamp: 0, endTimestamp: 0, fetchCicd: false, sortKey, sortOrder, skip, limit, filters
        }).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      case "onetime":
        await api.fetchTestingDetails({
          startTimestamp: now - func.recencyPeriod, endTimestamp: now, fetchCicd: false, sortKey, sortOrder, skip, limit, filters
        }).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
    }

    ret = ret.sort((a, b) => (a.orderPriority > b.orderPriority) ? 1 : ((b.orderPriority > a.orderPriority) ? -1 : 0));
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

const coreTable = (
<GithubServerTable
    key={updateTable}
    pageLimit={50}
    fetchData={fetchTableData}
    sortOptions={sortOptions} 
    resourceName={resourceName} 
    filters={filters}
    hideQueryField={true}
    calenderFilter={true}
    calenderLabel={"Last run"}
    disambiguateLabel={disambiguateLabel} 
    headers={headers}
    getActions = {getActions}
    hasRowActions={true}
    loading={loading}
    getStatus={func.getTestResultStatus}
  />   
)

const OnetimeTable = {
  id:  'onetime',
  content: "One time",
  component: (
    coreTable
  )
}

const CicdTable = {
  id:  'cicd',
  content: "CI/CD",
  component: (
    coreTable
  )
}

const RecurringTable = {
  id:  'recurring',
  content: "Recurring",
  component: (
    coreTable
  )
}

function handleCurrTab(tab) {
  setCurrentTab(tab.id)
}

const TestTabs = (
  <Card padding={"0"} key="tabs">
    <LayoutWithTabs
      key="tabs"
      tabs={[OnetimeTable, CicdTable, RecurringTable ]}
      currTab={handleCurrTab}
    />
  </Card>
)

const components = [ TestTabs]

  return (
    <PageWithMultipleCards
    title={<Text variant="headingLg" fontWeight="semibold">Test results</Text>}
    isFirstPage={true}
    components={components}
    />
  );
}

export default TestRunsPage
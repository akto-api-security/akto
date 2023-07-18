import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {
  Text,
  Button} from '@shopify/polaris';
import {
  CircleCancelMinor,
  CalendarMinor,
  ReplayMinor,
  FraudProtectMinor,
  PlayMinor,
  ClockMinor
} from '@shopify/polaris-icons';
import api from "../api";
import Store from "../../../store";
import TestingStore from "../testingStore";
import { useEffect, useState } from 'react';
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";

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
    icon: FraudProtectMinor,
  },
  {
    text: 'Run type',
    value: 'run_type',
    itemOrder: 3,
    icon: PlayMinor
  },
  {
    text: 'Run time',
    value: 'run_time',
    itemOrder: 3,
    icon: ClockMinor
  },
]

const sortOptions = [
  { label: 'Run time', value: 'time asc', directionLabel: 'Newest run', sortKey: 'scheduleTimestamp' },
  { label: 'Run time', value: 'time desc', directionLabel: 'Oldest run', sortKey: 'scheduleTimestamp' },
  { label: 'Severity', value: 'severity asc', directionLabel: 'Highest severity', sortKey: 'total_severity' },
  { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest severity', sortKey: 'total_severity' },
];

const resourceName = {
  singular: 'Test run',
  plural: 'Test runs',
};

let filters = [
  {
    key: 'severityStatus',
    label: 'Severity',
    title: 'Severity',
    choices: [],
  },
  {
    key: 'runTypeStatus',
    label: 'Run type',
    title: 'Run type',
    choices: [],
  }
]

function disambiguateLabel(key, value) {
  switch (key) {
    case 'severityStatus':
      return (value).map((val) => `${val} severity`).join(', ');
    case 'runTypeStatus':
      return (value).map((val) => `${val}`).join(', ');
    default:
      return value;
  }
}

function TestRunsPage() {

  const setToastConfig = Store(state => state.setToastConfig)
  const setToast = (isActive, isError, message) => {
      setToastConfig({
        isActive: isActive,
        isError: isError,
        message: message
      })
  }

  const stopTest = (hexId) =>{
    api.stopTest(hexId).then((resp) => {
      setToast(true, false, "Test run stopped")
    }).catch((resp) => {
      setToast(true, true, "Unable to stop test run")
    });
  }

  const rerunTest = (hexId) =>{
    api.rerunTest(hexId).then((resp) => {
      setToast(true, false, "Test re-run")
    }).catch((resp) => {
      setToast(true, true, "Unable to re-run test")
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
      icon: CircleCancelMinor,
      destructive:true,
      onAction: () => {stopTest(hexId || "")},
      disabled: true,
  }
]}

function getActions(item){
  let arr = []
  let section1 = {items:[]}
  let actionsList = getActionsList(item.hexId);
  if(item['run_type'] === 'One-time'){
    section1.items.push(actionsList[0])
  }else{
    section1.items.push(actionsList[1])
  }

  if(item['run_type'] === 'CI/CD'){
    section1.items.push(actionsList[0])
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

const storeSetTestRuns = TestingStore(state => state.setTestRuns);
const testRuns = TestingStore(state => state.testRuns);
const [loading, setLoading] = useState(true);

useEffect(()=>{
  async function fetchData () {
    await api.fetchTestRunTableInfo().then(({testingRuns, latestTestingRunResultSummaries}) => {
      let testRuns = transform.prepareTestRuns(testingRuns, latestTestingRunResultSummaries);
      storeSetTestRuns(testRuns);
      setLoading(false);
    })
  }
  fetchData();
}, [])

  return (
    <PageWithMultipleCards
    title={
      <Text as="div" variant="headingLg">
           Test results
         </Text>
    }
    primaryAction={<Button primary>New test run</Button>}
    components={[
      <GithubSimpleTable 
        key="table"
        data={testRuns} 
        sortOptions={sortOptions} 
        resourceName={resourceName} 
        filters={filters}
        disambiguateLabel={disambiguateLabel} 
        headers={headers}
        getActions = {getActions}
        hasRowActions={true}
        loading={loading}
      />
    ]}
    />
  );
}

export default TestRunsPage
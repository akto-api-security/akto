import GithubTable from "../../../components/tables/GithubTable"
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack} from '@shopify/polaris';
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
import PageWithMultipleCards from "../PageWithMultipleCards";

let headers = [
  {
    icon: {
      text: "",
      value: "icon",
      row_order: 0,
    },
    name: {
      text: "Test name",
      value: "name",
      item_order: 0,

    }
  },
  {
    severityList: {
      text: 'Severity',
      value: 'severity',
      item_order: 1,
    }
  },
  {
    icon: {
      text: "",
      value: "",
      row_order: 0,
    },
    details: [
      {
        text: "Number of tests",
        value: "number_of_tests_str",
        item_order: 2,
        icon: FraudProtectMinor,
      },
      {
        text: 'Run type',
        value: 'run_type',
        item_order: 2,
        icon: PlayMinor
      },
      {
        text: 'Run time',
        value: 'run_time',
        item_order: 2,
        sortKey: 'run_time_epoch',
        icon: ClockMinor
      },
    ]
  }
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
    availableChoices: new Set()
  },
  {
    key: 'runTypeStatus',
    label: 'Run type',
    title: 'Run type',
    choices: [],
    availableChoices: new Set()
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
      filters = transform.prepareFilters(testRuns,filters);
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
      <GithubTable 
        data={testRuns} 
        sortOptions={sortOptions} 
        resourceName={resourceName} 
        filters={filters}
        disambiguateLabel={disambiguateLabel} 
        headers={headers}
        getActions = {getActions}
        hasRowActions={true}
        loading={loading}
        page={1}
      />
    ]}
    />
  );
}

export default TestRunsPage
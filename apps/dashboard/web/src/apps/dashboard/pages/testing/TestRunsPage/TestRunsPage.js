import GithubTable from "../../../components/tables/GithubTable"
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack} from '@shopify/polaris';
import {
  CircleCancelMinor,
  CircleTickMinor,
  CalendarMinor,
  ReplayMinor,
  FraudProtectMinor,
  PlayMinor,
  ClockMinor
} from '@shopify/polaris-icons';
import api from "../api";
import func from '@/util/func';
import Store from "../../../store";

import { useState, useCallback, useEffect } from 'react';

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

const MAX_SEVERITY_THRESHOLD = 100000;

function getOrderPriority(state){
    switch(state._name){
        case "RUNNING": return 1;
        case "SCHEDULED": return 2;
        case "STOPPED": return 4;
        default: return 3;
    }
}

function getTestingRunType(testingRun, testingRunResultSummary){
    if(testingRunResultSummary.metadata!=null){
        return 'CI/CD';
    }
    if(testingRun.periodInSeconds==0){
        return 'One-time'
    }
    return 'Recurring';
}

function getTotalSeverity(countIssues){
    let ts = 0;
    if(countIssues==null){
        return 0;
    }
    ts = MAX_SEVERITY_THRESHOLD*(countIssues['High']*MAX_SEVERITY_THRESHOLD + countIssues['Medium']) + countIssues['Low']
    return ts;
}

function getRuntime(scheduleTimestamp ,endTimestamp, state){
    if(state._name=='RUNNING'){
        return "Currently running";
    }
    const currTime = Date.now();
    if(endTimestamp == -1){
      if(currTime > scheduleTimestamp){
        return "Was scheduled for " + func.prettifyEpoch(scheduleTimestamp)
    } else {
        return "Next run in " + func.prettifyEpoch(scheduleTimestamp)
    }
}
return 'Last run ' + func.prettifyEpoch(endTimestamp);
}

function getAlternateTestsInfo(state){
    switch(state._name){
        case "RUNNING": return "Tests are still running";
        case "SCHEDULED": return "Tests have been scheduled";
        case "STOPPED": return "Tests have been stopper";
        default: return "Information unavailable";
    }
}

function getTestsInfo(testResultsCount, state){
    return (testResultsCount == null) ? getAlternateTestsInfo(state) : testResultsCount + " tests"
}

const sortOptions = [
  { label: 'Severity', value: 'severity asc', directionLabel: 'Highest severity', sortKey: 'total_severity' },
  { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest severity', sortKey: 'total_severity' },
  { label: 'Run time', value: 'time asc', directionLabel: 'Newest run', sortKey: 'scheduleTimestamp' },
  { label: 'Run time', value: 'time desc', directionLabel: 'Oldest run', sortKey: 'scheduleTimestamp' },
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

  const [testRuns, setTestRuns] = useState([])

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

useEffect(()=>{
  api.fetchTestRunTableInfo().then(({testingRuns, latestTestingRunResultSummaries}) => {
    // console.log(testingRuns, latestTestingRunResultSummaries)
    let testRuns = []
    testingRuns.forEach((data)=>{
      let obj={};
      let testingRunResultSummary = latestTestingRunResultSummaries[data['hexId']];
      if(testingRunResultSummary==null){
          testingRunResultSummary = {};
      }
      if(testingRunResultSummary.countIssues!=null){
          testingRunResultSummary.countIssues['High'] = testingRunResultSummary.countIssues['HIGH']
          testingRunResultSummary.countIssues['Medium'] = testingRunResultSummary.countIssues['MEDIUM']
          testingRunResultSummary.countIssues['Low'] = testingRunResultSummary.countIssues['LOW']
          delete testingRunResultSummary.countIssues['HIGH']
          delete testingRunResultSummary.countIssues['MEDIUM']
          delete testingRunResultSummary.countIssues['LOW']
      }
      obj['hexId'] = data.hexId;
      obj['orderPriority'] = getOrderPriority(data.state)
      obj['icon'] = func.getTestingRunIcon(data.state);
      obj['name'] = data.name || "Test"
      obj['number_of_tests_str'] = getTestsInfo(testingRunResultSummary.testResultsCount, data.state)
      obj['run_type'] = getTestingRunType(data, testingRunResultSummary);
      obj['run_time_epoch'] = data.endTimestamp == -1 ? data.scheduleTimestamp : data.endTimestamp
      obj['scheduleTimestamp'] = data.scheduleTimestamp
      obj['run_time'] = getRuntime(data.scheduleTimestamp ,data.endTimestamp, data.state)
      obj['severity'] = func.getSeverity(testingRunResultSummary.countIssues)
      obj['total_severity'] = getTotalSeverity(testingRunResultSummary.countIssues);
      obj['severityStatus'] = func.getSeverityStatus(testingRunResultSummary.countIssues)
      obj['runTypeStatus'] = [obj['run_type']]
      filters.forEach((filter, index) => {
      let key = filter["key"]
        switch(key){
          case 'severityStatus' : obj["severityStatus"].map((item) => filter.availableChoices.add(item)); break;
          case 'runTypeStatus' : obj["runTypeStatus"].map((item) => filter.availableChoices.add(item)); 
        }
        filters[index] = filter
      })
      testRuns.push(obj);
  })
  setTestRuns(testRuns);
  filters.forEach((filter, index) => {
      let choiceList = []
      filter.availableChoices.forEach((choice) => {
        choiceList.push({label:choice, value:choice})
      })
      filters[index].choices = choiceList
    })  
  })
  
}, [])

  return (
    <VerticalStack gap="4">
      <HorizontalStack align="space-between" blockAlign="center">
        <Text as="div" variant="headingLg">
          Test results
        </Text>
        <Button primary>New test run</Button>
      </HorizontalStack>
      <GithubTable 
        data={testRuns} 
        sortOptions={sortOptions} 
        resourceName={resourceName} 
        filters={filters}
        disambiguateLabel={disambiguateLabel} 
        headers={headers}
        getActions = {getActions}
        hasRowActions={true}
        nextPage={"singleTestRunPage"}
        // func={pull_data}
      />
    </VerticalStack>
  );
}

export default TestRunsPage
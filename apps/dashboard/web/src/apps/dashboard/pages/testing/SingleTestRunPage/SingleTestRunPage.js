import GithubTable from "../../../components/tables/GithubTable"
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack,
  Icon,
  Badge,
  Box,
  Popover,
  ActionList
} from '@shopify/polaris';
import { saveAs } from 'file-saver'
import {
  MobileBackArrowMajor,
  SearchMinor,
  FraudProtectMinor,
  LinkMinor
} from '@shopify/polaris-icons';
import api from "../api";
import globalFunctions from '@/util/func';
import { useNavigate, Outlet } from "react-router-dom";

import { useParams } from 'react-router';
import { useState, useCallback, useEffect } from 'react';
import testingFunc from "../util/func";

let headers = [
  {
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
        text: "Detected time",
        value: "detected_time",
        item_order: 2,
        icon: SearchMinor,
      },
      {
        text: 'Test category',
        value: 'testCategory',
        item_order: 2,
        icon: FraudProtectMinor
      },
      {
        text: 'url',
        value: 'url',
        item_order: 2,
        icon: LinkMinor
      },
    ]
  }
]

const MAX_SEVERITY_THRESHOLD = 100000;

function getTotalSeverity(severity){
    if(severity==null || severity.length==0){
        return 0;
    }
    let ts = MAX_SEVERITY_THRESHOLD*((severity[0].confidence=='High')*MAX_SEVERITY_THRESHOLD + (severity[0].confidence=='Medium')) + (severity[0].confidence=='Low')
    // console.log(ts);
    return ts;
}

const sortOptions = [
  { label: 'Severity', value: 'severity asc', directionLabel: 'Highest severity', sortKey: 'total_severity' },
  { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest severity', sortKey: 'total_severity' },
  { label: 'Run time', value: 'time asc', directionLabel: 'Newest run', sortKey: 'endTimestamp' },
  { label: 'Run time', value: 'time desc', directionLabel: 'Oldest run', sortKey: 'endTimestamp' },
];

const resourceName = {
  singular: 'Test run result',
  plural: 'Test run results',
};

function disambiguateLabel(key, value) {
  switch (key) {
    case 'severityStatus':
      return (value).map((val) => `${val} severity`).join(', ');
    case 'apiFilter':
      return value.length + 'API' + (value.length==1 ? '' : 's')
    case 'categoryFilter':
    case 'testFilter':
      return (value).map((val) => val).join(', ');
    default:
      return value;
  }
}

let filters = [
  {
    key: 'severityStatus',
    label: 'Severity',
    title: 'Severity',
    choices: [],
    availableChoices: new Set()
  },
  {
    key: 'apiFilter',
    label: 'API',
    title: 'API',
    choices: [],
    availableChoices: new Set()
  },
  {
    key: 'categoryFilter',
    label: 'Category',
    title: 'Category',
    choices: [],
    availableChoices: new Set()
  },
  {
    key: 'testFilter',
    label: 'Test',
    title: 'Test',
    choices: [],
    availableChoices: new Set()
  }
]

function valToString(val) {
  if (val instanceof Set) {
      return [...val].join(" & ")
  } else {
      return val || "-"
  }
}

const downloadAsCSV = (data, trss) => {
  let headerTextToValueMap = Object.keys(data[0])

  let csv = headerTextToValueMap.join(",")+"\r\n"
  data.forEach(i => {
      csv += Object.values(headerTextToValueMap).map(h => valToString(i[h])).join(",") + "\r\n"
  })
  let blob = new Blob([csv], {
      type: "application/csvcharset=UTF-8"
  });
  saveAs(blob, (trss.hexId || "file") + ".csv");
}

const bulkActions = [
  {
    content: 'Edit tests',
    onAction: () => console.log('Todo: Edit tests'),
  },
  {
    content: 'Slack alert',
    onAction: () => console.log('Todo: Slack alert'),
  }
];

function SingleTestRunPage() {

  const [testRunResult, setTestRunResult] = useState([])
  const [testingRunResultSummary, setTestingRunResultSummary] = useState({});
  const params= useParams()

useEffect(()=>{

    const hexId = params.hexId;

    api.fetchAllSubCategories().then((resp) => {
      let subCategoryMap = {}
      resp.subCategories.forEach((x) => {
        subCategoryMap[x.name] = x
      })
      let subCategoryFromSourceConfigMap = {}
      resp.testSourceConfigs.forEach((x) => {
        subCategoryFromSourceConfigMap[x.id] = x
      })
      let testCategoryMap = {}
      resp.categories.forEach((x) => {
        testCategoryMap[x.name] = x
      })
      api.fetchTestingRunResultSummaries(hexId).then(({ testingRunResultSummaries }) => {
        setTestingRunResultSummary(testingRunResultSummaries[0])
        // console.log(testingRunResultSummaries[0])

        api.fetchTestingRunResults(testingRunResultSummaries[0].hexId).then(({ testingRunResults }) => {
          // console.log(testingRunResults)
          // console.log(subCategoryMap)
          let testRunResult = []
          testingRunResults.forEach((data) => {
            let obj = {};
            obj['hexId'] = data.hexId;
            // change this logic. breaks for fuzzing/nuclei tests.
            obj['name'] = subCategoryMap[data.testSubType].testName
            obj['detected_time'] = "Detected " + globalFunctions.prettifyEpoch(data.endTimestamp)
            obj["endTimestamp"] = data.endTimestamp
            obj['testCategory'] = testCategoryMap[data.testSuperType].shortName
            obj['url'] = "Detected in " + data.apiInfoKey.method + " " + data.apiInfoKey.url 
            // make Sentence case.
            obj['severity'] = data.vulnerable ? [{confidence : globalFunctions.toSentenceCase(testCategoryMap[data.testSuperType].severity._name)}] : []
            obj['total_severity'] = getTotalSeverity(obj['severity'])
            obj['severityStatus'] = obj["severity"].length > 0 ? [obj["severity"][0].confidence] : []
            obj['apiFilter'] = [data.apiInfoKey.method + " " + data.apiInfoKey.url]
            obj['categoryFilter'] = [testCategoryMap[data.testSuperType].shortName]
            obj['testFilter'] = [subCategoryMap[data.testSubType].testName]
            testRunResult.push(obj);
            filters.forEach((filter, index) => {
              let key = filter["key"]
                switch(key){
                  case 'severityStatus' : obj["severityStatus"].map((item) => filter.availableChoices.add(item)); break;
                  case 'apiFilter' : obj['apiFilter'].map((item) => filter.availableChoices.add(item)); break;
                  case 'categoryFilter' : obj['categoryFilter'].map((item) => filter.availableChoices.add(item)); break;
                  case 'testFilter' : obj['testFilter'].map((item) => filter.availableChoices.add(item)); break;

                }
                filters[index] = filter
              })

          })
          setTestRunResult(testRunResult);
          filters.forEach((filter, index) => {
            let choiceList = []
            filter.availableChoices.forEach((choice) => {
              choiceList.push({label:choice, value:choice})
            })
            filters[index].choices = choiceList
          })  
        })
      })
    }).catch((err) => {
      console.log(err);
})
  
}, [])

const [popoverActive, setPopoverActive] = useState(false);
const togglePopoverActive = useCallback(
    () => setPopoverActive((popoverActive) => !popoverActive),
    [],
);

const navigate = useNavigate();
function navigateBack(){
  navigate("/dashboard/testing/")
}

const promotedBulkActions = (selectedDataHexIds) => { 
  return [
  {
    content: 'Export',
    onAction: () => {
      downloadAsCSV(testRunResult.filter((data) => {return selectedDataHexIds.includes(data.hexId)}), testingRunResultSummary)
    },
  },
]};

  return (
    <VerticalStack gap="4">
      <HorizontalStack align="space-between" blockAlign="center">
        <HorizontalStack gap="4">
          <HorizontalStack blockAlign="start">
            <Box>
          <Button icon={MobileBackArrowMajor} onClick={navigateBack}></Button>
          </Box>
          </HorizontalStack>
          <VerticalStack gap="3">
            <HorizontalStack gap="2" align="start">
              <Box>
              <Icon color="primary" source={testingFunc.getTestingRunIcon(testingRunResultSummary.state) }></Icon>
              </Box>
              <Text variant='headingLg'>
                Test run name
              </Text>
              {testingFunc.getSeverity(testingRunResultSummary.countIssues)
              .map((item) =>
                <Badge key={item.confidence} status={testingFunc.getStatus(item)}>{item.count ? item.count : ""} {globalFunctions.toSentenceCase(item.confidence)}</Badge>
                )}
            </HorizontalStack>
            <Text color="subdued">
              {/* make an API call as in the /testing page and use data from there. */}
            Last scanned {globalFunctions.prettifyEpoch(testingRunResultSummary.endTimestamp)} for a duration of {testingRunResultSummary.endTimestamp - testingRunResultSummary.startTimestamp} seconds
            </Text>
          </VerticalStack>
        </HorizontalStack>
        <HorizontalStack gap="2">
        <Popover
          active={popoverActive}
          activator={<Button onClick={togglePopoverActive} monochrome removeUnderline plain disclosure>Actions</Button>}
          autofocusTarget="first-node"
          onClose={togglePopoverActive}
        >
        <ActionList
            actionRole="menuitem"
            items={[{content: 'Import'}, {content: 'Export'}]}
        />
      </Popover>
        <Button monochrome removeUnderline plain>Export</Button>
        </HorizontalStack>
      </HorizontalStack>
    <GithubTable 
    data={testRunResult} 
    sortOptions={sortOptions} 
    resourceName={resourceName} 
    filters={filters} 
    disambiguateLabel={disambiguateLabel} 
    headers={headers}
    getActions = {() => {}}
    selectable = {true}
    promotedBulkActions = {promotedBulkActions}
    bulkActions = {bulkActions}
  />
  </VerticalStack>
  );
}

export default SingleTestRunPage
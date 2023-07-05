import GithubTable from "../../../components/tables/GithubTable"
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack,
  Icon,
  Badge,
  Box,
} from '@shopify/polaris';
import { saveAs } from 'file-saver'
import {
  MobileBackArrowMajor,
  SearchMinor,
  FraudProtectMinor,
  LinkMinor
} from '@shopify/polaris-icons';
import api from "../api";
import func from '@/util/func';
import { useNavigate } from "react-router-dom";
import { useParams } from 'react-router';
import { useState, useEffect } from 'react';

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

function SingleTestRunPage() {

  const [testRunResults, setTestRunResults] = useState([])
  const [testingRunResultSummary, setTestingRunResultSummary] = useState({});
  const params= useParams()

useEffect(()=>{

    const hexId = params.hexId;
    filters.forEach((filter, index) => {
      filters[index].availableChoices = new Set()
      filters[index].choices = []
    })
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
          // console.log(testCategoryMap)
          // console.log(subCategoryMap)
          let testRunResults = []
          testingRunResults.forEach((data) => {
            let obj = {};
            obj['hexId'] = data.hexId;
            // change this logic. breaks for fuzzing/nuclei tests.
            obj['name'] = func.getRunResultSubCategory(data, subCategoryFromSourceConfigMap, subCategoryMap, "testName")
            obj['detected_time'] = "Detected " + func.prettifyEpoch(data.endTimestamp)
            obj["endTimestamp"] = data.endTimestamp
            obj['testCategory'] = func.getRunResultCategory(data, subCategoryMap, subCategoryFromSourceConfigMap, "shortName")
            obj['url'] = "Detected in " + data.apiInfoKey.method + " " + data.apiInfoKey.url 
            obj['severity'] = data.vulnerable ? [{confidence : func.toSentenceCase(func.getRunResultSeverity(data, subCategoryMap))}] : []
            obj['total_severity'] = getTotalSeverity(obj['severity'])
            obj['severityStatus'] = obj["severity"].length > 0 ? [obj["severity"][0].confidence] : []
            obj['apiFilter'] = [data.apiInfoKey.method + " " + data.apiInfoKey.url]
            obj['categoryFilter'] = [obj['testCategory']]
            obj['testFilter'] = [obj['name']]
            if(obj['name'] && obj['testCategory']){
              testRunResults.push(obj);
            }
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
          setTestRunResults(testRunResults);
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

const navigate = useNavigate();
function navigateBack(){
  navigate("/dashboard/testing/")
}

const promotedBulkActions = (selectedDataHexIds) => { 
  return [
  {
    content: 'Export',
    onAction: () => {
      downloadAsCSV(testRunResults.filter((data) => {return selectedDataHexIds.includes(data.hexId)}), testingRunResultSummary)
    },
  },
]};

  return (
    <VerticalStack gap="10">
      <HorizontalStack align="space-between" blockAlign="center">
        <HorizontalStack gap="4">
          <div style={{marginBottom:"auto"}}>
          <Button icon={MobileBackArrowMajor} onClick={navigateBack} textAlign="start" />
          </div>
          <VerticalStack gap="3">
            <HorizontalStack gap="2" align="start">
              <Box>
              <Icon color="primary" source={func.getTestingRunIcon(testingRunResultSummary.state) }></Icon>
              </Box>
              <Text variant='headingLg'>
                Test run name
              </Text>
              {func.getSeverity(testingRunResultSummary.countIssues)
              .map((item) =>
                <Badge key={item.confidence} status={func.getStatus(item)}>{item.count ? item.count : ""} {func.toSentenceCase(item.confidence)}</Badge>
                )}
            </HorizontalStack>
            <Text color="subdued">
              {/* make an API call as in the /testing page and use data from there. */}
            Last scanned {func.prettifyEpoch(testingRunResultSummary.endTimestamp)} for a duration of {testingRunResultSummary.endTimestamp - testingRunResultSummary.startTimestamp} seconds
            </Text>
          </VerticalStack>
        </HorizontalStack>
        <HorizontalStack gap="2">
        <Button monochrome removeUnderline plain onClick={() => downloadAsCSV(testRunResults, testingRunResultSummary)}>Export</Button>
        </HorizontalStack>
      </HorizontalStack>
    <GithubTable 
    data={testRunResults} 
    sortOptions={sortOptions} 
    resourceName={resourceName} 
    filters={filters} 
    disambiguateLabel={disambiguateLabel} 
    headers={headers}
    getActions = {() => {}}
    selectable = {true}
    promotedBulkActions = {promotedBulkActions}
  />
  </VerticalStack>
  );
}

export default SingleTestRunPage
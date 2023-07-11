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
import TestingStore from "../testingStore";
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";

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

function SingleTestRunPage() {

  const [testRunResults, setTestRunResults] = useState([])
  const selectedTestRun = TestingStore(state => state.selectedTestRun);
  const setSelectedTestRun = TestingStore(state => state.setSelectedTestRun);
  const subCategoryFromSourceConfigMap = TestingStore(state => state.subCategoryFromSourceConfigMap);
  const subCategoryMap = TestingStore(state => state.subCategoryMap);
  const params= useParams()
  const [loading, setLoading] = useState(true);

useEffect(()=>{
    const hexId = params.hexId;
    async function fetchData() {
      if(selectedTestRun==null || Object.keys(selectedTestRun)==0 || selectedTestRun.hexId != hexId){
        await api.fetchTestingRunResultSummaries(hexId).then(async ({ testingRun, testingRunResultSummaries }) => {
          let selectedTestRun = transform.prepareTestRun(testingRun, testingRunResultSummaries[0]);
            setSelectedTestRun(selectedTestRun);
          })
      } else if(Object.keys(subCategoryMap)!=0 && Object.keys(subCategoryFromSourceConfigMap)!=0){
        await api.fetchTestingRunResults(selectedTestRun.testingRunResultSummaryHexId).then(({ testingRunResults }) => {
          let testRunResults = transform.prepareTestRunResults(testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
          setTestRunResults(testRunResults)
          filters = transform.prepareFilters(testRunResults, filters);
          setLoading(false);
        })
      }
    }
    fetchData();
}, [selectedTestRun, subCategoryMap, subCategoryFromSourceConfigMap])

const navigate = useNavigate();
function navigateBack(){
  navigate("/dashboard/testing/")
}

const promotedBulkActions = (selectedDataHexIds) => { 
  return [
  {
    content: `Export ${selectedDataHexIds.length} record${selectedDataHexIds.length==1 ? '' : 's'}`,
    onAction: () => {
      func.downloadAsCSV(testRunResults.filter((data) => {return selectedDataHexIds.includes(data.hexId)}), selectedTestRun)
    },
  },
]};

  return (
    <PageWithMultipleCards
    title={
          <VerticalStack gap="3">
            <HorizontalStack gap="2" align="start">
              { selectedTestRun?.icon && <Box>
                <Icon color="primary" source={selectedTestRun.icon }></Icon>
              </Box>
              }
              <Text variant='headingLg'>
                {
                  selectedTestRun?.name || "Test run name"
                }
              </Text>
              {
                selectedTestRun?.severity && 
                selectedTestRun.severity
                .map((item) =>
                <Badge key={item.confidence} status={func.getStatus(item)}>
                  <Text fontWeight="regular">
                  {item.count ? item.count : ""} {func.toSentenceCase(item.confidence)}
                  </Text>
                </Badge>
                )}
            </HorizontalStack>
            <Text color="subdued" fontWeight="regular" variant="bodyMd">
              {
                selectedTestRun && 
                selectedTestRun?.pickedUpTimestamp < selectedTestRun?.run_time_epoch &&
                `Last scanned ${func.prettifyEpoch(selectedTestRun.run_time_epoch)} for a duration of ${selectedTestRun.run_time_epoch - selectedTestRun.pickedUpTimestamp} second${(selectedTestRun.run_time_epoch - selectedTestRun.pickedUpTimestamp)==1 ? '':'s'}`
              }
            </Text>
          </VerticalStack>
    }
    backAction = {{onAction:navigateBack}}
    primaryAction={<Button monochrome removeUnderline plain onClick={() => func.downloadAsCSV(testRunResults, selectedTestRun)}>Export</Button>}
    components = {[
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
      loading={loading}
      page={2}
    />
    ]}
    />
  );
}

export default SingleTestRunPage
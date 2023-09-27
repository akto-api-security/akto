import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack,
  Icon,
  Badge,
  Box,
  Tooltip,
} from '@shopify/polaris';
import {
  CircleTickMinor,
  ArchiveMinor,
  LinkMinor,
  ReplayMinor
} from '@shopify/polaris-icons';
import api from "../api";
import func from '@/util/func';
import { useParams } from 'react-router';
import { useState, useEffect } from 'react';
import TestingStore from "../testingStore";
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import WorkflowTestBuilder from "../workflow_test/WorkflowTestBuilder";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import TooltipText from "../../../components/shared/TooltipText";
import PersistStore from "../../../../main/PersistStore";

let headers = [
  {
    text: "Test name",
    value: "name",
    itemOrder: 1,

  },
  {
    text: 'Severity',
    value: 'severity',
    itemOrder: 2,
  },
  {
    text: "Detected time",
    value: "detected_time",
    itemOrder: 3,
    icon: CircleTickMinor,
  },
  {
    text: 'Test category',
    value: 'testCategory',
    itemOrder: 3,
    icon: ArchiveMinor
  },
  {
    text: 'url',
    value: 'url',
    itemOrder: 3,
    icon: LinkMinor
  },
]

const sortOptions = [
  { label: 'Severity', value: 'severity asc', directionLabel: 'Highest severity', sortKey: 'total_severity' },
  { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest severity', sortKey: 'total_severity' },
  { label: 'Run time', value: 'time asc', directionLabel: 'Newest run', sortKey: 'endTimestamp' },
  { label: 'Run time', value: 'time desc', directionLabel: 'Oldest run', sortKey: 'endTimestamp' },
];

const resourceName = {
  singular: 'test run result',
  plural: 'test run results',
};

function disambiguateLabel(key, value) {
  switch (key) {
    case 'severityStatus':
      return (value).map((val) => `${val} severity`).join(', ');
    case 'apiFilter':
      return value.length + 'API' + (value.length==1 ? '' : 's')
    case 'categoryFilter':
    case 'testFilter':
      return func.convertToDisambiguateLabelObj(value, null, 2)
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
  },
  {
    key: 'apiFilter',
    label: 'API',
    title: 'API',
    choices: [],
  },
  {
    key: 'categoryFilter',
    label: 'Category',
    title: 'Category',
    choices: [],
  },
  {
    key: 'testFilter',
    label: 'Test',
    title: 'Test',
    choices: [],
  }
]

function SingleTestRunPage() {

  const [testRunResults, setTestRunResults] = useState([])
  const [ selectedTestRun, setSelectedTestRun ] = useState({})
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const subCategoryMap = PersistStore(state => state.subCategoryMap);
  const params= useParams()
  const [loading, setLoading] = useState(false);
  const [tempLoading , setTempLoading] = useState(false)
  const [workflowTest, setWorkflowTest ] = useState(false);
  const hexId = params.hexId;

  async function fetchData(setData) {
    let localSelectedTestRun = {}
    await api.fetchTestingRunResultSummaries(hexId).then(async ({ testingRun, testingRunResultSummaries, workflowTest }) => {
      if(testingRun.testIdConfig == 1){
        setWorkflowTest(workflowTest);
      }
      localSelectedTestRun = transform.prepareTestRun(testingRun, testingRunResultSummaries[0]);
      if(setData){
        setSelectedTestRun(localSelectedTestRun);
      }
      if(localSelectedTestRun.testingRunResultSummaryHexId) {
          await api.fetchTestingRunResults(localSelectedTestRun.testingRunResultSummaryHexId).then(({ testingRunResults }) => {
            setTempLoading(false);
            let testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
            setTestRunResults(testRunResults)
          })
        }
      }) 
    return localSelectedTestRun;
}

  const refreshSummaries = () => {
    let intervalId = setInterval(async() => {
      let localSelectedTestRun = await fetchData(false);
      if(localSelectedTestRun.orderPriority !== 1){
        clearInterval(intervalId)
      }
    },2000)
  }

  useEffect(()=>{
    async function loadData(){
      setLoading(true);
      await fetchData(true);
      setLoading(false);
    }
    loadData();
    refreshSummaries();
  }, [])

const promotedBulkActions = (selectedDataHexIds) => { 
  return [
  {
    content: `Export ${selectedDataHexIds.length} record${selectedDataHexIds.length==1 ? '' : 's'}`,
    onAction: () => {
      func.downloadAsCSV(testRunResults.filter((data) => {return selectedDataHexIds.includes(data.id)}), selectedTestRun)
    },
  },
]};

  function getHeadingStatus(selectedTestRun) {

    if (selectedTestRun?.pickedUpTimestamp < selectedTestRun?.run_time_epoch) {
      return `Last scanned ${func.prettifyEpoch(selectedTestRun.run_time_epoch)} for a duration of 
      ${selectedTestRun.run_time_epoch - selectedTestRun.pickedUpTimestamp} second${(selectedTestRun.run_time_epoch - selectedTestRun.pickedUpTimestamp) === 1 ? '' : 's'}`
    }

    switch (selectedTestRun.orderPriority) {
      case 1:
        return "Test is running";
      case 4:
        return "Test has been scheduled";
      default:
        return "";
    }
  }

  const ResultTable = (
      <GithubSimpleTable
        key="table"
        data={testRunResults}
        sortOptions={sortOptions}
        resourceName={resourceName}
        filters={filters}
        disambiguateLabel={disambiguateLabel}
        headers={headers}
        selectable={true}
        promotedBulkActions={promotedBulkActions}
        loading={loading || tempLoading}
        getStatus={func.getTestResultStatus}
      />
  )

  const workflowTestBuilder = (
    <WorkflowTestBuilder
      key="workflow-test"
      endpointsList={[]}
      apiCollectionId={0}
      originalStateFromDb={workflowTest}
      defaultOpenResult={true}
      class={"white-background"}
    />
  )

  const components = [!workflowTest ? ResultTable : workflowTestBuilder];

  const rerunTest = (hexId) =>{
    api.rerunTest(hexId).then((resp) => {
      func.setToast(true, false, "Test re-run")
    }).catch((resp) => {
      func.setToast(true, true, "Unable to re-run test")
    });
  }

  const openVulnerabilityReport = () => {
    let summaryId = selectedTestRun.testingRunResultSummaryHexId
    window.open('/dashboard/testing/summary/' + summaryId, '_blank');
  }

  return (
    <PageWithMultipleCards
    title={
          <VerticalStack gap="3">
            <HorizontalStack gap="2" align="start">
              { selectedTestRun?.icon && <Box>
                <Icon color={selectedTestRun.iconColor} source={selectedTestRun.icon }></Icon>
              </Box>
              }
              <Box maxWidth="50vw">
                <TooltipText 
                  tooltip={selectedTestRun?.name} 
                  text={selectedTestRun?.name || "Test run name"} 
                  textProps={{variant:"headingLg"}}/>
              </Box>
              {
                selectedTestRun?.severity && 
                selectedTestRun.severity
                .map((item) =>
                <Badge key={item} status={func.getTestResultStatus(item)}>
                  <Text fontWeight="regular">
                    {item}
                  </Text>
                </Badge>
                )}
                <Tooltip content={"Re-run test"} hoverDelay={400}>
                  <Button icon={ReplayMinor} plain onClick={() => {rerunTest(hexId)}}/>
                </Tooltip>
            </HorizontalStack>
            <Text color="subdued" fontWeight="regular" variant="bodyMd">
              {
                getHeadingStatus(selectedTestRun)
              }
            </Text>
          </VerticalStack>
    }
    backUrl={`/dashboard/testing/`}
    primaryAction={!workflowTest ? <Button monochrome removeUnderline plain onClick={() => func.downloadAsCSV(testRunResults, selectedTestRun)}>Export</Button> : undefined}
    secondaryActions={!workflowTest ? <Button monochrome removeUnderline plain onClick={() => openVulnerabilityReport()}>Export vulnerability report</Button> : undefined}
      components={components}
    />
  );
}

export default SingleTestRunPage
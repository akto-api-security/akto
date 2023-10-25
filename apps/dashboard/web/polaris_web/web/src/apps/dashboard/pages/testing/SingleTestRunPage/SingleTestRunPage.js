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
  LegacyCard,
  Tag,
  IndexFiltersMode,
} from '@shopify/polaris';
import { RefreshMinor } from '@shopify/polaris-icons';
import api from "../api";
import func from '@/util/func';
import { useParams } from 'react-router';
import { useState, useEffect, useRef } from 'react';
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import WorkflowTestBuilder from "../workflow_test/WorkflowTestBuilder";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import TooltipText from "../../../components/shared/TooltipText";
import PersistStore from "../../../../main/PersistStore";
import TrendChart from "./TrendChart";

let headers = [
  {
    value: "nameComp",
    title: 'Issue name',
  },
  {
    title: 'Severity',
    value: 'severityComp',
  },
  {
    value: 'testCategory',
    title: 'Category',
    isText: true,
  },
  {
    title: 'CWE tags',
    value: 'cweDisplayComp',
  },
  {
    title: 'Number of urls',
    value: 'totalUrls',
    isText:true
  },
  {
    value: "scanned_time_comp",
    title: 'Scanned',
  },
  {
    title: '',
    isCollapsible: true
  }
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
    case 'urlFilters':
      return value.length + ' API' + (value.length==1 ? '' : 's')
    case 'cwe':
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
  },
  {
    key: 'cwe',
    label: 'CWE',
    title: 'CWE',
    choices: [],
  },
  {
    key: 'urlFilters',
    choices: [],
    label: 'API',
    title: 'API'
  }
]

const TabHeading = (type, testRunResults) => {
  return (
    <HorizontalStack gap={2}>
      <Text> {func.toSentenceCase(type)} </Text>
      <Tag>{testRunResults[type].length}</Tag>
    </HorizontalStack>
  )
}

function SingleTestRunPage() {

  const [testRunResults, setTestRunResults] = useState({ vulnerable: [], all: [] })
  const [ selectedTestRun, setSelectedTestRun ] = useState({});
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const subCategoryMap = PersistStore(state => state.subCategoryMap);
  const params= useParams()
  const [loading, setLoading] = useState(false);
  const [tempLoading , setTempLoading] = useState({vulnerable: false, all: false, running: false})
  const [selectedTab, setSelectedTab] = useState("vulnerable")
  const [selected, setSelected] = useState(0)
  const [workflowTest, setWorkflowTest ] = useState(false);
  const refreshId = useRef(null);
  const hexId = params.hexId;

  function fillData(data, key){
    setTestRunResults((prev) => {
      prev[key] = data;
      return {...prev};
    })
    setTempLoading((prev) => {
      prev[key] = false;
      return {...prev};
    });
  }

  async function setSummary(summary){
    setTempLoading((prev) => {
      prev.running = false;
      return prev;
    });
    clearInterval(refreshId.current);
    setSelectedTestRun((prev) => {
      let tmp = {...summary};
      tmp.countIssues = transform.prepareCountIssues(tmp.countIssues);
      prev = {...prev, ...transform.prepareDataFromSummary(tmp, prev.testRunState)}

      return {...prev};
    });

    await fetchTestingRunResultsData(summary.hexId);
  }
  async function fetchTestingRunResultsData(summaryHexId){
    setLoading(false);
    setTempLoading((prev) => {
      prev.vulnerable = true;
      prev.all = true;
      return {...prev};
    });
    let testRunResults = [];
    await api.fetchTestingRunResults(summaryHexId, true).then(({ testingRunResults }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })
    
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'vulnerable')
    await api.fetchTestingRunResults(summaryHexId).then(({ testingRunResults }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'all')
  }

  async function fetchData(setData) {
    let localSelectedTestRun = {}
    await api.fetchTestingRunResultSummaries(hexId).then(async ({ testingRun, testingRunResultSummaries, workflowTest }) => {
      if(testingRun==undefined){
        return {};
      }

      if(testingRun.testIdConfig == 1){
        setWorkflowTest(workflowTest);
      }
      let cicd = false;
      let res = await api.fetchTestingDetails(0,0,true,false,"",1,0,10,{endTimestamp: [testingRun.endTimestamp, testingRun.endTimestamp]});
      if(res.testingRuns.map(x=>x.hexId).includes(testingRun.hexId)){
        cicd = true;
      }
      localSelectedTestRun = transform.prepareTestRun(testingRun, testingRunResultSummaries[0], cicd, false);
      if(localSelectedTestRun.orderPriority === 1 || localSelectedTestRun.orderPriority === 2){
        if(setData){
          setTimeout(() => {
            refreshSummaries();
          }, 2000)
        }
        setTempLoading((prev) => {
            prev.running = true;
            return {...prev};
        });
      }

      if(setData){
        setSelectedTestRun(localSelectedTestRun);
      }

      setTimeout(() => {
        setLoading(false);
      }, 500)

      if(localSelectedTestRun.testingRunResultSummaryHexId) {
        await fetchTestingRunResultsData(localSelectedTestRun.testingRunResultSummaryHexId);
        }
      }) 
    return localSelectedTestRun;
}

  const refreshSummaries = () => {
    let intervalId = setInterval(async() => {
      let localSelectedTestRun = await fetchData(false);
      if(localSelectedTestRun.id){
        if(localSelectedTestRun.orderPriority !== 1){
          setSelectedTestRun(localSelectedTestRun);
          setTempLoading((prev) => {
            prev.running = false;
            return prev;
          });
          clearInterval(intervalId)
        } else {
          setSelectedTestRun((prev) => {
            if(func.deepComparison(prev, localSelectedTestRun)){
              return prev;
            }
            return localSelectedTestRun;
          });
        }
      } else {
        clearInterval(intervalId)
      }
      refreshId.current = intervalId;
    },5000)
  }

  useEffect(()=>{
    async function loadData(){
      setLoading(true);
      await fetchData(true);
    }
    loadData();
  }, [])

const promotedBulkActions = (selectedDataHexIds) => { 
  return [
  {
    content: `Export ${selectedDataHexIds.length} record${selectedDataHexIds.length==1 ? '' : 's'}`,
    onAction: () => {
      func.downloadAsCSV((testRunResults[selectedTab]).filter((data) => {return selectedDataHexIds.includes(data.id)}), selectedTestRun)
    },
  },
]};

  function getHeadingStatus(selectedTestRun) {

    switch (selectedTestRun?.summaryState) {
      case "RUNNING":
        return "Test is running";
      case "SCHEDULED":
        return "Test has been scheduled";
      case "STOPPED":
        return "Test has been stopped";
      case "COMPLETED":
        return `Scanned ${func.prettifyEpoch(selectedTestRun.startTimestamp)} for a duration of
        ${func.getTimeTakenByTest(selectedTestRun.startTimestamp, selectedTestRun.endTimestamp)}`;
      case "FAIL":
        return "Test execution has failed during run";
      default:
        return "";
    }
  }

  const modifyData = (data, filters) =>{
    if(filters?.urlFilters?.length > 0){
      let filteredData = data.map(element => {
        let filteredUrls = element.urls.filter(obj => filters.urlFilters.includes(obj.url))
        console.log(filteredUrls)
        return {
          ...element,
          urls: filteredUrls,
          totalUrls: filteredUrls.length,
          collapsibleRow: transform.getCollapisbleRow(filteredUrls)
        }
      });
      return filteredData
    }else{
      return data
    }
  }

  const tableTabs = [
    {
      content: 'Vulnerable',
      index: 0,
      badge: testRunResults["vulnerable"]?.length?.toString(),
      onAction: ()=> {setSelectedTab('vulnerable')},
      id: 'vulnerable',
    },
    {
        content: 'All',
        index: 1,
        badge: testRunResults["all"]?.length?.toString(),
        onAction: ()=> {setSelectedTab('all')},
        id: 'all',
    },
  ]

  const handleSelectedTab = (selectedIndex) => {
      setLoading(true)
      setSelected(selectedIndex)
      setTimeout(()=>{
          setLoading(false)
      },200)
  }
  
  const resultTable = (
    <GithubSimpleTable
        key={"table"}
        data={testRunResults[selectedTab]}
        sortOptions={sortOptions}
        resourceName={resourceName}
        filters={filters}
        disambiguateLabel={disambiguateLabel}
        headers={headers}
        selectable={false}
        promotedBulkActions={promotedBulkActions}
        loading={loading || ( tempLoading[selectedTab]) || tempLoading.running}
        getStatus={func.getTestResultStatus}
        mode={IndexFiltersMode.Default}
        headings={headers}
        useNewRow={true}
        condensedHeight={true}
        useModifiedData={true}
        modifyData={(data,filters) => modifyData(data,filters)}
        notHighlightOnselected={true}
        selected={selected}
        tableTabs={tableTabs}
        onSelect={handleSelectedTab}
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

  const metadataComponent = () => {

    if(!selectedTestRun.metadata){
      return undefined
    }

    return (
      <LegacyCard title="Metadata" sectioned key="metadata">
      {
        selectedTestRun.metadata ? Object.keys(selectedTestRun.metadata).map((key) => {
          return (
            <HorizontalStack key={key} spacing="tight">
              <Text>{key} : {selectedTestRun.metadata[key]}</Text>
            </HorizontalStack>
          )
        }) : ""
      }
    </LegacyCard>
    )
  }

  const components = [ 
  <TrendChart key={tempLoading.running} hexId={hexId} setSummary={setSummary} show={selectedTestRun.run_type && selectedTestRun.run_type!='One-time'}/> , 
    metadataComponent(), loading ? <SpinnerCentered key="loading"/> : (!workflowTest ? resultTable : workflowTestBuilder)];

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

  const openVulnerabilityReport = () => {
    let summaryId = selectedTestRun.testingRunResultSummaryHexId
    window.open('/dashboard/testing/summary/' + summaryId, '_blank');
  }

  return (
    <PageWithMultipleCards
    title={
        <Box paddingBlockStart={1}>
          <VerticalStack gap="2">
            <HorizontalStack gap="2" align="start">
              { selectedTestRun?.icon && <Box>
                <Icon color={selectedTestRun.iconColor} source={selectedTestRun.icon }></Icon>
              </Box>
              }
              <Box maxWidth="35vw">
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
                  <Button icon={RefreshMinor} plain onClick={() => {rerunTest(hexId)}}/>
                </Tooltip>
            </HorizontalStack>
            <Text color="subdued" fontWeight="regular" variant="bodyMd">
              {
                getHeadingStatus(selectedTestRun)
              }
            </Text>
          </VerticalStack>
          </Box>
    }
    backUrl={`/dashboard/testing/`}
    primaryAction={!workflowTest ? <Box paddingInlineEnd={1}><Button primary onClick={() => 
      func.downloadAsCSV((testRunResults[selectedTab]), selectedTestRun)
      }>Export</Button></Box>: undefined}
      secondaryActions={!workflowTest ? <Button onClick={() => openVulnerabilityReport()}>Export vulnerability report</Button> : undefined}
      components={components}
    />
  );
}

export default SingleTestRunPage
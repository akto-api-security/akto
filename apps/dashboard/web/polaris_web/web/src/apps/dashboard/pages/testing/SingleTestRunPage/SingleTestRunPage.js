import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {
  Text,
  Button,
  VerticalStack,
  HorizontalStack,
  Icon,
  Badge,
  Box,
  LegacyCard,
  IndexFiltersMode,
  Link,
  Popover,
  ActionList,
  Modal,
} from '@shopify/polaris';

import {
  CircleInformationMajor,
  FileMinor,
  ArchiveMinor,
  PriceLookupMinor,
  ReportMinor
} from '@shopify/polaris-icons';
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
import { CellType } from "../../../components/tables/rows/GithubRow";
import useTable from "../../../components/tables/TableContext";
import ReRunModal from "./ReRunModal";

let headers = [
  {
    value: "nameComp",
    title: 'Issue name',
  },
  {
    title: 'Severity',
    value: 'severityComp',
    sortActive: true
  },
  {
    value: 'testCategory',
    title: 'Category',
    type: CellType.TEXT,
  },
  {
    title: 'CWE tags',
    value: 'cweDisplayComp',
  },
  {
    title: 'Number of urls',
    value: 'totalUrls',
    type: CellType.TEXT
  },
  {
    value: "scanned_time_comp",
    title: 'Scanned',
    sortActive: true
  },
  {
    title: '',
    type: CellType.COLLAPSIBLE
  }
]

const sortOptions = [
  { label: 'Severity', value: 'severity asc', directionLabel: 'Highest severity', sortKey: 'total_severity', columnIndex: 2},
  { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest severity', sortKey: 'total_severity', columnIndex: 2 },
  { label: 'Run time', value: 'time asc', directionLabel: 'Newest run', sortKey: 'endTimestamp', columnIndex: 6 },
  { label: 'Run time', value: 'time desc', directionLabel: 'Oldest run', sortKey: 'endTimestamp', columnIndex: 6 },
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
      return value.length + ' API' + (value.length === 1 ? '' : 's')
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

function SingleTestRunPage() {

  const [testRunResults, setTestRunResults] = useState({ vulnerable: [], no_vulnerability_found: [], skipped: [] })
  const [testRunResultsText, setTestRunResultsText] = useState({ vulnerable: [], no_vulnerability_found: [], skipped: [] })
  const [ selectedTestRun, setSelectedTestRun ] = useState({});
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const subCategoryMap = PersistStore(state => state.subCategoryMap);
  const params= useParams()
  const [loading, setLoading] = useState(false);
  const [tempLoading , setTempLoading] = useState({vulnerable: false, no_vulnerability_found: false, skipped: false, running: false})
  const [selectedTab, setSelectedTab] = useState("vulnerable")
  const [selected, setSelected] = useState(0)
  const [workflowTest, setWorkflowTest ] = useState(false);
  const [secondaryPopover, setSecondaryPopover] = useState(false)
  const [primaryPopover, setPrimaryPopover] = useState(false)
  const refreshId = useRef(null);
  const hexId = params.hexId;

  const collectionsMap = PersistStore(state => state.collectionsMap)

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

  function fillTempData(data, key){
    setTestRunResultsText((prev) => {
      prev[key] = data;
      return {...prev};
    })
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
      prev.no_vulnerability_found = true;
      prev.skipped = true;
      return {...prev};
    });
    let testRunResults = [];
    await api.fetchTestingRunResults(summaryHexId, "VULNERABLE").then(({ testingRunResults }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })
    fillTempData(testRunResults, 'vulnerable')
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'vulnerable')

    await api.fetchTestingRunResults(summaryHexId, "SKIPPED_EXEC").then(({ testingRunResults }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })
    fillTempData(testRunResults, 'skipped')
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'skipped')

    await api.fetchTestingRunResults(summaryHexId, "SECURED").then(({ testingRunResults }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })
    fillTempData(testRunResults, 'no_vulnerability_found')
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'no_vulnerability_found')
  }
  async function fetchData(setData) {
    let localSelectedTestRun = {}
    await api.fetchTestingRunResultSummaries(hexId).then(async ({ testingRun, testingRunResultSummaries, workflowTest, testingRunType }) => {
      if(testingRun==undefined){
        return {};
      }

      if(testingRun.testIdConfig == 1){
        setWorkflowTest(workflowTest);
      }
      let cicd = testingRunType === "CI_CD";
      localSelectedTestRun = transform.prepareTestRun(testingRun, testingRunResultSummaries[0], cicd, false);
      if(localSelectedTestRun.orderPriority === 1){
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
  }, [subCategoryMap])

const promotedBulkActions = (selectedDataHexIds) => { 
  return [
  {
    content: `Export ${selectedDataHexIds.length} record${selectedDataHexIds.length==1 ? '' : 's'}`,
    onAction: () => {
      func.downloadAsCSV((testRunResultsText[selectedTab]).filter((data) => {return selectedDataHexIds.includes(data.id)}), selectedTestRun)
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
      case "FAILED":
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
        return {
          ...element,
          urls: filteredUrls,
          totalUrls: filteredUrls.length,
          collapsibleRow: transform.getCollapsibleRow(filteredUrls)
        }
      });
      return filteredData
    }else{
      return data
    }
  }

  const definedTableTabs = ['Vulnerable', 'Skipped', 'No vulnerability found']

  const { tabsInfo } = useTable()
  const tableCountObj = func.getTabsCount(definedTableTabs, testRunResults)
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

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
        filterStateUrl={"dashboard/testing/" + selectedTestRun?.id + "/" + selectedTab}
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

  const openVulnerabilityReport = () => {
    let summaryId = selectedTestRun.testingRunResultSummaryHexId
    window.open('/dashboard/testing/summary/' + summaryId, '_blank');
  }

  const EmptyData = () => {
    return(
      <div style={{margin: 'auto', marginTop: '20vh'}}>
        <Box width="300px" padding={4}>
          <VerticalStack gap={5}>
            <HorizontalStack align="center">
              <div style={{borderRadius: '50%', border: '6px solid white', padding: '4px', display: 'flex', alignItems: 'center', height: '50px', width: '50px'}}>
                <Icon source={CircleInformationMajor} />
              </div>
            </HorizontalStack>
            <VerticalStack gap={2}>
            <HorizontalStack align="center">
                <Text variant="bodyLg" fontWeight="semibold">
                  No test run data found
                </Text>
              </HorizontalStack>
              <Text variant="bodyMd" alignment="center">
                The next summary will be ready with the upcoming test.
              </Text>
            </VerticalStack>
          </VerticalStack>
        </Box>
      </div>
    )
  }

  const allResultsLength = testRunResults.skipped.length + testRunResults.no_vulnerability_found.length + testRunResults.vulnerable.length
  const useComponents = (!workflowTest && allResultsLength === 0) ? [<EmptyData key="empty"/>] : components
  const headingComp = (
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
        </HorizontalStack>
        <HorizontalStack gap={"2"}>
          <Link monochrome target="_blank" url={"/dashboard/observe/inventory/" + selectedTestRun?.apiCollectionId} removeUnderline>
            <HorizontalStack gap={"1"}>
              <Box><Icon color="subdued" source={ArchiveMinor}/></Box>
              <Text color="subdued" variant="bodyMd">{collectionsMap[selectedTestRun?.apiCollectionId]}</Text>
            </HorizontalStack>
          </Link>
          <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px'/>
          <HorizontalStack gap={"1"}>
            <Box><Icon color="subdued" source={PriceLookupMinor}/></Box>
            <Text color="subdued" variant="bodyMd">{getHeadingStatus(selectedTestRun)}</Text>
          </HorizontalStack>
        </HorizontalStack>
      </VerticalStack>
    </Box>
  )

  const exportActionComp = (
    <Popover
      active={primaryPopover}
      onClose={() => setPrimaryPopover(false)}
      activator={<Button disclosure primary onClick={() => setPrimaryPopover(!primaryPopover)}>Export results</Button>}
      autofocusTarget="first-node"
    >
      <ActionList
         actionRole="menuitem"
         items={[
          {content: 'Export as CSV', icon: FileMinor, onAction: () => {func.downloadAsCSV((testRunResults[selectedTab]), selectedTestRun)}},
          {content: 'Export vulnerability report', icon: ReportMinor, onAction: () => openVulnerabilityReport()}
         ]}
      />
    </Popover>
  )

  const moreActionsList = transform.getActions(selectedTestRun, refreshSummaries)

  const moreActionsComp = (
    <Popover
      active={secondaryPopover}
      onClose={() => setSecondaryPopover(false)}
      activator={<Button disclosure onClick={() => setSecondaryPopover(!secondaryPopover)}>More actions</Button>}
      autofocusTarget="first-node"
    >
      <ActionList
         actionRole="menuitem"
         sections={moreActionsList}
      />
    </Popover>
  )

  return (
    <>
      <PageWithMultipleCards
        title={headingComp}
        backUrl={`/dashboard/testing/`}
        primaryAction={!workflowTest ? exportActionComp: undefined}
        secondaryActions={!workflowTest ? moreActionsComp: undefined}
        components={useComponents}
      />
      <ReRunModal selectedTestRun={selectedTestRun} refreshSummaries={refreshSummaries}/>
    </>
  );
}

export default SingleTestRunPage
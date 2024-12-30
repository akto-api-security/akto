import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import {
  Text,
  Button,
  BlockStack,
  InlineStack,
  Icon,
  Badge,
  Box,
  LegacyCard,
  IndexFiltersMode,
  Link,
  Popover,
  ActionList,
  Card,
  ProgressBar,
  Tooltip,
  Banner,
  Modal
} from '@shopify/polaris';

import {
  InfoIcon,
  ArchiveIcon,
  SearchListIcon,
  SearchResourceIcon,
  RefreshIcon,
  PersonIcon,
  EditIcon
} from "@shopify/polaris-icons";
import api from "../api";
import func from '@/util/func';
import { useParams } from 'react-router';
import { useState, useEffect, useRef, useMemo, useReducer } from 'react';
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import WorkflowTestBuilder from "../workflow_test/WorkflowTestBuilder";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import TooltipText from "../../../components/shared/TooltipText";
import PersistStore from "../../../../main/PersistStore";
import TrendChart from "./TrendChart";
import useTable from "../../../components/tables/TableContext";
import ReRunModal from "./ReRunModal";
import TestingStore from "../testingStore";
import { useSearchParams } from "react-router-dom";
import TestRunResultPage from "../TestRunResultPage/TestRunResultPage";
import { usePolling } from "../../../../main/PollingProvider";
import LocalStore from "../../../../main/LocalStorageStore";
import {produce} from "immer"
import AdvancedSettingsComponent from "../../observe/api_collections/component/AdvancedSettingsComponent";

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

  const [testRunResults, setTestRunResults] = useState({ vulnerable: [], no_vulnerability_found: [], skipped: [], need_configurations: [], ignored_issues: [] })
  const [testRunResultsText, setTestRunResultsText] = useState({ vulnerable: [], no_vulnerability_found: [], skipped: [], need_configurations: [], ignored_issues: [] })
  const [ selectedTestRun, setSelectedTestRun ] = useState({});
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const subCategoryMap = LocalStore(state => state.subCategoryMap);
  const params= useParams()
  const [loading, setLoading] = useState(false);
  const [tempLoading , setTempLoading] = useState({vulnerable: false, no_vulnerability_found: false, skipped: false, running: false,need_configurations:false,ignored_issues:false})
  const [selectedTab, setSelectedTab] = useState("vulnerable")
  const [selected, setSelected] = useState(0)
  const [workflowTest, setWorkflowTest ] = useState(false);
  const [secondaryPopover, setSecondaryPopover] = useState(false)
  const  setErrorsObject = TestingStore((state) => state.setErrorsObject)
  const currentTestingRuns = []
  const [refresh, setRefresh] = useState(false)

  const initialTestingObj = {testsInitiated: 0,testsInsertedInDb: 0,testingRunId: -1}
  const [currentTestObj, setCurrentTestObj] = useState(initialTestingObj)
  const [missingConfigs, setMissingConfigs] = useState([])
  const [showEditableSettings, setShowEditableSettings] = useState(false) ;
  const [testingRunConfigSettings, setTestingRunConfigSettings] = useState([])
  const [testingRunConfigId, setTestingRunConfigId] = useState(-1)

  const refreshId = useRef(null);
  const hexId = params.hexId;
  const [conditions, dispatchConditions] = useReducer(produce((draft, action) => func.conditionsReducer(draft, action)), []);


  const [searchParams, setSearchParams] = useSearchParams();
  const resultId = searchParams.get("result")
  const collectionsMap = PersistStore(state => state.collectionsMap)
  // const { currentTestsObj } = usePolling()

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
      prev.need_configurations = true
      prev.ignored_issues = true
      return {...prev};
    });
    let testRunResults = [];
    let vulnerableTestingRunResults = [];
    await api.fetchTestingRunResults(summaryHexId, "VULNERABLE").then(({ testingRunResults }) => {
      vulnerableTestingRunResults = testingRunResults
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })

    let ignoredTestRunResults = []
    await api.fetchIssuesByStatusAndSummaryId(summaryHexId, ["IGNORED", "FIXED"]).then((issues) => {
      const ignoredTestingResults = vulnerableTestingRunResults.filter(result => {
        return issues.some(issue =>
            issue.id.apiInfoKey.apiCollectionId === result.apiInfoKey.apiCollectionId &&
            issue.id.apiInfoKey.method === result.apiInfoKey.method &&
            issue.id.apiInfoKey.url === result.apiInfoKey.url &&
            issue.id.testSubCategory === result.testSubType
        )
      })
    
      ignoredTestRunResults = transform.prepareTestRunResults(hexId, ignoredTestingResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })

    const updatedTestRunResults = testRunResults.filter(result => {
      return !ignoredTestRunResults.some(ignoredResult => {
        return JSON.stringify(result) === JSON.stringify(ignoredResult)
      })
    })    

    fillTempData(updatedTestRunResults, 'vulnerable')
    fillData(transform.getPrettifiedTestRunResults(updatedTestRunResults), 'vulnerable')

    fillTempData(ignoredTestRunResults, 'ignored_issues')
    fillData(transform.getPrettifiedTestRunResults(ignoredTestRunResults), 'ignored_issues')


    await api.fetchTestingRunResults(summaryHexId, "SKIPPED_EXEC_API_REQUEST_FAILED").then(({ testingRunResults, errorEnums }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
      errorEnums['UNKNOWN_ERROR_OCCURRED'] = "OOPS! Unknown error occurred."
      setErrorsObject(errorEnums)
    })
    fillTempData(testRunResults, 'domain_unreachable')
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'domain_unreachable')
    await api.fetchTestingRunResults(summaryHexId, "SKIPPED_EXEC").then(({ testingRunResults, errorEnums }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
      errorEnums['UNKNOWN_ERROR_OCCURRED'] = "OOPS! Unknown error occurred."
      setErrorsObject(errorEnums)
    })
    fillTempData(testRunResults, 'skipped')
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'skipped')

    await api.fetchTestingRunResults(summaryHexId, "SKIPPED_EXEC_NEED_CONFIG").then(({ testingRunResults }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })
    fillTempData(testRunResults, 'need_configurations')
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'need_configurations')
    if(testRunResults.length > 0){
      setMissingConfigs(transform.getMissingConfigs(testRunResults))
    }

    await api.fetchTestingRunResults(summaryHexId, "SECURED").then(({ testingRunResults }) => {
      testRunResults = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
    })
    fillTempData(testRunResults, 'no_vulnerability_found')
    fillData(transform.getPrettifiedTestRunResults(testRunResults), 'no_vulnerability_found')
  }
  async function fetchData(setData) {
    let localSelectedTestRun = {}
    await api.fetchTestingRunResultSummaries(hexId).then(async ({ testingRun, testingRunResultSummaries, workflowTest, testingRunType }) => {
      if(testingRun===undefined){
        return {};
      }

      if(testingRun.testIdConfig === 1){
        setWorkflowTest(workflowTest);
      }
      let cicd = testingRunType === "CI_CD";
      const timeNow = func.timeNow()
      const defaultIgnoreTime = LocalStore.getState().defaultIgnoreSummaryTime
      testingRunResultSummaries.sort((a,b) => {
        const isAWithinTimeAndRunning = (timeNow - defaultIgnoreTime <= a.startTimestamp) && a.state === 'RUNNING';
        const isBWithinTimeAndRunning = (timeNow - defaultIgnoreTime <= b.startTimestamp) && b.state === 'RUNNING';

        if (isAWithinTimeAndRunning && isBWithinTimeAndRunning) {
            return b.startTimestamp - a.startTimestamp;
        }
        if (isAWithinTimeAndRunning) return -1;
        if (isBWithinTimeAndRunning) return 1;
        return b.startTimestamp - a.startTimestamp;
      })
      localSelectedTestRun = transform.prepareTestRun(testingRun, testingRunResultSummaries[0], cicd, false);
      setTestingRunConfigSettings(testingRun.testingRunConfig?.configsAdvancedSettings || [])
      setTestingRunConfigId(testingRun.testingRunConfig?.id || -1)

      if(setData){
        setSelectedTestRun(localSelectedTestRun);
      }
      if((localSelectedTestRun.testingRunResultSummaryHexId) && ((testRunResults[selectedTab].length === 0) || setData)) {
        await fetchTestingRunResultsData(localSelectedTestRun.testingRunResultSummaryHexId);
      }
    })  
    setLoading(false);
    return localSelectedTestRun;
}


  useEffect(()=>{
    async function loadData(){
      if(Object.keys(subCategoryMap).length === 0 || testRunResults[selectedTab].length === 0){
        setLoading(true);
        await fetchData(true);
      }
    }
    loadData();
  }, [subCategoryMap])

  useEffect(() => {
    if (resultId === null || resultId.length === 0) {
      let found = false;
        for (var ind in currentTestingRuns) {
            let obj = currentTestingRuns[ind];
            if (obj.testingRunId === hexId) {
              found = true;
                setCurrentTestObj(prevObj => {
                    if (JSON.stringify(prevObj) !== JSON.stringify(obj)) {
                        setRefresh(refresh => !refresh);
                        return obj;
                    }
                    return prevObj; // No state change if object is the same
                });
                break;
            }
        }

        if (!found) {
            setCurrentTestObj(prevObj => {
                if (JSON.stringify(prevObj) !== JSON.stringify(initialTestingObj)) {
                    return initialTestingObj;
                }
                return prevObj; // No state change if object is the same
            });
        }
    }

}, []);

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
        return "No summary for test exists";
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

  const baseUrl = window.location.origin+"/dashboard/testing/roles/details?system=";

  const bannerComp = (
    missingConfigs.length > 0 ? 
    <div className="banner-wrapper">
      <Banner tone="critical">
        <InlineStack gap={300}>
          <Box>
            <Text fontWeight="semibold">
              {`${missingConfigs.length} configuration${missingConfigs.length > 1 ? 's' : ''} missing: `}  
            </Text>
          </Box>
          <InlineStack gap={200}>
            {missingConfigs.map((config) => {
              return(<Link url={baseUrl + config.toUpperCase()} key={config} target="_blank">
                {config}
              </Link>) 
            })}
          </InlineStack>
        </InlineStack>
      </Banner>
    </div> : null
  )

  const definedTableTabs = ['Vulnerable', 'Need configurations','Skipped', 'No vulnerability found','Domain unreachable','Ignored Issues']

  const { tabsInfo } = useTable()
  const tableCountObj = func.getTabsCount(definedTableTabs, testRunResults)
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)
  const tableHeaders = transform.getHeaders(selectedTab)

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
        headers={tableHeaders}
        selectable={false}
        promotedBulkActions={promotedBulkActions}
        loading={loading || ( tempLoading[selectedTab]) || tempLoading.running}
        getStatus={func.getTestResultStatus}
        mode={IndexFiltersMode.Default}
        headings={tableHeaders}
        useNewRow={true}
        condensedHeight={true}
        useModifiedData={true}
        modifyData={(data,filters) => modifyData(data,filters)}
        notHighlightOnselected={true}
        selected={selected}
        tableTabs={tableTabs}
        onSelect={handleSelectedTab}
        filterStateUrl={"/dashboard/testing/" + selectedTestRun?.id + "/#" + selectedTab}
        bannerComp={{
          "comp": bannerComp,
          "selected": 1
          }
        }
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
            <InlineStack key={key} spacing="tight">
              <Text>{key} : {selectedTestRun.metadata[key]}</Text>
            </InlineStack>
          );
        }) : ""
      }
    </LegacyCard>
    );
  }

  const progress = useMemo(() => {
    return currentTestObj.testsInitiated === 0 ? 0 : Math.floor((currentTestObj.testsInsertedInDb * 100) / currentTestObj.testsInitiated);
}, [currentTestObj.testingRunId]);

const runningTestsComp = useMemo(() => (
    currentTestObj.testingRunId !== -1 ? (
        <Card key={"test-progress"}>
            <BlockStack gap={"300"}>
              <Text variant="headingSm">{`Running ${currentTestObj.testsInitiated} tests`}</Text>
              <div style={{ display: "flex", gap: '4px', alignItems: 'center' }}>
                  <ProgressBar progress={progress} tone="primary" size="small" />
                  <Text tone="subdued">{`${progress}%`}</Text>
              </div>
            </BlockStack>
        </Card>
    ) : null
), [currentTestObj, progress]);

const handleModifyConfig = async() => {
  const settings = transform.prepareConditionsForTesting(conditions)
  await api.modifyTestingRunConfig(testingRunConfigId, settings).then(() =>{
    func.setToast(true, false, "Modified testing run config successfully")
    setShowEditableSettings(false)
  })
}

const editableConfigsComp = (
  <Modal
    large
    fullScreen
    open={showEditableSettings}
    onClose={() => setShowEditableSettings(false)}
    title={"Edit test configurations"}
    primaryAction={{
        content: 'Save',
        onAction: () => handleModifyConfig()
    }}
    >
    <Modal.Section>
        <AdvancedSettingsComponent 
          key={"configSettings"} 
          conditions={conditions} 
          dispatchConditions={dispatchConditions} 
          hideButton={true}
        /> 
    </Modal.Section>
  </Modal>
)

  const components = [ 
    runningTestsComp,<TrendChart key={tempLoading.running} hexId={hexId} setSummary={setSummary} show={selectedTestRun.run_type && selectedTestRun.run_type!=='One-time'}/> , 
    metadataComponent(), loading ? <SpinnerCentered key="loading"/> : (!workflowTest ? resultTable : workflowTestBuilder), editableConfigsComp];

  const openVulnerabilityReport = () => {
    let summaryId = selectedTestRun.testingRunResultSummaryHexId
    window.open('/dashboard/testing/summary/' + summaryId, '_blank');
  }

  const handleAddSettings = () => {
    if(conditions.length === 0  && testingRunConfigSettings.length > 0){
      testingRunConfigSettings.forEach((condition) => {
        const operatorType = condition.operatorType
        condition.operationsGroupList.forEach((obj) => {
          const finalObj = {'data': obj, 'operator': {'type': operatorType}}
          dispatchConditions({type:"add", obj: finalObj})
        })
      })
    }
  }

  const EmptyData = () => {
    return (
      <div style={{margin: 'auto', marginTop: '20vh'}}>
        <Box width="300px" padding={400}>
          <BlockStack gap={500}>
            <InlineStack align="center">
              <div style={{borderRadius: '50%', border: '6px solid white', padding: '4px', display: 'flex', alignItems: 'center', height: '50px', width: '50px'}}>
                <Icon source={InfoIcon} />
              </div>
            </InlineStack>
            <BlockStack gap={200}>
              <InlineStack align="center">
                <Text variant="bodyLg" fontWeight="semibold">
                  No test run data found
                </Text>
              </InlineStack>
              <Text variant="bodyMd" alignment="center">
                The next summary will be ready with the upcoming test.
              </Text>
            </BlockStack>
          </BlockStack>
        </Box>
      </div>
    );
  }

  const allResultsLength = testRunResults.skipped.length + testRunResults.need_configurations.length + testRunResults.no_vulnerability_found.length + testRunResults.vulnerable.length + testRunResults.ignored_issues.length + progress
  const useComponents = (!workflowTest && allResultsLength === 0 && (selectedTestRun.run_type && selectedTestRun.run_type ==='One-time')) ? [<EmptyData key="empty"/>] : components
  const headingComp = (
    <Box paddingBlockStart={100}>
      <BlockStack gap="200">
        <InlineStack gap="200" align="start">
          { selectedTestRun?.icon && <Box>
            <Icon tone={selectedTestRun.iconColor} source={selectedTestRun.icon }></Icon>
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
            <Badge key={item} tone={func.getTestResultStatus(item)}>
              <Text fontWeight="regular">
                {item}
              </Text>
            </Badge>
            )}
          <Button   onClick={() => fetchData(true)} variant="monochromePlain"><Tooltip content="Refresh page" dismissOnMouseOut> <Icon source={RefreshIcon} /></Tooltip></Button>
        </InlineStack>
        <InlineStack gap={"200"}>
          <InlineStack gap={"100"}>
            <Box><Icon tone="subdued" source={PersonIcon}/></Box>
            <Text tone="subdued" fontWeight="medium" variant="bodyMd">created by:</Text>
            <Text tone="subdued" variant="bodyMd">{selectedTestRun.userEmail}</Text>
          </InlineStack>
          <Box width="1px" borderColor="border-secondary" borderInlineStartWidth="1" minHeight='16px'/>
          <Link monochrome target="_blank" url={"/dashboard/observe/inventory/" + selectedTestRun?.apiCollectionId} removeUnderline>
            <InlineStack gap={"100"}>
              <Box><Icon tone="subdued" source={ArchiveIcon}/></Box>
              <Text tone="subdued" variant="bodyMd">{collectionsMap[selectedTestRun?.apiCollectionId]}</Text>
            </InlineStack>
          </Link>
          <Box width="1px" borderColor="border-secondary" borderInlineStartWidth="1" minHeight='16px'/>
          <InlineStack gap={"100"}>
            <Box><Icon tone="subdued" source={SearchListIcon}/></Box>
            <Text tone="subdued" variant="bodyMd">{getHeadingStatus(selectedTestRun)}</Text>
          </InlineStack>
        </InlineStack>
      </BlockStack>
    </Box>
  )

  let moreActionsList = transform.getActions(selectedTestRun)
  moreActionsList.push({title: 'Export', items: [
    {
     content: 'Export vulnerability report', 
     icon: SearchResourceIcon, 
     onAction: () => openVulnerabilityReport()
    }
  ]})
  moreActionsList.push({title: 'Update', items:[
    {
      content: 'Edit testing config settings',
      icon: EditIcon,
      onAction: () => { setShowEditableSettings(true); handleAddSettings(); }
    }
  ]})
  const moreActionsComp = (
    <Popover
      active={secondaryPopover}
      onClose={() => setSecondaryPopover(false)}
      activator={<div className="polaris-secondaryAction-button"><Button variant="secondary" disclosure onClick={() => setSecondaryPopover(!secondaryPopover)}>More actions</Button></div>}
      autofocusTarget="first-node"
    >
      <ActionList
        actionRole="menuitem"
        sections={moreActionsList}
      />
       
    </Popover>
  )

  return <>
    <PageWithMultipleCards
      title={headingComp}
      backUrl={`/dashboard/testing/`}
      primaryAction={!workflowTest ? <Box paddingInlineEnd={100}><Button

        onClick={() => 
          func.downloadAsCSV((testRunResultsText[selectedTab]), selectedTestRun)
          }
        variant="primary">Export results</Button></Box>: undefined}
      secondaryActions={!workflowTest ? moreActionsComp: undefined}
      components={useComponents}
    />
    <ReRunModal selectedTestRun={selectedTestRun} shouldRefresh={false}/>
    {(resultId !== null && resultId.length > 0) ? <TestRunResultPage /> : null}
  </>;
}

export default SingleTestRunPage
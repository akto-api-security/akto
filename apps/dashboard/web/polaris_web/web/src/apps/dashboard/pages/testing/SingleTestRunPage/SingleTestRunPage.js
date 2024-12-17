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
  Card,
  ProgressBar,
  Tooltip,
  Banner,
  Modal
} from '@shopify/polaris';

import {
  CircleInformationMajor,
  ArchiveMinor,
  PriceLookupMinor,
  ReportMinor,
  RefreshMajor,
  CustomersMinor,
  EditMajor
} from '@shopify/polaris-icons';
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
import GithubServerTable from "../../../components/tables/GithubServerTable";

let sortOptions = [
  { label: 'Severity', value: 'severity asc', directionLabel: 'Highest severity', sortKey: 'total_severity', columnIndex: 3 },
  { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest severity', sortKey: 'total_severity', columnIndex: 3 },
  { label: 'Run time', value: 'time asc', directionLabel: 'Newest run', sortKey: 'endTimestamp', columnIndex: 7 },
  { label: 'Run time', value: 'time desc', directionLabel: 'Oldest run', sortKey: 'endTimestamp', columnIndex: 7 },
];

const resourceName = {
  singular: 'test run result',
  plural: 'test run results',
};

let filters = [
  {
    key: 'severityStatus',
    label: 'Severity',
    title: 'Severity',
    choices: [
      {label: 'High', value: 'HIGH'},
      {label: 'Medium', value: 'MEDIUM'},
      {label: 'Low', value: 'LOW'}
    ],
  },
  {
    key: 'method',
    label: 'Method',
    title: 'Method',
    choices: [
      {label: 'Get', value: 'GET'},
      {label: 'Post', value: 'POST'},
      {label: 'Put', value: 'PUT'},
      {label: 'Patch', value: 'PATCH'},
      {label: 'Delete', value: 'DELETE'}
    ],
  },
  {
    key: 'categoryFilter',
    label: 'Category',
    title: 'Category',
    choices: [],
  },
  {
    key: 'apiCollectionId',
    label: 'Collection',
    title: 'Collection',
    choices: [],
  },
  {
    key: 'collectionIds',
    label: 'API groups',
    title: 'API groups',
    choices: [],
  }
]

function SingleTestRunPage() {

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
  const [updateTable, setUpdateTable] = useState("")
  const [testRunResultsCount, setTestRunResultsCount] = useState({})

  const initialTestingObj = {testsInitiated: 0,testsInsertedInDb: 0,testingRunId: -1}
  const [currentTestObj, setCurrentTestObj] = useState(initialTestingObj)
  const [missingConfigs, setMissingConfigs] = useState([])
  const [showEditableSettings, setShowEditableSettings] = useState(false) ;
  const [testingRunConfigSettings, setTestingRunConfigSettings] = useState([])
  const [testingRunConfigId, setTestingRunConfigId] = useState(-1)
  const apiCollectionMap = PersistStore(state => state.collectionsMap);

  const filtersMap = PersistStore.getState().filtersMap;

  const [testingRunResultSummariesObj, setTestingRunResultSummariesObj] = useState({})
  const [allResultsLength, setAllResultsLength] = useState(undefined)
  const [currentSummary, setCurrentSummary] = useState('')

  const tableTabMap = {
    vulnerable: "VULNERABLE",
    domain_unreachable: "SKIPPED_EXEC_API_REQUEST_FAILED",
    skipped: "SKIPPED_EXEC",
    need_configurations: "SKIPPED_EXEC_NEED_CONFIG",
    no_vulnerability_found: "SECURED",
    ignored_issues: "IGNORED_ISSUES"
  }

  const refreshId = useRef(null);
  const hexId = params.hexId;
  const [conditions, dispatchConditions] = useReducer(produce((draft, action) => func.conditionsReducer(draft, action)), []);


  const [searchParams, setSearchParams] = useSearchParams();
  const resultId = searchParams.get("result")
  const collectionsMap = PersistStore(state => state.collectionsMap)
  // const { currentTestsObj } = usePolling()

  function disambiguateLabel(key, value) {
    switch (key) {
      case 'method':
      case 'severityStatus':
        return func.convertToDisambiguateLabel(value, func.toSentenceCase, 2)
      case "collectionIds":
      case "apiCollectionId":
          return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
      case 'categoryFilter':
      case 'testFilter':
        return func.convertToDisambiguateLabelObj(value, null, 2)
      default:
        return value;
    }
  }

  const tableTabsOrder = [
    "vulnerable",
    "need_configurations",
    "skipped", 
    "no_vulnerability_found",
    "domain_unreachable",
    "ignored_issues"
  ]

  function fillTempData(data, key){
    setTestRunResultsText((prev) => {
      prev[key] = data;
      return {...prev};
    })
  }

  async function setSummary(summary, initialCall=false){
    setTempLoading((prev) => {
      prev.running = false;
      return prev;
    });
    clearInterval(refreshId.current);
    setSelectedTestRun((prev) => {
      let tmp = {...summary};
      if(tmp === null || tmp?.countIssues === null || tmp?.countIssues === undefined){
        tmp.countIssues = {
          "HIGH": 0,
          "MEDIUM": 0,
          "LOW": 0
        }
      }
      tmp.countIssues = transform.prepareCountIssues(tmp.countIssues);
      prev = {...prev, ...transform.prepareDataFromSummary(tmp, prev.testRunState)}

      return {...prev};
    });
    setCurrentSummary(summary);
    if(!initialCall){
      setUpdateTable(Date.now().toString())
    }
  }

  useEffect(() => {
    setUpdateTable(Date.now().toString())
  }, [testingRunResultSummariesObj])

  const fetchTestingRunResultSummaries = async () => {
    let tempTestingRunResultSummaries = [];
    await api.fetchTestingRunResultSummaries(hexId).then(({ testingRun, testingRunResultSummaries, workflowTest, testingRunType }) => {
      tempTestingRunResultSummaries = testingRunResultSummaries
      setTestingRunResultSummariesObj({
        testingRun, workflowTest, testingRunType
      })
    })
    const timeNow = func.timeNow()
    const defaultIgnoreTime = LocalStore.getState().defaultIgnoreSummaryTime
      tempTestingRunResultSummaries.sort((a,b) => {
        const isAWithinTimeAndRunning = (timeNow - defaultIgnoreTime <= a.startTimestamp) && a.state === 'RUNNING';
        const isBWithinTimeAndRunning = (timeNow - defaultIgnoreTime <= b.startTimestamp) && b.state === 'RUNNING';
  
        if (isAWithinTimeAndRunning && isBWithinTimeAndRunning) {
            return b.startTimestamp - a.startTimestamp;
        }
        if (isAWithinTimeAndRunning) return -1;
        if (isBWithinTimeAndRunning) return 1;
        return b.startTimestamp - a.startTimestamp;
      })
    if (tempTestingRunResultSummaries && tempTestingRunResultSummaries.length > 0) {
      setSummary(tempTestingRunResultSummaries[0], true)
    }
  }

  const fetchTableData = async (sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) => {
    let testRunResultsRes = []
    let testRunCountMap = []
    const { testingRun, workflowTest, testingRunType } = testingRunResultSummariesObj
    if(testingRun === undefined){
      return {value: [], total: 0}
    }

    if(testingRun.testIdConfig === 1){
      setWorkflowTest(workflowTest);
    }
    let cicd = testingRunType === "CI_CD";
    const localSelectedTestRun = transform.prepareTestRun(testingRun, currentSummary , cicd, false);
    setTestingRunConfigSettings(testingRun.testingRunConfig?.configsAdvancedSettings || [])
    setTestingRunConfigId(testingRun.testingRunConfig?.id || -1)

    setSelectedTestRun(localSelectedTestRun);
    if(localSelectedTestRun.testingRunResultSummaryHexId) {
      if(selectedTab === 'ignored_issues') {
        let ignoredTestRunResults = []
        await api.fetchIssuesByStatusAndSummaryId(localSelectedTestRun.testingRunResultSummaryHexId, ["IGNORED"]).then((resp) => {
          const ignoredIssuesTestingResult = resp?.testingRunResultList || [];
          ignoredTestRunResults = transform.prepareTestRunResults(hexId, ignoredIssuesTestingResult, subCategoryMap, subCategoryFromSourceConfigMap)
        })
        testRunResultsRes = ignoredTestRunResults
      } else {
        await api.fetchTestingRunResults(localSelectedTestRun.testingRunResultSummaryHexId, tableTabMap[selectedTab], sortKey, sortOrder, skip, limit, filters, queryValue).then(({ testingRunResults, testCountMap, errorEnums }) => {
          testRunCountMap = testCountMap
          testRunResultsRes = transform.prepareTestRunResults(hexId, testingRunResults, subCategoryMap, subCategoryFromSourceConfigMap)
          if(selectedTab === 'domain_unreachable' || selectedTab === 'skipped' || selectedTab === 'need_configurations') {
            errorEnums['UNKNOWN_ERROR_OCCURRED'] = "OOPS! Unknown error occurred."
            setErrorsObject(errorEnums)
            setMissingConfigs(transform.getMissingConfigs(testRunResultsRes))
          }
          const orderedValues = tableTabsOrder.map(key => testCountMap[tableTabMap[key]] || 0)
          setTestRunResultsCount(orderedValues)
        })
      }
    }
    fillTempData(testRunResultsRes, selectedTab)
    return {value: transform.getPrettifiedTestRunResults(testRunResultsRes), total: testRunCountMap[tableTabMap[selectedTab]]}
  }

  useEffect(() => {
    fetchTestingRunResultSummaries()
    filters = func.getCollectionFilters(filters)
    let result = []
    let store = {}
    Object.values(subCategoryMap).forEach((x) => {
        let superCategory = x.superCategory
        if (!store[superCategory.name]) {
            result.push({ "label": superCategory.displayName, "value": superCategory.name })
            store[superCategory.name] = []
        }
        store[superCategory.name].push(x._name);
    })
    filters.forEach(filter => {
      if (filter.key === 'categoryFilter') {
        filter.choices = [].concat(result)
      }
    })
    if (resultId === null || resultId.length === 0) {
      let found = false;
        for (var ind in currentTestingRuns) {
            let obj = currentTestingRuns[ind];
            if (obj.testingRunId === hexId) {
              found = true;
                setCurrentTestObj(prevObj => {
                    if (JSON.stringify(prevObj) !== JSON.stringify(obj)) {
                        setUpdateTable(Date.now().toString());
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
      <Banner status="critical">
        <HorizontalStack gap={3}>
          <Box>
            <Text fontWeight="semibold">
              {`${missingConfigs.length} configuration${missingConfigs.length > 1 ? 's' : ''} missing: `}  
            </Text>
          </Box>
          <HorizontalStack gap={2}>
            {missingConfigs.map((config) => {
              return(<Link url={baseUrl + config.toUpperCase()} key={config} target="_blank">
                {config}
              </Link>) 
            })}
          </HorizontalStack>
        </HorizontalStack>
      </Banner>
    </div> : null
  )

  const definedTableTabs = ['Vulnerable', 'Need configurations','Skipped', 'No vulnerability found','Domain unreachable','Ignored Issues']

  const { tabsInfo } = useTable()
  const tableCountObj = func.getTabsCount(definedTableTabs, {}, Object.values(testRunResultsCount))
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)
  const tableHeaders = transform.getHeaders(selectedTab)

  const handleSelectedTab = (selectedIndex) => {
      setLoading(true)
      setSelected(selectedIndex)
      setUpdateTable("")
      
      sortOptions = sortOptions.map(option => {
        if (selectedIndex === 0 || selectedIndex == 5) {
          if (option.label === 'Severity') {
            return { ...option, columnIndex: 3 }
          } else if (option.label === 'Run time') {
            return { ...option, columnIndex: 7 }
          }
        } else if (selectedIndex === 1) {
          if (option.label === 'Run time') {
            return { ...option, columnIndex: 6 }
          }
        } else if (selectedIndex === 2) {
          if (option.label === 'Run time') {
            return { ...option, columnIndex: 6 }
          }
        } else if (selectedIndex === 3) {
          if (option.label === 'Run time') {
            return { ...option, columnIndex: 6 }
          }
        } else if (selectedIndex === 4) {
          if (option.label === 'Run time') {
            return { ...option, columnIndex: 7 }
          }
        }
        return option
      })

      filters = filters.filter(filter => filter.key !== 'severityStatus')

      if(selectedIndex === 0 || selectedIndex === 5) {
        filters = [
          {
            key: 'severityStatus',
            label: 'Severity',
            title: 'Severity',
            choices: [
              {label: 'High', value: 'HIGH'},
              {label: 'Medium', value: 'MEDIUM'},
              {label: 'Low', value: 'LOW'}
            ],
          },
          ...filters
        ]
      }
      
      setTimeout(()=>{
          setLoading(false)
      },200)
  }

  const resultTable = (
    <GithubServerTable
      key={"table"}
      pageLimit={selectedTab === 'vulnerable' ? 150 : 50}
      fetchData={fetchTableData}
      sortOptions={sortOptions}
      resourceName={resourceName}
      hideQueryField={true}
      filters={filters}
      disambiguateLabel={disambiguateLabel}
      headers={tableHeaders}
      selectable={false}
      promotedBulkActions={promotedBulkActions}
      loading={loading}
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
      }}
      callFromOutside={updateTable}
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

  const progress = useMemo(() => {
    return currentTestObj.testsInitiated === 0 ? 0 : Math.floor((currentTestObj.testsInsertedInDb * 100) / currentTestObj.testsInitiated);
}, [currentTestObj.testingRunId]);

const runningTestsComp = useMemo(() => (
    currentTestObj.testingRunId !== -1 ? (
        <Card key={"test-progress"}>
            <VerticalStack gap={"3"}>
                <Text variant="headingSm">{`Running ${currentTestObj.testsInitiated} tests`}</Text>
                <div style={{ display: "flex", gap: '4px', alignItems: 'center' }}>
                    <ProgressBar progress={progress} color="primary" size="small" />
                    <Text color="subdued">{`${progress}%`}</Text>
                </div>
            </VerticalStack>
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

  const openVulnerabilityReport = async() => {
    const currentPageKey = "/dashboard/testing/" + selectedTestRun?.id + "/#" + selectedTab
    let selectedFilters = filtersMap[currentPageKey]?.filters || [];
    let filtersObj = {
      testingRunResultSummaryId: [currentSummary.hexId]
    }

    selectedFilters.forEach((filter) => {
      filtersObj[filter.key] = filter.value
    })
  
    await api.generatePDFReport(filtersObj, []).then((res) => {
      const responseId = res.split("=")[1];
      window.open('/dashboard/testing/summary/' + responseId.split("}")[0], '_blank');
    })
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

  useEffect(() => {
    if(Object.values(testRunResultsCount).length === 0) {
      setAllResultsLength(Object.values(testRunResultsCount).reduce((acc, val) => acc+val, 0))
    }
  }, [testRunResultsCount])
  const useComponents = (!workflowTest && allResultsLength === undefined && (selectedTestRun.run_type && selectedTestRun.run_type ==='One-time')) ? [<EmptyData key="empty"/>] : components
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
            <Button plain monochrome onClick={() => setUpdateTable(Date.now().toString())}><Tooltip content="Refresh page" dismissOnMouseOut> <Icon source={RefreshMajor} /></Tooltip></Button>
        </HorizontalStack>
        <HorizontalStack gap={"2"}>
          <HorizontalStack gap={"1"}>
            <Box><Icon color="subdued" source={CustomersMinor}/></Box>
            <Text color="subdued" fontWeight="medium" variant="bodyMd">created by:</Text>
            <Text color="subdued" variant="bodyMd">{selectedTestRun.userEmail}</Text>
          </HorizontalStack>
          <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px'/>
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

  let moreActionsList = transform.getActions(selectedTestRun)
  moreActionsList.push({title: 'Export', items: [
    {
     content: 'Export vulnerability report', 
     icon: ReportMinor, 
     onAction: () => openVulnerabilityReport()
    }
  ]})
  moreActionsList.push({title: 'Update', items:[
    {
      content: 'Edit testing config settings',
      icon: EditMajor,
      onAction: () => { setShowEditableSettings(true); handleAddSettings(); }
    }
  ]})
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
        primaryAction={!workflowTest ? <Box paddingInlineEnd={1}><Button primary onClick={() => 
          func.downloadAsCSV((testRunResultsText[selectedTab]), selectedTestRun)
          }>Export results</Button></Box>: undefined}
        secondaryActions={!workflowTest ? moreActionsComp: undefined}
        components={useComponents}
      />
      <ReRunModal selectedTestRun={selectedTestRun} shouldRefresh={false}/>
      {(resultId !== null && resultId.length > 0) ? <TestRunResultPage /> : null}
    </>
  );
}

export default SingleTestRunPage
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
  Modal,
} from '@shopify/polaris';

import {
  CircleInformationMajor,
  ArchiveMinor,
  PriceLookupMinor,
  ReportMinor,
  RefreshMajor,
  CustomersMinor,
  PlusMinor,
  SettingsMinor,
  ViewMajor
} from '@shopify/polaris-icons';
import api from "../api";
import observeApi from "../../observe/api";
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
import LocalStore from "../../../../main/LocalStorageStore";
import { produce } from "immer"
import GithubServerTable from "../../../components/tables/GithubServerTable";
import RunTest from '../../observe/api_collections/RunTest';
import TableStore from '../../../components/tables/TableStore'
import issuesFunctions from '@/apps/dashboard/pages/issues/module';
import TestingRunEndpointsModal from './TestingRunEndpointsModal';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

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

let filterOptions = [
  {
    key: 'severityStatus',
    label: 'Severity',
    title: 'Severity',
    choices: [
      { label: 'Critical', value: 'CRITICAL' },
      { label: 'High', value: 'HIGH' },
      { label: 'Medium', value: 'MEDIUM' },
      { label: 'Low', value: 'LOW' }
    ],
  },
  {
    key: 'method',
    label: 'Method',
    title: 'Method',
    choices: [
      { label: 'Get', value: 'GET' },
      { label: 'Post', value: 'POST' },
      { label: 'Put', value: 'PUT' },
      { label: 'Patch', value: 'PATCH' },
      { label: 'Delete', value: 'DELETE' }
    ],
  },
  {
    key: 'categoryFilter',
    label: 'Category',
    title: 'Category',
    choices: [],
  },
  {
    key: 'testFilter',
    label: 'Issue name',
    title: 'Issue name',
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
    label: mapLabel('API', getDashboardCategory()) + ' groups',
    title: mapLabel('API', getDashboardCategory()) + ' groups',
    choices: [],
  },
  {
    key: 'apiNameFilter',
    label: mapLabel('API', getDashboardCategory()) + ' Name',
    title: mapLabel('API', getDashboardCategory()) + ' name',
    choices: [],
  }
]

function SingleTestRunPage() {

  const [testRunResultsText, setTestRunResultsText] = useState({ vulnerable: [], no_vulnerability_found: [], skipped: [], need_configurations: [], ignored_issues: [] })
  const [selectedTestRun, setSelectedTestRun] = useState({});
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const params = useParams()
  const [loading, setLoading] = useState(false);
  const [tempLoading, setTempLoading] = useState({ vulnerable: false, no_vulnerability_found: false, skipped: false, running: false, need_configurations: false, ignored_issues: false })
  const [selectedTab, setSelectedTab] = useState("vulnerable")
  const [selected, setSelected] = useState(0)
  const [workflowTest, setWorkflowTest] = useState(false);
  const [secondaryPopover, setSecondaryPopover] = useState(false)
  const setErrorsObject = TestingStore((state) => state.setErrorsObject)
  const setTestingEndpointsApisList = TestingStore((state) => state.setTestingEndpointsApisList)
  const currentTestingRuns = []
  const [updateTable, setUpdateTable] = useState("")
  const [testRunResultsCount, setTestRunResultsCount] = useState({})
  const [testRunCountMap, setTestRunCountMap] = useState({
      "ALL": 0,
      "SKIPPED_EXEC_NEED_CONFIG": 0,
      "VULNERABLE": 0,
      "SKIPPED_EXEC_API_REQUEST_FAILED": 0,
      "SKIPPED_EXEC": 0,
      "IGNORED_ISSUES": 0
    })  
  const [testMode, setTestMode] = useState(false)

  const initialTestingObj = { testsInitiated: 0, testsInsertedInDb: 0, testingRunId: -1 }
  const [currentTestObj, setCurrentTestObj] = useState(initialTestingObj)
  const [missingConfigs, setMissingConfigs] = useState([])
  const [showEditableSettings, setShowEditableSettings] = useState(false);
  const [testingRunConfigSettings, setTestingRunConfigSettings] = useState([])
  const [testingRunConfigId, setTestingRunConfigId] = useState(-1)
  const apiCollectionMap = PersistStore(state => state.collectionsMap);

  const filtersMap = PersistStore.getState().filtersMap;

  const [testingRunResultSummariesObj, setTestingRunResultSummariesObj] = useState({})
  const [allResultsLength, setAllResultsLength] = useState(undefined)
  const [currentSummary, setCurrentSummary] = useState('')
  const [testResultsStatsCount, setTestResultsStatsCount] = useState(0)
  const [allTestResultsStats, setAllTestResultsStats] = useState({
    count429: 0,
    count500: 0,
    countCloudflare: 0,
    totalCount: 0
  })

  const localCategoryMap = LocalStore.getState().categoryMap
  const localSubCategoryMap = LocalStore.getState().subCategoryMap
  const [useLocalSubCategoryData, setUseLocalSubCategoryData] = useState(false)
  const [copyUpdateTable, setCopyUpdateTable] = useState("");
  const [confirmationModal, setConfirmationModal] = useState(false);

  const tableTabMap = {
    vulnerable: "VULNERABLE",
    domain_unreachable: "SKIPPED_EXEC_API_REQUEST_FAILED",
    skipped: "SKIPPED_EXEC",
    need_configurations: "SKIPPED_EXEC_NEED_CONFIG",
    no_vulnerability_found: "SECURED",
    ignored_issues: "IGNORED_ISSUES"
  }

  const [copyFilters, setCopyFilters] = useState({})

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
      case 'apiNameFilter':
        return func.convertToDisambiguateLabelObj(value, null, 1)
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

  function fillTempData(data, key) {
    setTestRunResultsText((prev) => {
      prev[key] = data;
      return { ...prev };
    })
  }

  async function fetchTestResultsStats(testingRunHexId, testingRunResultSummaryHexId) {
    try {
      if (testingRunHexId && testingRunResultSummaryHexId) {
        const reqBase = { testingRunHexId: testingRunHexId, testingRunResultSummaryHexId: testingRunResultSummaryHexId }
        
        const [res429, res5xx, resCf] = await Promise.allSettled([
          api.fetchTestResultsStatsCount({ ...reqBase, patternType: 'HTTP_429' }),
          api.fetchTestResultsStatsCount({ ...reqBase, patternType: 'HTTP_5XX' }),
          api.fetchTestResultsStatsCount({ ...reqBase, patternType: 'CLOUDFLARE' })
        ]);

        const count429 = res429.status === 'fulfilled' ? (res429.value || 0) : 0;
        const count500 = res5xx.status === 'fulfilled' ? (res5xx.value || 0) : 0;
        const countCloudflare = resCf.status === 'fulfilled' ? (resCf.value || 0) : 0;

        setTestResultsStatsCount(count429);
        setAllTestResultsStats({
          count429,
          count500,
          countCloudflare,
          totalCount: count429 + count500 + countCloudflare
        });
      } else {
        setTestResultsStatsCount(0);
        setAllTestResultsStats({ count429: 0, count500: 0, countCloudflare: 0, totalCount: 0 });
      }
    } catch (error) {
      setTestResultsStatsCount(0);
      setAllTestResultsStats({ count429: 0, count500: 0, countCloudflare: 0, totalCount: 0 });
    }
  }

  async function setSummary(summary, initialCall = false) {
    setTempLoading((prev) => {
      prev.running = false;
      return prev;
    });
    clearInterval(refreshId.current);
    setSelectedTestRun((prev) => {
      let tmp = { ...summary };
      if (tmp === null || tmp?.countIssues === null || tmp?.countIssues === undefined) {
        tmp.countIssues = {
          "CRITICAL": 0,
          "HIGH": 0,
          "MEDIUM": 0,
          "LOW": 0
        }
      }
      tmp.countIssues = transform.prepareCountIssues(tmp.countIssues);
      prev = { ...prev, ...transform.prepareDataFromSummary(tmp, prev.testRunState) }

      return { ...prev };
    });
    let updateTable = currentSummary.hexId !== summary.hexId;
    setCurrentSummary(summary);
    
    // Fetch test results stats for the new summary
    if (summary && summary.hexId) {
      fetchTestResultsStats(hexId, summary.hexId);
    }
    
    if (!initialCall && updateTable) {
      setUpdateTable(Date.now().toString())
    }
  }

  useEffect(() => {
    if (
      (localCategoryMap && Object.keys(localCategoryMap).length > 0) &&
      (localSubCategoryMap && Object.keys(localSubCategoryMap).length > 0)
    ) {
      setUseLocalSubCategoryData(true)
    }
    setUpdateTable(Date.now().toString())
  }, [testingRunResultSummariesObj])

  filterOptions = func.getCollectionFilters(filterOptions)
  let store = {}
  let result = []
  let issueName = []
  Object.values(localSubCategoryMap).forEach((x) => {
      let superCategory = x.superCategory
      if (!store[superCategory.name]) {
          result.push({ "label": superCategory.displayName, "value": superCategory.name })
          store[superCategory.name] = []
      }
      store[superCategory.name].push(x._name);
      issueName.push({"label": x.testName, "value": x._name})
  })
  filterOptions.forEach((filter) => {
    if (filter.key === 'categoryFilter') {
      filter.choices = [].concat(result)
    } else if (filter.key === 'testFilter') { 
      filter.choices = [].concat(issueName)
    }
  })

  const populateTestingEndpointsApisList = (apiEndpoints) => {
    const testingEndpointsApisList = transform.prepareTestingEndpointsApisList(apiEndpoints)
    setTestingEndpointsApisList(testingEndpointsApisList)
  } 

  const populateApiNameFilterChoices = async (testingRun) => {
    if (testingRun?.testingEndpoints) {
      const {testingEndpoints} = testingRun;
      let apiEndpoints = [];

      if (testingEndpoints.type === "COLLECTION_WISE") {
        const collectionId = testingEndpoints.apiCollectionId;
        if (collectionId) {
          try {
            const response = await observeApi.fetchApiInfosForCollection(
                collectionId);
            if (response?.apiInfoList) {
              const limitedEndpoints = response.apiInfoList.slice(
                  0, 5000);

              const limitedEndpointsIds = limitedEndpoints.map(endpoint => endpoint.id);
              populateTestingEndpointsApisList(limitedEndpointsIds);

              apiEndpoints = getApiEndpointsMap(limitedEndpoints, testingEndpoints.type);
            }
          } catch (error) {
            console.error("Error fetching collection endpoints:", error);
          }
        }
      } else if (testingEndpoints.type === "CUSTOM"
          && testingEndpoints.apisList) {
        const limitedApis = testingEndpoints.apisList.slice(0, 5000);
        populateTestingEndpointsApisList(limitedApis);
        apiEndpoints = getApiEndpointsMap(limitedApis, testingEndpoints.type);
      }

      filterOptions = filterOptions.map(filter => {
        if (filter.key === 'apiNameFilter') {
          return {
            ...filter,
            choices: apiEndpoints
          };
        }
        return filter;
      });
      setUpdateTable(Date.now().toString());
    }
  }

  const getApiEndpointsMap = (endpoints, type) => {
    if(type === null || type === undefined || type === "COLLECTION_WISE"){
      return endpoints.map(endpoint => ({
        label: endpoint.id.url,
        value: endpoint.id.url
      }));
    }else{
      return endpoints.map(endpoint => ({
        label: endpoint.url,
        value: endpoint.url
      }));
    }
  }

  const fetchTestingRunResultSummaries = async () => {
    let tempTestingRunResultSummaries = [];
    await api.fetchTestingRunResultSummaries(hexId).then(async ({ testingRun, testingRunResultSummaries, workflowTest, testingRunType }) => {
      tempTestingRunResultSummaries = testingRunResultSummaries
      setTestingRunResultSummariesObj({
        testingRun, workflowTest, testingRunType
      })
      if (testingRun) {
        await populateApiNameFilterChoices(testingRun)
      }
    })
    const timeNow = func.timeNow()
    const defaultIgnoreTime = LocalStore.getState().defaultIgnoreSummaryTime
    tempTestingRunResultSummaries.sort((a, b) => {
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
    let localCountMap = testRunCountMap;
    const { testingRun, workflowTest, testingRunType } = testingRunResultSummariesObj
    if (testingRun === undefined) {
      return { value: [], total: 0 }
    }

    if (testingRun.testIdConfig === 1) {
      setWorkflowTest(workflowTest);
    }


    if(filters?.categoryFilter?.length > 0 && filters?.testFilter?.length > 0){
      let filterSubCategory = []
      filters?.categoryFilter?.forEach((issue) => {
          filterSubCategory = filterSubCategory.concat(store[issue])
      })
      filterSubCategory = [...filterSubCategory, ...filters?.testFilter]
      filters.testFilter = [...filterSubCategory]
    }

    let cicd = testingRunType === "CI_CD";
    const localSelectedTestRun = transform.prepareTestRun(testingRun, currentSummary, cicd, false);
    setTestingRunConfigSettings(testingRun.testingRunConfig?.configsAdvancedSettings || [])
    setTestingRunConfigId(testingRun.testingRunConfig?.id || -1)
    setSelectedTestRun(localSelectedTestRun);
    if (localSelectedTestRun.testingRunResultSummaryHexId) {
      await api.fetchTestingRunResults(localSelectedTestRun.testingRunResultSummaryHexId, tableTabMap[selectedTab], sortKey, sortOrder, skip, limit, filters, queryValue).then(({ testingRunResults, errorEnums, issuesDescriptionMap, jiraIssuesMapForResults }) => {
          testRunResultsRes = transform.prepareTestRunResults(hexId, testingRunResults, localSubCategoryMap, subCategoryFromSourceConfigMap, issuesDescriptionMap, jiraIssuesMapForResults)
          if (selectedTab === 'domain_unreachable' || selectedTab === 'skipped' || selectedTab === 'need_configurations') {
            errorEnums['UNKNOWN_ERROR_OCCURRED'] = "OOPS! Unknown error occurred."
            setErrorsObject(errorEnums)
            setMissingConfigs(transform.getMissingConfigs(testRunResultsRes))
          }
      })
      if (!func.deepComparison(copyFilters, filters) || copyUpdateTable !== updateTable) {
        if(copyUpdateTable !== updateTable){
          setCopyUpdateTable(updateTable)
        }else{
          setCopyFilters(filters)
        }
        await api.fetchTestRunResultsCount(localSelectedTestRun.testingRunResultSummaryHexId, filters).then((testCountMap) => {
          if(testCountMap !== null){
            localCountMap = JSON.parse(JSON.stringify(testCountMap))  
          }
          let countOthers = 0;
          Object.keys(localCountMap).forEach((x) => {
            if (x !== 'ALL') {
              countOthers += localCountMap[x]
            }
          })
          localCountMap['SECURED'] = localCountMap['ALL'] >= countOthers ? localCountMap['ALL'] - countOthers : 0
          localCountMap['VULNERABLE'] = Math.abs(localCountMap['VULNERABLE'] - localCountMap['IGNORED_ISSUES']);
          const orderedValues = tableTabsOrder.map(key => localCountMap[tableTabMap[key]] || 0)
          setTestRunResultsCount(orderedValues)
          setTestRunCountMap(JSON.parse(JSON.stringify(localCountMap)));
        })
      }
    }
    const key = tableTabMap[selectedTab]
    const total = localCountMap[key]
    fillTempData(testRunResultsRes, selectedTab)
    return { value: transform.getPrettifiedTestRunResults(testRunResultsRes), total: total }
  }

  useEffect(() => { handleAddSettings() }, [testingRunConfigSettings])

  useEffect(() => {
    fetchTestingRunResultSummaries()
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

    issuesFunctions.fetchIntegrationCustomFieldsMetadata();
  }, []);

  const promotedBulkActions = () => {
    let totalSelectedItemsSet = new Set(TableStore.getState().selectedItems.flat())

    return [
      {
        content: `Rerun ${totalSelectedItemsSet.size} test${totalSelectedItemsSet.size === 1 ? '' : 's'}`,
        onAction: () => {
        if (totalSelectedItemsSet.size > 0) {
          transform.rerunTest(selectedTestRun.id, null, false, [...totalSelectedItemsSet], selectedTestRun.testingRunResultSummaryHexId)
        }
        },
      },
    ]
  };

  function getHeadingStatus(selectedTestRun) {

    switch (selectedTestRun?.summaryState) {
      case "RUNNING":
        return "Test is running";
      case "SCHEDULED":
        return "Test has been scheduled";
      case "STOPPED":
        return "Test has been stopped";
      case "COMPLETED":

        let delta = Math.abs(selectedTestRun.startTimestamp - selectedTestRun.endTimestamp);
        let earlyFinish = "";
        let testRunTime = testingRunResultSummariesObj?.testingRun?.testRunTime;
        if (testRunTime == null || testRunTime == undefined || testRunTime <= 0) {
          testRunTime = 1800;
        }

        if (delta >= testRunTime) {
          earlyFinish = "| Test exited because max time limit reached"
        }

        return `Scanned ${func.prettifyEpoch(selectedTestRun.startTimestamp)} for a duration of
        ${func.getTimeTakenByTest(selectedTestRun.startTimestamp, selectedTestRun.endTimestamp)} ${earlyFinish}`;
      case "FAILED":
      case "FAIL":
        return "Test execution has failed during run";
      default:
        return "No summary for test exists";
    }
  }

  const modifyData = (data, filters) => {
    if (filters?.urlFilters?.length > 0) {
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
    } else {
      return data
    }
  }

  const baseUrl = window.location.origin + "/dashboard/testing/roles/details?system=";

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
                return (<Link url={baseUrl + config.toUpperCase()} key={config} target="_blank">
                  {config}
                </Link>)
              })}
            </HorizontalStack>
          </HorizontalStack>
        </Banner>
      </div> : null
  )

  const definedTableTabs = ['Vulnerable', 'Need configurations', 'Skipped', 'No vulnerability found', 'Domain unreachable', 'Ignored Issues']

  const { tabsInfo } = useTable()
  const tableCountObj = func.getTabsCount(definedTableTabs, {}, Object.values(testRunResultsCount))
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)
  const tableHeaders = transform.getHeaders(selectedTab)

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setUpdateTable("")

    sortOptions = sortOptions.map(option => {
      if (selectedIndex === 0 || selectedIndex === 5) {
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

    filterOptions = filterOptions.filter(filter => filter.key !== 'severityStatus')

    if (selectedIndex === 0 || selectedIndex === 5) {
      filterOptions = [
        {
          key: 'severityStatus',
          label: 'Severity',
          title: 'Severity',
          choices: [
            { label: 'High', value: 'HIGH' },
            { label: 'Medium', value: 'MEDIUM' },
            { label: 'Low', value: 'LOW' }
          ],
        },
        ...filterOptions
      ]
    }

    setTimeout(() => {
      setLoading(false)
    }, 200)
  }

  function getCollectionId() {
    const testingEndpoints = testingRunResultSummariesObj?.testingRun?.testingEndpoints;

    if (!testingEndpoints) return undefined;

    if (testingEndpoints.type === "COLLECTION_WISE") {
      return testingEndpoints.apiCollectionId;
    }

    return (testingEndpoints.apisList?.length > 0) ? testingEndpoints.apisList[0].apiCollectionId : undefined;
  }
  
  const [activeFromTesting, setActiveFromTesting] = useState(false)
  
  const [showTestingEndpointsModal, setShowTestingEndpointsModal] = useState(false)

  const resultTable = (
    <>
      <RunTest
        key={"run-test"} 
        activeFromTesting={activeFromTesting} 
        setActiveFromTesting={setActiveFromTesting} 
        preActivator={true}
        testIdConfig={testingRunResultSummariesObj?.testingRun} 
        apiCollectionId={getCollectionId()} 
        setTestMode={setTestMode} 
        setShowEditableSettings={setShowEditableSettings} 
        showEditableSettings={showEditableSettings}
        parentAdvanceSettingsConfig={conditions} 
        useLocalSubCategoryData={useLocalSubCategoryData} 
        testRunType={testingRunResultSummariesObj?.testingRunType} 
        disabled={window.USER_ROLE === "GUEST"}
        shouldDisable={selectedTestRun.type === "CI_CD" || selectedTestRun.type === "RECURRING"}
      />
      <TestingRunEndpointsModal
        key={"testing-endpoints-modal"}
        showTestingEndpointsModal={showTestingEndpointsModal}
        setShowTestingEndpointsModal={setShowTestingEndpointsModal}
        testingEndpoints={testingRunResultSummariesObj?.testingRun?.testingEndpoints}
      />
      <GithubServerTable
        key={"table"}
        pageLimit={selectedTab === 'vulnerable' ? 150 : 50}
        fetchData={fetchTableData}
        sortOptions={sortOptions}
        resourceName={resourceName}
        hideQueryField={true}
        filters={filterOptions}
        disambiguateLabel={disambiguateLabel}
        headers={tableHeaders}
        selectable={true}
        promotedBulkActions={promotedBulkActions}
        loading={loading}
        getStatus={func.getTestResultStatus}
        mode={IndexFiltersMode.Default}
        headings={tableHeaders}
        useNewRow={true}
        isMultipleItemsSelected={true}
        condensedHeight={true}
        useModifiedData={true}
        modifyData={(data, filters) => modifyData(data, filters)}
        notHighlightOnselected={false}
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
    <Modal
        open={confirmationModal}
        onClose={() => setConfirmationModal(false)}
        title="Re-Calculate issues count"
        primaryAction={{
          content: 'Re-Calculate',
          onAction: () => handleRefreshTableCount(currentSummary.hexId),
        }}
        secondaryActions={[
          {
            content: 'Cancel',
            onAction: () => setConfirmationModal(false),
          },
        ]}
      >
        <Modal.Section>
          <Text>{"Are you sure you want to re-calculate issues count? This will recalculate the total number of issues based on the latest" + mapLabel('test results', getDashboardCategory()) + " and may affect the FIXED or IGNORED issues in the current testing run"}</Text>
        </Modal.Section>
      </Modal>
    </>
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

    if (!selectedTestRun.metadata) {
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


  const components = [
    runningTestsComp, <TrendChart key={tempLoading.running} hexId={hexId} setSummary={setSummary} show={true} totalVulnerabilities={tableCountObj.vulnerable} />,
    metadataComponent(), loading ? <SpinnerCentered key="loading" /> : (!workflowTest ? resultTable : workflowTestBuilder)];

  const openVulnerabilityReport = async (summaryMode = false) => {
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
      const summaryModeQueryParam = summaryMode === true ? 'summaryMode=true' : '';
      const redirectUrl = `/dashboard/testing/summary/${responseId.split("}")[0]}?${summaryModeQueryParam}`;
      window.open(redirectUrl, '_blank');
    })
  }

  const handleAddSettings = () => {
    if (conditions.length === 0 && testingRunConfigSettings.length > 0) {
      testingRunConfigSettings.forEach((condition) => {
        const operatorType = condition.operatorType
        condition.operationsGroupList.forEach((obj) => {
          const finalObj = { 'data': obj, 'operator': { 'type': operatorType } }
          dispatchConditions({ type: "add", obj: finalObj })
        })
      })
    }
  }

  const handleRefreshTableCount = async (summaryHexId) => {
    await api.handleRefreshTableCount(summaryHexId).then((res) => {
      func.setToast(true, false, "Re-calculating issues count")
      setSecondaryPopover(false)
      setConfirmationModal(false)
    })
  }

  const EmptyData = () => {
    return (
      <div style={{ margin: 'auto', marginTop: '20vh' }}>
        <Box width="300px" padding={4}>
          <VerticalStack gap={5}>
            <HorizontalStack align="center">
              <div style={{ borderRadius: '50%', border: '6px solid white', padding: '4px', display: 'flex', alignItems: 'center', height: '50px', width: '50px' }}>
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
    if (Object.values(testRunResultsCount).length === 0) {
      setAllResultsLength(Object.values(testRunResultsCount).reduce((acc, val) => acc + val, 0))
    }
  }, [testRunResultsCount])
  const useComponents = (!workflowTest && allResultsLength === undefined && (selectedTestRun.run_type && selectedTestRun.run_type === 'One-time')) ? [<EmptyData key="empty" />] : components
  const headingComp = (
    <Box paddingBlockStart={1}>
      <VerticalStack gap="2">
        <HorizontalStack gap="2" align="start">
          {selectedTestRun?.icon && <Box>
            <Icon color={selectedTestRun.iconColor} source={selectedTestRun.icon}></Icon>
          </Box>
          }
          <Box maxWidth="35vw">
            <TooltipText
              tooltip={selectedTestRun?.name}
              text={selectedTestRun?.name || "Test run name"}
              textProps={{ variant: "headingLg" }} />
          </Box>
          {
            selectedTestRun?.severity &&
            selectedTestRun.severity.map((item) => {
              const sev = item.split(' ')
              const tempSev = sev.length > 1 ? sev[1].toUpperCase() : ''
              return (
                <div key={item} className={`badge-wrapper-${tempSev}`}>
                  <Badge>{item}</Badge>
                </div>
              )
            }
            )}
          <Button plain monochrome onClick={() => setUpdateTable(Date.now().toString())}><Tooltip content="Refresh page" dismissOnMouseOut> <Icon source={RefreshMajor} /></Tooltip></Button>
        </HorizontalStack>
        <HorizontalStack gap={"2"}>
          <HorizontalStack gap={"1"}>
            <Box><Icon color="subdued" source={CustomersMinor} /></Box>
            <Text color="subdued" fontWeight="medium" variant="bodyMd">created by:</Text>
            <Text color="subdued" variant="bodyMd">{selectedTestRun.userEmail}</Text>
          </HorizontalStack>
          <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
          <Link monochrome target="_blank" url={"/dashboard/observe/inventory/" + selectedTestRun?.apiCollectionId} removeUnderline>
            <HorizontalStack gap={"1"}>
              <Box><Icon color="subdued" source={ArchiveMinor} /></Box>
              <Text color="subdued" variant="bodyMd">{collectionsMap[selectedTestRun?.apiCollectionId]}</Text>
            </HorizontalStack>
          </Link>
          <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
          <HorizontalStack gap={"1"}>
            <Box><Icon color="subdued" source={PriceLookupMinor} /></Box>
            <Text color="subdued" variant="bodyMd">{getHeadingStatus(selectedTestRun)}</Text>
          </HorizontalStack>
          {allTestResultsStats.totalCount > 0 && (
            <>
              <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
              <HorizontalStack gap={"1"}>
                <Box><Icon color="subdued" source={CircleInformationMajor} /></Box>
                <Tooltip 
                  content={
                    <VerticalStack gap="2">
                      <Text variant="bodyMd">API request error statistics breakdown:</Text>
                      <VerticalStack gap="1">
                        <Text variant="bodySm">• 429 errors: {allTestResultsStats.count429}</Text>
                        <Text variant="bodySm">• 5XX errors: {allTestResultsStats.count500}</Text>
                        <Text variant="bodySm">• Cloudflare errors: {allTestResultsStats.countCloudflare}</Text>
                      </VerticalStack>
                      <Box paddingBlockStart="1" borderBlockStartWidth="1" borderColor="border-subdued">
                        <Text variant="bodySm" color="subdued" fontWeight="medium">Approximate counts based on sampled data.</Text>
                      </Box>
                    </VerticalStack>
                  } 
                  hasUnderline={false}
                >
                  <HorizontalStack gap="1" align="center">
                    <Text color="subdued" fontWeight="medium" variant="bodyMd" style={{ cursor: 'pointer' }}>API error stats:</Text>
                  </HorizontalStack>
                </Tooltip>
                {(() => {
                  const total = currentSummary?.testResultsCount || 0;
                  const severityFor = (count) => {
                    const percentage = total > 0 ? (count / total) * 100 : 0;
                    if (percentage > 70) return 'CRITICAL';
                    if (percentage >= 40) return 'HIGH';
                    return 'MEDIUM';
                  }
                  return (
                    <HorizontalStack gap="2" align="center">
                      {(() => { const sev = severityFor(allTestResultsStats.count429); return (
                        <div className={`badge-wrapper-${sev.toUpperCase()}`}>
                          <Badge>
                            429: {allTestResultsStats.count429}
                          </Badge>
                        </div>
                      )})()}
                      {(() => { const sev = severityFor(allTestResultsStats.count500); return (
                        <div className={`badge-wrapper-${sev.toUpperCase()}`}>
                          <Badge>
                            5XX: {allTestResultsStats.count500}
                          </Badge>
                        </div>
                      )})()}
                      {(() => { const sev = severityFor(allTestResultsStats.countCloudflare); return (
                        <div className={`badge-wrapper-${sev.toUpperCase()}`}>
                          <Badge>
                          Cloudflare errors: {allTestResultsStats.countCloudflare}
                          </Badge>
                        </div>
                      )})()}
                    </HorizontalStack>
                  );
                })()}
              </HorizontalStack>
            </>
          )}
        </HorizontalStack>
      </VerticalStack>
    </Box>
  )


  let moreActionsList = transform.getActions(selectedTestRun)
  moreActionsList.push({
    title: 'Export', items: [
      {
        content: 'Export summary report',
        icon: ReportMinor,
        onAction: () => openVulnerabilityReport(true)
      },
      {
        content: 'Export vulnerability report',
        icon: ReportMinor,
        onAction: () => openVulnerabilityReport(false)
      }
    ]
  })
  moreActionsList.push({
    title: 'Edit',
    items: [
      {
        content: mapLabel("More Tests", getDashboardCategory()),
        icon: PlusMinor,
        onAction: () => { setActiveFromTesting(true) }
      },
      {
        content: 'Configurations',
        icon: SettingsMinor,
        onAction: () => { setShowEditableSettings(true); handleAddSettings() }
      }
    ]
  })
  moreActionsList.push({
    title: 'More',
    items: [
      {
        content: 'See ' + mapLabel("APIs", getDashboardCategory()),
        icon: ViewMajor,
        onAction: () => { setShowTestingEndpointsModal(true) }
      },
      {
        content: 'Re-Calculate Issues Count',
        icon: RefreshMajor,
        onAction: () => { setConfirmationModal(true) }
      }
    ]
  })
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
        primaryAction={!workflowTest ? <Box paddingInlineEnd={1}><Button primary onClick={() =>
          func.downloadAsCSV((testRunResultsText[selectedTab]), selectedTestRun)
        }>Export results</Button></Box> : undefined}
        secondaryActions={!workflowTest ? moreActionsComp : undefined}
        components={useComponents}
      />
      <ReRunModal selectedTestRun={selectedTestRun} shouldRefresh={false} />
      {(resultId !== null && resultId.length > 0) ? <TestRunResultPage /> : null}
    </>
  );
}

export default SingleTestRunPage

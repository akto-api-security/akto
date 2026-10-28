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
  ViewMajor,
  CircleAlertMajor,
  FileMinor,
  MagicMinor
} from '@shopify/polaris-icons';
import api from "../api";
import observeApi from "../../observe/api";
import observeFunc from "../../observe/transform";
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
import TestRunLogs from "./TestRunLogs";
import useTable from "../../../components/tables/TableContext";
import ReRunModal from "./ReRunModal";
import TestingStore from "../testingStore";
import { useSearchParams } from "react-router-dom";
import TestRunResultPage from "../TestRunResultPage/TestRunResultPage";
import { isRunAutomatedTestsEnabled } from "../TestRunResultPage/smartTestingUtils";
import LocalStore from "../../../../main/LocalStorageStore";
import { produce } from "immer"
import GithubServerTable from "../../../components/tables/GithubServerTable";
import RunTest from '../../observe/api_collections/RunTest';
import TableStore from '../../../components/tables/TableStore'
import issuesFunctions from '@/apps/dashboard/pages/issues/module';
import issuesApi from '../../issues/api';
import TestingRunEndpointsModal from './TestingRunEndpointsModal';
import { getDashboardCategory, mapLabel, categoryToShortName } from '../../../../main/labelHelper';
import MarkdownReportGenerator from "../../../components/shared/MarkdownReportGenerator";
import { saveAs } from 'file-saver';
import SeveritySelector from '../../issues/components/SeveritySelector';
import { buildResponseCodeFilterChoices, getHttpStatusIssueList, HTTP_CODE_LABELS } from "../TestRunsPage/runStatusUtils";
import { usePermissions, withPermissions } from "@/util/permissions";

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
  },
  {
    key: 'aiVerdict',
    label: 'AI verdict',
    title: 'AI verdict',
    choices: [
      { label: 'False positive', value: 'FALSE_POSITIVE' },
      { label: 'True positive', value: 'TRUE_POSITIVE' },
      { label: 'Not verified', value: 'NOT_VERIFIED' }
    ],
  },
]

// CSV export pages through fetchTestingRunResults in raw-result units (skip/limit count results, not
// the grouped rows the table shows). The backend honours `limit` except for the Ignored tab, which
// it fixes at 1000 (StartTestAction.fetchTestingRunResults).
const EXPORT_PAGE_SIZE = 500
const EXPORT_PAGE_SIZE_IGNORED = 1000
const EXPORT_MAX_RESULTS = 50000

// Turns the raw per-query-mode counts into the per-tab counts the page shows.
function withDerivedCounts(rawCountMap) {
  const counts = { ...rawCountMap }
  let countOthers = 0
  Object.keys(counts).forEach((x) => {
    if (x !== 'ALL') {
      countOthers += counts[x]
    }
  })
  counts['SECURED'] = counts['ALL'] >= countOthers ? counts['ALL'] - countOthers : 0
  counts['VULNERABLE'] = Math.abs(counts['VULNERABLE'] - counts['IGNORED_ISSUES'])
  return counts
}

function SingleTestRunPage() {

  const [selectedTestRun, setSelectedTestRun] = useState({});
  const subCategoryFromSourceConfigMap = PersistStore(state => state.subCategoryFromSourceConfigMap);
  const params = useParams()
  const { canCall } = usePermissions()
  const [loading, setLoading] = useState(false);
  const [tempLoading, setTempLoading] = useState({ vulnerable: false, no_vulnerability_found: false, skipped: false, running: false, need_configurations: false, ignored_issues: false })
  const [selectedTab, setSelectedTab] = useState("vulnerable")
  const [selected, setSelected] = useState(0)
  const [workflowTest, setWorkflowTest] = useState(false);
  const [secondaryPopover, setSecondaryPopover] = useState(false)
  const [logsModalActive, setLogsModalActive] = useState(false)
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
  const [statusCounts, setStatusCounts] = useState({})

  const localCategoryMap = LocalStore.getState().categoryMap
  const localSubCategoryMap = LocalStore.getState().subCategoryMap
  const [useLocalSubCategoryData, setUseLocalSubCategoryData] = useState(false)
  const [copyUpdateTable, setCopyUpdateTable] = useState("");
  const [confirmationModal, setConfirmationModal] = useState(false);
  const [aiVerifyModalActive, setAiVerifyModalActive] = useState(false);
  // Summary already sent for AI verification from this page, so the button can't be clicked twice.
  const [aiVerifyStartedFor, setAiVerifyStartedFor] = useState(null);
  // Result hexIds to verify; empty = every vulnerable result of the run.
  const [aiVerifyTargets, setAiVerifyTargets] = useState([]);
  const [reopenInProgress, setReopenInProgress] = useState(false);
  const [severityModalActive, setSeverityModalActive] = useState(false);
  const [selectedTestResultItems, setSelectedTestResultItems] = useState([]);
  const [severityUpdateInProgress, setSeverityUpdateInProgress] = useState(false);
  const [chartRefreshCounter, setChartRefreshCounter] = useState(0);

  const tableTabMap = {
    vulnerable: "VULNERABLE",
    domain_unreachable: "SKIPPED_EXEC_API_REQUEST_FAILED",
    skipped: "SKIPPED_EXEC",
    need_configurations: "SKIPPED_EXEC_NEED_CONFIG",
    no_vulnerability_found: "SECURED",
    ignored_issues: "IGNORED_ISSUES"
  }

  const [copyFilters, setCopyFilters] = useState({})
  const [responseCodeFilterChoices, setResponseCodeFilterChoices] = useState([])

  const refreshId = useRef(null);
  const exportInProgress = useRef(false);
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
      case 'responseCodeFilter':
        return func.convertToDisambiguateLabelObj(value, HTTP_CODE_LABELS, 2)
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

  // Pull the full per-status-code execution breakdown (403, 404, 429, 5xx, cloudflare, domain
  // unreachable, skipped, ...) from the same bulk endpoint the test runs list column uses, so the
  // header surfaces every error code present in the run instead of just 429/5xx/cloudflare.
  async function fetchTestResultsStats(testingRunResultSummaryHexId) {
    try {
      if (testingRunResultSummaryHexId) {
        const summaries = await api.fetchTestRunStatusSummaries([testingRunResultSummaryHexId]);
        setStatusCounts(summaries?.[testingRunResultSummaryHexId] || {});
      } else {
        setStatusCounts({});
      }
    } catch (error) {
      setStatusCounts({});
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
      fetchTestResultsStats(summary.hexId);
      api.fetchDistinctResponseCodes(summary.hexId)
        .then((codes) => setResponseCodeFilterChoices(buildResponseCodeFilterChoices(codes)))
        .catch(() => setResponseCodeFilterChoices([]));
    } else {
      setResponseCodeFilterChoices([]);
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
    issueName.push({ "label": x.testName, "value": x._name })
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
      const { testingEndpoints } = testingRun;
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
      } else if (testingEndpoints.type === "MULTI_COLLECTION" && testingEndpoints.apiCollectionIds) {
        try {
          let allEndpoints = [];
          for (const collectionId of testingEndpoints.apiCollectionIds) {
            try {
              const response = await observeApi.fetchApiInfosForCollection(collectionId);
              if (response?.apiInfoList) {
                allEndpoints = allEndpoints.concat(response.apiInfoList);
              }
            } catch (error) {
            }
          }

          const limitedEndpoints = allEndpoints.slice(0, 5000);
          const limitedEndpointsIds = limitedEndpoints.map(endpoint => endpoint.id);
          populateTestingEndpointsApisList(limitedEndpointsIds);

          apiEndpoints = getApiEndpointsMap(limitedEndpoints, testingEndpoints.type);
        } catch (error) {
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
    if (type === null || type === undefined || type === "COLLECTION_WISE" || type === "MULTI_COLLECTION") {
      return endpoints.map(endpoint => ({
        label: endpoint.id.url,
        value: endpoint.id.url
      }));
    } else {
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

  // A category filter plus an issue-name filter selects the union of both; sets filters.testFilter in place.
  const mergeCategoryIntoTestFilter = (filters) => {
    if (filters?.categoryFilter?.length > 0 && filters?.testFilter?.length > 0) {
      let filterSubCategory = []
      filters?.categoryFilter?.forEach((issue) => {
        filterSubCategory = filterSubCategory.concat(store[issue])
      })
      filterSubCategory = [...filterSubCategory, ...filters?.testFilter]
      filters.testFilter = [...filterSubCategory]
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


    mergeCategoryIntoTestFilter(filters)

    let cicd = testingRunType === "CI_CD";
    const localSelectedTestRun = transform.prepareTestRun(testingRun, currentSummary, cicd, false);
    setTestingRunConfigSettings(testingRun.testingRunConfig?.configsAdvancedSettings || [])
    setTestingRunConfigId(testingRun.testingRunConfig?.id || -1)
    setSelectedTestRun(localSelectedTestRun);
    if (localSelectedTestRun.testingRunResultSummaryHexId) {
      // Start both API calls in parallel
      const shouldFetchCount = !func.deepComparison(copyFilters, filters) || copyUpdateTable !== updateTable;

      const fetchResultsPromise = api.fetchTestingRunResults(
        localSelectedTestRun.testingRunResultSummaryHexId,
        tableTabMap[selectedTab],
        sortKey,
        sortOrder,
        skip,
        limit,
        filters,
        queryValue
      ).then(({ testingRunResults, errorEnums, issuesDescriptionMap, jiraIssuesMapForResults, devrevIssuesMapForResults }) => {
        testRunResultsRes = transform.prepareTestRunResults(hexId, testingRunResults, localSubCategoryMap, subCategoryFromSourceConfigMap, issuesDescriptionMap, jiraIssuesMapForResults, devrevIssuesMapForResults)
        if (selectedTab === 'domain_unreachable' || selectedTab === 'skipped' || selectedTab === 'need_configurations') {
          errorEnums['UNKNOWN_ERROR_OCCURRED'] = "OOPS! Unknown error occurred."
          setErrorsObject(errorEnums)
          setMissingConfigs(transform.getMissingConfigs(testRunResultsRes))
        }
      }).catch((error) => {
        console.error('Error fetching test run results:', error);
        // Continue with empty results if this fails
      });

      const fetchCountPromise = shouldFetchCount ? api.fetchTestRunResultsCount(
        localSelectedTestRun.testingRunResultSummaryHexId,
        filters
      ).then((testCountMap) => {
        if (copyUpdateTable !== updateTable) {
          setCopyUpdateTable(updateTable)
        } else {
          setCopyFilters(filters)
        }
        if (testCountMap !== null) {
          localCountMap = JSON.parse(JSON.stringify(testCountMap))
        }
        localCountMap = withDerivedCounts(localCountMap)
        const orderedValues = tableTabsOrder.map(key => localCountMap[tableTabMap[key]] || 0)
        setTestRunResultsCount(orderedValues)
        setTestRunCountMap(JSON.parse(JSON.stringify(localCountMap)));
      }).catch((error) => {
        console.error('Error fetching test run results count:', error);
        // Continue with existing count map if this fails
      }) : Promise.resolve();

      // Wait for the results promise (needed for return value), but don't wait for count promise
      await fetchResultsPromise;
      // Start count promise in background - it will set state when it completes
      fetchCountPromise.catch(() => { }); // Suppress unhandled rejection warning
    }
    const key = tableTabMap[selectedTab]
    const total = localCountMap[key]
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

  const handleBulkSeverityUpdate = async (newSeverity) => {
    // Prevent duplicate concurrent updates
    if (severityUpdateInProgress) {
      func.setToast(true, true, "Severity update already in progress");
      return;
    }

    const hexIds = selectedTestResultItems.flat();
    setSeverityUpdateInProgress(true);

    try {
      await api.bulkUpdateTestResultsSeverity(hexIds, newSeverity);

      func.setToast(true, false, `Severity updated for ${hexIds.length} test result${hexIds.length === 1 ? "" : "s"} across all test runs`);
      setSeverityModalActive(false);

      // Trigger refresh:
      // 1. fetchTestingRunResultSummaries updates testingRunResultSummariesObj
      //    → triggers useEffect (line 315) → calls setUpdateTable automatically
      // 2. chartRefreshCounter triggers TrendChart to refetch its data
      setTimeout(async () => {
        await fetchTestingRunResultSummaries();
        setChartRefreshCounter(prev => prev + 1);
      }, 500);

    } catch (error) {
      func.setToast(true, true, error.message || "Failed to update severity");
      setSeverityModalActive(false);
    } finally {
      setSeverityUpdateInProgress(false);
    }
  };

  const openSeverityUpdateModal = (items) => {
    setSelectedTestResultItems(items);
    setSeverityModalActive(true);
  };

  const showVerifyWithAi = !workflowTest && (testRunCountMap?.VULNERABLE || 0) > 0

  // Reopens the issues behind the selected results in the Ignored Issues tab, the same as "Reopen"
  // in the result flyout, then recounts this run so the results move back to Vulnerable.
  const handleBulkReopen = async (resultHexIds) => {
    if (reopenInProgress) return;
    setReopenInProgress(true);
    try {
      const responses = await Promise.all(resultHexIds.map((hexId) =>
        api.fetchIssueFromTestRunResultDetails(hexId).catch(() => null)
      ));
      const issueIds = [];
      const seen = new Set();
      responses.forEach((res) => {
        const issue = res?.runIssues;
        if (issue?.id && issue.testRunIssueStatus === "IGNORED") {
          const key = JSON.stringify(issue.id);
          if (!seen.has(key)) {
            seen.add(key);
            issueIds.push(issue.id);
          }
        }
      });
      if (issueIds.length === 0) {
        func.setToast(true, true, "No ignored issues found for the selected results");
        return;
      }
      await issuesApi.bulkUpdateIssueStatus(issueIds, "OPEN", "");
      // The status update recounts each issue's latest run; recount this run too in case it's an older one.
      await api.handleRefreshTableCount(currentSummary.hexId);
      func.setToast(true, false, `${issueIds.length} issue${issueIds.length === 1 ? "" : "s"} re-opened`);
      setTimeout(async () => {
        await fetchTestingRunResultSummaries();
        setUpdateTable(Date.now().toString());
        setChartRefreshCounter(prev => prev + 1);
      }, 1500);
    } catch (e) {
      // request() already shows the server's error as a toast
    } finally {
      setReopenInProgress(false);
    }
  };

  const promotedBulkActions = () => {
    let totalSelectedItemsSet = new Set(TableStore.getState().selectedItems.flat())

    return [
      ...(selectedTab === 'ignored_issues' ? [{
        content: `Reopen ${totalSelectedItemsSet.size} issue${totalSelectedItemsSet.size === 1 ? '' : 's'}`,
        disabled: reopenInProgress,
        onAction: () => {
          if (totalSelectedItemsSet.size > 0) {
            handleBulkReopen([...totalSelectedItemsSet])
          }
        },
        requires: 'api/bulkUpdateIssueStatus',
      }] : []),
      ...(showVerifyWithAi && selectedTab === 'vulnerable' ? [{
        content: `Verify ${totalSelectedItemsSet.size} with AI`,
        disabled: aiVerifyStartedFor === currentSummary?.hexId,
        onAction: () => {
          if (totalSelectedItemsSet.size > 0) {
            openAiVerifyModal([...totalSelectedItemsSet])
          }
        },
        requires: 'api/verifyTestRunWithAi',
      }] : []),
      {
        content: 'Update severity',
        onAction: () => {
          if (totalSelectedItemsSet.size > 0) {
            openSeverityUpdateModal([...totalSelectedItemsSet]);
          }
        },
        requires: 'api/bulkUpdateTestResultsSeverity',
      },
      {
        content: `Rerun ${totalSelectedItemsSet.size} test${totalSelectedItemsSet.size === 1 ? '' : 's'}`,
        onAction: () => {
          if (totalSelectedItemsSet.size > 0) {
            transform.rerunTest(selectedTestRun.id, null, false, [...totalSelectedItemsSet], selectedTestRun.testingRunResultSummaryHexId)
          }
        },
        requires: 'api/startTest',
      },
      {
        content: 'Export selected Test Results as Markdown',
        onAction: () => {
          const selectedItems = TableStore.getState().selectedItems.flat().map(item => JSON.parse(item));
          const testRunData = {
            name: selectedTestRun.name,
            start: selectedTestRun.scheduleTimestamp,
            total_severity: selectedTestRun.total_severity,
            results: selectedItems
          };
          const markdown = MarkdownReportGenerator.generateMarkdown(testRunData, 'Test Run');
          const blob = new Blob(["\uFEFF" + markdown], { type: "text/markdown;charset=utf-8" });
          saveAs(blob, "test_run_report.md");
        }
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

  const tokenRateLimitBannerComp = selectedTestRun?.metadata?.tokenRateLimited ? (
    <div className="banner-wrapper">
      <Banner status="warning">
        <Text>{selectedTestRun.metadata.tokenRateLimited}</Text>
      </Banner>
    </div>
  ) : null

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

  let tableFilterOptions = filterOptions.filter((filter) => filter.key !== 'responseCodeFilter');
  if (responseCodeFilterChoices.length > 0) {
    tableFilterOptions = [
      ...tableFilterOptions,
      {
        key: 'responseCodeFilter',
        label: 'Response code',
        title: 'Response code',
        choices: responseCodeFilterChoices,
      },
    ];
  }

  function getCollectionId() {
    const testingEndpoints = testingRunResultSummariesObj?.testingRun?.testingEndpoints;

    if (!testingEndpoints) return undefined;

    if (testingEndpoints.type === "COLLECTION_WISE") {
      return testingEndpoints.apiCollectionId;
    }

    if (testingEndpoints.type === "MULTI_COLLECTION") {
      return testingEndpoints.apiCollectionIds;
    }

    return (testingEndpoints.apisList?.length > 0) ? testingEndpoints.apisList[0].apiCollectionId : undefined;
  }

  // Sort and filters of the current tab as the table has them persisted, for when the export is
  // started from outside the table (the table passes its own when its CSV icon is used).
  const buildExportContext = () => {
    const stored = PersistStore.getState().filtersMap["/dashboard/testing/" + selectedTestRun?.id + "/#" + selectedTab]
    const filters = tableHeaders.reduce((map, h) => { map[h.filterKey || h.value] = []; return map }, {})
    ;(stored?.filters || []).forEach((filter) => {
      const value = filter.value
      filters[filter.key] = Array.isArray(value) ? value : (value?.values ?? value)
    })
    const [sortKey, sortDirection] = (stored?.sort?.[0] || sortOptions[0].value).split(" ")
    return { sortKey, sortOrder: sortDirection === 'asc' ? -1 : 1, filters, queryValue: undefined }
  }

  // Exports every result of the current tab, one CSV row per test result, honouring the active
  // filters. Aborts without a file when a page fails or the run is over the cap; a count mismatch
  // is reported instead of being presented as a complete export.
  const exportTestRunResults = async (exportContext) => {
    const summaryHexId = selectedTestRun?.testingRunResultSummaryHexId
    if (!summaryHexId) {
      func.setToast(true, true, "No test run results to export")
      return
    }
    if (exportInProgress.current) return
    exportInProgress.current = true
    try {
      const queryMode = tableTabMap[selectedTab]
      const tabLabel = definedTableTabs[tableTabsOrder.indexOf(selectedTab)]
      const { sortKey, sortOrder, filters: tableFilters, queryValue } = exportContext || buildExportContext()
      const filters = structuredClone(tableFilters)
      mergeCategoryIntoTestFilter(filters)

      func.setToast(true, false, "Exporting CSV, please wait...")
      // The raw count bounds the paging: the backend drops ignored issues from a vulnerable page
      // after fetching it, so a short page doesn't mean the end. A missing count is unknown, not zero.
      const rawCounts = await api.fetchTestRunResultsCount(summaryHexId, filters)
      const counts = rawCounts && typeof rawCounts.ALL === 'number' ? withDerivedCounts(rawCounts) : null
      const fetchBound = queryMode === 'SECURED' ? counts?.SECURED : rawCounts?.[queryMode]
      if (!counts || typeof fetchBound !== 'number') {
        throw new Error("Could not determine how many results to export")
      }
      if (fetchBound === 0) {
        func.setToast(true, false, `No ${tabLabel.toLowerCase()} results to export`)
        return
      }
      if (fetchBound > EXPORT_MAX_RESULTS) {
        throw new Error(`${observeFunc.formatNumberWithCommas(fetchBound)} results is over the ${observeFunc.formatNumberWithCommas(EXPORT_MAX_RESULTS)} export limit. Apply filters to narrow it down.`)
      }

      const pageSize = queryMode === 'IGNORED_ISSUES' ? EXPORT_PAGE_SIZE_IGNORED : EXPORT_PAGE_SIZE
      const seen = new Set()
      const results = []
      for (let skip = 0; skip < fetchBound; skip += pageSize) {
        const res = await api.fetchTestingRunResults(summaryHexId, queryMode, sortKey, sortOrder, skip, pageSize, structuredClone(filters), queryValue)
        if (!Array.isArray(res?.testingRunResults)) {
          throw new Error("Could not fetch all test results for the export")
        }
        transform.prepareTestRunResults(hexId, res.testingRunResults, localSubCategoryMap, subCategoryFromSourceConfigMap, res.issuesDescriptionMap, res.jiraIssuesMapForResults, res.devrevIssuesMapForResults)
          .forEach((result) => {
            if (!seen.has(result.id)) {
              seen.add(result.id)
              results.push(result)
            }
          })
      }

      func.downloadAsCSV(transform.getTestRunResultsCsvRows(results, tabLabel), { name: `${selectedTestRun.name || "test_run"} - ${tabLabel}` })
      const expected = counts[queryMode]
      if (results.length === expected) {
        func.setToast(true, false, `CSV exported successfully (${observeFunc.formatNumberWithCommas(results.length)} results)`)
      } else {
        func.setToast(true, true, `Exported ${observeFunc.formatNumberWithCommas(results.length)} of ${observeFunc.formatNumberWithCommas(expected)} results. Some results may be missing, try again.`)
      }
    } catch (e) {
      func.setToast(true, true, e?.message || "CSV export failed")
    } finally {
      exportInProgress.current = false
    }
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
        disabled={!canCall('api/startTest')}
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
        filters={tableFilterOptions}
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
        onExportCsv={exportTestRunResults}
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
      <Modal
        open={aiVerifyModalActive}
        onClose={() => setAiVerifyModalActive(false)}
        title="Verify with AI"
        primaryAction={{
          content: 'Verify',
          onAction: () => handleVerifyWithAi(currentSummary.hexId, aiVerifyTargets),
        }}
        secondaryActions={[
          {
            content: 'Cancel',
            onAction: () => setAiVerifyModalActive(false),
          },
        ]}
      >
        <Modal.Section>
          <Text>{`AI will review ${aiVerifyTargets.length > 0 ? `the ${aiVerifyTargets.length} selected result${aiVerifyTargets.length === 1 ? '' : 's'}` : 'all vulnerable results of this test run'} and add its verdict and confidence to each issue's description. No issue is ignored automatically; select the false positives and ignore them yourself.`}</Text>
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
    return undefined;
  }

  // Filter out keys with empty string values and the 'error' key
  const filteredMetadata = Object.keys(selectedTestRun.metadata)
    .filter(key => key !== 'error' && key !== 'tokenRateLimited' && selectedTestRun.metadata[key] !== '');

  if (filteredMetadata.length === 0) {
    return undefined;
  }

  return (
    <LegacyCard title="Metadata" sectioned key="metadata">
      {filteredMetadata.map((key) => (
        <HorizontalStack key={key} spacing="tight">
          <Text>{key} : {selectedTestRun.metadata[key]}</Text>
        </HorizontalStack>
      ))}
    </LegacyCard>
  );
};

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
    runningTestsComp, <TrendChart key={tempLoading.running} hexId={hexId} setSummary={setSummary} show={true} totalVulnerabilities={tableCountObj.vulnerable} refreshTrigger={chartRefreshCounter} />,
    tokenRateLimitBannerComp, metadataComponent(), loading ? <SpinnerCentered key="loading" /> : (!workflowTest ? resultTable : workflowTestBuilder)];

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
      const params = new URLSearchParams();
      if (summaryMode === true) params.set('summaryMode', 'true');
      params.set('category', categoryToShortName[getDashboardCategory()] || 'API');
      const redirectUrl = `/dashboard/testing/summary/${responseId.split("}")[0]}?${params.toString()}`;
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

  const handleVerifyWithAi = async (summaryHexId, resultHexIds) => {
    setAiVerifyModalActive(false)
    try {
      await api.verifyTestRunWithAi(summaryHexId, resultHexIds)
      setAiVerifyStartedFor(summaryHexId)
      func.setToast(true, false, "AI verification started. Refresh in a few minutes to see the verdicts in the issue descriptions.")
    } catch (e) {
      // request() already shows the server's error (e.g. "Verification already running") as a toast
    }
  }

  const openAiVerifyModal = (resultHexIds) => {
    setSecondaryPopover(false)
    setAiVerifyTargets(resultHexIds)
    setAiVerifyModalActive(true)
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
          {selectedTestRun?.testingEndpoints?.type === "MULTI_COLLECTION" && Array.isArray(selectedTestRun?.testingEndpoints?.apiCollectionIds) ? (() => {
            const ids = selectedTestRun.testingEndpoints.apiCollectionIds;
            const showCount = 2;
            const shown = ids.slice(0, showCount);
            const hidden = ids.length - showCount;
            return (
              <>
                {shown.map((cid) => (
                  <Link key={cid} monochrome target="_blank" url={"/dashboard/observe/inventory/" + cid} removeUnderline>
                    <HorizontalStack gap={"1"}>
                      <Box><Icon color="subdued" source={ArchiveMinor} /></Box>
                      <Text color="subdued" variant="bodyMd">{collectionsMap[cid]}</Text>
                    </HorizontalStack>
                  </Link>
                ))}
                {hidden > 0 && (
                  <Badge status="info">+{hidden}</Badge>
                )}
              </>
            );
          })() : (
            <Link monochrome target="_blank" url={"/dashboard/observe/inventory/" + selectedTestRun?.apiCollectionId} removeUnderline>
              <HorizontalStack gap={"1"}>
                <Box><Icon color="subdued" source={ArchiveMinor} /></Box>
                <Text color="subdued" variant="bodyMd">{collectionsMap[selectedTestRun?.apiCollectionId]}</Text>
              </HorizontalStack>
            </Link>
          )}
          <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
          <HorizontalStack gap={"1"}>
            <Box><Icon color="subdued" source={PriceLookupMinor} /></Box>
            <Text color="subdued" variant="bodyMd">{getHeadingStatus(selectedTestRun)}</Text>
          </HorizontalStack>
          {selectedTestRun?.authError && (
            <>
              <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
              <HorizontalStack gap={"1"}>
                <Box><Icon color="critical" source={CircleAlertMajor} /></Box>
                <Text color="critical" variant="bodyMd" fontWeight="semibold">{selectedTestRun.authError}</Text>
              </HorizontalStack>
            </>
          )}
          {(() => {
            const issues = getHttpStatusIssueList(statusCounts);
            if (issues.length === 0) {
              return null;
            }
            const total = currentSummary?.testResultsCount || 0;
            const severityFor = (issue) => {
              if (issue.highlighted) return 'CRITICAL';
              const percentage = total > 0 ? (issue.count / total) * 100 : 0;
              if (percentage > 70) return 'CRITICAL';
              if (percentage >= 40) return 'HIGH';
              return 'MEDIUM';
            };
            return (
              <>
                <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
                <HorizontalStack gap={"1"}>
                  <Box><Icon color="subdued" source={CircleInformationMajor} /></Box>
                  <Tooltip
                    content={
                      <VerticalStack gap="2">
                        <Text variant="bodyMd">{mapLabel('API request', getDashboardCategory())} error statistics breakdown:</Text>
                        <VerticalStack gap="1">
                          {issues.map((issue) => (
                            <Text key={issue.key} variant="bodySm">{`- ${issue.label}: ${issue.countLabel}`}</Text>
                          ))}
                        </VerticalStack>
                        <Box paddingBlockStart="1" borderBlockStartWidth="1" borderColor="border-subdued">
                          <Text variant="bodySm" color="subdued" fontWeight="medium">Approximate counts based on sampled data.</Text>
                        </Box>
                      </VerticalStack>
                    }
                    hasUnderline={false}
                  >
                    <HorizontalStack gap="1" align="center">
                      <Text color="subdued" fontWeight="medium" variant="bodyMd" style={{ cursor: 'pointer' }}>{mapLabel('API', getDashboardCategory())} error stats:</Text>
                    </HorizontalStack>
                  </Tooltip>
                  <HorizontalStack gap="2" align="center">
                    {issues.map((issue) => {
                      const sev = severityFor(issue);
                      return (
                        <div key={issue.key} className={`badge-wrapper-${sev}`}>
                          <Badge>
                            {`${issue.label}: ${issue.countLabel}`}
                          </Badge>
                        </div>
                      );
                    })}
                  </HorizontalStack>
                </HorizontalStack>
              </>
            );
          })()}
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
      },
      {
        content: 'Export Test Results as Markdown',
        icon: ReportMinor,
        onAction: async () => {
          const { testingRunResultSummaryHexId } = selectedTestRun;
          if (!testingRunResultSummaryHexId) return;

          // We need to fetch count first or just try a large limit. 
          // Or better, loop like we saw in modifyDataForCSV (but SingleTestRunPage might differ).
          // Since we don't have modifyDataForCSV here, I'll use a safe large limit approach for now.

          try {

            const { testingRunResults } = await api.fetchTestingRunResults(
              testingRunResultSummaryHexId,
              "VULNERABLE",
              "severity",
              -1,
              0,
              5000,
              undefined,
              undefined
            );

            const formattedResults = transform.prepareTestRunResults(
              hexId,
              testingRunResults,
              localSubCategoryMap,
              subCategoryFromSourceConfigMap,
              {},
              {},
              {}
            );

            const testRunData = {
              name: selectedTestRun.name,
              start: selectedTestRun.scheduleTimestamp,
              total_severity: selectedTestRun.total_severity,
              run_type: selectedTestRun.run_type,
              results: formattedResults
            };

            const markdown = MarkdownReportGenerator.generateMarkdown(testRunData, 'Test Run');
            const blob = new Blob(["\uFEFF" + markdown], { type: "text/markdown;charset=utf-8" });
            saveAs(blob, "test_run_report.md");

          } catch (e) {
            console.error(e);
            // Toast error
          }
        }
      }
    ]
  })
  moreActionsList.push({
    title: 'Edit',
    items: [
      {
        content: mapLabel("More Tests", getDashboardCategory()),
        icon: PlusMinor,
        onAction: () => { setActiveFromTesting(true) },
        requires: 'api/modifyTestingRunConfig'
      },
      {
        content: 'Configurations',
        icon: SettingsMinor,
        onAction: () => { setShowEditableSettings(true); handleAddSettings() },
        requires: 'api/modifyTestingRunConfig'
      }
    ]
  })
  moreActionsList.push({
    title: 'More',
    items: [
      {
        content: 'See ' + mapLabel("APIs", getDashboardCategory()),
        icon: ViewMajor,
        onAction: () => {
          if (selectedTestRun?.testingEndpoints?.type === "MULTI_COLLECTION" && Array.isArray(selectedTestRun?.testingEndpoints?.apiCollectionIds)) {
            // Show modal for all collections
            setShowTestingEndpointsModal({ multi: true, collectionIds: selectedTestRun.testingEndpoints.apiCollectionIds });
          } else {
            setShowTestingEndpointsModal({ multi: false, collectionId: selectedTestRun?.apiCollectionId });
          }
        }
      },
      {
        content: 'Re-Calculate Issues Count',
        icon: RefreshMajor,
        onAction: () => { setConfirmationModal(true) },
        requires: 'api/handleRefreshTableCount'
      },
      ...(showVerifyWithAi ? [{
        content: aiVerifyStartedFor === currentSummary?.hexId ? 'AI verification running' : 'Verify all with AI',
        icon: MagicMinor,
        disabled: aiVerifyStartedFor === currentSummary?.hexId,
        onAction: () => openAiVerifyModal([]),
        requires: 'api/verifyTestRunWithAi'
      }] : []),
      ...(currentSummary?.hexId ? [{
        content: 'View logs',
        icon: FileMinor,
        onAction: () => { setSecondaryPopover(false); setLogsModalActive(true) }
      }] : [])
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
        sections={withPermissions(moreActionsList)}
      />

    </Popover>
  )

  return (
    <>
      <PageWithMultipleCards
        title={headingComp}
        primaryAction={!workflowTest ? <Box paddingInlineEnd={1}><Button primary onClick={() => exportTestRunResults()}>Export results</Button></Box> : undefined}
        secondaryActions={!workflowTest ? moreActionsComp : undefined}
        components={useComponents}
        backUrl={"/dashboard/testing"}
      />
      <ReRunModal selectedTestRun={selectedTestRun} shouldRefresh={false} />
      {currentSummary?.hexId ? (
        <TestRunLogs
          open={logsModalActive}
          onClose={() => setLogsModalActive(false)}
          summaryHexId={currentSummary.hexId}
          startTimestamp={currentSummary.startTimestamp}
          endTimestamp={currentSummary.endTimestamp}
        />
      ) : null}
      <SeveritySelector
        open={severityModalActive}
        onClose={() => setSeverityModalActive(false)}
        onConfirm={handleBulkSeverityUpdate}
        selectedCount={selectedTestResultItems.length}
        pageType="test result"
        disabled={severityUpdateInProgress}
      />
      {(resultId !== null && resultId.length > 0) ? (
        <TestRunResultPage
          runAutomatedTests={isRunAutomatedTestsEnabled(testingRunResultSummariesObj?.testingRun?.runAutomatedTests)}
        />
      ) : null}
    </>
  );
}

export default SingleTestRunPage

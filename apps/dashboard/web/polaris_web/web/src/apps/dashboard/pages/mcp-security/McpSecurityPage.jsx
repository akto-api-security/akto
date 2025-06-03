import { Page, Box, Button, Badge, HorizontalStack, Text } from '@shopify/polaris';
import EmptyScreensLayout from '../../components/banners/EmptyScreensLayout';
import LayoutWithTabs from '../../components/layouts/LayoutWithTabs';
import { useState, useEffect, useMemo } from 'react';
import GithubSimpleTable from '../../components/tables/GithubSimpleTable';
import { IndexFiltersMode } from '@shopify/polaris';
import { CellType } from '../../components/tables/rows/GithubRow';
import func from '@/util/func';
import useTable from '../../components/tables/TableContext';
import api from '../observe/api';
import PersistStore from '../../../main/PersistStore';
import GithubServerTable from '../../components/tables/GithubServerTable';
import { useNavigate } from 'react-router-dom';
import testingApi from '../testing/api';
import transform from '../testing/transform';
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards';
import SummaryCardInfo from '../../components/shared/SummaryCardInfo';
import { useStiggContext } from '@stigg/react-sdk';

const headers = [
  {
    text: "Name",
    value: "displayNameComp",
    title: "Name",
    sortActive: true
  },
  {
    text: "Endpoints",
    value: "urlsCount",
    title: "Endpoints",
    sortActive: true
  },
  {
    text: "Risk Score",
    value: "riskScoreComp",
    title: "Risk Score",
    sortActive: true
  },
  {
    text: "Hostname",
    value: "hostName",
    title: "Hostname",
    type: CellType.TEXT
  },
  {
    text: "Discovered",
    value: "discovered",
    title: "Discovered",
    type: CellType.TEXT,
    sortActive: true
  }
];

const sortOptions = [
  { label: 'Name', value: 'name asc', directionLabel: 'A-Z', sortKey: 'displayName', columnIndex: 1 },
  { label: 'Name', value: 'name desc', directionLabel: 'Z-A', sortKey: 'displayName', columnIndex: 1 },
  { label: 'Endpoints', value: 'endpoints asc', directionLabel: 'More', sortKey: 'urlsCount', columnIndex: 2 },
  { label: 'Endpoints', value: 'endpoints desc', directionLabel: 'Less', sortKey: 'urlsCount', columnIndex: 2 },
  { label: 'Risk Score', value: 'risk asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: 3 },
  { label: 'Risk Score', value: 'risk desc', directionLabel: 'Low risk', sortKey: 'riskScore', columnIndex: 3 },
  { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: 5 },
  { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs', columnIndex: 5 }
];

const resourceName = {
  singular: 'collection',
  plural: 'collections'
};

const testResultsHeaders = [
  {
    text: "Test name",
    title: 'Test run name',
    value: "testName",
    itemOrder: 1,
  },
  {
    text: "Number of tests",
    title: "Number of tests",
    value: "number_of_tests",
    itemOrder: 3,
    type: CellType.TEXT,
  },
  {
    text: "Severity",
    value: 'severity',
    title: 'Issues',
    filterKey: "severityStatus",
    itemOrder: 2,
  },
  {
    text: 'Run time',
    value: 'run_time',
    title: 'Status',
    itemOrder: 3,
    type: CellType.TEXT,
    sortActive: true
  },
  {
    text: 'Scan frequency',
    title: 'Scan frequency',
    value: 'scan_frequency',
    type: CellType.TEXT,
  },
  {
    text: 'Total Apis',
    title: 'Total Endpoints',
    value: 'total_apis',
    type: CellType.TEXT
  },
  {
    title: '',
    type: CellType.ACTION,
  }
];

const testResultsSortOptions = [
  { label: 'Run time', value: 'scheduleTimestamp asc', directionLabel: 'Newest run', sortKey: 'scheduleTimestamp', columnIndex: 4 },
  { label: 'Run time', value: 'scheduleTimestamp desc', directionLabel: 'Oldest run', sortKey: 'scheduleTimestamp', columnIndex: 4 }
];

const testResultsResourceName = {
  singular: 'test run',
  plural: 'test runs',
};

// Test Results Table Tabs
const testResultsDefinedTableTabs = ['All', 'One time', 'Continuous Testing', 'Scheduled', 'CI/CD'];

const McpSecurityPage = () => {
  const [selectedTab, setSelectedTab] = useState(0);
  const [data, setData] = useState({ 'hostname': [], 'deactivated': [] });
  const [selected, setSelected] = useState(0);
  const [loading, setLoading] = useState(true);
  const { tabsInfo } = useTable();
  const definedTableTabs = ['Hostname', 'Deactivated'];
  const tableCountObj = func.getTabsCount(definedTableTabs, data);
  const discoveryTableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelected, selected, tabsInfo);
  const setAllCollections = PersistStore(state => state.setAllCollections);
  const navigate = useNavigate();
  const [testResultsSelected, setTestResultsSelected] = useState(0);
  const [testResultsCurrentTab, setTestResultsCurrentTab] = useState('one_time');
  const [testResultsLoading, setTestResultsLoading] = useState(false);
  const [testResultsCountMap, setTestResultsCountMap] = useState({});
  const { stigg } = useStiggContext();

  // Check if user has MCP_SECURITY feature access
  const hasMcpSecurityAccess = useMemo(() => {
    const stiggFeatures = window.STIGG_FEATURE_WISE_ALLOWED;
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
      return false;
    }
    return stiggFeatures['MCP_SECURITY']?.isGranted || false;
  }, []);

  // Generate testResultsTableTabs using useMemo
  const testResultsTableTabs = () => {
    const testResultsTableCountObj = func.getTabsCount(testResultsDefinedTableTabs, {}, [testResultsCountMap['allTestRuns'], testResultsCountMap['oneTime'], testResultsCountMap['continuous'], testResultsCountMap['scheduled'], testResultsCountMap['cicd']]);
    return func.getTableTabsContent(testResultsDefinedTableTabs, testResultsTableCountObj, setTestResultsCurrentTab, testResultsCurrentTab, tabsInfo);
  }

  const handleSelectedTab = (selectedIndex) => {
    setSelected(selectedIndex);
  };

  const convertToCollectionData = (collection) => {
    return {
      id: collection.id,
      displayName: collection.name,
      urlsCount: collection.urlsCount,
      riskScore: collection.riskScore || 0,
      hostName: collection.hostName,
      discovered: collection.startTs ? new Date(collection.startTs).toLocaleString() : 'N/A',
      deactivated: collection.deactivated,
      type: collection.type
    };
  };

  const fetchData = async () => {
    setLoading(true);
    try {
      const apiCollectionsResp = await api.getAllCollectionsBasic();
      const collections = (apiCollectionsResp.apiCollections || []).map(convertToCollectionData);
      
      const hostnameCollections = collections.filter(c => c.hostName && !c.deactivated);
      const deactivatedCollections = collections.filter(c => c.deactivated);
      
      // Filter out any potential null or undefined collection objects
      const filteredHostnameCollections = hostnameCollections.filter(item => item && item.id !== undefined && item.id !== null);
      const filteredDeactivatedCollections = deactivatedCollections.filter(item => item && item.id !== undefined && item.id !== null);
      
      setData({
        'hostname': filteredHostnameCollections,
        'deactivated': filteredDeactivatedCollections
      });
      
      setAllCollections(apiCollectionsResp?.apiCollections.filter(x => x?.deactivated !== true) || []);
    } catch (error) {
      console.error('Error fetching collections:', error);
    } finally {
      setLoading(false);
    }
  };

  // Function to fetch counts for Test Results tabs
  async function fetchTestResultsCounts() {
    await testingApi.getCountsMap().then((resp) => {
      setTestResultsCountMap(resp);
    });
  }

  const handleTestResultsSelectedTab = (selectedIndex) => {
    setTestResultsSelected(selectedIndex);
    setTestResultsCurrentTab(testResultsDefinedTableTabs[selectedIndex]);
  };

  useEffect(() => {
    fetchData();
    fetchTestResultsCounts();
  }, []);

  // Discovery tab row click: go to API collection detail page
  const handleDiscoveryRowClick = (row) => {
    if (row && row.id) {
      navigate(`/dashboard/observe/api-collections/${row.id}`);
    }
  };

  // Test Results tab row click: go to test run detail page
  const handleTestResultsRowClick = (row) => {
    if (row && row.id) {
      navigate(`/dashboard/testing/${row.id}`);
    }
  };

  // Test Results table fetch logic (matching TestRunsPage)
  async function fetchTestResultsTableData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
    setTestResultsLoading(true);
    let ret = [];
    let total = 0;
    let tabId = testResultsDefinedTableTabs[testResultsSelected];
    let type = null;
    switch (tabId) {
      case 'CI/CD':
        type = 'CI_CD';
        break;
      case 'Scheduled':
        type = 'RECURRING';
        break;
      case 'One time':
        type = 'ONE_TIME';
        break;
      case 'Continuous Testing':
        type = 'CONTINUOUS_TESTING';
        break;
      default:
        type = null;
    }
    await testingApi.fetchTestingDetails(
      undefined, undefined, sortKey, sortOrder, skip, limit, filters, type, queryValue
    ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
      ret = transform.processData(testingRuns, latestTestingRunResultSummaries);
      total = testingRunsCount;
    });
    setTestResultsLoading(false);
    return { value: ret, total: total };
  }

  const discoveryTab = {
    id: 'discovery',
    content: 'Discovery',
    component: (
      <Box width="100%" paddingBlockStart={4}>
        <GithubSimpleTable
          key="discovery-table"
          pageLimit={100}
          data={data[definedTableTabs[selected]]}
          sortOptions={sortOptions}
          resourceName={resourceName}
          filters={[]}
          disambiguateLabel={(key, value) => func.convertToDisambiguateLabel(value, func.toSentenceCase, 2)}
          headers={headers}
          selectable={true}
          mode={IndexFiltersMode.Default}
          headings={headers}
          useNewRow={true}
          condensedHeight={true}
          tableTabs={discoveryTableTabs}
          onSelect={handleSelectedTab}
          selected={selected}
          csvFileName="Collections"
          loading={loading}
          onRowClick={handleDiscoveryRowClick}
        />
      </Box>
    )
  };

  const testResultsTab = {
    id: 'test-results',
    content: 'Test Results',
    component: (
      <Box width="100%" paddingBlockStart={4}>
        {testResultsTableTabs.length > 0 && (
          <GithubServerTable
            key="test-results-table"
            pageLimit={50}
            fetchData={fetchTestResultsTableData}
            sortOptions={testResultsSortOptions}
            resourceName={testResultsResourceName}
            filters={[]}
            disambiguateLabel={(key, value) => func.convertToDisambiguateLabel(value, func.toSentenceCase, 2)}
            headers={testResultsHeaders}
            hasRowActions={true}
            loading={testResultsLoading}
            mode={IndexFiltersMode.Default}
            headings={testResultsHeaders}
            useNewRow={true}
            condensedHeight={true}
            onRowClick={handleTestResultsRowClick}
            tableTabs={testResultsTableTabs}
            onSelect={handleTestResultsSelectedTab}
            selected={testResultsSelected}
          />
        )}
      </Box>
    )
  };

  // Example summary items (replace with real data when available)
  const summaryItems = [
    { title: 'MCP Hosts Detected', data: 21 },
    { title: 'Tested Hosts', data: 3 },
    { title: 'Issues Found', data: 5, color: 'critical' },
    { title: 'Hosts with Sensitive Data', data: 7 },
  ];

  return (
    <>
      {/* Tab cursor style override */}
      <style>{`
        .Polaris-Tabs__Tab, .Polaris-Tabs__Tab:active, .Polaris-Tabs__Tab:focus, .Polaris-Tabs__Tab:hover {
          cursor: pointer !important;
        }
      `}</style>
      <PageWithMultipleCards
        title={
          <HorizontalStack gap="2">
            <Text as="h1" variant="headingLg">MCP Security</Text>
            <Badge>Beta</Badge>
          </HorizontalStack>
        }
        isFirstPage={true}
        components={[
          <>
            {hasMcpSecurityAccess ? (
              <>
                <SummaryCardInfo summaryItems={summaryItems} key="summary" />
                <Box width="100%" key="tabs">
                  <LayoutWithTabs
                    tabs={[discoveryTab, testResultsTab]}
                    currTab={() => {}}
                  />
                </Box>
              </>
            ) : (
              <Box width="100%" key="beta-card">
                <EmptyScreensLayout
                  iconSrc={"/public/mcp.svg"}
                  headingText={"MCP Security is in beta"}
                  description={"MCP Security is currently in beta. Contact our sales team to learn more about this feature and get access."}
                  bodyComponent={<Button url="https://www.akto.io/api-security-demo" target="_blank" primary>Contact sales</Button>}
                />
              </Box>
            )}
          </>
        ]}
      />
    </>
  );
}

export default McpSecurityPage; 
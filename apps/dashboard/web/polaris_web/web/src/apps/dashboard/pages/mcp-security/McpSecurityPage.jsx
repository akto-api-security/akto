import { Box, Button, Badge, HorizontalStack, Text } from '@shopify/polaris';
import EmptyScreensLayout from '../../components/banners/EmptyScreensLayout';
import LayoutWithTabs from '../../components/layouts/LayoutWithTabs';
import { useState, useMemo } from 'react';
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards';
import SummaryCardInfo from '../../components/shared/SummaryCardInfo';
import ApiCollections from '../observe/api_collections/ApiCollections';
import TestRunsPage from '../testing/TestRunsPage/TestRunsPage';
import { getDashboardCategory, mapLabel } from '../../../main/labelHelper';

function McpSecurityPage() {
  // Check if user has MCP_SECURITY feature access
  const hasMcpSecurityAccess = useMemo(() => {
    const stiggFeatures = window.STIGG_FEATURE_WISE_ALLOWED;
    if (!stiggFeatures || Object.keys(stiggFeatures).length === 0) {
      return false;
    }
    return stiggFeatures['MCP_SECURITY']?.isGranted || true;
  }, []);

  const [apiCollectionIds, setApiCollectionIds] = useState([]);
  const [summaryData, setSummaryData] = useState({
    mcpHostsDetected: 0,
    testedHosts: 0,
    issuesFound: 0,
    hostsWithSensitiveData: 0,
  });

  function saveSummaryData(data) {

    if(data.all){
      const newSummary = {
        mcpHostsDetected: data.all.length || 0, 
        testedHosts: data.all.filter(x => x.testedEndpoints > 0).length || 0,
        issuesFound: data.all.reduce((acc, x) => acc + (x.severityInfo.CRITICAL || 0) + (x.severityInfo.HIGH || 0) + (x.severityInfo.MEDIUM || 0) + (x.severityInfo.LOW || 0), 0),
        hostsWithSensitiveData: data.all.filter(x => x.sensitiveInRespTypes.length > 0).length || 0,
      };
      // Only update state if data actually changed to prevent infinite loop
      setSummaryData(prev => {
        if (
          prev.mcpHostsDetected !== newSummary.mcpHostsDetected ||
          prev.testedHosts !== newSummary.testedHosts ||
          prev.issuesFound !== newSummary.issuesFound ||
          prev.hostsWithSensitiveData !== newSummary.hostsWithSensitiveData
        ) {
          return newSummary;
        }
        return prev;
      });
    }
  }

  function customCollectionDataFilter(x) {
    let valid = x?.envType && x?.envType.filter(env => env?.value.includes('MCP')).length > 0;
    setApiCollectionIds((prev) => {
      if (valid && !prev.includes(x.id)) {
        return [...prev, x.id];
      }
      if (!valid && prev.includes(x.id)) {
        return prev.filter(id => id !== x.id);
      }
      return prev;
    }
    );
    return valid
  }

  const discoveryTab = {
    id: 'discovery',
    content: 'Discovery',
    component: (
      <ApiCollections
        onlyShowCollectionsTable={true}
        sendData={saveSummaryData}
        customCollectionDataFilter={customCollectionDataFilter}
      />
    )
  };

  const testResultsTab = {
    id: 'test-results',
    content: mapLabel('Test results', getDashboardCategory()),
    component: (
      <Box width="100%" paddingBlockStart={4}>
        <TestRunsPage
        // Setting it to -1. When there are no MCP collections, the test results page will show all the test runs. We only need to show test runs for MCP collections only.
          scopeApiCollectionIds={apiCollectionIds?.length > 0 ? apiCollectionIds : [-1]}
          showOnlyTable={true}
        />
      </Box>
    )
  };

  const summaryItems = [
    { title: 'MCP Hosts Detected', data: summaryData.mcpHostsDetected },
    { title: 'Tested Hosts', data: summaryData.testedHosts },
    { title: 'Issues Found', data: summaryData.issuesFound, color: 'critical' },
    { title: 'Hosts with Sensitive Data', data: summaryData.hostsWithSensitiveData, color: 'warning' },
  ];

  return (
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
                  currTab={() => { }}
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
  );
}

export default McpSecurityPage;
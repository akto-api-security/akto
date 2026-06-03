import React, { memo } from 'react';
import { Text, LegacyCard, HorizontalStack, Button, Collapsible, HorizontalGrid, Box, Divider, VerticalStack } from '@shopify/polaris';
import { ChevronDownMinor, ChevronUpMinor } from '@shopify/polaris-icons';
import ChartypeComponent from './ChartypeComponent';
import ApiCollectionCoverageGraph from './ApiCollectionCoverageGraph';
import ApisTestedOverTimeGraph from './ApisTestedOverTimeGraph';
import TestRunOverTimeGraph from './TestRunOverTimeGraph';
import CategoryWiseScoreGraph from './CategoryWiseScoreGraph';
import { isApiSecurityCategory, isDastCategory } from '../../../../main/labelHelper';
import func from '@/util/func';
import SpinnerCentered from "../../../components/progress/SpinnerCentered";

const MemoizedApiCollectionCoverageGraph = memo(ApiCollectionCoverageGraph);
const MemoizedTestRunOverTimeGraph = memo(TestRunOverTimeGraph);
const MemoizedApisTestedOverTimeGraph = memo(ApisTestedOverTimeGraph);
const MemoizedCategoryWiseScoreGraph = memo(CategoryWiseScoreGraph);

function buildChartData(entries) {
  return Object.fromEntries(
    Object.entries(entries).filter(([, value]) => (value?.text || 0) > 0)
  );
}

function getExecutionHeadline(executionSummary) {
  const totalRuns = executionSummary?.totalRuns || 0;
  const runStatusCounts = executionSummary?.runStatusCounts || {};
  const failed = runStatusCounts.failed || 0;
  const stopped = runStatusCounts.stopped || 0;
  const incorrect = runStatusCounts.incorrect || 0;
  const successful = runStatusCounts.successful || 0;
  const authErrorRuns = executionSummary?.authErrorRuns || 0;
  const issueRuns = failed + stopped + incorrect;

  if (totalRuns === 0) {
    return 'No scan runs in selected range';
  }
  if (issueRuns === 0 && authErrorRuns === 0) {
    return `${successful} of ${totalRuns} scan run${totalRuns === 1 ? '' : 's'} completed successfully`;
  }

  const parts = [`${issueRuns} of ${totalRuns} scan run${totalRuns === 1 ? '' : 's'} had execution issues`];
  if (authErrorRuns > 0) {
    parts.push(`${authErrorRuns} auth error${authErrorRuns === 1 ? '' : 's'}`);
  }
  return parts.join(' · ');
}

function getFailureReasonRows(failureReasonCounts) {
  if (!failureReasonCounts) {
    return [];
  }
  return Object.entries(failureReasonCounts)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1])
    .map(([reason, count]) => ({ reason, count }));
}

const SummaryCardComponent = ({
  severityMap,
  subCategoryInfo,
  collapsible,
  setCollapsible,
  executionCollapsible,
  setExecutionCollapsible,
  startTimestamp,
  endTimestamp,
  loading,
  executionSummary,
  executionSummaryLoading,
  apiCollectionIds,
}) => {
  const totalVulnerabilities = (severityMap?.CRITICAL?.text || 0) +
                              (severityMap?.HIGH?.text || 0) +
                              (severityMap?.MEDIUM?.text || 0) +
                              (severityMap?.LOW?.text || 0);

  const iconSource = collapsible ? ChevronUpMinor : ChevronDownMinor;
  const executionIconSource = executionCollapsible ? ChevronUpMinor : ChevronDownMinor;

  const subCategoryInfoCamel = React.useMemo(() => {
    if (!subCategoryInfo || Object.keys(subCategoryInfo).length === 0) {
      return subCategoryInfo;
    }
    const converted = {};
    Object.entries(subCategoryInfo).forEach(([key, value]) => {
      const camelKey = func.capsSnakeToCamel(key);
      const pascalKey = camelKey.charAt(0).toUpperCase() + camelKey.slice(1);
      converted[pascalKey] = value;
    });
    return converted;
  }, [subCategoryInfo]);

  const runStatusChart = React.useMemo(() => {
    const runStatusCounts = executionSummary?.runStatusCounts || {};
    return buildChartData({
      Successful: { text: runStatusCounts.successful || 0, color: 'rgb(0,127,95)' },
      Incorrect: { text: runStatusCounts.incorrect || 0, color: 'rgb(185,137,0)' },
      Failed: { text: runStatusCounts.failed || 0, color: 'rgb(215,44,13)' },
      Stopped: { text: runStatusCounts.stopped || 0, color: 'rgb(145,153,163)' },
      Running: { text: runStatusCounts.running || 0, color: 'rgb(145,153,163)' },
    });
  }, [executionSummary]);

  const errorCategoriesChart = React.useMemo(() => {
    const resultCounts = executionSummary?.resultCounts || {};
    const httpErrorCounts = executionSummary?.httpErrorCounts || {};
    const runStatusCounts = executionSummary?.runStatusCounts || {};
    const testLevelErrors = buildChartData({
      Skipped: { text: resultCounts.SKIPPED_EXEC || 0, color: 'rgb(145,153,163)' },
      'Need configuration': { text: resultCounts.SKIPPED_EXEC_NEED_CONFIG || 0, color: 'rgb(185,137,0)' },
      'Domain unreachable': { text: resultCounts.SKIPPED_EXEC_API_REQUEST_FAILED || 0, color: 'rgb(215,44,13)' },
      '403 Forbidden': { text: httpErrorCounts.HTTP_403 || 0, color: 'rgb(185,137,0)' },
      '401 Unauthorized': { text: httpErrorCounts.HTTP_401 || 0, color: 'rgb(185,137,0)' },
      '429 Rate limit': { text: httpErrorCounts.HTTP_429 || 0, color: 'rgb(145,153,163)' },
      '5XX errors': { text: httpErrorCounts.HTTP_5XX || 0, color: 'rgb(215,44,13)' },
      Cloudflare: { text: httpErrorCounts.CLOUDFLARE || 0, color: 'rgb(145,153,163)' },
    });
    const runLevelErrors = buildChartData({
      'Auth errors (runs)': { text: executionSummary?.authErrorRuns || 0, color: 'rgb(215,44,13)' },
      'Failed runs': { text: runStatusCounts.failed || 0, color: 'rgb(215,44,13)' },
      'Stopped runs': { text: runStatusCounts.stopped || 0, color: 'rgb(145,153,163)' },
      'Completed with issues': { text: runStatusCounts.incorrect || 0, color: 'rgb(185,137,0)' },
    });
    return { ...runLevelErrors, ...testLevelErrors };
  }, [executionSummary]);

  const failureReasonRows = React.useMemo(
    () => getFailureReasonRows(executionSummary?.failureReasonCounts),
    [executionSummary]
  );

  const executionHeadline = executionSummaryLoading
    ? 'Loading execution summary...'
    : getExecutionHeadline(executionSummary);
  const hasExecutionDetails = Object.keys(runStatusChart).length > 0
    || Object.keys(errorCategoriesChart).length > 0
    || failureReasonRows.length > 0;

  return (
    <LegacyCard>
      <LegacyCard.Section title={<Text fontWeight="regular" variant="bodySm" color="subdued">Vulnerabilities</Text>}>
        <HorizontalStack align="space-between">
          <Text fontWeight="semibold" variant="bodyMd">Found {totalVulnerabilities} vulnerabilities in total</Text>
          {
            loading ?
              <Box key="spinner-box">
                <SpinnerCentered height="0px" />
              </Box>
              :
              <Button plain monochrome icon={iconSource} onClick={() => setCollapsible(!collapsible)} />
          }
        </HorizontalStack>
        {totalVulnerabilities > 0 ?
        <Collapsible open={collapsible} transition={{duration: '500ms', timingFunction: 'ease-in-out'}}>
          <LegacyCard.Subsection>
            <Box paddingBlockStart={3} paddingBlockEnd={3}><Divider/></Box>
            <VerticalStack gap={"5"}>
              <HorizontalGrid columns={2} gap={6}>
                <ChartypeComponent chartSize={190} navUrl={"/dashboard/issues"} data={subCategoryInfoCamel} title={"Issue Categories"} isNormal={true} boxHeight={'250px'}/>
                <ChartypeComponent
                    data={severityMap}
                    navUrl={"/dashboard/issues"} title={"Issue Severity"} isNormal={true} boxHeight={'250px'} dataTableWidth="250px" boxPadding={8}
                    pieInnerSize="50%"
                    chartOnLeft={false}
                    chartSize={190}
                />
              </HorizontalGrid>
              {!(isApiSecurityCategory() || isDastCategory()) ? (
                <MemoizedCategoryWiseScoreGraph
                  key={"category-score-graph"}
                  startTimestamp={startTimestamp}
                  endTimestamp={endTimestamp}
                  dataSource="redteaming"
                  apiCollectionIds={apiCollectionIds}
                />
              ) : null}
                <VerticalStack gap={4}>
                  <HorizontalGrid columns={2} gap={4}>
                    <MemoizedApiCollectionCoverageGraph apiCollectionIds={apiCollectionIds} />
                    <MemoizedTestRunOverTimeGraph apiCollectionIds={apiCollectionIds} />
                  </HorizontalGrid>
                  {!(isApiSecurityCategory() || isDastCategory()) ? <></> :<MemoizedApisTestedOverTimeGraph apiCollectionIds={apiCollectionIds} />}
                </VerticalStack>

            </VerticalStack>
          </LegacyCard.Subsection>
        </Collapsible>
        : null }
      </LegacyCard.Section>

      <LegacyCard.Section title={<Text fontWeight="regular" variant="bodySm" color="subdued">Execution overview</Text>}>
        <HorizontalStack align="space-between">
          <Text fontWeight="semibold" variant="bodyMd">{executionHeadline}</Text>
          {
            executionSummaryLoading ?
              <Box key="execution-spinner-box">
                <SpinnerCentered height="0px" />
              </Box>
              :
              <Button plain monochrome icon={executionIconSource} onClick={() => setExecutionCollapsible(!executionCollapsible)} />
          }
        </HorizontalStack>
        {hasExecutionDetails ? (
          <Collapsible open={executionCollapsible} transition={{ duration: '500ms', timingFunction: 'ease-in-out' }}>
            <LegacyCard.Subsection>
              <Box paddingBlockStart={3} paddingBlockEnd={3}><Divider /></Box>
              <HorizontalGrid columns={2} gap={6}>
                {Object.keys(runStatusChart).length > 0 ? (
                  <ChartypeComponent
                    chartSize={190}
                    data={runStatusChart}
                    title="Run status"
                    isNormal={true}
                    boxHeight="250px"
                  />
                ) : null}
                {Object.keys(errorCategoriesChart).length > 0 ? (
                  <ChartypeComponent
                    chartSize={190}
                    data={errorCategoriesChart}
                    title="What went wrong"
                    isNormal={true}
                    boxHeight="250px"
                    dataTableWidth="250px"
                    boxPadding={8}
                    pieInnerSize="50%"
                    chartOnLeft={false}
                  />
                ) : null}
              </HorizontalGrid>
              {failureReasonRows.length > 0 ? (
                <Box paddingBlockStart={4}>
                  <Text fontWeight="semibold" variant="bodySm">Failure reasons</Text>
                  <Box paddingBlockStart={2}>
                    <VerticalStack gap="2">
                      {failureReasonRows.map(({ reason, count }) => (
                        <HorizontalStack key={reason} align="space-between" blockAlign="center">
                          <Text variant="bodySm" color="subdued">{reason}</Text>
                          <Text variant="bodySm" fontWeight="semibold">{count}</Text>
                        </HorizontalStack>
                      ))}
                    </VerticalStack>
                  </Box>
                </Box>
              ) : null}
            </LegacyCard.Subsection>
          </Collapsible>
        ) : null}
      </LegacyCard.Section>
    </LegacyCard>
  );
};

export default SummaryCardComponent;

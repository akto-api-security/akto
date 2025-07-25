import React, { useEffect, useState } from 'react';
import SummaryCard from '../../dashboard/new_components/SummaryCard';
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart';
import dashboardApi from '../../dashboard/api';
import api from '../api';
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";

const TestSummaryInfo = ({ severityMap = {} }) => {
  const [metrics, setMetrics] = useState({
    totalApisTested: 0,
    testCoveragePercent: '0%',
    totalTestRuns: 0,
    totalCriticalIssues: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchMetrics() {
      setLoading(true);
      try {
        const allTimeStart = 0;
        const allTimeEnd = Math.floor(Date.now() / 1000) + 86400;
        const resp = await api.fetchTestingDetails(
          allTimeStart,
          allTimeEnd,
          null,
          null,
          0,
          10000,
          {},
          null,
          null
        );
        let totalApisTested = 0;
        let totalApis = 0;
        if (
          resp.testingRuns &&
          Array.isArray(resp.testingRuns) &&
          resp.latestTestingRunResultSummaries
        ) {
          resp.testingRuns.forEach(run => {
            const summary = resp.latestTestingRunResultSummaries[run.hexId];
            console.log('SUMMARY FOR RUN', run.hexId, summary); // <-- log the summary object
            if (summary && typeof summary.totalApis === 'number') {
              totalApisTested += summary.totalApis;
            }
            if (summary && typeof summary.totalApisInCollection === 'number') {
              totalApis += summary.totalApisInCollection;
            }
          });
        }
        let testCoveragePercent = '0%';
        if (totalApis > 0) {
          testCoveragePercent = ((totalApisTested * 100) / totalApis).toFixed(2) + '%';
        }
        let totalTestRuns = 0;
        if (resp.testingRuns && Array.isArray(resp.testingRuns)) {
          totalTestRuns = resp.testingRuns.length;
        }
        const summaryInfo = await api.getSummaryInfo(allTimeStart, allTimeEnd);
        let totalCriticalIssues = 0;
        if (summaryInfo && summaryInfo.CRITICAL) {
          totalCriticalIssues = summaryInfo.CRITICAL;
        } else if (summaryInfo && summaryInfo.countIssues && summaryInfo.countIssues.CRITICAL) {
          totalCriticalIssues = summaryInfo.countIssues.CRITICAL;
        }
        setMetrics({
          totalApisTested,
          testCoveragePercent,
          totalTestRuns,
          totalCriticalIssues,
        });
      } catch (error) {
      }
      setLoading(false);
    }
    fetchMetrics();
  }, []);

  const totalVulnerabilities =
    (parseInt(severityMap?.CRITICAL?.text) || 0) +
    (parseInt(severityMap?.HIGH?.text) || 0) +
    (parseInt(severityMap?.MEDIUM?.text) || 0) +
    (parseInt(severityMap?.LOW?.text) || 0);

  const summaryInfo = [
    {
      title: 'Total vulnerabilities',
      data: totalVulnerabilities,
      variant: 'heading2xl',
      color: 'critical',
      smoothChartComponent: null,
      tooltipContent: 'Total number of vulnerabilities (all severities) found in all test runs.'
    },
    {
      title: 'Critical Issues',
      data: metrics.totalCriticalIssues,
      variant: 'heading2xl',
      color: 'critical',
      smoothChartComponent: null,
      tooltipContent: 'Total number of CRITICAL issues detected in all test runs.'
    },
    {
      title: 'APIs Tested',
      data: metrics.totalApisTested,
      variant: 'heading2xl',
      color: 'success',
      smoothChartComponent: null,
      tooltipContent: 'Number of APIs that have been tested at least once.'
    },
    // {
    //   title: 'Test Coverage',
    //   data: metrics.testCoveragePercent,
    //   variant: 'heading2xl',
    //   color: 'warning',
    //   smoothChartComponent: null,
    //   tooltipContent: 'Percentage of total APIs that have been tested.'
    // },
    {
      title: 'Total Test Runs',
      data: metrics.totalTestRuns,
      variant: 'heading2xl',
      smoothChartComponent: null,
      tooltipContent: 'Total number of test runs executed.'
    },
  ];

  if (loading) return null;

  return <SummaryCard summaryItems={summaryInfo} />;
};

export default TestSummaryInfo; 
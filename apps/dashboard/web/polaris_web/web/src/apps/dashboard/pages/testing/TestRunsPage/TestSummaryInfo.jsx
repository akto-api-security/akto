import React, { useEffect, useState } from 'react';
import SummaryCard from '../../dashboard/new_components/SummaryCard';
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart';
import dashboardApi from '../../dashboard/api';
import api from '../api';

const TestSummaryInfo = () => {
  const [metrics, setMetrics] = useState({
    totalApis: 0,
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
        const endpointsResp = await dashboardApi.fetchEndpointsCount(0, 0);
        const totalApis = endpointsResp?.newCount || 0;

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
        if (
          resp.testingRuns &&
          Array.isArray(resp.testingRuns) &&
          resp.latestTestingRunResultSummaries
        ) {
          resp.testingRuns.forEach(run => {
            const summary = resp.latestTestingRunResultSummaries[run.hexId];
            if (summary && typeof summary.totalApis === 'number') {
              totalApisTested += summary.totalApis;
            }
          });
        }

        let testCoveragePercent = '0%';
        if (totalApis > 0) {
          testCoveragePercent = Math.ceil((totalApisTested * 100) / totalApis) + '%';
        }

        let totalTestRuns = 0;
        if (resp.testingRuns && Array.isArray(resp.testingRuns)) {
          totalTestRuns = resp.testingRuns.filter(run => {
            if (run.summaryState) return run.summaryState === "COMPLETED";
            if (run.state) return run.state === "COMPLETED";
            return false;
          }).length;
        }

        const summaryInfo = await api.getSummaryInfo(allTimeStart, allTimeEnd);
        let totalCriticalIssues = 0;
        if (summaryInfo && summaryInfo.CRITICAL) {
          totalCriticalIssues = summaryInfo.CRITICAL;
        } else if (summaryInfo && summaryInfo.countIssues && summaryInfo.countIssues.CRITICAL) {
          totalCriticalIssues = summaryInfo.countIssues.CRITICAL;
        }

        setMetrics({
          totalApis,
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

  const summaryInfo = [
    {
      title: 'Total APIs',
      data: metrics.totalApis,
      variant: 'heading2xl',
      smoothChartComponent: null,
      tooltipContent: 'Total number of unique APIs discovered in your inventory.'
    },
    {
      title: 'APIs Tested',
      data: metrics.totalApisTested,
      variant: 'heading2xl',
      color: 'success',
      smoothChartComponent: null,
      tooltipContent: 'Number of unique APIs that have been tested at least once.'
    },
    {
      title: 'Test Coverage',
      data: metrics.testCoveragePercent,
      variant: 'heading2xl',
      color: 'warning',
      smoothChartComponent: null,
      tooltipContent: 'Percentage of total APIs that have been tested.'
    },
    {
      title: 'Total Test Runs',
      data: metrics.totalTestRuns,
      variant: 'heading2xl',
      smoothChartComponent: null,
      tooltipContent: 'Total number of test runs executed.'
    },
    {
      title: 'Critical Issues',
      data: metrics.totalCriticalIssues,
      variant: 'heading2xl',
      color: 'critical',
      smoothChartComponent: null,
      tooltipContent: 'Total number of CRITICAL issues detected in all test runs.'
    },
  ];

  if (loading) return null;

  return <SummaryCard summaryItems={summaryInfo} />;
};

export default TestSummaryInfo; 
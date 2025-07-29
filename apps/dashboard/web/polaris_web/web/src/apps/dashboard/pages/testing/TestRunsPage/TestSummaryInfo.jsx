import React, { useEffect, useState } from 'react';
import SummaryCard from '../../dashboard/new_components/SummaryCard';
import SmoothAreaChart from '../../dashboard/new_components/SmoothChart';
import observeFunc from "../../observe/transform.js";
import func from "@/util/func";
import api from '../api';
import dashboardApi from '../../dashboard/api';

const TestSummaryInfo = ({ severityMap = {}, startTimestamp, endTimestamp }) => {
  // State for trend data only
  const [totalVulnerabilities, setTotalVulnerabilities] = useState([]);
  const [totalVulnerabilitiesDelta, setTotalVulnerabilitiesDelta] = useState(0);
  const [criticalIssues, setCriticalIssues] = useState([]);
  const [criticalIssuesDelta, setCriticalIssuesDelta] = useState(0);
  const [uniqueApisTested, setUniqueApisTested] = useState([]);
  const [uniqueApisTestedDelta, setUniqueApisTestedDelta] = useState(0);
  const [testCoverage, setTestCoverage] = useState([]);
  const [testCoverageDelta, setTestCoverageDelta] = useState(0);
  const [totalTestRuns, setTotalTestRuns] = useState([]);
  const [totalTestRunsDelta, setTotalTestRunsDelta] = useState(0);

  const fetchMetrics = async () => {
    try {
      const allTimeStart = startTimestamp || 0;
      const allTimeEnd = endTimestamp || Math.floor(Date.now() / 1000) + 86400;
      
      // Fetch all data in parallel
      const [testingResp, apiStatsResp, summaryInfoResp] = await Promise.all([
        api.fetchTestingDetails(allTimeStart, allTimeEnd, null, null, 0, 20000, {}, null, null),
        dashboardApi.fetchApiStats(allTimeStart, allTimeEnd),
        api.getSummaryInfo(allTimeStart, allTimeEnd)
      ]);

      // Extract data
      const uniqueApisTestedCount = apiStatsResp?.apiStatsEnd?.apisTestedInLookBackPeriod || 0;
      const totalUniqueApisInScope = apiStatsResp?.apiStatsEnd?.totalInScopeForTestingApis || uniqueApisTestedCount;
      const testRunsCount = testingResp?.testingRuns?.length || 0;
      const criticalIssuesCount = summaryInfoResp?.CRITICAL || summaryInfoResp?.countIssues?.CRITICAL || 0;
      
      // Calculate test coverage
      const testCoveragePercent = totalUniqueApisInScope > 0 ? 
        parseFloat(((uniqueApisTestedCount * 100) / totalUniqueApisInScope).toFixed(2)) : 0;
      
      // Calculate total vulnerabilities
      const totalVulnCount = 
        (parseInt(severityMap?.CRITICAL?.text) || 0) +
        (parseInt(severityMap?.HIGH?.text) || 0) +
        (parseInt(severityMap?.MEDIUM?.text) || 0) +
        (parseInt(severityMap?.LOW?.text) || 0);

      // Set trend data using observeFunc pattern
      observeFunc.setIssuesState([0, totalVulnCount], setTotalVulnerabilities, setTotalVulnerabilitiesDelta, true);
      observeFunc.setIssuesState([0, criticalIssuesCount], setCriticalIssues, setCriticalIssuesDelta, true);
      observeFunc.setIssuesState([0, uniqueApisTestedCount], setUniqueApisTested, setUniqueApisTestedDelta, true);
      observeFunc.setIssuesState([0, testCoveragePercent], setTestCoverage, setTestCoverageDelta, false);
      observeFunc.setIssuesState([0, testRunsCount], setTotalTestRuns, setTotalTestRunsDelta, true);
      
    } catch (error) {
      console.error('Error fetching metrics:', error);
    }
  };

  useEffect(() => {
    fetchMetrics();
  }, [startTimestamp, endTimestamp, severityMap]);

  const summaryInfo = [
    {
      title: 'Total vulnerabilities',
      data: observeFunc.formatNumberWithCommas(totalVulnerabilities[totalVulnerabilities.length - 1] || 0),
      variant: 'heading2xl',
      color: 'critical',
      byLineComponent: observeFunc.generateByLineComponent(totalVulnerabilitiesDelta, func.timeDifference(startTimestamp || 0, endTimestamp || Math.floor(Date.now() / 1000))),
      smoothChartComponent: (<SmoothAreaChart tickPositions={totalVulnerabilities} />),
      tooltipContent: 'Total number of vulnerabilities found in all test runs.'
    },
    {
      title: 'Critical Issues',
      data: observeFunc.formatNumberWithCommas(criticalIssues[criticalIssues.length - 1] || 0),
      variant: 'heading2xl',
      color: 'critical',
      byLineComponent: observeFunc.generateByLineComponent(criticalIssuesDelta, func.timeDifference(startTimestamp || 0, endTimestamp || Math.floor(Date.now() / 1000))),
      smoothChartComponent: (<SmoothAreaChart tickPositions={criticalIssues} />),
      tooltipContent: 'Total number of CRITICAL issues detected in all test runs.'
    },
    {
      title: 'Unique APIs Tested',
      data: observeFunc.formatNumberWithCommas(uniqueApisTested[uniqueApisTested.length - 1] || 0),
      variant: 'heading2xl',
      color: 'success',
      byLineComponent: observeFunc.generateByLineComponent(uniqueApisTestedDelta, func.timeDifference(startTimestamp || 0, endTimestamp || Math.floor(Date.now() / 1000))),
      smoothChartComponent: (<SmoothAreaChart tickPositions={uniqueApisTested} />),
      tooltipContent: 'Number of unique APIs that have been tested at least once.'
    },
    {
      title: 'Test Coverage',
      data: (testCoverage[testCoverage.length - 1] || 0).toFixed(2) + "%",
      variant: 'heading2xl',
      color: (testCoverage[testCoverage.length - 1] || 0) > 80 ? 'success' : 'warning',
      byLineComponent: observeFunc.generateByLineComponent(testCoverageDelta.toFixed(2), func.timeDifference(startTimestamp || 0, endTimestamp || Math.floor(Date.now() / 1000))),
      smoothChartComponent: (<SmoothAreaChart tickPositions={testCoverage} />),
      tooltipContent: 'Percentage of unique APIs tested out of total unique APIs in scope.'
    },
    {
      title: 'Total Test Runs',
      data: observeFunc.formatNumberWithCommas(totalTestRuns[totalTestRuns.length - 1] || 0),
      variant: 'heading2xl',
      byLineComponent: observeFunc.generateByLineComponent(totalTestRunsDelta, func.timeDifference(startTimestamp || 0, endTimestamp || Math.floor(Date.now() / 1000))),
      smoothChartComponent: (<SmoothAreaChart tickPositions={totalTestRuns} />),
      tooltipContent: 'Total number of test runs executed.'
    },
  ];

  return <SummaryCard summaryItems={summaryInfo} />;
};

export default TestSummaryInfo; 
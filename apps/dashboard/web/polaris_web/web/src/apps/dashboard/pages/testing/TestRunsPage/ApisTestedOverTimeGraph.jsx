import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import AreaChart from '../../../components/charts/AreaChart';
import api from '../api';

const ApisTestedOverTimeGraph = ({ showOnlyTable = false, scopeApiCollectionIds = null }) => {
  const [chartData, setChartData] = useState([]);
  const [showTestingComponents, setShowTestingComponents] = useState(false);

  useEffect(() => {
    async function fetchApisTestedOverTime() {
      setShowTestingComponents(false);
      try {
        const weeklyApiCounts = [];
        const now = new Date();
        const currentWeekStart = new Date(now);
        currentWeekStart.setDate(now.getDate() - now.getDay());
        currentWeekStart.setHours(0, 0, 0, 0);

        for (let week = 0; week < 6; week++) {
          const weekStart = new Date(currentWeekStart);
          weekStart.setDate(currentWeekStart.getDate() - (week * 7));
          weekStart.setHours(0, 0, 0, 0);

          const weekEnd = new Date(weekStart);
          weekEnd.setDate(weekStart.getDate() + 6);
          weekEnd.setHours(23, 59, 59, 999);

          if (week === 0) {
            weekEnd.setTime(now.getTime());
          }

          const weekStartTimestamp = Math.floor(weekStart.getTime() / 1000);
          const weekEndTimestamp = Math.floor(weekEnd.getTime() / 1000);

          let weekFilters = {};
          if (showOnlyTable) {
            weekFilters["apiCollectionId"] = scopeApiCollectionIds || [];
          }

          const response = await api.fetchTestingDetails(
            weekStartTimestamp,
            weekEndTimestamp,
            null,
            null,
            0,
            20000,
            weekFilters,
            null,
            null
          );

          let totalApisTested = 0;
          if (
            response.testingRuns &&
            Array.isArray(response.testingRuns) &&
            response.latestTestingRunResultSummaries
          ) {
            response.testingRuns.forEach(run => {
              const summary = response.latestTestingRunResultSummaries[run.hexId];
              if (summary && typeof summary.totalApis === 'number') {
                totalApisTested += summary.totalApis;
              }
            });
          }

          weeklyApiCounts.push(totalApisTested);
        }

        weeklyApiCounts.reverse();
        setChartData(weeklyApiCounts);
        setShowTestingComponents(true);
      } catch (error) {
        console.error("Error fetching APIs tested over time:", error);
        setShowTestingComponents(true);
      }
    }

    fetchApisTestedOverTime();
  }, [showOnlyTable, scopeApiCollectionIds]);

  const emptyCardComponent = (
    <Text alignment='center' color='subdued'>
      No API testing data found for the selected time period.
    </Text>
  );

  const testingGraph = (chartData && chartData.length > 0 && chartData.some(count => count > 0)) ? (
    <InfoCard
      component={
        <AreaChart
          data={chartData}
          height={280}
          yAxisTitle="APIs Tested"
          color="#6D3BEF"
        />
      }
      title="APIs Tested Over Time"
      titleToolTip="Track API testing activity over the last 5 weeks, showing the number of APIs tested per week."
      linkText=""
      linkUrl=""
    />
  ) : (
    <EmptyCard 
      title="APIs Tested Over Time" 
      subTitleComponent={showTestingComponents ? emptyCardComponent : <Text alignment='center' color='subdued'>Loading...</Text>} 
    />
  );

  return testingGraph;
};

export default ApisTestedOverTimeGraph;
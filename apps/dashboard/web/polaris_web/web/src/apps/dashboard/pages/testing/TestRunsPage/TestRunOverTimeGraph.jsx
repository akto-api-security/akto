import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import LineChart from '../../../components/charts/LineChart';
import api from '../api';

const TestRunOverTimeGraph = ({ showOnlyTable = false, scopeApiCollectionIds = null }) => {
  const [chartData, setChartData] = useState([]);
  const [showTestingComponents, setShowTestingComponents] = useState(false);

  useEffect(() => {
    async function fetchTestRunsOverTime() {
      setShowTestingComponents(false);
      try {
        const weeklyData = [];
        const now = new Date();
        const currentWeekStart = new Date(now);
        currentWeekStart.setDate(now.getDate() - now.getDay()); 
        currentWeekStart.setHours(0, 0, 0, 0);

        for (let week = 0; week < 5; week++) {
          const weekStart = new Date(currentWeekStart);
          weekStart.setDate(currentWeekStart.getDate() - (week * 7));
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
            1000,
            weekFilters,
            null,
            null
          );

          let runCount = 0;
          if (response.testingRuns && Array.isArray(response.testingRuns)) {
            runCount = response.testingRuns.length;
          }
          weeklyData.push({
            week: `W${5-week}`,
            count: runCount,
            weekStart: weekStart.toLocaleDateString(),
            weekEnd: weekEnd.toLocaleDateString()
          });
        }
        weeklyData.reverse();

        const chartSeriesData = weeklyData.map((item, idx) => [idx + 1, item.count]);
        setChartData([
          {
            name: 'Test Runs',
            data: chartSeriesData,
            color: '#fcb400',
            marker: { enabled: true, radius: 5 }
          }
        ]);
        setShowTestingComponents(true);
      } catch (error) {
        console.error("Error fetching test runs over time:", error);
        setShowTestingComponents(true);
      }
    }
    fetchTestRunsOverTime();
  }, [showOnlyTable, scopeApiCollectionIds]);

  const emptyCardComponent = (
    <Text alignment='center' color='subdued'>
      No test run data found for the selected time period.
    </Text>
  );

  const defaultChartOptions = {
    legend: {
      enabled: false
    },
    xAxis: {
      type: 'linear',
      tickInterval: 1,
      labels: {
        formatter: function () {
          return 'W' + this.value;
        }
      },
      title: {
        text: 'Weeks'
      },
      visible: true,
      gridLineWidth: 0
    },
    yAxis: [
      {
        title: {
          text: 'Test Runs',
        },
        min: 0,
        gridLineWidth: 1,
        visible: true
      }
    ],
    exporting: { enabled: false } 
  };

  const tooltipFormatter = function() {
    return `${this.y} Test Runs`;
  };

  const allZero =
    chartData &&
    chartData.length > 0 &&
    chartData[0].data.length > 0 &&
    chartData[0].data.every(([_, count]) => count === 0);

  const testingGraph =
    chartData &&
    chartData.length > 0 &&
    chartData[0].data.length > 0 &&
    !allZero ? (
      <InfoCard
        component={
          <LineChart
            type="line"
            height={280}
            data={chartData}
            yAxisTitle="Test Runs"
            text={true}
            showGridLines={true}
            noGap={false}
            width={40}
            defaultChartOptions={defaultChartOptions}
            tooltipFormatter={tooltipFormatter}
            color="#6D3BEF"
            exportingDisabled={true} 
          />
        }
        title="Test Runs Over Time"
        titleToolTip="Track test run activity over the last 5 weeks, showing the number of test runs per week."
        linkText=""
        linkUrl=""
      />
    ) : (
      <EmptyCard 
        title="Test Runs Over Time" 
        subTitleComponent={showTestingComponents ? emptyCardComponent : <Text alignment='center' color='subdued'>Loading...</Text>} 
      />
    );

  return testingGraph;
};

export default TestRunOverTimeGraph; 
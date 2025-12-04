import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import LineChart from '../../../components/charts/LineChart';
import api from '../api';
import func from '@/util/func';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const TestRunOverTimeGraph = ({ showOnlyTable = false }) => {
  const [chartData, setChartData] = useState([]);
  const [showTestingComponents, setShowTestingComponents] = useState(false);

  async function fetchTestRunsOverTime() {
    setShowTestingComponents(false);
    let tempData = [];
    await api.allTestsCountsRanges().then((res) => {
      Object.keys(res).forEach((key) => {
        const timeInMillis = func.getEpochMillis(key, "weekOfYear");
        const count = res[key] || 0;
        const dataPoint = [timeInMillis, count];
        tempData.push(dataPoint);
      })
    });
    tempData.sort((a, b) => a[0] - b[0]); // Sort by timestamp

    setChartData([{
        name: mapLabel("Test runs", getDashboardCategory()),
        data: tempData,
        color: '#6D3BEF'  // Using a consistent color for test runs
    }]);
    setShowTestingComponents(true);
  }

  useEffect(() => {
    fetchTestRunsOverTime();
  }, [showOnlyTable]);

  const testingGraph = (chartData?.length > 0) ? (
    <InfoCard
      component={
         <LineChart
            data={chartData}
            height={320}
            yAxisTitle="Number of testing runs"
            type="line"
            text={true}
            showGridLines={true}
          />
      }
      title={mapLabel("Test runs", getDashboardCategory()) + " Over Time"}
      titleToolTip={"Track test run activity over the last 5 weeks, showing the number of" + mapLabel("Test runs", getDashboardCategory()) + " per week."}
    />
  ) : (
    <EmptyCard 
      title={mapLabel("Test runs", getDashboardCategory()) + " Over Time"} 
      subTitleComponent={
        showTestingComponents ? 
          <Text alignment='center' color='subdued'>No test run data found for the selected time period.</Text> : 
          <Text alignment='center' color='subdued'>Loading...</Text>
      } 
    />
  );

  return testingGraph;
};

export default TestRunOverTimeGraph; 
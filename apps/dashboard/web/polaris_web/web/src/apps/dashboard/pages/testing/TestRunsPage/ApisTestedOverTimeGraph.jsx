import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import api from '../../observe/api';
import func from '@/util/func';
import LineChart from '../../../components/charts/LineChart';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';


const ApisTestedOverTimeGraph = ({ showOnlyTable = false }) => {
  const [chartData, setChartData] = useState([]);
  const [showTestingComponents, setShowTestingComponents] = useState(false);

  async function fetchChartData() {
    let tempChartData = [];
    const res = await api.allApisTestedRanges();
    Object.keys(res).forEach((key) => {
      const timeInMillis = func.getEpochMillis(key, "weekOfYear");
      const count = res[key] || 0;
      const dataPoint = [timeInMillis, count];
      tempChartData.push(dataPoint)
    });
    setChartData([{
      name: `${mapLabel("APIs", getDashboardCategory())}  tested`,
      data: tempChartData,
      color: '#6D3BEF'  // Using a consistent color for APIs tested
    }]);
    setShowTestingComponents(true);
  }

  useEffect(() => {
    fetchChartData()
  }, [showOnlyTable]);

  const emptyCardComponent = (
    <Text alignment='center' color='subdued'>
      No API testing data found for the selected time period.
    </Text>
  );

  const testingGraph = (chartData && chartData.length > 0) ? (
    <InfoCard
      component={
        <LineChart
          data={chartData}
          height={280}
          yAxisTitle={`${mapLabel("APIs", getDashboardCategory())} ` + mapLabel("tested", getDashboardCategory())}
          type="line"
          text={true}
          showGridLines={true}
        />
      }
      title={`${mapLabel("APIs", getDashboardCategory())} ` + mapLabel("tested", getDashboardCategory()) + " over time"}
      titleToolTip={`Track ${mapLabel("API", getDashboardCategory())} testing activity over the last 5 weeks, showing the number of ${mapLabel("APIs", getDashboardCategory())} tested per week.`}
      linkText=""
      linkUrl=""
    />
  ) : (
    <EmptyCard 
      title={`${mapLabel("APIs", getDashboardCategory())} ` + mapLabel("tested", getDashboardCategory()) + " over time"}
    subTitleComponent={showTestingComponents ? emptyCardComponent : <Text alignment='center' color='subdued'>Loading...</Text>} 
    />
  );

  return testingGraph;
};

export default ApisTestedOverTimeGraph;
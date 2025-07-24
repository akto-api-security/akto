import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import StackedChart from '../../../components/charts/StackedChart';
import api from '../api';
import PersistStore from '../../../../main/PersistStore';

const TESTED_COLOR = '#6BADD7'; 
const UNTESTED_COLOR = '#FD8D3C'; 

const splitLabel = (label, maxLen = 20) => {
  if (label.length <= maxLen) return label;
  const parts = [];
  let str = label;
  while (str.length > maxLen) {
    parts.push(str.slice(0, maxLen));
    str = str.slice(maxLen);
  }
  if (str) parts.push(str);
  return parts.join('\n');
};

const ApiCollectionCoverageGraph = () => {
  const [chartData, setChartData] = useState([]);
  const [categories, setCategories] = useState([]);
  const [showTestingComponents, setShowTestingComponents] = useState(false);

  const fetchCoverageData = async () => {
    setShowTestingComponents(false);
    try {
      const coverageInfo = await api.getCoverageInfoForCollections();
      const allCollectionsArr = PersistStore.getState().allCollections;

      const userCollectionsArr = allCollectionsArr.filter(
        c => !c.deactivated && !c.automated
      );

      const top5 = userCollectionsArr
        .filter(c => c.urlsCount > 0)
        .sort((a, b) => b.urlsCount - a.urlsCount)
        .slice(0, 5);

      const results = top5.map(col => {
        const testedCount = Math.min(coverageInfo[col.id] || 0, col.urlsCount);
        const untestedCount = Math.max(col.urlsCount - testedCount, 0);
        return {
          name: col.displayName,
          tested: testedCount,
          untested: untestedCount
        };
      });

      const collectionNames = results.map(item => splitLabel(item.name, 20));
      setCategories(collectionNames);

      const testedData = results.map((item, index) => [index, item.tested]);
      const untestedData = results.map((item, index) => [index, item.untested]);

      const stackedChartData = [
        {
          name: 'Tested',
          data: testedData,
          color: TESTED_COLOR
        },
        {
          name: 'Untested',
          data: untestedData,
          color: UNTESTED_COLOR
        }
      ];

      setChartData(stackedChartData);
      setShowTestingComponents(true);
    } catch (error) {
      console.error('Error fetching coverage data:', error);
      setShowTestingComponents(true);
    }
  };

  useEffect(() => {
    fetchCoverageData();
  }, []);

  const customXAxis = {
    type: 'category',
    categories: categories,
    visible: true,
    gridLineWidth: 0
  };

  const defaultChartOptions = { 
    legend: {
      enabled: false, 
      align: 'right',
      verticalAlign: 'top',
      layout: 'vertical',
      floating: true,
      x: -10,
      y: 10,
      itemMarginTop: 4,
      itemMarginBottom: 4
    }

  };

  const emptyCardComponent = (
    <Text alignment='center' color='subdued'>
      No collections found with API endpoints.
    </Text>
  );

  const coverageGraph = (chartData && chartData.length > 0) ? (
    <InfoCard
      component={
        <StackedChart
          type="column"
          height="280px"
          data={chartData}
          yAxisTitle="Number of APIs"
          text={true}
          showGridLines={true}
          customXaxis={customXAxis}
          noGap={false}
          width={40}
          defaultChartOptions={defaultChartOptions}
          exportingDisabled={true}
        />
      }
      title="API Collection Coverage"
      titleToolTip="Overview of API testing coverage across your top collections, showing tested vs untested endpoints."
      linkText=""
      linkUrl=""
    />
  ) : (
    <EmptyCard 
      title="API Collection Coverage" 
      subTitleComponent={showTestingComponents ? emptyCardComponent : <Text alignment='center' color='subdued'>Loading...</Text>} 
    />
  );

  return coverageGraph;
};

export default ApiCollectionCoverageGraph;
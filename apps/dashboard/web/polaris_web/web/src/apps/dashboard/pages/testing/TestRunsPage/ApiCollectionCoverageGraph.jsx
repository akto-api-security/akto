import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import StackedChart from '../../../components/charts/StackedChart';
import api from '../api';
import PersistStore from '../../../../main/PersistStore';

const TESTED_COLOR = '#007F5F'; 
const UNTESTED_COLOR = '#E4E5E7'; 

const ApiCollectionCoverageGraph = () => {
  const [chartData, setChartData] = useState([]);
  const [collectionNames, setCollectionNames] = useState([]);
  const [showTestingComponents, setShowTestingComponents] = useState(false);

  const fetchCoverageData = async () => {
    setShowTestingComponents(false);
    try {
      const coverageInfo = await api.getCoverageInfoForCollections();
      const userCollections = PersistStore.getState().allCollections
        .filter(c => !c.deactivated && !c.automated && c.urlsCount > 0)
        .sort((a, b) => b.urlsCount - a.urlsCount)
        .slice(0, 5);

      const results = userCollections.map(col => ({
        name: col.displayName,
        tested: Math.min(coverageInfo[col.id] || 0, col.urlsCount),
        untested: Math.max(col.urlsCount - Math.min(coverageInfo[col.id] || 0, col.urlsCount), 0)
      }));

      setCollectionNames(results.map(item => item.name));
      setChartData([
        {
          name: 'Tested',
          data: results.map((item, index) => [index, item.tested]),
          color: TESTED_COLOR
        },
        {
          name: 'Untested',
          data: results.map((item, index) => [index, item.untested]),
          color: UNTESTED_COLOR
        }
      ]);
      setShowTestingComponents(true);
    } catch (error) {
      console.error('Error fetching coverage data:', error);
      setShowTestingComponents(true);
    }
  };

  useEffect(() => {
    fetchCoverageData();
  }, []);

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
          customXaxis={{
            type: 'category',
            categories: collectionNames,
            visible: true,
            gridLineWidth: 0
          }}
          noGap={false}
          width={40}
          defaultChartOptions={{
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
          }}
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
      subTitleComponent={
        showTestingComponents ? 
          <Text alignment='center' color='subdued'>No collections found with API endpoints.</Text> : 
          <Text alignment='center' color='subdued'>Loading...</Text>
      } 
    />
  );

  return coverageGraph;
};

export default ApiCollectionCoverageGraph;
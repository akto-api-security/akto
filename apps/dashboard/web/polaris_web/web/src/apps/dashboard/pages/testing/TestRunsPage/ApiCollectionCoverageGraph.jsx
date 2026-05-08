import React, { useEffect, useState } from 'react';
import { Text } from '@shopify/polaris';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import StackedChart from '../../../components/charts/StackedChart';
import api from '../../observe/api';
import PersistStore from '../../../../main/PersistStore';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const TESTED_COLOR = '#007F5F'; 
const UNTESTED_COLOR = '#E4E5E7'; 

const ApiCollectionCoverageGraph = () => {
  const [chartData, setChartData] = useState([]);
  const [collectionNames, setCollectionNames] = useState([]);
  const [showTestingComponents, setShowTestingComponents] = useState(false);
  const allCollections = PersistStore.getState().allCollections || [];

  const fetchCoverageData = async () => {
    setShowTestingComponents(false);
    try {
      const coverageInfo = await api.getCoverageInfoForCollections();

      const sortedCollections = allCollections.filter(col => col?.type !== "API_GROUP")
        .map(col => {
          const tested = Math.min(coverageInfo[col.id] || 0, col.urlsCount);
          const ratio = col.urlsCount > 0 ? tested / col.urlsCount : 0;
          return { ...col, tested, ratio };
        })
        .sort((a, b) => {
          if (a.ratio === b.ratio) {
            return b.urlsCount - a.urlsCount; // more endpoints first if ratio is same
          }
          return a.ratio - b.ratio; // lower ratio first
        })
        .slice(0, 5);

      const results = sortedCollections.map(col => ({
        name: col.displayName,
        tested: col.tested,
        untested: Math.max(col.urlsCount - col.tested, 0)
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
          yAxisTitle={`Number of ${mapLabel("APIs", getDashboardCategory())}`}
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
      title={mapLabel("API Collection Coverage", getDashboardCategory())}
      titleToolTip={"Overview of " + mapLabel("API testing", getDashboardCategory()) + " coverage across your top collections, showing tested vs untested endpoints."}
      linkText=""
      linkUrl=""
    />
  ) : (
    <EmptyCard 
      title={mapLabel("API Collection Coverage", getDashboardCategory())}
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
import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import ChartypeComponent from './ChartypeComponent';
import PersistStore from '../../../../main/PersistStore';
import observeApi from '../../observe/api';
import { Text, Box } from '@shopify/polaris';

const LastTwoWeeksApiTestCoverageChart = () => {
  const [coverageData, setCoverageData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchCoverage() {
      setLoading(true);
      const now = Math.floor(Date.now() / 1000);
      const twoWeeksAgo = now - 14 * 24 * 60 * 60;
      const allCollections = PersistStore.getState().allCollections || [];
      let totalNew = 0, tested = 0, untested = 0;

      for (const collection of allCollections) {
        try {
          const resp = await observeApi.fetchApiInfosForCollection(collection.id);
          const apiInfoList = resp?.apiInfoList || [];
          for (const api of apiInfoList) {
            const discovered = api.discoveredTimestamp || api.startTs || 0;
            if (discovered >= twoWeeksAgo) {
              totalNew++;
              if (api.lastTested && api.lastTested > discovered) {
                tested++;
              } else {
                untested++;
              }
            }
          }
        } catch (e) {
        }
      }

      setCoverageData({ totalNew, tested, untested });
      setLoading(false);
    }
    fetchCoverage();
  }, []);

  if (loading) {
    return <EmptyCard title="New APIs in Last 2 Weeks - Test Coverage" subTitleComponent={<Text alignment='center' color='subdued'>Loading...</Text>} />;
  }

  if (!coverageData || coverageData.totalNew === 0) {
    return <EmptyCard title="New APIs in Last 2 Weeks - Test Coverage" subTitleComponent={<Text alignment='center' color='subdued'>No new APIs discovered in the last 2 weeks.</Text>} />;
  }

  const donutData = {
    'Tested': {
      text: coverageData.tested,
      color: '#6BADD7', 
    },
    'Untested': {
      text: coverageData.untested,
      color: '#FED3D1', 
    },
  };

  return (
  <InfoCard
    title="New APIs in Last 2 Weeks - Test Coverage"
    titleToolTip="Shows the number of new APIs discovered in the last 2 weeks and how many have been tested."
    component={
      <Box 
        display="flex" 
        flexDirection="column"
        alignItems="center" 
        justifyContent="center" 
        width="100%" 
        height="100%"
        padding="2"
      >
        <ChartypeComponent
          data={donutData}
          title={"Test Coverage"}
          isNormal={true}
          boxHeight={'220px'}
          pieInnerSize="60%"
          chartSize={220}
          chartOnLeft={false} 
        />
      </Box>
    }
    minHeight={"300px"}
  />
);

};

export default LastTwoWeeksApiTestCoverageChart;

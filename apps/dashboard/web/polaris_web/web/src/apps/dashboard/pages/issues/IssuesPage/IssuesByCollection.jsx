import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';
import PersistStore from '../../../../main/PersistStore';

const IssuesByCollection = ({ collectionsData }) => {
  const [barData, setBarData] = useState([]);
  const apiCollectionMap = PersistStore.getState().collectionsMap;  

  function processCollectionsData() {
    let tempBarData = [];
    if (!collectionsData || collectionsData.length === 0) {
      setBarData([]);
      return;
    }
    const collectionIssuesCount = {};
    Object.keys(collectionsData).forEach(key => {
      const apiSplit = key.split(' ');
      const collectionId = apiSplit[0];
      let count = collectionIssuesCount[collectionId] || 0;
      collectionIssuesCount[collectionId] = count + 1;
    });
    Object.keys(collectionIssuesCount).sort((a, b) => collectionIssuesCount[b] - collectionIssuesCount[a]).slice(0, 5).forEach((collectionId) => {
      tempBarData.push({
        text: apiCollectionMap[collectionId] || `Collection ${collectionId}`,
        value: collectionIssuesCount[collectionId],
        color: '#B692F6',
      });
    });
    setBarData(tempBarData);
  }

  useEffect(() => {
    processCollectionsData();
  }, [collectionsData]);

  return (
    (barData.length > 0 && barData.every(item => item.value === 0)) ? (
      <EmptyCard
        title="Issues by Collection"
        subTitleComponent={<Text alignment='center' color='subdued'>No issues found</Text>}
      />
    ) : (
      <InfoCard
        title="Issues by Collection"
        titleToolTip="Top 5 collections with the highest issues"
        component={
          <BarGraph
            data={barData}
            areaFillHex="true"
            height={"320px"}
            barGap={12}
            showGridLines={true}
            showYAxis={true}
            yAxisTitle="Number of Issues"
            barWidth={30}
            defaultChartOptions={{ legend: { enabled: false } }}
          />
        }
      />
    )
  );
};

export default IssuesByCollection; 
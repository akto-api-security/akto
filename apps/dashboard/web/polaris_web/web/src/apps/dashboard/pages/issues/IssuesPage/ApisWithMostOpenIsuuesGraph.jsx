import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import observeFunc from '../../observe/transform';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';

const ApisWithMostOpenIsuuesGraph = ({ issuesData = [], collectionsData = [] }) => {
  const [barData, setBarData] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function processIssuesData() {
      setLoading(true);
      try {
        const nonDefaultCollectionIds = collectionsData.map(c => c.id);

        const filteredIssues = issuesData.filter(issue => {
          const apiCollectionId = issue?.id?.apiInfoKey?.apiCollectionId;
          const isOpenIssue = issue?.testRunIssueStatus === "OPEN";
          return apiCollectionId && nonDefaultCollectionIds.includes(apiCollectionId) && isOpenIssue;
        });

        const endpointMap = {};
        filteredIssues.forEach(issue => {
          const apiInfoKey = issue?.id?.apiInfoKey;
          if (!apiInfoKey || !apiInfoKey.method || !apiInfoKey.url) return;
          const endpointKey = apiInfoKey.url;
          if (!endpointMap[endpointKey]) {
            endpointMap[endpointKey] = { count: 0, apiInfoKey };
          }
          endpointMap[endpointKey].count += 1;
        });

        const endpointArr = Object.entries(endpointMap).map(([url, { count, apiInfoKey }]) => ({ 
          apiInfoKey, 
          openIssueCount: count 
        }));
        const top5 = endpointArr.sort((a, b) => b.openIssueCount - a.openIssueCount).slice(0, 5);

        const barGraphData = top5.map(({ apiInfoKey, openIssueCount }) => {
          const prettyUrl = observeFunc.getTruncatedUrl(apiInfoKey.url);
          return {
            text: prettyUrl,
            value: openIssueCount,
            color: '#B692F6', 
          };
        });
        setBarData(barGraphData);
      } catch (e) {
        console.error('Error processing issues data for graph:', e);
        setBarData([]);
      }
      setLoading(false);
    }
    
    if (issuesData.length > 0 && collectionsData.length > 0) {
      processIssuesData();
    } else {
      setLoading(false);
      setBarData([]);
    }
  }, [issuesData, collectionsData]);

  return (
    (barData.length === 0 || barData.every(item => item.value === 0)) ? (
      <EmptyCard
        title="APIs With Most Open Issues"
        subTitleComponent={<Text alignment='center' color='subdued'>No open issues found</Text>}
      />
    ) : (
      <InfoCard
        title="APIs With Most Open Issues"
        titleToolTip="Top 5 API endpoints with the most unresolved (OPEN) issues."
        component={
          <BarGraph
            data={barData}
            areaFillHex="true"
            height={"320px"}
            barGap={12}
            showGridLines={true}
            showYAxis={true}
            yAxisTitle="Number of Unresolved Issues"
            barWidth={30}
            defaultChartOptions={{ legend: { enabled: false } }}
          />
        }
      />
    )
  );
};

export default ApisWithMostOpenIsuuesGraph; 
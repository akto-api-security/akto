import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import api from '../api';
import apiCollectionsApi from "../../observe/api";
import observeFunc from '../../observe/transform';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';

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

const ApisWithMostOpenIsuuesGraph = () => {
  const [barData, setBarData] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchTop5ApiEndpointsWithOpenIssues() {
      setLoading(true);
      try {
        const collectionsResp = await apiCollectionsApi.getAllCollectionsBasic();
        const nonDefaultCollectionIds = (collectionsResp.apiCollections || [])
          .filter(c => !c.deactivated && !c.automated)
          .map(c => c.id);

        const resp = await api.fetchIssues(0, 20000, ["OPEN"], [], [], [], -1, -1, 0, 0, true, []);
        const issues = resp.issues || [];

        const filteredIssues = issues.filter(issue => {
          const apiCollectionId = issue?.id?.apiInfoKey?.apiCollectionId;
          return apiCollectionId && nonDefaultCollectionIds.includes(apiCollectionId);
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
            text: splitLabel(prettyUrl, 20),
            value: openIssueCount,
            color: '#B692F6', 
          };
        });
        setBarData(barGraphData);
      } catch (e) {
        console.error('Error fetching top 5 API endpoints with open issues:', e);
        setBarData([]);
      }
      setLoading(false);
    }
    fetchTop5ApiEndpointsWithOpenIssues();
  }, []);

  return (
    (barData.length > 0 && barData.every(item => item.value === 0)) ? (
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
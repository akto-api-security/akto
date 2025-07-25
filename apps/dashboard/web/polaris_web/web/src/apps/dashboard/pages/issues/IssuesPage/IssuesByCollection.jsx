import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import apiCollectionsApi from '../../observe/api';
import api from '../api';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';

const IssuesByCollection = () => {
  const [barData, setBarData] = useState([]);

  useEffect(() => {
    async function fetchCollectionsIssueCounts() {
      try {
        const collectionsResp = await apiCollectionsApi.getAllCollectionsBasic();
        const collections = (collectionsResp.apiCollections || []).filter(c => !c.deactivated && !c.automated);
        const severityResp = await apiCollectionsApi.getSeverityInfoForCollections();
        const severityInfo = severityResp || {};
        const data = collections.map(c => {
          const sev = severityInfo[c.id] || {};
          const totalIssues = Object.values(sev).reduce((sum, v) => {
            if (v === null || v === undefined || typeof v !== 'number' || isNaN(v)) {
              return sum;
            }
            return sum + v;
          }, 0);
          return {
            name: c.displayName || c.name,
            issueCount: totalIssues
          };
        });
        const top5 = data.sort((a, b) => b.issueCount - a.issueCount).slice(0, 5);
        const barGraphData = top5.map(({ name, issueCount }) => ({
          text: name,
          value: issueCount,
          color: '#FED3D1',
        }));
        setBarData(barGraphData);
      } catch (e) {
        setBarData([]);
        console.error('Error fetching collection issue counts:', e);
      }
    }
    fetchCollectionsIssueCounts();
  }, []);

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
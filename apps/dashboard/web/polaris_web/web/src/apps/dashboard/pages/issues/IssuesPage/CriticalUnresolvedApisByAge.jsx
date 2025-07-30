import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import dashboardApi from '../../dashboard/api.js';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';

const AGE_BUCKETS = [
  '<7 days',
  '7-14 days',
  '15-30 days',
  '>30 days'
];

const CriticalUnresolvedApisByAge = () => {
  const [barData, setBarData] = useState([]);

  useEffect(() => {
    async function fetchUnresolvedHighSeverityIssuesByAge() {
      try {
        const now = Math.floor(Date.now() / 1000);
        const thirtyDaysAgo = now - (30 * 24 * 60 * 60);

        const trendResp = await dashboardApi.fetchCriticalIssuesTrend(thirtyDaysAgo, now);
        const { issuesTrend, epochKey } = trendResp;

        let buckets = {
          '<7 days': 0,
          '7-14 days': 0,
          '15-30 days': 0,
          '>30 days': 0
        };

        const currentTime = Math.floor(Date.now() / 1000);

        const criticalData = issuesTrend.CRITICAL || {};
        Object.entries(criticalData).forEach(([dateKey, count]) => {
          const [year, timeUnit] = dateKey.split('_');
          let creationTime;
          if (epochKey === 'dayOfYear') {
            const date = new Date(parseInt(year), 0, parseInt(timeUnit));
            creationTime = Math.floor(date.getTime() / 1000);
          } else if (epochKey === 'weekOfYear') {
            const date = new Date(parseInt(year), 0, 1 + (parseInt(timeUnit) - 1) * 7);
            creationTime = Math.floor(date.getTime() / 1000);
          } else {
            const date = new Date(parseInt(year), parseInt(timeUnit) - 1, 1);
            creationTime = Math.floor(date.getTime() / 1000);
          }
          
          const ageDays = (currentTime - creationTime) / (60 * 60 * 24);
          if (ageDays < 7) buckets['<7 days'] += count;
          else if (ageDays < 15) buckets['7-14 days'] += count;
          else if (ageDays < 31) buckets['15-30 days'] += count;
          else buckets['>30 days'] += count;
        });
        
        const barGraphData = AGE_BUCKETS.map(bucket => ({
          text: bucket,
          value: buckets[bucket],
          color: '#8B5CF4',
        }));
        setBarData(barGraphData);
      } catch (e) {
        setBarData([]);
        console.error('Error fetching unresolved high severity issues by age:', e);
      }
    }
    fetchUnresolvedHighSeverityIssuesByAge();
  }, []);

  return (
    (barData.length > 0 && barData.every(item => item.value === 0)) ? (
      <EmptyCard
        title="Unresolved Critical Issues by Age"
        subTitleComponent={<Text alignment='center' color='subdued'>No unresolved critical issues found</Text>}
      />
    ) : (
      <InfoCard
        title="Unresolved Critical Issues by Age"
        titleToolTip="Distribution of unresolved (OPEN) severity issues by age."
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

export default CriticalUnresolvedApisByAge; 
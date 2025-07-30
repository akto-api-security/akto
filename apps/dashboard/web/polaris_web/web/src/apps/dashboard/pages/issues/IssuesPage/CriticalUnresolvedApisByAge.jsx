import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import dashboardApi from '../../dashboard/api.js';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';
import func from '@/util/func.js';

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
        const now = func.timeNow();
        const trendResp = await dashboardApi.fetchCriticalIssuesTrend(0, now, ["CRITICAL"]);
        const { issuesTrend, epochKey } = trendResp;

        let buckets = AGE_BUCKETS.reduce((acc, bucket) => {
          acc[bucket] = 0;
          return acc;
        }, {});

        const currentTime = func.timeNow();
        // time is 30 days ago so it will come weekOfYear
        const criticalData = issuesTrend.CRITICAL || {};
        Object.entries(criticalData).forEach(([dateKey, count]) => {
          const creationTime = func.getEpochMillis(dateKey, epochKey) / 1000; // Convert to seconds
          const ageDays = (currentTime - creationTime) / (60 * 60 * 24);
          if (ageDays < 7) buckets['<7 days'] += count;
          else if (ageDays < 15) buckets['7-14 days'] += count;
          else if (ageDays < 31) buckets['15-30 days'] += count;
          else buckets['>30 days'] += count;
        });
        
        const barGraphData = AGE_BUCKETS.map(bucket => ({
          text: bucket,
          value: buckets[bucket],
          color: '#DF2909',
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
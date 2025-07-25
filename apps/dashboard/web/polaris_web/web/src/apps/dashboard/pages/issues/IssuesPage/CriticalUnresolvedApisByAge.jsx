import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import api from '../api';
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
        const resp = await api.fetchIssues(0, 20000, ["OPEN"], [], ["CRITICAL"], [], -1, -1, 0, 0, true, []);
        const issues = resp.issues || [];
        const now = Math.floor(Date.now() / 1000); 
        let buckets = {
          '<7 days': 0,
          '7-14 days': 0,
          '15-30 days': 0,
          '>30 days': 0
        };
        issues.forEach(issue => {
          const created = issue.creationTime;
          if (!created || typeof created !== "number") return;
          const ageDays = (now - created) / (60 * 60 * 24);
          
          if (ageDays < 7) buckets['<7 days']++;
          else if (ageDays < 15) buckets['7-14 days']++;
          else if (ageDays < 31) buckets['15-30 days']++;
          else buckets['>30 days']++;
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
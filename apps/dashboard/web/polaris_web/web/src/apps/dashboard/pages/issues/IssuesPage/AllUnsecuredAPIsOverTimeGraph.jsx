import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import api from "../../dashboard/api"
import func from '@/util/func';
import LineChart from '../../../components/charts/LineChart';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const AllUnsecuredAPIsOverTimeGraph = () => {
  const [chartData, setChartData] = useState([]);

  useEffect(() => {
    async function fetchMonthlySeverityCounts() {
      try {
        const now = func.timeNow();
        const allSevArr = func.getAktoSeverities();

        const resp = await api.fetchCriticalIssuesTrend(0, now, allSevArr);
        const issuesTrend = resp.issuesTrend || {};
        
        const currentDate = new Date();
        const last12Months = [];
        
        for (let i = 11; i >= 0; i--) {
          let targetYear = currentDate.getUTCFullYear();
          let targetMonth = currentDate.getUTCMonth() - i;
          while (targetMonth < 0) {
            targetMonth += 12;
            targetYear -= 1;
          }
          const monthDate = new Date(Date.UTC(targetYear, targetMonth, 1));
          const monthKey = `${targetYear}_${targetMonth + 1}`;
          last12Months.push({
            key: monthKey,
            label: monthDate.toLocaleString('en-US', { month: 'short', year: 'numeric' }),
            timestamp: monthDate.getTime()
          });
        }

        const chartData = allSevArr.map(sev => ({
          name: sev.charAt(0) + sev.slice(1).toLowerCase(),
          data: last12Months.map(({ key, timestamp }) => {
            const severityData = issuesTrend[sev] || {};
            const count = severityData[key] || 0;
            return [timestamp, count];
          }),
          color: func.getHexColorForSeverity(sev),
        }));
        
        setChartData(chartData);
      } catch (e) {
        setChartData([]);
        console.error('Error fetching monthly severity counts:', e);
      }
    }
    fetchMonthlySeverityCounts();
  }, []);

  const allZero = chartData.length > 0 && chartData.every(s => s.data.every(([timestamp, value]) => value === 0));

  return (
    allZero ? (
      <EmptyCard
        title={`Unsecured ${mapLabel("APIs", getDashboardCategory())} Over Time`}
        subTitleComponent={<Text alignment='center' color='subdued'>{`No unsecured ${mapLabel("APIs", getDashboardCategory())} found`}</Text>}
      />
    ) : (
      <InfoCard
        title={`Unsecured ${mapLabel("APIs", getDashboardCategory())} Over Time`}
        titleToolTip="Monthly count of issues by severity for the last 12 months."
        component={
          <LineChart
            data={chartData}
            height={320}
            yAxisTitle="Number of Issues"
            type="line"
            text={true}
            showGridLines={true}
          />
        }
      />
    )
  );
};

export default AllUnsecuredAPIsOverTimeGraph;
import React, { useEffect, useState } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import api from '../api';
import func from '@/util/func';
import MultiLineChart from '../../../components/charts/MultiLineChart';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';

const SEVERITY_COLORS = {
  CRITICAL: '#DF2909',
  HIGH: '#FED3D1',
  MEDIUM: '#FFD79D',
  LOW: '#E4E5E7',
};

const AllUnsecuredAPIsOverTimeGraph = () => {
  const [series, setSeries] = useState([]);
  const [categories, setCategories] = useState([]);

  useEffect(() => {
    async function fetchMonthlySeverityCounts() {
      try {
        const resp = await api.fetchIssues(0, 20000, [], [], [], [], -1, -1, 0, 0, true, []);
        const issues = resp.issues || [];
        const monthlyCounts = {};
        issues.forEach(issue => {
          const epoch = issue.creationTime;
          if (!epoch || typeof epoch !== 'number') return;
          const date = new Date(epoch * 1000);
          const year = date.getUTCFullYear();
          const month = date.getUTCMonth() + 1;
          const monthKey = `${year}-${String(month).padStart(2, '0')}`;
          const severity = (issue.severity || '').toUpperCase();
          if (!severity) return;
          if (!monthlyCounts[monthKey]) monthlyCounts[monthKey] = {};
          monthlyCounts[monthKey][severity] = (monthlyCounts[monthKey][severity] || 0) + 1;
        });
        const allSevArr = func.getAktoSeverities();
        const now = new Date();
        const last12Months = [];
        for (let i = 11; i >= 0; i--) {
          let targetYear = now.getUTCFullYear();
          let targetMonth = now.getUTCMonth() - i;
          while (targetMonth < 0) {
            targetMonth += 12;
            targetYear -= 1;
          }
          const monthDate = new Date(Date.UTC(targetYear, targetMonth, 1));
          const monthKey = `${targetYear}-${String(targetMonth + 1).padStart(2, '0')}`;
          last12Months.push({
            key: monthKey,
            label: monthDate.toLocaleString('en-US', { month: 'short', year: 'numeric' })
          });
        }
        const result = {};
        last12Months.forEach(({ key }) => {
          result[key] = {};
          allSevArr.forEach(sev => {
            result[key][sev] = (monthlyCounts[key] && typeof monthlyCounts[key][sev] === 'number') ? monthlyCounts[key][sev] : 0;
          });
        });

        const chartSeries = allSevArr.map(sev => ({
          name: sev.charAt(0) + sev.slice(1).toLowerCase(),
          data: last12Months.map(({ key }) => result[key][sev]),
          color: SEVERITY_COLORS[sev] || undefined,
        }));
        setSeries(chartSeries);
        setCategories(last12Months.map(({ label }) => label));
      } catch (e) {
        setSeries([]);
        setCategories([]);
        console.error('Error fetching monthly severity counts:', e);
      }
    }
    fetchMonthlySeverityCounts();
  }, []);

  const allZero = series.length > 0 && series.every(s => s.data.every(v => v === 0));

  return (
    allZero ? (
      <EmptyCard
        title="Unsecured APIs Over Time"
        subTitleComponent={<Text alignment='center' color='subdued'>No unsecured APIs found</Text>}
      />
    ) : (
      <InfoCard
        title="Unsecured APIs Over Time"
        titleToolTip="Monthly count of issues by severity for the last 12 months."
        component={
          <MultiLineChart
            series={series}
            categories={categories}
            height={320}
            yAxisTitle="Number of Issues"
          />
        }
      />
    )
  );
};

export default AllUnsecuredAPIsOverTimeGraph;
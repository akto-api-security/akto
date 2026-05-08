import React, { useEffect } from 'react';
import InfoCard from '../../dashboard/new_components/InfoCard';
import BarGraph from '../../../components/charts/BarGraph';
import observeFunc from '../../observe/transform';
import EmptyCard from '../../dashboard/new_components/EmptyCard';
import { Text } from '@shopify/polaris';
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper.js'

const ApisWithMostOpenIsuuesGraph = ({ issuesData}) => {
  const [barData, setBarData] = React.useState([]);
  function processIssuesData() {
    if (!issuesData || Object.keys(issuesData).length === 0) {
      setBarData([]);
      return;
    }
    let tempBarData = [];
    Object.keys(issuesData).sort((a, b) => issuesData[b] - issuesData[a]).slice(0, 5).forEach((apiKey) => {
      const apiKeyParts = apiKey.split(' ');
      tempBarData.push({
        text: observeFunc.getTruncatedUrl(apiKeyParts[1]) + ' ' + apiKeyParts[2],
        value: issuesData[apiKey],
        color: '#B692F6',
      });
    });

    setBarData(tempBarData);
  }

  useEffect(() => {
    processIssuesData();
  }, [issuesData]);


  return (
    (barData.length === 0 || barData.every(item => item.value === 0)) ? (
      <EmptyCard
        title={`${mapLabel("APIs with most open issues", getDashboardCategory())}`}
        subTitleComponent={<Text alignment='center' color='subdued'>No open issues found</Text>}
      />
    ) : (
      <InfoCard
        title={`${mapLabel("APIs with most open issues", getDashboardCategory())}`}
        titleToolTip={`Top 5 ${mapLabel('API endpoints', getDashboardCategory())} with the most unresolved (OPEN) issues.`}
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
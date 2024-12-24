import React, { useEffect, useState } from "react";
import InfoCard from "../../dashboard/new_components/InfoCard";
import ChartypeComponent from "../../testing/TestRunsPage/ChartypeComponent";
import BarGraph from "../../../components/charts/BarGraph";

const TopThreatTypeChart = ({ data }) => {
  const [chartData, setChartData] = useState([]);
  useEffect(() => {
    const chartData = data
      .sort((a, b) => a.text.localeCompare(b.text))
      .map((x) => ({
        text: x.text.replaceAll("_", " "),
        value: x.value,
        color: x.color,
      }));
    setChartData(chartData);
  }, [data]);
  return (
    <InfoCard
      title={"Top Threat Types"}
      titleToolTip={"Top Threat Types"}
      component={
        <BarGraph
          data={chartData}
          areaFillHex="true"
          height={"300px"}
          defaultChartOptions={{
            legend: {
              enabled: false,
            },
          }}
          showYAxis={true}
          yAxisTitle="# of APIs"
          barWidth={100}
          barGap={12}
          showGridLines={true}
        />
      }
    />
  );
};

export default TopThreatTypeChart;

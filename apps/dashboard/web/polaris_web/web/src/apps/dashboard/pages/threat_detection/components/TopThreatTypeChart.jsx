import React, { useEffect, useState } from "react";
import InfoCard from "../../dashboard/new_components/InfoCard";
import BarGraph from "../../../components/charts/BarGraph";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

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
      title={`Top ${mapLabel("Threat", getDashboardCategory())} Types`}
      titleToolTip={`Top ${mapLabel("Threat", getDashboardCategory())} Types`}
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
          yAxisTitle={`# of ${mapLabel("APIs", getDashboardCategory())}`}
          barWidth={100 - (data.length * 6)}
          barGap={12}
          showGridLines={true}
        />
      }
    />
  );
};

export default TopThreatTypeChart;

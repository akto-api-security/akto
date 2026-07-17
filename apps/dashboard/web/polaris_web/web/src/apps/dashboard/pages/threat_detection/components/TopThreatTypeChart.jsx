import React, { useEffect, useState } from "react";
import InfoCard from "../../dashboard/new_components/InfoCard";
import BarGraph from "../../../components/charts/BarGraph";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

const TopThreatTypeChart = ({ data, onBarClick }) => {
  const [chartData, setChartData] = useState([]);
  useEffect(() => {
    const chartData = data
      .sort((a, b) => a.text.localeCompare(b.text))
      .map((x) => ({
        text: x.text.replaceAll("_", " "),
        value: x.value,
        color: x.color,
        filterKey: x.filterKey,
      }));
    setChartData(chartData);
  }, [data]);
  return (
    <InfoCard
      title={mapLabel("Top Threat Violation Types", getDashboardCategory())}
      titleToolTip={mapLabel("Number of detections for each threat category, from most to least common.", getDashboardCategory())}
      fillHeight
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
          onBarClick={onBarClick}
        />
      }
    />
  );
};

export default TopThreatTypeChart;

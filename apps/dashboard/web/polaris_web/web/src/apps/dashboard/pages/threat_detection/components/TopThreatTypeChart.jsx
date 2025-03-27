import React, { useEffect, useState } from "react";
import InfoCard from "../../dashboard/new_components/InfoCard";
import BarGraph from "../../../components/charts/BarGraph";

const TopThreatTypeChart = ({ data, onSubCategoryClick }) => {
  const [chartData, setChartData] = useState([]);
  useEffect(() => {
    const chartData = data
      .sort((a, b) => a.text.localeCompare(b.text))
      .map((x) => ({
        text: x.text.replaceAll("_", " "),
        value: x.value,
        color: x.color,
        subCategory: x.subCategory,
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
          onBarClick={onSubCategoryClick}
          yAxisTitle="# of APIs"
          barWidth={100 - (data.length * 6)}
          barGap={12}
          showGridLines={true}
        />
      }
    />
  );
};

export default TopThreatTypeChart;

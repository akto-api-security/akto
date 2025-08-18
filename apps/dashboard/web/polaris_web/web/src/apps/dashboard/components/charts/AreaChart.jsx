import HighchartsReact from "highcharts-react-official";
import Highcharts from "highcharts";
import React from "react";
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";

function AreaChart({ data, height = 280, color = '#6D3BEF', yAxisTitle = `${mapLabel('APIs Tested', getDashboardCategory())}`, tooltipFormatter }) {
  const chartOptions = {
    chart: {
      type: 'area',
      height: height,
    },
    title: { text: null },
    legend: { enabled: false },
    xAxis: {
      type: 'linear',
      tickInterval: 1,
      labels: {
        formatter: function () { return 'W' + this.value; }
      },
      startOnTick: false,
      endOnTick: false,
      tickPlacement: 'on',
      minPadding: 0,
      maxPadding: 0
    },
    yAxis: {
      title: { text: yAxisTitle },
      min: 0
    },
    tooltip: {
      formatter: tooltipFormatter || function () { return `${this.y} APIs`; }
    },
    plotOptions: {
      area: {
        marker: { enabled: true, radius: 4 },
        fillOpacity: 0.3
      }
    },
    credits: {
      enabled: false
    },
    exporting: {
      enabled: false
    },
    series: [{
      pointStart: 1,
      data: data,
      color: color
    }]
  };

  return <HighchartsReact highcharts={Highcharts} options={chartOptions} />;
}

export default AreaChart; 
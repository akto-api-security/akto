import React from 'react';
import HighchartsReact from 'highcharts-react-official';
import Highcharts from 'highcharts';

const MultiLineChart = ({ series, categories, height = 320, yAxisTitle = '', tooltipFormatter }) => {
  const chartOptions = {
    chart: {
      type: 'line',
      height: height,
    },
    title: { text: null },
    legend: { enabled: true },
    xAxis: {
      categories: categories,
      title: { text: 'Month' },
      labels: { style: { fontSize: '13px' } },
    },
    yAxis: {
      title: { text: yAxisTitle },
      min: 0,
      allowDecimals: false,
    },
    tooltip: {
      shared: true,
      formatter: tooltipFormatter || undefined,
    },
    plotOptions: {
      line: {
        marker: {
          enabled: true,
          radius: 4,
        },
        lineWidth: 3,
      },
      series: {
        states: {
          inactive: { opacity: 1 }
        }
      }
    },
    credits: { enabled: false },
    exporting: { enabled: false },
    series: series,
  };

  return <HighchartsReact highcharts={Highcharts} options={chartOptions} />;
};

export default MultiLineChart; 
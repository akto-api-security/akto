import HighchartsReact from "highcharts-react-official";
import Highcharts from "highcharts";
import { useEffect, useRef, useState } from "react";
import func from "../../../../util/func";
import SpinnerCentered from "../progress/SpinnerCentered";

function StackedAreaChart(props) {
  const { 
    height, 
    backgroundColor, 
    data, 
    title, 
    tooltipFormatter, 
    yAxisTitle,
    defaultChartOptions,
    exportingDisabled,
    showGridLines,
    customXaxis
  } = props;
  
  const chartComponentRef = useRef(null);

  const coreChartOptions = {
    chart: {
      type: 'area',
      height: (height) + 'px',
      spacing: [5, 0, 0, 0],
      backgroundColor: backgroundColor
    },
    credits: {
      enabled: false,
    },
    exporting: {
      enabled: !exportingDisabled
    },
    title: {
      text: title,
      align: 'left',
      margin: 20
    },
    tooltip: {
      shared: true,
    },
    plotOptions: {
      area: {
        stacking: 'percent',
        marker: { enabled: false },
        lineWidth: 0,
        states: { hover: { lineWidthPlus: 0 } },
        fillOpacity: 0.9
      }
    },
    xAxis: customXaxis ? customXaxis : {
      type: 'datetime',
      dateTimeLabelFormats: {
        day: '%b %e',
        month: '%b',
      },
      title: {
        text: 'Date',
      },
      visible: true,
      gridLineWidth: 0,
    },
    yAxis: {
      title: {
        text: yAxisTitle,
      },
      visible: true,
      gridLineWidth: showGridLines ? 1 : 0,
      min: 0,
      max: 100,
      labels: {
        formatter() {
          return this.value + "%";
        },
      },
    },
    time: {
      useUTC: false
    },
    ...defaultChartOptions
  };

  const processChartData = (data) => {
    if (!data || !Array.isArray(data)) return [];
    
    const processed = data.map(item => ({
      name: item.name,
      data: item.data,
      color: item.color,
      visible: item.visible !== false,
      fillOpacity: 0.9
    }));
    return processed;
  };

  const [chartOptions, setChartOptions] = useState({...coreChartOptions, series: []});

  useEffect(() => {
    setChartOptions((prev) => {
      let tmp = processChartData(data);
      if (func.deepComparison(prev.series, tmp)) {
        return prev;
      }
      
      prev.series = tmp;
      prev.tooltip.formatter = tooltipFormatter;
      return {...prev};
    });
  }, [data, tooltipFormatter]);

  return (
    data.length > 0 ? <HighchartsReact
      key={"stacked-area-chart"}
      highcharts={Highcharts}
      options={chartOptions}
      ref={chartComponentRef}
    /> : <SpinnerCentered/>
  );
}

export default StackedAreaChart;

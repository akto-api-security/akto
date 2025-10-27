import HighchartsReact from "highcharts-react-official";
import Highcharts from "highcharts";
import HighchartsSankey from "highcharts/modules/sankey";
import { useEffect, useRef, useState } from "react";
import func from "../../../../util/func";

// Initialize Sankey module
if (typeof Highcharts === 'object') {
  HighchartsSankey(Highcharts);
}

function SankeyChart(props) {
  const { 
    height, 
    backgroundColor, 
    data, 
    title, 
    tooltipFormatter, 
    defaultChartOptions,
    color,
    exportingDisabled,
    nodeWidth,
    nodePadding,
    linkOpacity
  } = props;
  
  const chartComponentRef = useRef(null);

  const coreChartOptions = {
    chart: {
      type: 'sankey',
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
      sankey: {
        linkOpacity: linkOpacity || 0.5,
        nodeWidth: nodeWidth || 30,
        nodePadding: nodePadding || 20,
        dataLabels: {
          enabled: true,
          style: {
            color: '#000000',
            fontSize: '12px',
            fontWeight: 'normal',
            textOutline: 'none'
          },
          formatter: function() {
            return this.point.isNode ? this.point.name : '';
          }
        }
      }
    },
    time: {
      useUTC: false
    },
    ...defaultChartOptions
  };

  const processChartData = (data) => {
    if (!data || !Array.isArray(data)) return [];
    
    return data.map(item => ({
      from: item.from,
      to: item.to,
      weight: item.weight,
      color: item.color || color
    }));
  };

  const [chartOptions, setChartOptions] = useState({...coreChartOptions, series: []});

  useEffect(() => {
    setChartOptions((prev) => {
      let tmp = processChartData(data);
      if (func.deepComparison(prev.series, tmp)) {
        return prev;
      }
      
      // Extract unique nodes from data
      const nodeSet = new Set();
      tmp.forEach((item) => {
        if (item.from) nodeSet.add(item.from);
        if (item.to) nodeSet.add(item.to);
      });
      
      const nodes = Array.from(nodeSet).map(name => ({ 
        id: name, 
        name 
      }));

      prev.series = [{
        type: 'sankey',
        name: 'Flow',
        keys: ['from', 'to', 'weight'],
        data: tmp,
        nodes: nodes
      }];
      
      prev.tooltip.formatter = tooltipFormatter;
      return {...prev};
    });
  }, [data, tooltipFormatter, color]);

  return (
    data.length > 0 ? <HighchartsReact
      key={"sankey-chart"}
      highcharts={Highcharts}
      options={chartOptions}
      ref={chartComponentRef}
    /> : <></>
  );
}

export default SankeyChart;

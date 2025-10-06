import { useEffect, useState } from "react";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";
import HighchartsSankey from "highcharts/modules/sankey";
import InfoCard from "../../dashboard/new_components/InfoCard";
import { Spinner } from "@shopify/polaris";
import api from "../api";

// Initialize Sankey module
if (typeof Highcharts === 'object') {
  HighchartsSankey(Highcharts);
}

function ThreatSankeyChart({ startTimestamp, endTimestamp }) {
  const [loading, setLoading] = useState(true);
  const [chartData, setChartData] = useState([]);

  const formatCategoryName = (category) => {
    if (!category) return 'Unknown';
    
    // Convert category codes to readable names based on common patterns
    const readableName = category.replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, l => l.toUpperCase());
    
    return readableName;
  };

  // Generate a palette of distinct colors (returns hex strings)
  const generateColorPalette = (count) => {
    if (count <= 0) return [];
    const colors = [];
    for (let i = 0; i < count; i++) {
      // distribute hues around the color wheel
      const hue = Math.round((i * 360) / count);
      const saturation = 65; // percent
      const lightness = 50; // percent
      colors.push(hslToHex(hue, saturation, lightness));
    }
    return colors;
  };

  // Convert HSL to hex
  const hslToHex = (h, s, l) => {
    // h: 0-360, s: 0-100, l:0-100
    s /= 100;
    l /= 100;
    const k = (n) => (n + h / 30) % 12;
    const a = s * Math.min(l, 1 - l);
    const f = (n) => {
      const color = l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
      return Math.round(255 * color)
        .toString(16)
        .padStart(2, '0');
    };
    return `#${f(0)}${f(8)}${f(4)}`;
  };

  useEffect(() => {
    let mounted = true;
    const fetchData = async () => {
      setLoading(true);
      try {
        const res = await api.fetchThreatCategoryCount(startTimestamp, endTimestamp);

        if (!mounted) return;

        if (res?.categoryCounts && Array.isArray(res.categoryCounts)) {
          const data = res.categoryCounts;
          const sankeyLinks = [];
          const categoryTotals = {};

          data.forEach((item) => {
            const category = item.category || 'Unknown';
            const subCategory = item.subCategory || category;
            const count = item.count || 0;

            if (count > 0) {
              if (category === subCategory) {
                const formattedName = formatCategoryName(category);
                sankeyLinks.push({ from: 'Total Threats', to: formattedName, weight: count });
              } else {
                const formattedCategory = formatCategoryName(category);
                const formattedSubCategory = formatCategoryName(subCategory);
                if (!categoryTotals[formattedCategory]) categoryTotals[formattedCategory] = 0;
                categoryTotals[formattedCategory] += count;
                sankeyLinks.push({ from: formattedCategory, to: formattedSubCategory, weight: count });
              }
            }
          });

          Object.keys(categoryTotals).forEach((category) => {
            sankeyLinks.push({ from: 'Total Threats', to: category, weight: categoryTotals[category] });
          });

          setChartData(sankeyLinks.length > 0 ? sankeyLinks : []);
        } else {
          setChartData([]);
        }
      } catch (error) {
        if (mounted) setChartData([]);
      } finally {
        if (mounted) setLoading(false);
      }
    };

    fetchData();
    return () => { mounted = false; };
  }, [startTimestamp, endTimestamp]);

  // Build chart options using the dynamic node colors and link coloring
  const chartOptions = (() => {
    // extract unique node names from links
    const nodeSet = new Set();
    chartData.forEach((lnk) => {
      if (lnk.from) nodeSet.add(lnk.from);
      if (lnk.to) nodeSet.add(lnk.to);
    });

    const nodeList = Array.from(nodeSet);

    // create palette and map to node names
    const palette = generateColorPalette(nodeList.length || 1);
    const colorMap = {};
    nodeList.forEach((name, idx) => {
      colorMap[name] = palette[idx];
    });

    // prepare nodes for Highcharts (id must match link from/to)
    const nodes = nodeList.map((name) => ({ id: name, name, color: colorMap[name] }));

    // color links by source node for visual continuity
    const dataWithColor = chartData.map((d) => ({ ...d, color: colorMap[d.from] || '#cccccc' }));

    return {
    chart: {
      height: 400,
      backgroundColor: '#ffffff'
    },
    title: {
      text: undefined
    },
    credits: {
      enabled: false
    },
    tooltip: {
      style: { color: '#000000' },
      headerFormat: '',
      pointFormat: '<b>{point.fromNode.name}</b> to <b>{point.toNode.name}</b><br/>Threats: {point.weight}',
      nodeFormat: '<b>{point.name}</b><br/>Total: {point.sum} threats'
    },
    series: [{
      type: 'sankey',
      name: 'Threat Flow',
      keys: ['from', 'to', 'weight'],
      data: dataWithColor,
      nodes,
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
      },
      nodeWidth: 30,
      nodePadding: 20,
      // leave colors to nodes/link colors generated above
    }],
    plotOptions: {
      sankey: {
        linkOpacity: 0.5
      }
    }
  };
})();

  if (loading) {
    return (
      <InfoCard
        title="Threat Categories"
        titleToolTip="Flow visualization of threat categories and attack types"
        component={
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '400px' }}>
            <Spinner size="large" />
          </div>
        }
      />
    );
  }

  return (
    <InfoCard
      title="Threat Categories"
      titleToolTip="Flow visualization of threat categories and attack types"
      component={
        <div style={{ width: '100%', height: '400px' }}>
          <HighchartsReact
            highcharts={Highcharts}
            options={chartOptions}
          />
        </div>
      }
    />
  );
}

export default ThreatSankeyChart;


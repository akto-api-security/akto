import { useEffect, useState } from "react";
import InfoCard from "../../dashboard/new_components/InfoCard";
import SankeyChart from "../../../components/charts/SankeyChart";
import api from "../api";

function ThreatSankeyChart({ startTimestamp, endTimestamp }) {
  const [chartData, setChartData] = useState([]);

  const formatCategoryName = (category) => {
    if (!category) return 'Unknown';
    
    // Convert category codes to readable names based on common patterns
    const readableName = category.replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, l => l.toUpperCase());
    
    return readableName;
  };

  useEffect(() => {
    let mounted = true;
    const fetchData = async () => {
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
      }
    };

    fetchData();
    return () => { mounted = false; };
  }, [startTimestamp, endTimestamp]);

  return (
    <InfoCard
      title="Threat Categories"
      titleToolTip="Flow visualization of threat categories and attack types"
      component={
        <SankeyChart
          height={400}
          backgroundColor="#ffffff"
          data={chartData}
          nodeWidth={30}
          nodePadding={20}
          linkOpacity={0.5}
          defaultChartOptions={{
            tooltip: {
              style: { color: '#000000' },
              headerFormat: '',
              pointFormat: '<b>{point.fromNode.name}</b> to <b>{point.toNode.name}</b><br/>Threats: {point.weight}',
              nodeFormat: '<b>{point.name}</b><br/>Total: {point.sum} threats'
            }
          }}
        />
      }
    />
  );
}

export default ThreatSankeyChart;


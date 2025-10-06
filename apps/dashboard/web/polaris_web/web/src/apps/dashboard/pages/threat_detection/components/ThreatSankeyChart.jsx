import { useEffect, useState } from "react";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";
import HighchartsSankey from "highcharts/modules/sankey";
import InfoCard from "../../dashboard/new_components/InfoCard";
import { Spinner } from "@shopify/polaris";
// import api from "../api"; // TODO: Uncomment when enabling API calls

// Initialize Sankey module
if (typeof Highcharts === 'object') {
  HighchartsSankey(Highcharts);
}

function ThreatSankeyChart({ startTimestamp, endTimestamp }) {
  const [loading, setLoading] = useState(true);
  const [chartData, setChartData] = useState([]);

  const fetchCategoryData = async () => {
    setLoading(true);
    
    // TODO: Uncomment this section to fetch real data from API
    /*
    try {
      const res = await api.fetchThreatCategoryCount(startTimestamp, endTimestamp);
      if (res?.categoryCounts && Array.isArray(res.categoryCounts)) {
        // API Response format: [{category: "...", subCategory: "...", count: 100}, ...]
        const data = res.categoryCounts;
        
        // Create Sankey links: Total Threats -> Category
        const sankeyLinks = [];
        
        // Group by category and subcategory
        const categoryMap = {};
        
        data.forEach((item) => {
          const category = item.category || item.subCategory || 'Unknown';
          const subCategory = item.subCategory;
          const count = item.count || 0;
          
          if (count > 0) {
            if (!categoryMap[category]) {
              categoryMap[category] = { total: 0, subcategories: {} };
            }
            categoryMap[category].total += count;
            
            if (subCategory && subCategory !== category) {
              categoryMap[category].subcategories[subCategory] = count;
            }
          }
        });
        
        // Create links from Total Threats to Categories
        Object.keys(categoryMap).forEach((category) => {
          const categoryData = categoryMap[category];
          const formattedCategory = formatCategoryName(category);
          
          // Link: Total Threats -> Category
          sankeyLinks.push({
            from: 'Total Threats',
            to: formattedCategory,
            weight: categoryData.total
          });
          
          // If there are subcategories, create links: Category -> Subcategory
          Object.keys(categoryData.subcategories).forEach((subCategory) => {
            const formattedSubCategory = formatCategoryName(subCategory);
            if (formattedSubCategory !== formattedCategory) {
              sankeyLinks.push({
                from: formattedCategory,
                to: formattedSubCategory,
                weight: categoryData.subcategories[subCategory]
              });
            }
          });
        });
        
        setChartData(sankeyLinks);
      } else {
        console.warn('No category data returned from API');
      }
    } catch (error) {
      console.error('Error fetching category data:', error);
    }
    */
    
    // DUMMY DATA (Based on actual MongoDB data) - Remove this when uncommenting API code above
    try {
      // Simulate API delay
      await new Promise(resolve => setTimeout(resolve, 500));
      
      // Based on actual DB query: ipGeo: 4874, RL: 126
      // Creating a more comprehensive demo dataset
      setChartData([
        // Main categories from Total Threats
        { from: 'Total Threats', to: 'Geo Blocking', weight: 4874 },
        { from: 'Total Threats', to: 'Rate Limiting', weight: 126 },
        { from: 'Total Threats', to: 'SQL Injection', weight: 450 },
        { from: 'Total Threats', to: 'Cross-Site Scripting', weight: 380 },
        { from: 'Total Threats', to: 'CSRF Attack', weight: 320 },
        { from: 'Total Threats', to: 'Authentication Attack', weight: 280 },
        { from: 'Total Threats', to: 'Other', weight: 180 },
      ]);
    } catch (error) {
      console.error('Error loading dummy data:', error);
    } finally {
      setLoading(false);
    }
  };

  // TODO: Uncomment this function when enabling API calls
  // eslint-disable-next-line no-unused-vars
  const formatCategoryName = (category) => {
    if (!category) return 'Unknown';
    
    // Convert category codes to readable names
    const categoryMap = {
      'SQL_INJECTION': 'SQL Injection',
      'XSS': 'Cross-Site Scripting',
      'CSRF': 'CSRF Attack',
      'SSRF': 'Server-Side Request Forgery',
      'XXE': 'XML External Entity',
      'RL': 'Rate Limiting',
      'Geo': 'Geo Blocking',
      'ipGeo': 'IP Geo Blocking',
      'BOLA': 'Broken Object Level Auth',
      'BFLA': 'Broken Function Level Auth',
      'CMD_INJECTION': 'Command Injection',
      'PATH_TRAVERSAL': 'Path Traversal',
      'AUTHENTICATION': 'Authentication Attack',
      'AUTHORIZATION': 'Authorization Attack'
    };
    
    return categoryMap[category] || category.replace(/_/g, ' ');
  };

  useEffect(() => {
    fetchCategoryData();
  }, [startTimestamp, endTimestamp]);

  const chartOptions = {
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
    series: [{
      type: 'sankey',
      name: 'Threat Flow',
      keys: ['from', 'to', 'weight'],
      data: chartData,
      dataLabels: {
        enabled: true,
        style: {
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
      colors: [
        '#E45357', // Critical red
        '#EF864C', // High orange
        '#F6C564', // Medium yellow
        '#6FD1A6', // Low green
        '#7F56D9', // Purple
        '#4A90E2', // Blue
        '#50C878', // Emerald
        '#FF6B6B'  // Light red
      ],
      tooltip: {
        headerFormat: '',
        pointFormat: '<b>{point.fromNode.name}</b> → <b>{point.toNode.name}</b>: {point.weight} threats',
        nodeFormat: '<b>{point.name}</b>: {point.sum} threats'
      }
    }],
    plotOptions: {
      sankey: {
        linkOpacity: 0.5
      }
    }
  };

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


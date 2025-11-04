import React, { useEffect, useState } from "react";
import InfoCard from "../../dashboard/new_components/InfoCard";
import BarGraph from "../../../components/charts/BarGraph";
import { getDashboardCategory, mapLabel, SUB_CATEGORY_ENDPOINT_SECURITY } from "../../../../main/labelHelper";
import PersistStore from "../../../../main/PersistStore";

// Dummy data for Cloud Security (GenAI threats)
const cloudSecurityDummyData = [
  { text: "Prompt Injection", value: 15, color: "#4e76cd" },
  { text: "Data Poisoning", value: 8, color: "#4e76cd" },
  { text: "Model Extraction", value: 12, color: "#4e76cd" },
  { text: "Adversarial Examples", value: 6, color: "#4e76cd" },
  { text: "Training Data Exposure", value: 10, color: "#4e76cd" },
  { text: "Inference Manipulation", value: 4, color: "#4e76cd" },
  { text: "Model Inversion", value: 7, color: "#4e76cd" },
];

// Dummy data for Endpoint Security (MCP threats)
const endpointSecurityDummyData = [
  { text: "File System Tampering", value: 13, color: "#4e76cd" },
  { text: "Memory Corruption", value: 5, color: "#4e76cd" },
  { text: "Network Intrusion", value: 16, color: "#4e76cd" },
  { text: "Service Hijacking", value: 7, color: "#4e76cd" },
];

const TopThreatTypeChart = ({ data }) => {
  const [chartData, setChartData] = useState([]);
  const currentSubCategory = PersistStore(state => state.subCategory);
  const isEndpointSecurity = currentSubCategory === SUB_CATEGORY_ENDPOINT_SECURITY;
  
  useEffect(() => {
    // Use dummy data based on subcategory for Agentic Security
    if (getDashboardCategory() === "Agentic Security") {
      const selectedDummyData = isEndpointSecurity ? endpointSecurityDummyData : cloudSecurityDummyData;
      console.log(`TopThreatTypeChart: Using ${isEndpointSecurity ? 'Endpoint' : 'Cloud'} Security dummy data`);
      setChartData(selectedDummyData);
    } else {
      // Use real data for other categories
      const processedData = data
        .sort((a, b) => a.text.localeCompare(b.text))
        .map((x) => ({
          text: x.text.replaceAll("_", " "),
          value: x.value,
          color: x.color,
        }));
      setChartData(processedData);
    }
  }, [data, currentSubCategory, isEndpointSecurity]);
  return (
    <InfoCard
      title={`Top ${mapLabel("Threat", getDashboardCategory())} Types ${getDashboardCategory() === "Agentic Security" ? `(${isEndpointSecurity ? 'Endpoint' : 'Cloud'})` : ''}`}
      titleToolTip={`Top ${mapLabel("Threat", getDashboardCategory())} Types`}
      component={
        <BarGraph
          key={getDashboardCategory() === "Agentic Security" ? `threat-chart-${currentSubCategory}` : 'threat-chart-default'}
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
          barWidth={100 - (chartData.length * 6)}
          barGap={12}
          showGridLines={true}
        />
      }
    />
  );
};

export default TopThreatTypeChart;

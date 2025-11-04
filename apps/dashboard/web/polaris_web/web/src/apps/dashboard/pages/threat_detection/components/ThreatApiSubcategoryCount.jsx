import React, { useEffect, useState } from "react";
import { DataTable } from "@shopify/polaris";
import InfoCard from "../../dashboard/new_components/InfoCard";
import { getDashboardCategory, mapLabel, SUB_CATEGORY_ENDPOINT_SECURITY } from "../../../../main/labelHelper";
import PersistStore from "../../../../main/PersistStore";

// Dummy data for Cloud Security (GenAI threat categories)
const cloudSecurityDummyData = [
  { text: "Model Poisoning", value: 22 },
  { text: "Prompt Injection", value: 18 },
  { text: "Data Leakage", value: 16 },
  { text: "Adversarial Attacks", value: 14 },
  { text: "Bias Amplification", value: 12 },
  { text: "Privacy Violations", value: 10 },
  { text: "Hallucination Risks", value: 8 }
];

// Dummy data for Endpoint Security (MCP threat categories)
const endpointSecurityDummyData = [
  { text: "File System Abuse", value: 15 },
  { text: "Network Intrusion", value: 13 },
  { text: "Privilege Escalation", value: 11 },
  { text: "System Compromise", value: 9 }
];

const ThreatApiSubcategoryCount = ({ data }) => {
  const [categoryData, setCategoryData] = useState([]);
  const currentSubCategory = PersistStore(state => state.subCategory);
  const isEndpointSecurity = currentSubCategory === SUB_CATEGORY_ENDPOINT_SECURITY;
  
  useEffect(() => {
    // Use dummy data based on subcategory for Agentic Security
    if (getDashboardCategory() === "Agentic Security") {
      const selectedDummyData = isEndpointSecurity ? endpointSecurityDummyData : cloudSecurityDummyData;
      console.log(`ThreatApiSubcategoryCount: Using ${isEndpointSecurity ? 'Endpoint' : 'Cloud'} Security category dummy data`);
      setCategoryData(selectedDummyData);
    } else {
      // Use real data for other categories
      setCategoryData(data || []);
    }
  }, [data, currentSubCategory, isEndpointSecurity]);

  const Data = () => (
    <DataTable
      columnContentTypes={["text", "numeric"]}
      headings={["Category", ""]}
      rows={categoryData
        .map((x) => [x.text, x.value])
        .sort((a, b) => b[0].localeCompare(a[0]))}
    />
  );

  return (
    <InfoCard
      title={`Top ${mapLabel("Guardrail", getDashboardCategory())} Categories ${getDashboardCategory() === "Agentic Security" ? `(${isEndpointSecurity ? 'Endpoint' : 'Cloud'})` : ''}`}
      titleToolTip={`Top ${mapLabel("Guardrail", getDashboardCategory())} Categories`}
      component={<Data />}
    />
  );
};

export default ThreatApiSubcategoryCount;

import React from "react";
import { DataTable } from "@shopify/polaris";
import InfoCard from "../../dashboard/new_components/InfoCard";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

const ThreatApiSubcategoryCount = ({ data }) => {
  const Data = () => (
    <DataTable
      columnContentTypes={["text", "numeric"]}
      headings={["Category", ""]}
      rows={data
        .map((x) => [x.text, x.value])
        .sort((a, b) => b[0].localeCompare(a[0]))}
    />
  );

  return (
    <InfoCard
      title={`Top ${mapLabel("Threat", getDashboardCategory())} Categories`}
      titleToolTip={`Top ${mapLabel("Threat", getDashboardCategory())} Categories`}
      component={<Data />}
    />
  );
};

export default ThreatApiSubcategoryCount;

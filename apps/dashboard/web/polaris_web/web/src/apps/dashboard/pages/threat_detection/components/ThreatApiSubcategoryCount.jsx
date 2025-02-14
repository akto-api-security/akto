import React from "react";
import { DataTable } from "@shopify/polaris";
import InfoCard from "../../dashboard/new_components/InfoCard";

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
      title={"Top Categories"}
      titleToolTip={"Top Threat Categories"}
      component={<Data />}
    />
  );
};

export default ThreatApiSubcategoryCount;

import React from "react";
import { Text } from "@shopify/polaris";
import InfoCard from "../../dashboard/new_components/InfoCard";
import PaginatedDataTable from "../../../components/shared/PaginatedDataTable";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

const ROWS_PER_PAGE = 4;

const ThreatApiSubcategoryCount = ({ data }) => {
  const sorted = [...data]
    .filter((x) => x && x.text != null)
    .sort((a, b) => b.value - a.value);

  return (
    <InfoCard
      title={mapLabel("Top Threat Categories", getDashboardCategory())}
      titleToolTip={mapLabel("Threat categories ranked by number of detections.", getDashboardCategory())}
      fillHeight
      component={
        <PaginatedDataTable
          columnContentTypes={["text", "numeric"]}
          headings={[
            <Text variant="headingSm" key="category">{mapLabel("Threat Category", getDashboardCategory())}</Text>,
            <Text variant="headingSm" key="count">Count</Text>,
          ]}
          rows={sorted.map((x) => [x.text, x.value])}
          rowsPerPage={ROWS_PER_PAGE}
        />
      }
    />
  );
};

export default ThreatApiSubcategoryCount;

import React, { useMemo } from "react";
import { Box, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { buildEvidenceColumnDefs } from "./insightsHelpers";

// One bounded InsightResult.Evidence table (see EVIDENCE_ROW_CAP in InsightService) rendered
// via the shared AgGridTable, not a hand-rolled <table> — columns are derived from whatever
// keys the backend actually sent, so this works for every insight's evidence shape unchanged.
export default function InsightEvidenceTable({ evidence }) {
    const columnDefs = useMemo(() => buildEvidenceColumnDefs(evidence), [evidence]);
    const rowHeight = 40;
    const rowCount = evidence?.rows?.length || 0;

    return (
        <Box paddingBlockStart="2">
            <Text variant="bodySm" fontWeight="semibold" color="subdued">
                {evidence.title}
                {evidence.truncated ? ` · showing ${rowCount} of ${evidence.totalRowCount}` : ""}
            </Text>
            <Box paddingBlockStart="2">
                <AgGridTable
                    rowData={evidence.rows || []}
                    columnDefs={columnDefs}
                    rowHeight={rowHeight}
                    headerHeight={36}
                    domLayout="autoHeight"
                    sideBar={false}
                    animateRows={false}
                    suppressCellFocus
                />
            </Box>
        </Box>
    );
}

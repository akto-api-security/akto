import React, { useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import PersistStore from "@/apps/main/PersistStore";
import { buildEvidenceColumnDefs } from "./insightsHelpers";

// One bounded InsightResult.Evidence table (see EVIDENCE_ROW_CAP in InsightService) rendered
// via the shared AgGridTable, not a hand-rolled <table> — columns are derived from whatever
// keys the backend actually sent, so this works for every insight's evidence shape unchanged.
// Rows that carry a `host` are navigable to that asset's inventory page — hostNameMap
// (PersistStore) is collectionId -> hostName, so it's inverted here to resolve the other way.
export default function InsightEvidenceTable({ evidence }) {
    const navigate = useNavigate();
    const hostNameMap = PersistStore((state) => state.hostNameMap);
    const columnDefs = useMemo(() => buildEvidenceColumnDefs(evidence), [evidence]);
    const rowHeight = 40;
    const rowCount = evidence?.rows?.length || 0;

    const collectionIdByHost = useMemo(() => {
        const reverse = {};
        Object.entries(hostNameMap || {}).forEach(([collectionId, host]) => {
            if (host) reverse[host] = collectionId;
        });
        return reverse;
    }, [hostNameMap]);

    const resolveCollectionId = useCallback(
        (row) => (row?.host ? collectionIdByHost[row.host] : undefined),
        [collectionIdByHost]
    );

    const handleRowClicked = useCallback(({ data }) => {
        const collectionId = resolveCollectionId(data);
        if (collectionId) navigate(`/dashboard/observe/inventory/${collectionId}`);
    }, [navigate, resolveCollectionId]);

    const getRowStyle = useCallback(
        ({ data }) => (resolveCollectionId(data) ? { cursor: "pointer" } : undefined),
        [resolveCollectionId]
    );

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
                    onRowClicked={handleRowClicked}
                    getRowStyle={getRowStyle}
                />
            </Box>
        </Box>
    );
}

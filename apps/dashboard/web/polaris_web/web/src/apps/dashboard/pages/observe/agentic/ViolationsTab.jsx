import React, { useState, useEffect } from "react";
import { Box, VerticalStack, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SeverityBadge } from "./AgenticCellRenderers";
import agenticObserveApi from "./agenticObserveApi";

function ViolSeverityCellRenderer({ data }) {
    if (!data) return null;
    return <SeverityBadge severity={data.severity} />;
}

function ViolTitleCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Box width="100%" overflowX="hidden">
            <Text variant="bodySm" fontWeight="semibold" truncate>{data.title}</Text>
        </Box>
    );
}

const SEVERITY_ORDER = { low: 1, medium: 2, high: 3, critical: 4 };

const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",      width: 160, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.timeEpoch || 0) - (nodeB?.data?.timeEpoch || 0) },
    { field: "severity", headerName: "Severity",  width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, comparator: (a, b) => (SEVERITY_ORDER[a] || 0) - (SEVERITY_ORDER[b] || 0) },
    { field: "title",    headerName: "Violation", flex: 1,    minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

export default function ViolationsTab({ asset }) {
    const [violations, setViolations] = useState([]);

    useEffect(() => {
        if (!asset?.id) { setViolations([]); return; }
        let cancelled = false;
        (async () => {
            try {
                const rows = await agenticObserveApi.fetchAgenticViolations({
                    apiCollectionIds: asset.collectionIds,
                    assetId: asset.collectionIds?.length ? undefined : asset.id,
                });
                if (!cancelled) setViolations(rows);
            } catch {
                if (!cancelled) setViolations([]);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id]);

    if (violations.length === 0) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No violations</Text>
                    <Text variant="bodySm" color="subdued">This asset is operating within policy.</Text>
                </VerticalStack>
            </Box>
        );
    }

    return (
        <AgGridTable
            rowData={violations}
            columnDefs={VIOLATIONS_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={() => window.open("/dashboard/protection/threat-activity", "_blank")}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search violations..."
            pagination
            paginationPageSize={20}
            sideBar={false}
        />
    );
}

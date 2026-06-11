import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Box, VerticalStack, Text, Badge } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SeverityBadge } from "./AgenticCellRenderers";
import { fetchAgenticViolations, openViolationInThreatActivity, deviceServiceKey } from "./agenticObserveApi";
import func from "@/util/func";

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

function ViolStatusCellRenderer({ data }) {
    if (!data?.status) return null;
    const s = data.status.toUpperCase();
    const statusMap = { ACTIVE: "success", UNDER_REVIEW: "warning", IGNORED: "subdued", TRAINING: "info" };
    return <Badge status={statusMap[s] || "default"} size="small">{func.toSentenceCase(s)}</Badge>;
}

const SEVERITY_ORDER = { low: 1, medium: 2, high: 3, critical: 4 };

const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",      width: 160, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.timeEpoch || 0) - (nodeB?.data?.timeEpoch || 0) },
    { field: "title",    headerName: "Violation", flex: 1,    minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "severity", headerName: "Severity",  width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, comparator: (a, b) => (SEVERITY_ORDER[a] || 0) - (SEVERITY_ORDER[b] || 0) },
    { field: "actor",    headerName: "Actor",     width: 140, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, valueFormatter: p => p.value || "-", cellStyle: { display: "flex", alignItems: "center", fontSize: 12 } },
    { field: "status",   headerName: "Status",    width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolStatusCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

export default function ViolationsTab({ asset, collections = [] }) {
    const [violations, setViolations] = useState([]);

    const hostNames = useMemo(() => {
        if (!asset?.collectionIds?.length || !collections.length) return [];
        const ids = new Set(asset.collectionIds.map(Number));
        return collections.filter(c => ids.has(Number(c.id)) && c.hostName).map(c => c.hostName);
    }, [asset?.id, collections]);

    useEffect(() => {
        if (!hostNames.length) { setViolations([]); return; }
        let cancelled = false;
        const hostSet = new Set(hostNames);
        const looseHostSet = new Set(hostNames.map(h => deviceServiceKey(h)).filter(Boolean));
        fetchAgenticViolations({})
            .then((rows) => {
                if (cancelled) return;
                const filtered = rows.filter(r => hostSet.has(r.host) || looseHostSet.has(deviceServiceKey(r.host)));
                setViolations(filtered.map((r) => ({
                    ...r,
                    time: r.timeEpoch ? func.formatChatTimestamp(r.timeEpoch) : "",
                })));
            })
            .catch(() => {
                if (!cancelled) setViolations([]);
            });
        return () => { cancelled = true; };
    }, [hostNames.join(",")]);

    const handleViolationClick = useCallback((e) => {
        if (!e.data) return;
        openViolationInThreatActivity(e.data);
    }, []);

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
            onRowClicked={handleViolationClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search violations..."
            pagination
            paginationPageSize={20}
            sideBar={{ toolPanels: ["columns", "filters"] }}
            domLayout="normal"
        />
    );
}

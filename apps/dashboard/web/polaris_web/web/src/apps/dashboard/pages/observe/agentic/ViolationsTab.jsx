import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Box, VerticalStack, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SeverityBadge } from "./AgenticCellRenderers";
import { fetchAgenticViolations, openViolationInThreatActivity, deviceServiceKey, isClaudeConfigHost } from "./agenticObserveApi";
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

const SEVERITY_ORDER = { low: 1, medium: 2, high: 3, critical: 4 };

const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",      width: 160, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.timeEpoch || 0) - (nodeB?.data?.timeEpoch || 0) },
    { field: "title",    headerName: "Violation", flex: 1,    minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "deviceId", headerName: "Device",    width: 200, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
    { field: "severity", headerName: "Severity",  width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, comparator: (a, b) => (SEVERITY_ORDER[a] || 0) - (SEVERITY_ORDER[b] || 0) },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

export default function ViolationsTab({ asset, collections = [], startTimestamp, endTimestamp, onViolationClick }) {
    const [violations, setViolations] = useState([]);

    // Reset immediately when the asset changes so stale rows from the previous asset
    // are never visible while the new fetch is in flight.
    useEffect(() => { setViolations([]); }, [asset?.id]);

    useEffect(() => {
        const isClaudeAsset = asset?.assetTagValue?.toLowerCase() === "claude";
        if (!asset?.collectionIds?.length || !collections.length) {
            if (!isClaudeAsset) { setViolations([]); return; }
        }

        const ids = new Set((asset.collectionIds || []).map(Number));
        const hostNames = collections.filter(c => ids.has(Number(c.id)) && c.hostName).map(c => c.hostName);

        if (!hostNames.length && !isClaudeAsset) { setViolations([]); return; }

        // Collect device IDs that have a claude-type collection among this asset's collections.
        // This covers both direct Claude AI Agent assets and Skills/MCPs whose collections
        // include a claude collection (and thus receive attributed claude-settings events).
        const claudeDeviceIds = new Set(
            hostNames
                .filter(h => { const parts = h.split("."); return parts[parts.length - 1]?.toLowerCase() === "claude"; })
                .map(h => h.split(".")[0])
                .filter(Boolean)
        );
        // For a Claude AI Agent with no per-device collections, match all claude-settings events.
        const matchAllClaudeConfig = isClaudeAsset && claudeDeviceIds.size === 0;

        let cancelled = false;
        const hostSet = new Set(hostNames);
        const looseHostSet = new Set(hostNames.map(h => deviceServiceKey(h)).filter(Boolean));
        fetchAgenticViolations({ startTimestamp, endTimestamp })
            .then((rows) => {
                if (cancelled) return;
                const filtered = rows.filter(r =>
                    hostSet.has(r.host) ||
                    looseHostSet.has(deviceServiceKey(r.host)) ||
                    (isClaudeConfigHost(r.host) && (
                        matchAllClaudeConfig ||
                        claudeDeviceIds.has(r.host.split(".")[0])
                    ))
                );
                setViolations(filtered.map((r) => ({
                    ...r,
                    time: r.timeEpoch ? func.formatChatTimestamp(r.timeEpoch) : "",
                    deviceId: r.host ? r.host.split(".")[0] : "",
                })));
            })
            .catch(() => {
                if (!cancelled) setViolations([]);
            });
        return () => { cancelled = true; };
    }, [asset?.id, asset?.assetTagValue, asset?.collectionIds, collections, startTimestamp, endTimestamp]);

    const handleViolationClick = useCallback((e) => {
        if (!e.data) return;
        if (onViolationClick) {
            onViolationClick(e.data);
        } else {
            openViolationInThreatActivity(e.data);
        }
    }, [onViolationClick]);

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

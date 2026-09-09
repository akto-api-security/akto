import React, { useCallback } from "react";
import { Box, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SeverityBadge } from "./AgenticCellRenderers";
import { fetchAgenticViolationsPage, openViolationInGuardrailViolations, deviceServiceKey } from "./agenticObserveApi";
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

// Only "time" (-> detectedAt) and "severity" have a server-side sort mapping (see onServerFetch) —
// "title"/"deviceId" aren't real backend fields (title is filterId, deviceId is derived from host),
// so sorting by them isn't offered.
const VIOLATIONS_COL_DEFS = [
    { field: "time",     headerName: "Time",      width: 160, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
    { field: "title",    headerName: "Violation", flex: 1,    minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, sortable: false },
    { field: "deviceId", headerName: "Device",    width: 200, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, sortable: false },
    { field: "severity", headerName: "Severity",  width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ViolSeverityCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// Server-side paginated/searched/sorted — scoped to this one asset's own hostnames (see
// AgenticObserveAction... no, this specific tab talks to threat-detection-backend's
// MaliciousEventService.listMaliciousRequests directly via SuspectSampleDataAction, not
// AgenticObserveAction — see fetchAgenticViolationsPage/agenticObserveApi.js). Host attribution
// (exact / loose device+service / claude-config) mirrors the same three tiers this tab always used.
// hostNames comes straight off the asset row itself (AgenticObserveAction's GroupSummary already
// collects every member collection's hostName server-side) — no need to fetch/filter the account's
// full collection list just to re-derive it client-side.
export default function ViolationsTab({ asset, startTimestamp, endTimestamp, onViolationClick, onTotalChange }) {
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const isClaudeAsset = asset?.assetTagValue?.toLowerCase() === "claude";
        const hostNames = asset?.hostNames || [];

        if (!hostNames.length && !isClaudeAsset) {
            return Promise.resolve({ value: [], total: 0 });
        }

        // Collect device IDs that have a claude-type collection among this asset's collections.
        // This covers both direct Claude AI Agent assets and Skills/MCPs whose collections
        // include a claude collection (and thus receive attributed claude-settings events).
        const claudeDeviceIds = hostNames
            .filter(h => { const parts = h.split("."); return parts[parts.length - 1]?.toLowerCase() === "claude"; })
            .map(h => h.split(".")[0])
            .filter(Boolean);
        const looseHostKeys = hostNames.map(h => deviceServiceKey(h)).filter(Boolean);

        const mongoOrder = sortOrder ? -sortOrder : -1; // AG-Grid asc/desc convention is inverted vs Mongo
        const sortBySeverity = sortKey === "severity";

        return fetchAgenticViolationsPage({
            startTimestamp, endTimestamp,
            hosts: hostNames,
            looseHostKeys,
            claudeDeviceIds,
            matchClaudeConfig: isClaudeAsset,
            skip,
            limit: limit || 20,
            sort: sortBySeverity ? { severity: mongoOrder } : { detectedAt: mongoOrder },
            sortBySeverity,
            searchText: searchString || undefined,
        }).then((res) => {
            // Reports the real, fully-attributed total up so callers relying on a coarser
            // server-side tally (e.g. an exact-hostName join) can correct their own display
            // once this - the same query the grid rows below come from - has an answer.
            onTotalChange?.(res.total ?? 0);
            return {
                value: res.violations.map((r) => ({
                    ...r,
                    time: r.timeEpoch ? func.formatChatTimestamp(r.timeEpoch) : "",
                    deviceId: r.host ? r.host.split(".")[0] : "",
                })),
                total: res.total,
            };
        });
    }, [asset?.id, asset?.assetTagValue, asset?.hostNames, startTimestamp, endTimestamp, onTotalChange]);

    const handleViolationClick = useCallback((e) => {
        if (!e.data) return;
        if (onViolationClick) {
            onViolationClick(e.data);
        } else {
            openViolationInGuardrailViolations(e.data);
        }
    }, [onViolationClick]);

    return (
        <AgGridTable
            key={asset.id}
            columnDefs={VIOLATIONS_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onServerFetch={onServerFetch}
            serverSideRowModel
            getRowId={(params) => params.data.refId || `${params.data.host}-${params.data.timeEpoch}-${params.data.title}`}
            onRowClicked={handleViolationClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            noOuterBorder
            searchPlaceholder="Search violations..."
            paginationPageSize={20}
            sideBar={{ toolPanels: ["columns", "filters"] }}
            domLayout="normal"
        />
    );
}

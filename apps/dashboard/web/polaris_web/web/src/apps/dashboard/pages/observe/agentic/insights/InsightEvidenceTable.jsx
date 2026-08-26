import React, { useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import PersistStore from "@/apps/main/PersistStore";
import { buildEvidenceColumnDefs } from "./insightsHelpers";

// One bounded InsightResult.Evidence table (see EVIDENCE_ROW_CAP in InsightService) rendered
// via the shared AgGridTable, not a hand-rolled <table> — columns are derived from whatever
// keys the backend actually sent, so this works for every insight's evidence shape unchanged.
// A row is navigable when it carries one of the same identifying fields the insight's own CTAs
// deep-link on (see InsightUtil.usersAndDevicesFilterParams/guardrailPolicyParams):
//   - host/mcpHost -> that asset's inventory page (hostNameMap, PersistStore, is collectionId ->
//               hostName, so it's inverted here to resolve the other way)
//   - user   -> Users and Devices, filtered to that user (same `?filters=groupName__` param
//               the view_users CTAs use)
//   - Policy -> Guardrail Policies, opened to that policy (same `?policy=` param the
//               switch_to_block_mode/view_the_hits CTAs use)
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

    const resolveRowTarget = useCallback((row) => {
        if (!row) return undefined;
        const host = row.host || row.mcpHost;
        if (host && collectionIdByHost[host]) {
            return `/dashboard/observe/inventory/${collectionIdByHost[host]}`;
        }
        if (row.user) {
            return `/dashboard/observe/users-and-devices?filters=${encodeURIComponent(`groupName__${row.user}`)}`;
        }
        // A "Policy" cell can hold a comma-joined list when a host has more than one non-blocking
        // policy covering it (see GuardrailCoverageGapProvider) — that string is display-only,
        // not a real policy name, so it's not safe to navigate on.
        if (row.Policy && row.Policy !== "-" && !row.Policy.includes(",")) {
            return `/dashboard/guardrails/policies?policy=${encodeURIComponent(row.Policy)}`;
        }
        return undefined;
    }, [collectionIdByHost]);

    const handleRowClicked = useCallback(({ data }) => {
        const target = resolveRowTarget(data);
        if (target) navigate(target);
    }, [navigate, resolveRowTarget]);

    const getRowStyle = useCallback(
        ({ data }) => (resolveRowTarget(data) ? { cursor: "pointer" } : undefined),
        [resolveRowTarget]
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

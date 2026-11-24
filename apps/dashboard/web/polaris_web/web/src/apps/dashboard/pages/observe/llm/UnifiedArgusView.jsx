import { useCallback, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import api from "./api";
import { lockedRowStyle } from "./LLMCellRenderers";
import { ARGUS_UNIFIED_COL_DEFS } from "./columns";

/**
 * The Argus traces table: one list holding both a conversation and a lone invocation.
 *
 * Replaces the Sessions/Traces toggle. A customer whose application sends a session id sees
 * their turns folded into one row per conversation; a customer whose application sends none
 * sees exactly what they saw before, one row per invocation. Most accounts have some of each,
 * and splitting them across two tabs meant neither tab answered "what happened recently".
 *
 * Clicking dispatches on what the row actually is, which is why onOpenSession and onOpenTrace
 * are separate props rather than one handler that has to re-derive the kind.
 */
// Mirrors SearchClient.ROW_KIND_SESSION on the backend.
const ROW_KIND_SESSION = "session";

export default function UnifiedArgusView({ currDateRange, onOpenSession, onOpenTrace }) {
    const [rows, setRows] = useState([]);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    const onServerFetch = useCallback(({ filters, skip, limit, searchString }) => {
        const { since, until } = getEpochs();
        return api.fetchSessionsAndTraces({
            startTime: since, endTime: until, skip, limit, filters, searchString,
        }).then(result => { setRows(result?.value || []); return result; });
    }, [getEpochs]);

    const handleRowClick = useCallback(p => {
        if (!p?.data) return;
        if (p.data._rowKind === ROW_KIND_SESSION) onOpenSession?.(p.data);
        else onOpenTrace?.(p.data);
    }, [onOpenSession, onOpenTrace]);

    return (
        <AgGridTable
            key={`${currDateRange.period.since}~${currDateRange.period.until}`}
            rowData={rows}
            columnDefs={ARGUS_UNIFIED_COL_DEFS}
            defaultColDef={{ resizable: true, sortable: false, filter: false }}
            searchPlaceholder="Search sessions and traces"
            rowSelection="single"
            pagination={false}
            paginationPageSize={20}
            noOuterBorder
            isServerMode={true}
            onServerFetch={onServerFetch}
            filterStateUrl={window.location.pathname + "/llm-unified"}
            getRowStyle={lockedRowStyle}
            onRowClicked={handleRowClick}
        />
    );
}

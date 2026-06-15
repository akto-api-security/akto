import { useCallback, useEffect, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import api from "./api";
import { enrichRow } from "./utils";
import { SESSION_COLUMN_DEFS } from "./columns";

// Sessions tab — one row per session (traces grouped on session id). Clicking a row
// drills into the Traces tab scoped to that session (handled by the parent via
// onOpenSession). Search + column filters use AgGridTable's built-in controls.
export default function SessionsView({ currDateRange, onOpenSession }) {
    const [sessions, setSessions] = useState([]);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        let cancelled = false;
        const { since, until } = getEpochs();
        api.fetchSessions(since, until, {})
            .then(rows => { if (!cancelled) setSessions((rows || []).map(enrichRow)); });
        return () => { cancelled = true; };
    }, [getEpochs]);

    return (
        <AgGridTable
            rowData={sessions}
            columnDefs={SESSION_COLUMN_DEFS}
            defaultColDef={{ resizable: true, sortable: false, filter: false }}
            searchPlaceholder="Search sessions"
            rowSelection="single"
            pagination={true}
            paginationPageSize={20}
            noOuterBorder
            getRowStyle={() => ({ cursor: "pointer" })}
            onRowClicked={(p) => p.data && onOpenSession?.(p.data.sessionIdentifier)}
        />
    );
}

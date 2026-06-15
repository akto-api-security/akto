import { useCallback, useEffect, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { enrichRow } from "./utils";
import api from "./api";
import { getTraceColumnDefs } from "./columns";
import TraceFlyout from "./TraceFlyout";

// Traces tab — one row per trace (aggregated by traceId in the backend).
// The backend returns all matching trace buckets (up to 500) in one call, so we use
// client-mode AgGrid with built-in pagination, search, and column filters.
export default function TracesView({ currDateRange, sessionFilter, onOpenSession, onViewMessages }) {
    const [rows, setRows] = useState([]);
    const [columnDefs, setColumnDefs] = useState(() => getTraceColumnDefs({ showSession: !sessionFilter, onSessionClick: onOpenSession }));
    const [selectedTrace, setSelectedTrace] = useState(null);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        setColumnDefs(getTraceColumnDefs({ showSession: !sessionFilter, onSessionClick: onOpenSession }));
    }, [sessionFilter, onOpenSession]);

    useEffect(() => {
        let cancelled = false;
        const { since, until } = getEpochs();
        api.fetchMessages(since, until, { sessionId: sessionFilter || "" })
            .then(data => { if (!cancelled) setRows((data || []).map(enrichRow)); });
        return () => { cancelled = true; };
    }, [getEpochs, sessionFilter]);

    return (
        <>
            <AgGridTable
                key={sessionFilter || "all"}
                rowData={rows}
                columnDefs={columnDefs}
                defaultColDef={{ resizable: true, sortable: true, filter: false }}
                searchPlaceholder="Search traces"
                rowSelection="single"
                pagination={true}
                paginationPageSize={20}
                noOuterBorder
                getRowStyle={() => ({ cursor: "pointer" })}
                onRowClicked={p => p.data && setSelectedTrace(p.data)}
            />
            <TraceFlyout
                trace={selectedTrace}
                onClose={() => setSelectedTrace(null)}
                onOpenSession={onOpenSession}
                onViewMessages={(traceId) => { setSelectedTrace(null); onViewMessages?.(traceId); }}
            />
        </>
    );
}

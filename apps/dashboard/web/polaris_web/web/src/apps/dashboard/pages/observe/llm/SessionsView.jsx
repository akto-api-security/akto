import { useCallback, useEffect, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SESSION_COLUMN_DEFS } from "./columns";
import api from "./api";

const DEFAULT_COL_DEF = { sortable: true, resizable: true, filter: false };

export default function SessionsView({ currDateRange, onOpenSession }) {
    const [rows, setRows] = useState([]);
    const [columnDefs, setColumnDefs] = useState(SESSION_COLUMN_DEFS);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        const { since, until } = getEpochs();
        api.fetchFilterChoices(since, until)
            .then(choices => setColumnDefs(SESSION_COLUMN_DEFS.map(col =>
                col.filterAllowed ? { ...col, filterParams: { values: choices[col.field] || [] } } : col
            )))
            .catch(() => setColumnDefs(SESSION_COLUMN_DEFS));
    }, [getEpochs]);

    const onServerFetch = useCallback(({ filters, skip, limit, searchString }) => {
        const pageSize = limit || 20;
        const { since, until } = getEpochs();

        // The backend treats sessionsAfterKey as an integer offset — pass skip directly.
        return api.fetchSessionsPaged({
            startTime: since, endTime: until,
            limit: pageSize, afterKey: String(skip || 0), filters,
            searchString: searchString
        }).then(result => {
            setRows(result.sessions);
            return { value: result.sessions, total: result.total };
        });
    }, [getEpochs]);

    const handleRowClick = useCallback(
        p => p.data && onOpenSession?.(p.data),
        [onOpenSession]
    );

    const getRowStyle = useCallback(() => ({ cursor: "pointer" }), []);

    // Re-key on date range change so AgGridTable remounts, resets its page to 0,
    // and triggers a fresh fetch with the new time window.
    const tableKey = `${currDateRange.period.since}~${currDateRange.period.until}`;

    return (
        <AgGridTable
            key={tableKey}
            rowData={rows}
            columnDefs={columnDefs}
            defaultColDef={DEFAULT_COL_DEF}
            height={500}
            domLayout="normal"
            rowHeight={44}
            headerHeight={40}
            searchPlaceholder="Search sessions..."
            rowSelection="single"
            paginationPageSize={20}
            animateRows
            suppressCellFocus
            getRowStyle={getRowStyle}
            onRowClicked={handleRowClick}
            sideBar={{ toolPanels: ["columns", "filters"] }}
            onServerFetch={onServerFetch}
        />
    );
}

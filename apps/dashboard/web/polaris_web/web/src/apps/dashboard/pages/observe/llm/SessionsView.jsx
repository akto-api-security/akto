import { useCallback, useRef, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SESSION_COLUMN_DEFS } from "./columns";
import api from "./api";

const DEFAULT_COL_DEF = { sortable: true, resizable: true, filter: false };

export default function SessionsView({ currDateRange, onOpenSession }) {
    const [rows, setRows] = useState([]);

    // Maps { filterKey → { pageNum → afterKey } } so each unique filter combination
    // has its own cursor chain and stale cursors are never used across filter changes.
    const cursorRegistry = useRef({});

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    const onServerFetch = useCallback(({ filters, skip, limit }) => {
        const pageSize  = limit || 20;
        const page      = Math.floor(skip / pageSize);
        const filterKey = JSON.stringify(filters || {});

        // Reset cursor chain for this filter combo when landing on page 0 (new filter or sort).
        if (page === 0) cursorRegistry.current[filterKey] = { 0: null };
        if (!cursorRegistry.current[filterKey]) cursorRegistry.current[filterKey] = { 0: null };

        const afterKey = cursorRegistry.current[filterKey][page] || null;
        const { since, until } = getEpochs();

        return api.fetchSessionsPaged({
            startTime: since, endTime: until,
            limit: pageSize, afterKey, filters,
        }).then(result => {
            if (result.nextAfterKey) {
                cursorRegistry.current[filterKey][page + 1] = result.nextAfterKey;
            }
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
            columnDefs={SESSION_COLUMN_DEFS}
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

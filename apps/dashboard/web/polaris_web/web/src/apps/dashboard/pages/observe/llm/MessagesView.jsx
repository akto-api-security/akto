import { useCallback, useEffect, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import api from "./api";
import { MESSAGE_FLAT_COLUMN_DEFS } from "./columns";

// Messages tab — flat span-level rows, server-paginated via searchPrompts.
// When traceFilter is set (drill-down from a trace flyout), passes traceId to the
// backend so only that trace's spans are returned. The same server path handles both.
export default function MessagesView({ currDateRange, traceFilter, onRowClicked, columnDefs: columnDefsProp }) {
    const [columnDefs, setColumnDefs] = useState(columnDefsProp || MESSAGE_FLAT_COLUMN_DEFS);
    const [rows, setRows] = useState([]);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        if (columnDefsProp) return;
        const { since, until } = getEpochs();
        api.fetchFilterChoices(since, until)
            .then(choices => setColumnDefs(MESSAGE_FLAT_COLUMN_DEFS.map(col =>
                col.filterAllowed ? { ...col, filterParams: { values: choices[col.field] || [] } } : col
            )))
            .catch(() => setColumnDefs(MESSAGE_FLAT_COLUMN_DEFS));
    }, [getEpochs, columnDefsProp]);

    const onServerFetch = useCallback(({ filters, sortKey, sortOrder, skip, limit, searchAfterJson, searchString }) => {
        const { since, until } = getEpochs();
        return api.searchPrompts({
            startTime: since, endTime: until,
            traceId: traceFilter || "",
            filters, sortKey, sortOrder, skip, limit, searchAfterJson, searchString,
        }).then(result => { setRows(result?.value || []); return result; });
    }, [getEpochs, traceFilter]);

    return (
        <>
            <AgGridTable
                key={traceFilter || "all"}
                rowData={rows}
                columnDefs={columnDefs}
                defaultColDef={{ resizable: true, sortable: false, filter: false }}
                searchPlaceholder="Search messages"
                rowSelection="single"
                pagination={false}
                paginationPageSize={20}
                noOuterBorder
                isServerMode={true}
                onServerFetch={onServerFetch}
                filterStateUrl={window.location.pathname + "/llm-messages"}
                getRowStyle={() => ({ cursor: "pointer" })}
                onRowClicked={onRowClicked}
            />
        </>
    );
}

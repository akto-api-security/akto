import { useCallback, useEffect, useRef, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SESSION_COLUMN_DEFS } from "./columns";
import api from "./api";

const DEFAULT_COL_DEF = { sortable: true, resizable: true, filter: false };

function mergeUnique(a, b) {
    return [...new Set([...(a || []), ...(b || [])])];
}

export default function SessionsView({ currDateRange, onOpenSession, initialFilters = null }) {
    const [rows, setRows] = useState([]);
    const [columnDefs, setColumnDefs] = useState(SESSION_COLUMN_DEFS);
    const gridRef = useRef(null);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    useEffect(() => {
        const { since, until } = getEpochs();
        api.fetchFilterChoices(since, until)
            .then(choices => {
                // Ensure initialFilter values are present in each column's option list
                // so the set-filter renders them as selected even if they have no sessions
                // in the current window yet.
                const merged = {
                    userName:  mergeUnique(choices.userName,  initialFilters?.userName),
                    topic:     mergeUnique(choices.topic,     initialFilters?.topic),
                    subTopic:  mergeUnique(choices.subTopic,  initialFilters?.subTopic),
                    serviceId: choices.serviceId || [],
                };
                setColumnDefs(SESSION_COLUMN_DEFS.map(col =>
                    col.filterAllowed ? { ...col, filterParams: { values: merged[col.field] || [] } } : col
                ));

                // Apply AG Grid filter model so the filter icon lights up on those columns.
                if (initialFilters && gridRef.current?.api) {
                    const model = {};
                    if (initialFilters.userName?.length) model.userName = { filterType: "set", values: initialFilters.userName };
                    if (initialFilters.topic?.length)    model.topic    = { filterType: "set", values: initialFilters.topic };
                    if (initialFilters.subTopic?.length) model.subTopic = { filterType: "set", values: initialFilters.subTopic };
                    if (Object.keys(model).length > 0) gridRef.current.api.setFilterModel(model);
                }
            })
            .catch(() => setColumnDefs(SESSION_COLUMN_DEFS));
    }, [getEpochs]); // eslint-disable-line react-hooks/exhaustive-deps

    const onServerFetch = useCallback(({ filters, skip, limit, searchString }) => {
        const pageSize = limit || 20;
        const { since, until } = getEpochs();

        // Merge initial URL filters; deduplicate so the grid filter + URL filter don't
        // double-send the same value after setFilterModel fires a second fetch.
        const mergedFilters = initialFilters ? {
            ...filters,
            userName: mergeUnique(filters?.userName, initialFilters.userName),
            topic:    mergeUnique(filters?.topic,    initialFilters.topic),
            subTopic: mergeUnique(filters?.subTopic, initialFilters.subTopic),
        } : filters;

        // The backend treats sessionsAfterKey as an integer offset — pass skip directly.
        return api.fetchSessionsPaged({
            startTime: since, endTime: until,
            limit: pageSize, afterKey: String(skip || 0), filters: mergedFilters,
            searchString: searchString
        }).then(result => {
            setRows(result.sessions);
            return { value: result.sessions, total: result.total };
        });
    }, [getEpochs, initialFilters]);

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
            gridRef={gridRef}
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

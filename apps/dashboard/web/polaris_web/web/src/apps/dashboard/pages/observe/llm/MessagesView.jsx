import { useCallback, useEffect, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import api from "./api";
import { MESSAGE_COLUMN_DEFS_DETAIL } from "./constants";
import SpansPanelModal from "./SpansPanelModal";

export default function MessagesView({ currDateRange }) {
    const [rows, setRows] = useState([]);
    const [columnDefs, setColumnDefs] = useState(MESSAGE_COLUMN_DEFS_DETAIL);
    const [spansModalOpen, setSpansModalOpen] = useState(false);
    const [spansModalTraceId, setSpansModalTraceId] = useState(null);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    // Load filter choices for set-filter columns on date range change
    useEffect(() => {
        const { since, until } = getEpochs();
        api.fetchFilterChoices(since, until).then(choices => {
            setColumnDefs(MESSAGE_COLUMN_DEFS_DETAIL.map(col => {
                if (!col.filterAllowed) return col;
                return { ...col, filterParams: { values: choices[col.field] || [] } };
            }));
        });
    }, [getEpochs]);

    const onServerFetch = useCallback(({ filters, sortKey, sortOrder, skip, searchAfterJson, searchString }) => {
        const { since, until } = getEpochs();
        return api.searchMessages({
            startTime: since,
            endTime: until,
            filters,
            sortKey,
            sortOrder,
            skip,
            limit: 500,
            searchAfterJson,
            searchString,
        }).then(result => {
            setRows(result?.value || []);
            return result;
        });
    }, [getEpochs]);

    return (
        <>
            <AgGridTable
                rowData={rows}
                columnDefs={columnDefs}
                searchPlaceholder="Search in message content"
                rowSelection="single"
                paginationPageSize={20}
                noOuterBorder
                pagination={false}
                isServerMode={true}
                onRowClicked={p => {
                    setSpansModalTraceId(p.data.traceId);
                    setSpansModalOpen(true);
                }}
                onServerFetch={onServerFetch}
                filterStateUrl={window.location.pathname + "/llm-messages"}
            />
            <SpansPanelModal
                open={spansModalOpen}
                onClose={() => setSpansModalOpen(false)}
                traceId={spansModalTraceId}
            />
        </>
    );
}

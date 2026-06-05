import { useCallback, useEffect, useState } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import api from "./api";
import { PROMPT_COLUMN_DEFS } from "./constants";
import PromptDetailModal from "./PromptDetailModal";

export default function PromptsView({ currDateRange }) {
    const [rows, setRows] = useState([]);
    const [columnDefs, setColumnDefs] = useState(PROMPT_COLUMN_DEFS);
    const [selectedPrompt, setSelectedPrompt] = useState(null);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    // Load filter choices for agSetColumnFilter columns on date range change
    useEffect(() => {
        const { since, until } = getEpochs();
        api.fetchFilterChoices(since, until).then(choices => {
            setColumnDefs(PROMPT_COLUMN_DEFS.map(col => {
                if (!col.filterAllowed) return col;
                return { ...col, filterParams: { values: choices[col.field] || [] } };
            }));
        });
    }, [getEpochs]);

    // Called by AgGridTable whenever filter/sort/page changes.
    // Must return the promise so AgGridTable can read total from the result.
    const onServerFetch = useCallback(({ filters, sortKey, sortOrder, skip, limit, searchAfterJson }) => {
        const { since, until } = getEpochs();
        return api.searchPrompts({ startTime: since, endTime: until, filters, sortKey, sortOrder, skip, limit, searchAfterJson })
            .then(result => {
                setRows(result?.value || []);
                return result;
            });
    }, [getEpochs]);

    return (
        <>
            <div style={{ height: "calc(100vh - 220px)", minHeight: 0, display: "flex", flexDirection: "column" }}>
                <AgGridTable
                    rowData={rows}
                    columnDefs={columnDefs}
                    fillHeight
                    searchPlaceholder="Search by user, service, or prompt content..."
                    rowSelection="single"
                    pagination
                    paginationPageSize={20}
                    noOuterBorder
                    onRowClicked={p => setSelectedPrompt(p.data)}
                    onServerFetch={onServerFetch}
                    filterStateUrl={window.location.pathname + "/llm-prompts"}
                    defaultColDef={{ resizable: true, sortable: false, filter: false }}
                />
            </div>
            <PromptDetailModal prompt={selectedPrompt} onClose={() => setSelectedPrompt(null)} />
        </>
    );
}

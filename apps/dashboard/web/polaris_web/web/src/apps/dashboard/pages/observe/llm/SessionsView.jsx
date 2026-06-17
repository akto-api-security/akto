import { useCallback } from "react";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { SESSION_COLUMN_DEFS } from "./columns";

const DEFAULT_COL_DEF = { sortable: true, resizable: true, filter: false };

export default function SessionsView({ rowData, onOpenSession }) {
    const handleRowClick = useCallback(
        p => p.data && onOpenSession?.(p.data.sessionIdentifier),
        [onOpenSession]
    );
    const getRowStyle = useCallback(() => ({ cursor: "pointer" }), []);

    return (
        <AgGridTable
            rowData={rowData}
            columnDefs={SESSION_COLUMN_DEFS}
            defaultColDef={DEFAULT_COL_DEF}
            height={500}
            domLayout="normal"
            rowHeight={44}
            headerHeight={40}
            searchPlaceholder="Search sessions..."
            rowSelection="single"
            pagination
            paginationPageSize={20}
            paginationPageSizeSelector={[20, 50, 100]}
            animateRows
            suppressCellFocus
            getRowStyle={getRowStyle}
            onRowClicked={handleRowClick}
            sideBar={{ toolPanels: ["columns", "filters"] }}
        />
    );
}

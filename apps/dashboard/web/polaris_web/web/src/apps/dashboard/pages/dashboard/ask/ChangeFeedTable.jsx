import { useCallback, useMemo } from "react"
import { Box, Text } from "@shopify/polaris"
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable"
import func from "@/util/func"
import { severityHex } from "./transform"

// "What changed" — configured exactly like InsightEvidenceTable.jsx (same rowHeight/headerHeight/
// sideBar/animateRows/suppressCellFocus), since AgGridTable has no built-in loading or empty
// state of its own — those are gated by the caller here, same as everywhere else this table is used.
const COLUMN_DEFS = [
    {
        field: "kind",
        headerName: "",
        width: 110,
        minWidth: 110,
        flex: 0,
        filter: false,
        sortable: false,
        cellStyle: (p) => ({ color: severityHex(p.value), fontWeight: 600 }),
        valueFormatter: (p) => String(p.value || "").toUpperCase(),
    },
    { field: "description", headerName: "Change", flex: 1, minWidth: 220, filter: false, sortable: false },
    {
        field: "timestamp",
        headerName: "When",
        width: 110,
        minWidth: 110,
        flex: 0,
        filter: false,
        sortable: false,
        valueFormatter: (p) => (typeof p.value === "number" ? func.prettifyEpoch(p.value) : ""),
    },
]

export default function ChangeFeedTable({ rows, loading, onOpenRoute }) {
    const columnDefs = useMemo(() => COLUMN_DEFS, [])

    const handleRowClicked = useCallback(({ data }) => {
        if (data?.route) onOpenRoute(data.route, data.params)
    }, [onOpenRoute])

    const getRowStyle = useCallback(
        ({ data }) => (data?.route ? { cursor: "pointer" } : undefined),
        []
    )

    if (!loading && (!rows || rows.length === 0)) {
        return <Text variant="bodySm" color="subdued">Nothing has changed recently.</Text>
    }

    return (
        <Box>
            <AgGridTable
                rowData={rows || []}
                columnDefs={columnDefs}
                rowHeight={40}
                headerHeight={36}
                domLayout="autoHeight"
                sideBar={false}
                animateRows={false}
                suppressCellFocus
                hardCodedKey
                onRowClicked={handleRowClicked}
                getRowStyle={getRowStyle}
            />
        </Box>
    )
}

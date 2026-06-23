import React, { useCallback, useMemo } from 'react'
import AgGridTable from '../../tables/AgGridTable'
import { buildTreeColumnDefs, buildTreeAutoGroupColumnDef } from './UrlTreeViewColumns'
import '../../../components/layouts/style.css'

const DEFAULT_COL_DEF = { cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'flex-start' } }
const GET_DATA_PATH = (r) => r.path

const AUTO_GROUP_INNER_RENDERER = (params) => {
    if (!params.node.group) {
        return params.data?.endpointComp || params.value || ''
    }
    const directChildCount = params.node.childrenAfterGroup?.length ?? 0
    return `${params.value} (${directChildCount})`
}

const AUTO_GROUP_COL_DEF = buildTreeAutoGroupColumnDef({ minWidth: 560, innerRenderer: AUTO_GROUP_INNER_RENDERER })

function UrlTreeView({ flatRows, tableHeaders, onRowClick, searchPlaceholder, groupDefaultExpanded }) {
    const columnDefs = useMemo(() => buildTreeColumnDefs(tableHeaders), [tableHeaders])

    const onRowClicked = useCallback((e) => {
        if (!e.node.group) {
            onRowClick?.(e.data)
        }
    }, [onRowClick])

    return (
        <AgGridTable
            defaultColDef={DEFAULT_COL_DEF}
            treeData={true}
            getDataPath={GET_DATA_PATH}
            autoGroupColumnDef={AUTO_GROUP_COL_DEF}
            rowData={flatRows}
            columnDefs={columnDefs}
            groupDefaultExpanded={groupDefaultExpanded ?? 0}
            searchPlaceholder={searchPlaceholder}
            onRowClicked={onRowClicked}
            rowHeight={44}
            domLayout="autoHeight"
            pagination={false}
            sideBar={false}
        />
    )
}

export default UrlTreeView

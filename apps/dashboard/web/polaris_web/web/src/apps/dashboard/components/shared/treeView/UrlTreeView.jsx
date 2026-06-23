import React, { useMemo } from 'react'
import AgGridTable from '../../tables/AgGridTable'
import { buildTreeColumnDefs, buildTreeAutoGroupColumnDef } from './UrlTreeViewColumns'
import '../../../components/layouts/style.css'

function UrlTreeView({ flatRows, tableHeaders, onRowClick, searchPlaceholder, groupDefaultExpanded }) {
    const columnDefs = useMemo(() => buildTreeColumnDefs(tableHeaders), [tableHeaders])

    const autoGroupColumnDef = useMemo(() => {
        const innerRenderer = (params) => {
            if (!params.node.group) {
                return params.data?.endpointComp || params.value || ''
            }
            const directChildCount = params.node.childrenAfterGroup?.length ?? 0
            return `${params.value} (${directChildCount})`
        }
        return buildTreeAutoGroupColumnDef({ minWidth: 560, innerRenderer })
    }, [])

    return (
        <AgGridTable
            defaultColDef={{ cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'flex-start' } }}
            treeData={true}
            getDataPath={(r) => r.path}
            autoGroupColumnDef={autoGroupColumnDef}
            rowData={flatRows}
            columnDefs={columnDefs}
            groupDefaultExpanded={groupDefaultExpanded ?? 0}
            searchPlaceholder={searchPlaceholder}
            onRowClicked={(e) => {
                if (!e.node.group) {
                    onRowClick?.(e.data)
                }
            }}
            rowHeight={44}
            domLayout="autoHeight"
            pagination={false}
            sideBar={false}
        />
    )
}

export default UrlTreeView

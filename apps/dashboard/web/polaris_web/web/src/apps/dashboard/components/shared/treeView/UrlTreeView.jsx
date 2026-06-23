import React, { useCallback, useMemo } from 'react'
import AgGridTable from '../../tables/AgGridTable'
import { buildTreeColumnDefs, buildTreeAutoGroupColumnDef } from './UrlTreeViewColumns'
import '../../../components/layouts/style.css'
import onboardingTransform from '../../../pages/onboarding/transform'

const DEFAULT_COL_DEF = { cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'flex-start' } }
const GET_DATA_PATH = (r) => r.path

// AG Grid React calls innerRenderer as a React component (props = AG Grid params).
// Leaf rows get an 18px arrow-placeholder space that group rows fill with the expand icon.
// Negative margin cancels it so leaves align with sibling group labels at the same depth.
function AutoGroupInnerRenderer(params) {
    if (!params.node.group) {
        const method = params.data?.method
        const url = params.data?.endpoint || params.data?.url || ''
        const color = method ? onboardingTransform.getTextColor(method) : undefined
        return (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: -18 }}>
                {method && (
                    <span style={{ color, fontWeight: 500, fontSize: 13, minWidth: 48, textAlign: 'right' }}>
                        {method}
                    </span>
                )}
                <span style={{ fontSize: 13, color: '#202223' }}>{url}</span>
            </div>
        )
    }
    const directChildCount = params.node.childrenAfterGroup?.length ?? 0
    return <span>{params.value} ({directChildCount})</span>
}

const AUTO_GROUP_COL_DEF = buildTreeAutoGroupColumnDef({ minWidth: 560, innerRenderer: AutoGroupInnerRenderer })

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

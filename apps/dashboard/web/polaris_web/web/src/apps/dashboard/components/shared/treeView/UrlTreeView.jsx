import React, { useCallback, useMemo } from 'react'
import AgGridTable from '../../tables/AgGridTable'
import { buildTreeColumnDefs, buildTreeAutoGroupColumnDef } from './UrlTreeViewColumns'
import '../../../components/layouts/style.css'
import onboardingTransform from '../../../pages/onboarding/transform'

const DEFAULT_COL_DEF = { cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'flex-start' } }
const GET_DATA_PATH = (r) => r.path

// AG Grid adds an expand-arrow placeholder (~18px) before innerRenderer on every row,
// but only group rows show the arrow. Leaf rows get the same empty space, visually
// pushing them right relative to sibling group nodes at the same depth.
// Fix: return a DOM element with marginLeft: -18px to cancel the arrow placeholder.
function buildLeafCell(data) {
    const wrapper = document.createElement('div')
    wrapper.style.cssText = 'display:flex;align-items:center;gap:8px;margin-left:-18px'

    const method = data?.method
    const url = data?.endpoint || data?.url || ''

    if (method) {
        const methodEl = document.createElement('span')
        methodEl.textContent = method
        methodEl.style.cssText = `color:${onboardingTransform.getTextColor(method)};font-weight:500;font-size:13px;min-width:48px;text-align:right`
        wrapper.appendChild(methodEl)
    }

    const urlEl = document.createElement('span')
    urlEl.textContent = url
    urlEl.style.cssText = 'font-size:13px;color:#202223'
    wrapper.appendChild(urlEl)

    return wrapper
}

const AUTO_GROUP_INNER_RENDERER = (params) => {
    if (!params.node.group) {
        return buildLeafCell(params.data)
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

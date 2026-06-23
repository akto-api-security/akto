import React from 'react'
import transform from '../../../pages/observe/transform'
import { RiskPill } from '../../../pages/observe/agentic/AgenticCellRenderers'

// Columns whose value field is a React element — AG Grid can't render them directly.
// We skip them and use textValue (the raw scalar) instead.
const COMP_COLUMNS = new Set([
    'sensitiveTagsComp', 'tagsComp',
    'sourceLocationComp', 'descriptionComp', 'lastTestedComp',
    'componentRiskAnalysisComp', 'methodComp', 'endpointComp',
])

// Columns to omit entirely from the tree view
const SKIP_COLUMNS = new Set([
    'sourceLocationComp', 'descriptionComp', 'lastTestedComp',
    'componentRiskAnalysisComp', 'methodComp', 'endpointComp',
    'sensitiveInReq', 'responseCodes',
])

/**
 * Build AG Grid columnDefs array from tableHeaders.
 * @param {Array} tableHeaders
 * @returns {Array} columnDefs
 */
function buildTreeColumnDefs(tableHeaders) {
    if (!tableHeaders) return []
    return tableHeaders
        .filter(h => h.value && !SKIP_COLUMNS.has(h.value))
        .map(h => {
            const headerName = typeof h.title === 'string' ? h.title : (h.text || h.value || '')

            // Issues: render severity badges via getIssuesList
            if (h.value === 'issuesComp') {
                return {
                    field: 'severityObj',
                    headerName,
                    flex: 1,
                    minWidth: 120,
                    cellRenderer: ({ data }) => {
                        const obj = data?.severityObj
                        if (!obj || Object.keys(obj).length === 0) return '-'
                        return transform.getIssuesList(obj)
                    },
                }
            }

            // Risk score: render colored Badge via RiskPill
            if (h.value === 'riskScoreComp') {
                return {
                    field: 'riskScore',
                    headerName,
                    flex: 1,
                    minWidth: 120,
                    cellRenderer: ({ value }) => value != null ? <RiskPill score={value} /> : null,
                }
            }

            // Sensitive params: render icons via prettifySubtypes
            if (h.value === 'sensitiveTagsComp') {
                return {
                    field: 'sensitiveTags',
                    headerName,
                    flex: 1,
                    minWidth: 140,
                    cellRenderer: (params) => {
                        const tags = params.data?.sensitiveTags
                        if (!tags || tags.length === 0) return null
                        return transform.prettifySubtypes(tags)
                    },
                }
            }

            // For other *Comp columns, use the textValue field (raw scalar)
            const field = COMP_COLUMNS.has(h.value) ? (h.textValue || h.value) : h.value
            return {
                field,
                headerName,
                flex: 1,
                minWidth: 120,
            }
        })
}

/**
 * Build the autoGroupColumnDef for AG Grid treeData.
 * @param {{ minWidth?: number, innerRenderer: Function }} options
 * @returns {object} autoGroupColumnDef
 */
function buildTreeAutoGroupColumnDef({ minWidth = 560, innerRenderer }) {
    return {
        headerName: 'Endpoint',
        minWidth,
        flex: 3,
        cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'flex-start' },
        cellRenderer: 'agGroupCellRenderer',
        cellRendererParams: {
            suppressCount: true,
            padding: 10,
            innerRenderer,
        },
    }
}

export { buildTreeColumnDefs, buildTreeAutoGroupColumnDef, COMP_COLUMNS, SKIP_COLUMNS }

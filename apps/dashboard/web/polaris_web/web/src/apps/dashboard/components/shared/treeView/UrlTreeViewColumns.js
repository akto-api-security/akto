import React from 'react'
import transform from '../../../pages/observe/transform'
import { RiskScoreCellRenderer } from '../../../pages/observe/agentic/AgenticCellRenderers'

const COMP_COLUMNS = new Set([
    'sensitiveTagsComp', 'tagsComp',
    'sourceLocationComp', 'descriptionComp', 'lastTestedComp',
    'componentRiskAnalysisComp', 'methodComp', 'endpointComp',
])

const SKIP_COLUMNS = new Set([
    'sourceLocationComp', 'descriptionComp', 'lastTestedComp',
    'componentRiskAnalysisComp', 'methodComp', 'endpointComp',
    'sensitiveInReq', 'responseCodes',
])

function buildTreeColumnDefs(tableHeaders) {
    if (!tableHeaders) return []
    return tableHeaders
        .filter(h => h.value && !SKIP_COLUMNS.has(h.value))
        .map(h => {
            const headerName = typeof h.title === 'string' ? h.title : (h.text || h.value || '')

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

            if (h.value === 'riskScoreComp') {
                return {
                    field: 'riskScore',
                    headerName,
                    flex: 1,
                    minWidth: 120,
                    cellRenderer: RiskScoreCellRenderer,
                }
            }

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

            const field = COMP_COLUMNS.has(h.value) ? (h.textValue || h.value) : h.value
            return { field, headerName, flex: 1, minWidth: 120 }
        })
}

function buildTreeAutoGroupColumnDef({ minWidth = 560, innerRenderer }) {
    return {
        headerName: 'Endpoint',
        minWidth,
        flex: 3,
        cellStyle: { display: 'flex', alignItems: 'center', justifyContent: 'flex-start' },
        cellRenderer: 'agGroupCellRenderer',
        cellRendererParams: { suppressCount: true, padding: 10, innerRenderer },
    }
}

export { buildTreeColumnDefs, buildTreeAutoGroupColumnDef }

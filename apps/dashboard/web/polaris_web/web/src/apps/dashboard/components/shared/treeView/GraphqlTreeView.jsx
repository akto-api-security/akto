import React, { useMemo, useState } from 'react'
import { Box, Button, Card, Text, VerticalStack } from '@shopify/polaris'
import { buildGraphQLFlatRows } from './graphqlTransform'
import AgGridTable from '../../tables/AgGridTable'

// Columns whose value field is a React element — AG Grid can't render them directly.
// We skip them and use textValue (the raw scalar) instead.
const COMP_COLUMNS = new Set([
    'riskScoreComp', 'issuesComp', 'sensitiveTagsComp', 'tagsComp',
    'sourceLocationComp', 'descriptionComp', 'lastTestedComp',
    'componentRiskAnalysisComp', 'methodComp', 'endpointComp',
])

// Columns to omit entirely from the tree view
const SKIP_COLUMNS = new Set([
    'sourceLocationComp', 'descriptionComp', 'lastTestedComp',
    'componentRiskAnalysisComp', 'methodComp', 'endpointComp',
])

function GraphqlTreeView({ endpoints, prettifyEndpoints, onTerminalClick, tableHeaders }) {
    const [groupByOperation, setGroupByOperation] = useState(false)

    const readyEndpoints = useMemo(() => {
        return prettifyEndpoints
            ? prettifyEndpoints(endpoints || [])
            : (endpoints || [])
    }, [endpoints, prettifyEndpoints])

    const flatRows = useMemo(() => {
        return buildGraphQLFlatRows(readyEndpoints, groupByOperation)
    }, [readyEndpoints, groupByOperation])

    const columnDefs = useMemo(() => {
        if (!tableHeaders) return []
        return tableHeaders
            .filter(h => h.value && !SKIP_COLUMNS.has(h.value))
            .map(h => {
                // For *Comp columns, use the textValue field (raw scalar) instead
                const field = COMP_COLUMNS.has(h.value) ? (h.textValue || h.value) : h.value
                const headerName = typeof h.title === 'string' ? h.title : (h.text || h.value || '')
                return {
                    field,
                    headerName,
                    flex: 1,
                    minWidth: 120,
                }
            })
    }, [tableHeaders])

    // Custom cell renderer for the group/tree column:
    // - Leaf rows: show endpointComp (the prettified method+url badge)
    // - Group rows: show the segment name (default AG Grid behaviour)
    const autoGroupColumnDef = useMemo(() => ({
        headerName: 'Endpoint',
        minWidth: 360,
        flex: 2,
        cellRenderer: 'agGroupCellRenderer',
        cellRendererParams: {
            suppressCount: false,
            innerRenderer: (params) => {
                if (!params.node.group) {
                    // endpointComp is a React element set by prettifyEndpoints
                    return params.data?.endpointComp || params.value || ''
                }
                return params.value || ''
            },
        },
    }), [])

    const treeExample = groupByOperation
        ? 'query\n  |-- SignupQuery (1)\n  |       |-- bankAccountDisclosures\n  |-- AccountQuery (1)\n          |-- bankAccountDisclosures'
        : 'query\n  |-- bankAccountDisclosures (2)\n          |-- SignupQuery\n          |-- AccountQuery'

    return (
        <VerticalStack gap="4">
            <Card padding="10">
                <VerticalStack gap={4}>
                    <Box width="500px">
                        <VerticalStack gap={4}>
                            <Text variant="headingLg">GraphQL Tree View</Text>
                            <Text color="subdued" variant="bodyMd">
                                {groupByOperation
                                    ? 'These two URLs share the same operation name, so they collapse into one group:'
                                    : 'These two URLs share the same field name, so they collapse into one group:'}
                            </Text>
                            <Box background="bg-subdued" padding="3" borderRadius="2">
                                <pre style={{ margin: 0, fontFamily: 'monospace', fontSize: '13px', lineHeight: '1.6', color: '#637381' }}>
                                    {'/graphql/query/SignupQuery/bankAccountDisclosures\n/graphql/query/AccountQuery/bankAccountDisclosures'}
                                </pre>
                            </Box>
                            <Text color="subdued" variant="bodyMd">
                                Results in:
                            </Text>
                            <Box background="bg-subdued" padding="3" borderRadius="2">
                                <pre style={{ margin: 0, fontFamily: 'monospace', fontSize: '13px', lineHeight: '1.6', color: '#637381' }}>
                                    {treeExample}
                                </pre>
                            </Box>
                            <Box paddingBlockStart={2}>
                                <Button
                                    primary
                                    onClick={() => setGroupByOperation(prev => !prev)}
                                >
                                    {groupByOperation ? 'Group by field name' : 'Group by operation name'}
                                </Button>
                            </Box>
                        </VerticalStack>
                    </Box>
                </VerticalStack>
            </Card>
            <AgGridTable
                treeData={true}
                getDataPath={(row) => row.path}
                autoGroupColumnDef={autoGroupColumnDef}
                rowData={flatRows}
                columnDefs={columnDefs}
                groupDefaultExpanded={0}
                searchPlaceholder="Search endpoints..."
                onRowClicked={(e) => {
                    if (!e.node.group) {
                        onTerminalClick?.(e.data)
                    }
                }}
                rowHeight={44}
                domLayout="autoHeight"
                pagination={false}
                sideBar={false}
            />
        </VerticalStack>
    )
}

export default GraphqlTreeView

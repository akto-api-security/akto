import React, { useMemo, useState } from 'react'
import { Box, Button, Card, Text, VerticalStack } from '@shopify/polaris'
import { buildGraphQLFlatRows } from './graphqlTransform'
import UrlTreeView from './UrlTreeView'

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
            <UrlTreeView
                flatRows={flatRows}
                tableHeaders={tableHeaders}
                onRowClick={onTerminalClick}
                searchPlaceholder="Search endpoints..."
            />
        </VerticalStack>
    )
}

export default GraphqlTreeView

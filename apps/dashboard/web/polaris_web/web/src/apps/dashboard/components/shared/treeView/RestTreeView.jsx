import React, { useMemo } from 'react'
import { Box, Card, Text, VerticalStack } from '@shopify/polaris'
import { buildRestFlatRows } from './restTransform'
import UrlTreeView from './UrlTreeView'

function RestTreeView({ endpoints, prettifyEndpoints, onTerminalClick, tableHeaders }) {
    const readyEndpoints = useMemo(() => {
        return prettifyEndpoints ? prettifyEndpoints(endpoints || []) : (endpoints || [])
    }, [endpoints, prettifyEndpoints])

    const flatRows = useMemo(() => buildRestFlatRows(readyEndpoints), [readyEndpoints])

    return (
        <VerticalStack gap="4">
            <Card padding="10">
                <VerticalStack gap={4}>
                    <Box width="500px">
                        <VerticalStack gap={4}>
                            <Text variant="headingLg">REST Tree View</Text>
                            <Text color="subdued" variant="bodyMd">
                                Endpoints are grouped by URL path segments. Numeric IDs and UUIDs are normalized to {'{param}'}.
                            </Text>
                            <Box background="bg-subdued" padding="3" borderRadius="2">
                                <pre style={{ margin: 0, fontFamily: 'monospace', fontSize: '13px', lineHeight: '1.6', color: '#637381' }}>
                                    {'/api/v1/users/123/orders\n/api/v1/users/456/orders\n\n→ api → v1 → users → {param} → orders'}
                                </pre>
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

export default RestTreeView

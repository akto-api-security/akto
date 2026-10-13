import { Box, Card, Text, VerticalStack } from '@shopify/polaris'
import SimpleIndexTable from '../../../../components/tables/SimpleIndexTable'

const HEADINGS = [
    { title: 'Category' },
    { title: 'Points removed' },
    { title: 'Fix' },
]

function RemediationSection({ rows }) {
    if (!rows || rows.length === 0) return null

    return (
        <Card padding="0">
            <Box padding="5" paddingBlockEnd="3">
                <VerticalStack gap="1">
                    <Text variant="bodyMd" fontWeight="semibold">Recommended fixes</Text>
                    <Text variant="bodySm" color="subdued">Ordered by how many points each fix removes.</Text>
                </VerticalStack>
            </Box>
            <SimpleIndexTable
                resourceName={{ singular: 'fix', plural: 'fixes' }}
                headings={HEADINGS}
                rows={rows.map((row) => [
                    <Text variant="bodyMd" fontWeight="medium">{row.category}</Text>,
                    <Text variant="bodyMd" fontWeight="semibold">{row.points.toFixed(1)}</Text>,
                    <Text variant="bodyMd" color="subdued">{row.remediation}</Text>,
                ])}
                getRowId={(cells, index) => rows[index].category}
            />
        </Card>
    )
}

export default RemediationSection

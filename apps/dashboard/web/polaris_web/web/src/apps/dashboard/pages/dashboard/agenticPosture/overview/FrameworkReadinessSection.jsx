import { Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import CustomProgressBar from '../../new_components/CustomProgressBar'

function colorForReadiness(value) {
    if (value >= 75) return '#23C48C'
    if (value >= 40) return '#F2B322'
    return '#F24122'
}

function FrameworkRow({ row }) {
    return (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between">
                <Text variant="bodyMd">{row.framework}</Text>
                <Text variant="bodyMd" fontWeight="semibold">{row.value}%</Text>
            </HorizontalStack>
            <CustomProgressBar progress={row.value} topColor={colorForReadiness(row.value)} height="10px" />
        </VerticalStack>
    )
}

function FrameworkReadinessSection({ frameworkReadiness, onOpen }) {
    const rows = frameworkReadiness?.frameworks || []

    if (rows.length === 0) {
        return (
            <Card>
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">
                        No guardrail activity demonstrating a compliance framework in this window.
                    </Text>
                </Box>
            </Card>
        )
    }

    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="3">
                    {rows.map((row) => (
                        <div key={row.framework} onClick={() => onOpen()} style={{ cursor: 'pointer' }}>
                            <FrameworkRow row={row} />
                        </div>
                    ))}
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default FrameworkReadinessSection

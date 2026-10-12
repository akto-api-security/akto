import { Badge, Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import DetailGrid from '../../../observe/agentic/DetailGrid'

function DataSection({ data }) {
    if (!data) return null

    // Unavailable is not the same as no detections: an unreadable guardrail backend must not
    // render as an agent that handles no sensitive data.
    if (data.available === false) {
        return (
            <Card padding="5">
                <Text variant="bodyMd" color="subdued" alignment="center">
                    Guardrail activity is unavailable, so sensitive data could not be determined.
                </Text>
            </Card>
        )
    }

    const types = data.types || []
    const items = [
        { label: 'Sensitive data access', value: data.sensitiveDataAccess ? 'Yes' : 'No' },
        { label: 'Sensitive data detections, 90d', value: String(data.detections || 0) },
    ]

    return (
        <Card padding="5">
            <VerticalStack gap="4">
                {types.length > 0 ? (
                    <HorizontalStack gap="2" wrap>
                        {types.map((type) => <Badge key={type}>{type}</Badge>)}
                    </HorizontalStack>
                ) : (
                    <Text variant="bodyMd" color="subdued">No sensitive data detected.</Text>
                )}
                <DetailGrid items={items} columns={2} />
            </VerticalStack>
        </Card>
    )
}

export default DataSection

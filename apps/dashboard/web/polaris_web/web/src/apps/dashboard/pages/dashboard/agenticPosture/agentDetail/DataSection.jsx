import { Badge, Box, Card, HorizontalStack, VerticalStack } from '@shopify/polaris'
import DetailGrid from '../../../observe/agentic/DetailGrid'

function DataSection({ data }) {
    if (!data) return null
    const items = [
        { label: 'Sensitive data access', value: data.sensitiveAccess ? 'Yes' : 'No' },
        {
            label: 'Can send externally',
            value: data.canSendExternally ? `Yes — ${data.canSendExternallyDetail || ''}` : 'No',
            isWarning: data.canSendExternally,
        },
    ]
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="4">
                    <HorizontalStack gap="2" wrap>
                        {(data.categories || []).map((cat) => <Badge key={cat}>{cat}</Badge>)}
                    </HorizontalStack>
                    <DetailGrid items={items} columns={2} />
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default DataSection

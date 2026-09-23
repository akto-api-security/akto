import { Badge, Box, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { LockMajor } from '@shopify/polaris-icons'

function ToolRow({ tool }) {
    return (
        <Card>
            <Box padding="4">
                <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="4">
                    <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                        {tool.privileged && <Box><Icon source={LockMajor} color="subdued" /></Box>}
                        <VerticalStack gap="0">
                            <Text variant="bodyMd" fontWeight="semibold">{tool.name}</Text>
                            <Text variant="bodySm" color="subdued">{tool.detail}</Text>
                        </VerticalStack>
                    </HorizontalStack>
                    <Badge status={tool.statusTone}>{tool.status}</Badge>
                </HorizontalStack>
            </Box>
        </Card>
    )
}

function ToolsSection({ tools }) {
    return (
        <VerticalStack gap="3">
            {(tools || []).map((tool) => <ToolRow key={tool.name} tool={tool} />)}
        </VerticalStack>
    )
}

export default ToolsSection

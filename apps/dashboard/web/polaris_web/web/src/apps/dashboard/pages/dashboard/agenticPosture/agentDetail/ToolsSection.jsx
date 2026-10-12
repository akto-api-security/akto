import { Badge, Box, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { ToolsMajor } from '@shopify/polaris-icons'
import { toolTone } from './tones'
import './AgentDetail.css'

function ToolRow({ tool }) {
    const tone = toolTone(tool)

    return (
        <Card padding="4">
            <HorizontalStack align="space-between" blockAlign="center" gap="4" wrap={false}>
                <HorizontalStack align="start" gap="4" blockAlign="center" wrap={false}>
                    <Box background={tone.background} borderRadius="2" padding="2">
                        <Icon source={ToolsMajor} color={tone.icon} />
                    </Box>
                    <VerticalStack gap="05">
                        <Text variant="bodyMd" fontWeight="semibold" breakWord>{tool.name}</Text>
                        {tool.detail && <Text variant="bodySm" color="subdued">{tool.detail}</Text>}
                    </VerticalStack>
                </HorizontalStack>
                {tool.privileged && (
                    <Badge status={tone.badge} progress="complete">{tool.capabilityLabel}</Badge>
                )}
            </HorizontalStack>
        </Card>
    )
}

function ToolsSection({ tools }) {
    const rows = tools || []
    if (rows.length === 0) {
        return (
            <Card padding="4">
                <Text variant="bodyMd" color="subdued" alignment="center">No tools discovered for this agent.</Text>
            </Card>
        )
    }
    return (
        <VerticalStack gap="3">
            {rows.map((tool) => <ToolRow key={`${tool.method} ${tool.url}`} tool={tool} />)}
        </VerticalStack>
    )
}

export default ToolsSection

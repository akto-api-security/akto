import { Badge, Box, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { LockMajor } from '@shopify/polaris-icons'
import DetailGrid from '../../../observe/agentic/DetailGrid'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'

function AgentHeaderCard({ header }) {
    if (!header) return null
    const metadataItems = [
        { label: 'Created', value: header.created },
        { label: 'Last active', value: header.lastActive },
        { label: 'Framework', value: header.framework },
        { label: 'Invocation', value: header.invocationTrigger },
    ].filter((item) => item.value)

    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="4">
                    <HorizontalStack align="space-between" blockAlign="start" wrap>
                        <HorizontalStack gap="4" blockAlign="center">
                            <Box
                                background="bg-critical-subdued"
                                borderRadius="2"
                                padding="3"
                            >
                                <Icon source={LockMajor} color="critical" />
                            </Box>
                            <VerticalStack gap="2">
                                <HorizontalStack gap="2" blockAlign="center" wrap>
                                    <Text variant="headingLg" as="h1">{header.name}</Text>
                                    <SeverityBadge severity={header.severity} />
                                    <Badge>{header.environment}</Badge>
                                </HorizontalStack>
                                {header.description && (
                                    <Box maxWidth="540px">
                                        <Text variant="bodyMd" color="subdued">{header.description}</Text>
                                    </Box>
                                )}
                            </VerticalStack>
                        </HorizontalStack>
                        <VerticalStack gap="0" inlineAlign="end">
                            <Text variant="heading2xl">{header.riskScore}<Text as="span" variant="bodyMd" color="subdued">/100</Text></Text>
                            <Text variant="bodySm" color="subdued">risk score</Text>
                        </VerticalStack>
                    </HorizontalStack>
                    {metadataItems.length > 0 && (
                        <Box borderBlockStartWidth="1" borderColor="border" paddingBlockStart="4">
                            <DetailGrid items={metadataItems} columns={4} />
                        </Box>
                    )}
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default AgentHeaderCard

import { Card, VerticalStack, Box, HorizontalStack, HorizontalGrid, Text, Button } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

const GuardrailCoverageCard = ({ enabledCount = 0, totalCount = 0, blocklistMode = false, toolIcons = [], itemId = "", onRemoveComponent }) => {
    const percentage = totalCount > 0 ? (enabledCount / totalCount) * 100 : 0

    return (
        <Card>
            <VerticalStack gap={4} align="space-between">
                <VerticalStack gap={4}>
                    <ComponentHeader title='AI Guardrail Coverage' itemId={itemId} onRemove={onRemoveComponent} />

                    {/* Enabled Guardrails */}
                    <VerticalStack gap={2}>
                        <HorizontalStack align='space-between' blockAlign='center'>
                            <HorizontalStack gap={2} blockAlign='center'>
                                <Box background='bg-fill-success' borderRadius='full' padding='025'>
                                    <Text as='span' variant='bodySm' fontWeight='bold' tone='text-inverse'>✓</Text>
                                </Box>
                                <Text variant='bodyMd'>Enabled Guardrails</Text>
                            </HorizontalStack>
                            <Text variant='bodyMd' fontWeight='bold'>
                                {enabledCount}/{totalCount}
                            </Text>
                        </HorizontalStack>
                        <Box background='bg-fill-tertiary' borderRadius='100' paddingBlock='025'>
                            <Box background='bg-fill-critical' borderRadius='100' width={`${percentage}%`} minHeight='8px' />
                        </Box>
                    </VerticalStack>

                    {/* Blocklist mode */}
                    {blocklistMode && (
                        <HorizontalStack gap={2} blockAlign='center'>
                            <Box background='bg-fill-critical' borderRadius='100' padding='025'>
                                <Text as='span' variant='bodySm' fontWeight='bold' tone='text-inverse'>⊘</Text>
                            </Box>
                            <Text variant='bodyMd'>Blocklist mode</Text>
                        </HorizontalStack>
                    )}

                    {/* Tool icons grid */}
                    {toolIcons.length > 0 && (
                        <HorizontalGrid columns={6} gap={2}>
                            {toolIcons.slice(0, 12).map((icon, idx) => (
                                <Box key={idx} minWidth='32px' minHeight='32px'>
                                    <img
                                        src={icon}
                                        alt={`tool-${idx}`}
                                        width='32'
                                        height='32'
                                    />
                                </Box>
                            ))}
                        </HorizontalGrid>
                    )}
                </VerticalStack>

                <Box>
                    <Button plain>View All (22)</Button>
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default GuardrailCoverageCard

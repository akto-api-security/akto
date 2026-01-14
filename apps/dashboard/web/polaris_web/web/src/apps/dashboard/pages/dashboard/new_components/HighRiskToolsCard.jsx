import { Card, VerticalStack, HorizontalStack, Box, Text, Button } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

const HighRiskToolsCard = ({ toolsData = [], itemId = "", onRemoveComponent }) => {
    return (
        <Card>
            <VerticalStack gap={4} align="space-between">
                <VerticalStack gap={4}>
                    <ComponentHeader title='High-Risk AI Tools Without Guardrails' itemId={itemId} onRemove={onRemoveComponent} />

                    <VerticalStack gap={3}>
                    {toolsData.map((tool, idx) => (
                        <HorizontalStack key={idx} gap={3} align='space-between' blockAlign='center'>
                            <HorizontalStack gap={3} blockAlign='center'>
                                <Box minWidth='24px' minHeight='24px'>
                                    <img
                                        src={tool.icon}
                                        alt={tool.name}
                                        width='24'
                                        height='24'
                                    />
                                </Box>
                                <Text variant='bodyMd' fontWeight='medium'>
                                    {tool.name}
                                </Text>
                            </HorizontalStack>

                            <HorizontalStack gap={2} align='end' blockAlign='center'>
                                <Box minWidth='100px'>
                                    <Box background='bg-fill-tertiary' borderRadius='100' paddingBlock='025'>
                                        <Box background='bg-fill-critical' borderRadius='100' width={`${tool.percentage}%`} minHeight='8px' />
                                    </Box>
                                </Box>
                                <Box minWidth='40px'>
                                    <Text variant='bodyMd' fontWeight='bold' alignment='end'>
                                        {tool.percentage}%
                                    </Text>
                                </Box>
                            </HorizontalStack>
                        </HorizontalStack>
                    ))}
                    </VerticalStack>
                </VerticalStack>

                <Box>
                    <Button plain>View All (42)</Button>
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default HighRiskToolsCard

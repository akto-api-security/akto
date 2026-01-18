import { Card, VerticalStack, Box, HorizontalStack, HorizontalGrid, Text } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

const ComplianceAtRisksCard = ({ complianceData = [], itemId = "", onRemoveComponent }) => {
    return (
        <Card>
            <VerticalStack gap={4}>
                <ComponentHeader title='Compliance at Risks' itemId={itemId} onRemove={onRemoveComponent} />

                <Box width='100%'>
                    <HorizontalGrid columns={4} gap={3}>
                        {complianceData.map((compliance, idx) => (
                            <VerticalStack key={idx} gap={2} align='center' inlineAlign='center'>
                                <Box width='100%' minHeight='80px' display='flex' alignItems='center' justifyContent='center'>
                                    <div style={{
                                        width: '100%',
                                        maxWidth: '100px',
                                        height: '80px',
                                        backgroundImage: `url(${compliance.icon})`,
                                        backgroundRepeat: 'no-repeat',
                                        backgroundPosition: 'center',
                                        backgroundSize: 'contain'
                                    }} />
                                </Box>

                                <Box width='100%'>
                                    <VerticalStack gap={1} align='center' inlineAlign='center'>
                                        <Text variant='headingSm' alignment='center' fontWeight='semibold'>
                                            {compliance.name}
                                        </Text>
                                        <Box width='100%'>
                                            <HorizontalStack gap={2} align='space-between' blockAlign='center'>
                                                <div style={{
                                                    flex: 1,
                                                    height: '5px',
                                                    backgroundColor: '#E5E7EB',
                                                    borderRadius: '3px',
                                                    overflow: 'hidden'
                                                }}>
                                                    <div style={{
                                                        width: `${compliance.percentage}%`,
                                                        height: '100%',
                                                        backgroundColor: compliance.color,
                                                        borderRadius: '3px'
                                                    }} />
                                                </div>
                                                <Text variant='bodySm' as='span' fontWeight='medium'>
                                                    {compliance.percentage}%
                                                </Text>
                                            </HorizontalStack>
                                        </Box>
                                    </VerticalStack>
                                </Box>
                            </VerticalStack>
                        ))}
                    </HorizontalGrid>
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default ComplianceAtRisksCard

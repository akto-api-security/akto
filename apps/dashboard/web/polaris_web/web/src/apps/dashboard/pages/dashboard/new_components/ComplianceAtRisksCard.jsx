import { Card, VerticalStack, Box, HorizontalStack, HorizontalGrid, Text } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

const ComplianceAtRisksCard = ({ complianceData = [], itemId = "", onRemoveComponent }) => {
    return (
        <Card>
            <VerticalStack gap={4}>
                <ComponentHeader title='Compliance at Risks' itemId={itemId} onRemove={onRemoveComponent} />

                <Box width='100%'>
                    <HorizontalGrid columns={4} gap={5}>
                        {complianceData.map((compliance, idx) => (
                            <VerticalStack key={idx} gap={5} inlineAlign='center' align='start'>
                                <div style={{
                                    width: '180px',
                                    height: '165px',
                                    backgroundImage: `url(${compliance.icon})`,
                                    backgroundRepeat: 'no-repeat',
                                    backgroundPosition: 'center',
                                    backgroundSize: 'contain'
                                }} />

                                <Box width='100%'>
                                    <VerticalStack gap={2} align='end' inlineAlign='center'>
                                        <Text variant='headingXl' alignment='center' fontWeight='semibold'>
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
                                                <Text variant='bodySm' as='span'>
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

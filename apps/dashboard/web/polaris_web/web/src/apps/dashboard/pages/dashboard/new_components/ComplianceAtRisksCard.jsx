import { Card, VerticalStack, Box, HorizontalStack, HorizontalGrid, Text, Tooltip } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

const ComplianceAtRisksCard = ({ complianceData = [], itemId = "", onRemoveComponent, tooltipContent = "" }) => {
    return (
        <Card>
            <Box paddingBlockEnd="500">
                <VerticalStack gap="500">
                    <ComponentHeader title='Compliance at Risks' itemId={itemId} onRemove={onRemoveComponent} tooltipContent={tooltipContent} />

                    <Box width='100%' paddingBlockStart="200">
                        <HorizontalGrid columns={4} gap="400">
                        {complianceData.map((compliance, idx) => (
                            <VerticalStack key={idx} gap="300" align='center' inlineAlign='center'>
                                <Box width='100%' height='80px' display='flex' alignItems='center' justifyContent='center'>
                                    <div style={{
                                        width: '100px',
                                        height: '80px',
                                        backgroundImage: `url("${compliance.icon}")`,
                                        backgroundRepeat: 'no-repeat',
                                        backgroundPosition: 'center center',
                                        backgroundSize: 'contain',
                                        margin: '0 auto'
                                    }} />
                                </Box>

                                <Box width='100%'>
                                    <VerticalStack gap={1} align='center' inlineAlign='center'>
                                        <Box width='100%' minHeight='40px' display='flex' alignItems='center' justifyContent='center'>
                                            <Tooltip content={compliance.name} preferredPosition="above">
                                                <Text 
                                                    variant='headingSm' 
                                                    alignment='center' 
                                                    fontWeight='semibold'
                                                    truncate
                                                    style={{
                                                        maxWidth: '100%',
                                                        overflow: 'hidden',
                                                        textOverflow: 'ellipsis',
                                                        whiteSpace: 'nowrap'
                                                    }}
                                                >
                                                    {compliance.name}
                                                </Text>
                                            </Tooltip>
                                        </Box>
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
                                                <Text variant='bodySm' as='span' fontWeight='medium' style={{ minWidth: '40px', textAlign: 'right' }}>
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
            </Box>
        </Card>
    )
}

export default ComplianceAtRisksCard

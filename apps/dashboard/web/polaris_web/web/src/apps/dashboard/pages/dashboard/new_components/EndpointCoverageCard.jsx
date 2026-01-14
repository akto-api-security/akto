import { Card, VerticalStack, HorizontalStack, Text, Box } from '@shopify/polaris'
import ComponentHeader from './ComponentHeader'

const EndpointCoverageCard = ({ coverageData = [], itemId = "", onRemoveComponent }) => {
    return (
        <Card>
            <VerticalStack gap={4}>
                <ComponentHeader title='Endpoint Coverage' itemId={itemId} onRemove={onRemoveComponent} />

                <Box>
                    <HorizontalStack gap={8} align='space-between' blockAlign='center'>
                        {coverageData.map((item, idx) => (
                            <Box key={idx} style={{ flex: 1 }}>
                                <HorizontalStack gap={3} blockAlign='center'>
                                    <img
                                        src={item.icon}
                                        alt={item.os}
                                        style={{ width: '48px', height: '48px', objectFit: 'contain' }}
                                    />
                                    <VerticalStack gap={1}>
                                        <Text variant='heading2xl' fontWeight='bold'>
                                            {item.count.toLocaleString()}
                                        </Text>
                                        <Text variant='bodySm' color='subdued'>
                                            {item.os}
                                        </Text>
                                    </VerticalStack>
                                </HorizontalStack>
                            </Box>
                        ))}
                    </HorizontalStack>
                </Box>
            </VerticalStack>
        </Card>
    )
}

export default EndpointCoverageCard

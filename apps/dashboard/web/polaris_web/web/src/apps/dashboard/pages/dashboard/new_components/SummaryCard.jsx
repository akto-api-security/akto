import { Box, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function SummaryCard({ summaryItems }) {
    return (
        <Card padding={0} key="info">
            <Box padding={2} paddingInlineStart={4} paddingInlineEnd={4}>
                <HorizontalGrid columns={summaryItems.length} gap={4}>
                    {summaryItems.map((item, index) => (
                        <Box borderInlineEndWidth={index < (summaryItems.length - 1) ? "1" : ""} key={index} paddingBlockStart={1} paddingBlockEnd={1} borderColor="transparent">
                            <HorizontalStack>
                                <VerticalStack gap="4">
                                    <Text variant="headingMd">
                                        {item.title}
                                    </Text>
                                    {item?.isComp ? item.data : <Text variant={item.variant ? item.variant : 'bodyLg'} color={item.color ? item.color : ""}>
                                        {item.data}
                                    </Text>
                                    }
                                    {item.byLineComponent ? item.byLineComponent : null}
                                </VerticalStack>

                                {item.sidePanel ? item.sidePanel : null}
                            </HorizontalStack>
                        </Box>
                    ))}
                </HorizontalGrid>
            </Box>
        </Card>
    )
}

export default SummaryCard
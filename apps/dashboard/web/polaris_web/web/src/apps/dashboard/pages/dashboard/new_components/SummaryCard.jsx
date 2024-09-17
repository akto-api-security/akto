import { Box, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function SummaryCard({ summaryItems }) {
    console.log(summaryItems);
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
                                    <HorizontalGrid gap={1} columns={2}>
                                        <VerticalStack gap={4}>
                                            {item?.isComp ? item.data : 
                                            <div className='custom-color'>
                                                <Text variant={item.variant ? item.variant : 'bodyLg'} color={item.color ? item.color : ""}>
                                                    {item.data}
                                                </Text>
                                            </div>
                                            }
                                            {item.byLineComponent ? item.byLineComponent : null}
                                        </VerticalStack>
                                        {item.smoothChartComponent ? item.smoothChartComponent : null}
                                    </HorizontalGrid>
                                </VerticalStack>
                            </HorizontalStack>
                        </Box>
                    ))}
                </HorizontalGrid>
            </Box>
        </Card>
    )
}

export default SummaryCard
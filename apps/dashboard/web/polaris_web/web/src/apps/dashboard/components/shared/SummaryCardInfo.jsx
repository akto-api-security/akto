import { Box, Card, HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function SummaryCardInfo({summaryItems}) {
    return (
        <Card padding={0} key="info">
            <Box padding={2} paddingInlineStart={4} paddingInlineEnd={4}>
                <HorizontalGrid columns={summaryItems.length} gap={4}>
                    {summaryItems.map((item, index) => (
                        <Box borderInlineEndWidth={index < (summaryItems.length - 1) ? "1" : ""} key={index} paddingBlockStart={1} paddingBlockEnd={1} borderColor="border-subdued">
                            <VerticalStack gap="1">
                                <Text color="subdued" variant="headingXs">
                                    {item.title}
                                </Text>
                                {item?.isComp ? item.data :<Text variant={item.variant ? item.variant : 'bodyMd'} fontWeight="semibold" color={item.color ? item.color :""}>
                                    {item.data}
                                </Text>
                                }
                            </VerticalStack>
                        </Box>
                    ))}
                </HorizontalGrid>
            </Box>
        </Card>
    )
}

export default SummaryCardInfo
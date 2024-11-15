import { Box, Card, InlineGrid, Text, BlockStack } from '@shopify/polaris'
import React from 'react'

function SummaryCardInfo({summaryItems}) {
    return (
        <Card padding={0} key="info">
            <Box padding={2} paddingInlineStart={4} paddingInlineEnd={4}>
                <InlineGrid columns={summaryItems.length} gap={4}>
                    {summaryItems.map((item, index) => (
                        <Box borderInlineEndWidth={index < (summaryItems.length - 1) ? "1" : ""} key={index} paddingBlockStart={1} paddingBlockEnd={1} borderColor="border-subdued">
                            <BlockStack gap="1">
                                <Text color="subdued" variant="headingSm">
                                    {item.title}
                                </Text>
                                {item?.isComp ? item.data :<Text variant={item.variant ? item.variant : 'bodyMd'} fontWeight="semibold" color={item.color ? item.color :""}>
                                    {item.data}
                                </Text>
                                }
                            </BlockStack>
                        </Box>
                    ))}
                </InlineGrid>
            </Box>
        </Card>
    );
}

export default SummaryCardInfo
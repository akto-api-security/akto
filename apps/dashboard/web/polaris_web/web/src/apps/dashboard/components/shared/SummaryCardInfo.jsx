import { Box, Card, InlineGrid, Text, BlockStack } from '@shopify/polaris'
import React from 'react'

function SummaryCardInfo({summaryItems}) {
    return (
        <Card padding={0} key="info">
            <Box padding={200} paddingInlineStart={400} paddingInlineEnd={400}>
                <InlineGrid columns={summaryItems.length} gap={400}>
                    {summaryItems.map((item, index) => (
                        <Box borderInlineEndWidth={index < (summaryItems.length - 1) ? "1" : ""} key={index} paddingBlockStart={100} paddingBlockEnd={100} borderColor="border-secondary">
                            <BlockStack gap="100">
                                <Text tone="subdued" variant="headingSm">
                                    {item.title}
                                </Text>
                                {item?.isComp ? item.data :<Text variant={item.variant ? item.variant : 'bodyMd'} fontWeight="semibold" tone={item.color ? item.color :""}>
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
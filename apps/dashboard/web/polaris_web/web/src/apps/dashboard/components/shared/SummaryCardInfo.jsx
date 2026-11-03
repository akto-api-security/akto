import { Box, Card, HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import Pressable from '../../pages/dashboard/ask/components/Pressable'

function SummaryCardInfo({summaryItems}) {
    return (
        <Card padding={0} key="info">
            <Box padding={2} paddingInlineStart={4} paddingInlineEnd={4}>
                <HorizontalGrid columns={summaryItems.length} gap={4}>
                    {summaryItems.map((item, index) => {
                        const content = (
                            <VerticalStack gap="1">
                                <Text color="subdued" variant="headingXs">
                                    {item.title}
                                </Text>
                                {item?.isComp ? item.data :<Text variant={item.variant ? item.variant : 'bodyMd'} fontWeight="semibold" color={item.color ? item.color :""}>
                                    {item.data}
                                </Text>
                                }
                            </VerticalStack>
                        )
                        const cellProps = { borderInlineEndWidth: index < (summaryItems.length - 1) ? "1" : "", paddingBlockStart: 1, paddingBlockEnd: 1, borderColor: "border-subdued" }
                        return item.onClick ? (
                            <Pressable key={index} onClick={item.onClick} accessibilityLabel={String(item.title)} hoverBackground="bg-subdued" {...cellProps}>
                                {content}
                            </Pressable>
                        ) : (
                            <Box key={index} {...cellProps}>
                                {content}
                            </Box>
                        )
                    })}
                </HorizontalGrid>
            </Box>
        </Card>
    )
}

export default SummaryCardInfo
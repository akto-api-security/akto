import { Box, Card, HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import Pressable from '../../pages/dashboard/ask/components/Pressable'

function ClickableSummaryCard({summaryItems}) {
    return (
        <Card padding={0} key="info">
            <Box overflowX="hidden" overflowY="hidden">
                <HorizontalGrid columns={summaryItems.length} gap={0}>
                    {summaryItems.map((item, index) => {
                        const cellProps = {
                            borderInlineEndWidth: index < (summaryItems.length - 1) ? "1" : "",
                            borderColor: "border-subdued",
                            paddingBlockStart: 3,
                            paddingBlockEnd: 3,
                            paddingInlineStart: 4,
                            paddingInlineEnd: 4,
                        }
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
                        const isFilter = item.selected !== undefined
                        return item.onClick ? (
                            <Pressable
                                key={index}
                                onClick={item.onClick}
                                accessibilityLabel={String(item.title)}
                                background={item.selected ? "bg-magic-hover" : undefined}
                                hoverBackground={isFilter ? (item.selected ? "bg-magic-hover" : "bg-magic-subdued") : "bg-subdued"}
                                {...cellProps}
                            >
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

function SummaryCardInfo({summaryItems}) {
    if (summaryItems.some((item) => item.onClick)) return <ClickableSummaryCard summaryItems={summaryItems} />
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

import { Box, Card, InlineGrid, InlineStack, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'

function SummaryCard({ summaryItems }) {
    return (
        <Card padding={0} key="info">
            <Box padding={3} paddingInlineStart={5} paddingInlineEnd={5}>
                <InlineGrid columns={summaryItems.length} gap={4}>
                    {summaryItems.map((item, index) => (
                        <Box borderInlineEndWidth={index < (summaryItems.length - 1) ? "1" : ""} key={index} borderColor="transparent">
                            <InlineStack>
                                <BlockStack gap="4">
                                    <TitleWithInfo
                                        titleComp={
                                        <Text variant="headingMd">
                                            {item.title}
                                        </Text>
                                        }
                                        docsUrl={item?.docsUrl}
                                        tooltipContent={item?.tooltipContent}
                                    />
                                    <InlineGrid gap={1} columns={2}>
                                        <BlockStack gap={4}>
                                            {item?.isComp ? item.data : 
                                            <div className='custom-color'>
                                                <Text variant={item.variant ? item.variant : 'bodyLg'} color={item.color ? item.color : ""}>
                                                    {item.data}
                                                </Text>
                                            </div>
                                            }
                                            {item.byLineComponent ? item.byLineComponent : null}
                                        </BlockStack>
                                        {item.smoothChartComponent ? item.smoothChartComponent : null}
                                    </InlineGrid>
                                </BlockStack>
                            </InlineStack>
                        </Box>
                    ))}
                </InlineGrid>
            </Box>
        </Card>
    );
}

export default SummaryCard
import { Badge, Box, Button, Card, InlineGrid, InlineStack, LegacyCard, Link, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import TooltipText from '../../../components/shared/TooltipText'

function TestSummaryCard({ summaryItem }) {
    return (
        <LegacyCard>
            <div style={{cursor: 'pointer'}} onClick={() => window.open(summaryItem.link, "_blank")}>
                <Box padding={5} maxWidth='286px' minWidth='286px' minHeight='164px'>
                    <BlockStack gap={8}>
                        <BlockStack gap={2}>
                            <Box maxWidth='260px'>
                                <TooltipText tooltip={summaryItem.testName} text={summaryItem.testName} textProps={{fontWeight: "semibold"}}/>
                            </Box>
                            <Text variant="headingSm" color='subdued' fontWeight='regular'>{summaryItem.time}</Text>
                        </BlockStack>
                        <InlineStack align='space-between'>
                            <BlockStack gap={2}>
                                <Text color='subdued' variant="headingSm" fontWeight='semibold'>Issues Found</Text>
                                <InlineStack gap={2}>
                                    <Badge size="small" tone="critical">{summaryItem.highCount}</Badge>
                                    <Badge size="small" tone="warning">{summaryItem.mediumCount}</Badge>
                                    <Badge size="small" tone="info">{summaryItem.lowCount}</Badge>
                                </InlineStack>
                            </BlockStack>
                            <BlockStack gap={2}>
                                <Text color='subdued' variant="headingSm" fontWeight='semibold'>APIs Tested</Text>
                                <Text variant='headingSm' fontWeight='medium'>{summaryItem.totalApis}</Text>
                            </BlockStack>
                        </InlineStack>
                    </BlockStack>
                </Box>
            </div>
        </LegacyCard>
    );
}

export default TestSummaryCard
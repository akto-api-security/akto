import { Badge, Box, Card, HorizontalGrid, HorizontalStack, LegacyCard, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function TestSummaryCard({ summaryItem }) {
    return (
        <LegacyCard>
            <Box padding={5} maxWidth='286px' minWidth='286px' minHeight='164px'>
                <VerticalStack gap={6}>
                    <VerticalStack gap={2}>
                        <Box>
                            <Text variant='headingSm'>{summaryItem.testName}</Text>
                        </Box>
                        <Text variant='headingXs' color='subdued' fontWeight='regular'>{summaryItem.time}</Text>
                    </VerticalStack>
                    <HorizontalStack align='space-between'>
                        <VerticalStack gap={2}>
                            <Text color='subdued' variant='headingXs' fontWeight='semibold'>Issues Found</Text>
                            <HorizontalStack gap={2}>
                                <Badge size="small" status="critical">{summaryItem.highCount}</Badge>
                                <Badge size="small" status="warning">{summaryItem.mediumCount}</Badge>
                                <Badge size="small" status="info">{summaryItem.lowCount}</Badge>
                            </HorizontalStack>
                        </VerticalStack>
                        <VerticalStack gap={2}>
                            <Text color='subdued' variant='headingXs' fontWeight='semibold'>APIs Tested</Text>
                            <Text variant='headingSm' fontWeight='medium'>{summaryItem.totalApis}</Text>
                        </VerticalStack>
                    </HorizontalStack>

                </VerticalStack>
            </Box>
        </LegacyCard>
    )
}

export default TestSummaryCard
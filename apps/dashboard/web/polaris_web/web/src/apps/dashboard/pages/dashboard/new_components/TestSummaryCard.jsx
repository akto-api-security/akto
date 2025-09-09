import { Badge, Box, Button, Card, HorizontalGrid, HorizontalStack, LegacyCard, Link, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import TooltipText from '../../../components/shared/TooltipText'
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper'

function TestSummaryCard({ summaryItem }) {
    const severityLevels = [
        { level: 'CRITICAL', count: summaryItem.criticalCount },
        { level: 'HIGH', count: summaryItem.highCount },
        { level: 'MEDIUM', count: summaryItem.mediumCount },
        { level: 'LOW', count: summaryItem.lowCount },
    ]

    return (
        <LegacyCard>
            <div style={{cursor: 'pointer'}} onClick={() => window.open(summaryItem.link, "_blank")}>
                <Box padding={5} maxWidth='286px' minWidth='286px' minHeight='164px'>
                    <VerticalStack gap={8}>
                        <VerticalStack gap={2}>
                            <Box maxWidth='260px'>
                                <TooltipText tooltip={summaryItem.testName} text={summaryItem.testName} textProps={{fontWeight: "semibold"}}/>
                            </Box>
                            <Text variant='headingXs' color='subdued' fontWeight='regular'>{summaryItem.time}</Text>
                        </VerticalStack>
                        <HorizontalStack align='space-between'>
                            <VerticalStack gap={2}>
                                <Text color='subdued' variant='headingXs' fontWeight='semibold'>Issues Found</Text>
                                <HorizontalStack gap={2}>
                                    {severityLevels.map(({ level, count }) => (
                                        <div key={level} className={`badge-wrapper-${level}`}>
                                            <Badge size="small">{count}</Badge>
                                        </div>
                                    ))}
                                </HorizontalStack>
                            </VerticalStack>
                            <VerticalStack gap={2}>
                                <Text color='subdued' variant='headingXs' fontWeight='semibold'>{mapLabel('APIs Tested', getDashboardCategory())}</Text>
                                <Text variant='headingSm' fontWeight='medium'>{summaryItem.totalApis}</Text>
                            </VerticalStack>
                        </HorizontalStack>

                    </VerticalStack>
                </Box>
            </div>
        </LegacyCard>
    )
}

export default TestSummaryCard
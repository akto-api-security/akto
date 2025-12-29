import { Box, HorizontalStack, Link, Text, VerticalStack } from "@shopify/polaris"

const BaseReportFooter = ({
    assessmentType = "API Security",
    leftBadgeLabel = "DAST",
    rightBadgeLabel = "API SECURITY"
}) => {
    return (
        <>
            <Box paddingBlockStart={6} paddingBlockEnd={6} paddingInlineStart={5} paddingInlineEnd={5}>
                <div className='badges-info-container'>
                    <Text color='subdued' alignment='center'>
                        Assessment conducted using Akto's {assessmentType} Platform<br />
                        G2 High Performer in {rightBadgeLabel.split(' ')[0]} Security - 2024
                    </Text>
                </div>

                <Box width='100%' paddingBlockStart={4}>
                    <HorizontalStack gap={4} align='center'>
                        <VerticalStack gap={2} align='center' inlineAlign='center'>
                            <Text fontWeight='semibold' color='subdued'>{leftBadgeLabel}</Text>
                            <Link url='https://www.g2.com/products/akto/reviews' target='_blank' removeUnderline>
                                <img src='/public/g2-badge-2.png' alt={`G2 ${leftBadgeLabel} Badge`} className='g2-badge' />
                            </Link>
                        </VerticalStack>

                        <VerticalStack gap={2} align='center' inlineAlign='center'>
                            <Text>{"\u2008"}</Text>
                            <Link url='https://www.g2.com/products/akto/reviews' target='_blank' removeUnderline>
                                <img src='/public/g2-badge-1.png' alt="G2 Badge" className='g2-badge' />
                            </Link>
                        </VerticalStack>

                        <VerticalStack gap={2} align='center' inlineAlign='center'>
                            <Text fontWeight='semibold' color='subdued'>{rightBadgeLabel}</Text>
                            <Link url='https://www.g2.com/products/akto/reviews' target='_blank' removeUnderline>
                                <img src='/public/g2-badge-2.png' alt={`G2 ${rightBadgeLabel} Badge`} className='g2-badge' />
                            </Link>
                        </VerticalStack>
                    </HorizontalStack>
                </Box>
            </Box>

            <div className='report-end-image-container'>
                <img src="/public/vul_report_bg.svg" alt="Footer Background" className='report-end-image' />
                <div className='report-end-image-text'>The End.</div>
            </div>
        </>
    )
}

export default BaseReportFooter

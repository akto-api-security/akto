import { Box, List, Text, VerticalStack } from '@shopify/polaris'
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'

const nextStepItems = (dashboardCategory) => [
    {
        title: "Immediate Response Actions",
        content: [
            `Block or rate-limit high-risk ${mapLabel("threat", dashboardCategory).toLowerCase()} actors immediately`,
            "Implement Web Application Firewall (WAF) rules for detected attack patterns",
            `Review and patch vulnerable endpoints identified in ${mapLabel("threat", dashboardCategory).toLowerCase()} analysis`,
            `Enable enhanced monitoring for targeted ${mapLabel("APIs", dashboardCategory)}`
        ]
    },
    {
        title: `Ongoing ${mapLabel("Threat", dashboardCategory)} Detection Measures`,
        content: [
            `Enable continuous real-time ${mapLabel("threat", dashboardCategory).toLowerCase()} detection and alerting`,
            `Integrate ${mapLabel("threat", dashboardCategory).toLowerCase()} intelligence feeds for proactive defense`,
            `Review ${mapLabel("API", dashboardCategory)} authentication and authorization mechanisms`,
            "Implement rate limiting and request throttling",
            `Maintain updated ${mapLabel("API", dashboardCategory)} inventory and attack surface visibility`
        ]
    }
]

const ThreatReportConclusion = () => {
    const dashboardCategory = getDashboardCategory()

    return (
        <Box id="threat-report-conclusion" paddingBlockStart={6} paddingBlockEnd={6} paddingInlineStart={5} paddingInlineEnd={5}>
            <VerticalStack gap="4">
                <Text variant="headingLg">3. Conclusion and Next Steps</Text>
                <VerticalStack gap="3">
                    <Text variant="bodyMd" color='subdued'>
                        This {mapLabel("threat", dashboardCategory).toLowerCase()} detection assessment was conducted using Akto's continuous {mapLabel("API", dashboardCategory)} {mapLabel("threat", dashboardCategory).toLowerCase()} detection platform, which identified and analyzed malicious activities targeting your {mapLabel("API endpoints", dashboardCategory)}. The successful attack attempts detected during this period provide valuable insights into your current {mapLabel("threat", dashboardCategory).toLowerCase()} landscape and security posture.
                    </Text>
                    <VerticalStack gap="4">
                        {nextStepItems(dashboardCategory).map((item, index) => {
                            return (
                                <Box key={index}>
                                    <Text color='subdued' fontWeight='semibold'>{index + 1}. {item.title}</Text>
                                    <Box paddingInlineStart={4} paddingBlockStart={2}>
                                        {
                                            item.content.map((content, contentIndex) => {
                                                return (
                                                    <List.Item key={content + contentIndex}>
                                                        <span className='subdued-color-text'>{content}</span>
                                                    </List.Item>
                                                )
                                            })
                                        }
                                    </Box>
                                </Box>
                            )
                        })}
                    </VerticalStack>
                    <Text color='subdued'>
                        Akto's platform will continue to monitor your {mapLabel("APIs", dashboardCategory)} for {mapLabel("threat", dashboardCategory).toLowerCase()} activities in real-time, identifying suspicious patterns and blocking malicious actors. Regular {mapLabel("threat", dashboardCategory).toLowerCase()} assessments and proactive security measures will help maintain strong protection across your {mapLabel("API", dashboardCategory)} landscape.
                    </Text>
                </VerticalStack>
            </VerticalStack>
        </Box>
    )
}

export default ThreatReportConclusion

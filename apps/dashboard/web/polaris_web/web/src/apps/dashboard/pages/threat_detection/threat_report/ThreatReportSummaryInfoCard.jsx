import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"

function ThreatReportSummaryInfoCard({ summaryItems }) {

    return (
        <HorizontalStack gap="4">
            {summaryItems.map((item, index) => {
                if(item?.visible === false) return null
                return (
                    <Box key={index} minWidth="200px">
                        <Card>
                            <VerticalStack gap="2">
                                <Text color="subdued" variant='headingXs'>{item.title}</Text>
                                {item?.isComp ? item.data : <Text variant='headingLg'>{item.data}</Text>}
                            </VerticalStack>
                        </Card>
                    </Box>
                )
            })}
        </HorizontalStack>
    )
}

export default ThreatReportSummaryInfoCard

import { Box, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import PropTypes from "prop-types"

export const ThreatSummary = ({criticalActors, activeActors, blockedActors, attackSuccessRate}) => {
    return (
        <Card padding="0">
            <Box padding={3} paddingInlineEnd={4} paddingInlineStart={4}>
                <HorizontalGrid columns={4} gap={5}>
                    <Box>
                        <VerticalStack gap={5}>
                            <TitleWithInfo
                                tooltipContent="Critical Actors"
                                titleText="Critical Actors"
                                textProps={{variant: 'headingMd'}}
                            />
                            <HorizontalGrid columns={2} gap={4}>
                                <VerticalStack gap={4}>
                                    <Text variant="heading2xl" color="critical" as="h3">6</Text>
                                </VerticalStack>
                            </HorizontalGrid>
                        </VerticalStack>
                    </Box>
                    <Box>
                        <VerticalStack gap={5}>
                            <TitleWithInfo
                                tooltipContent="Active Actors"
                                titleText="Active Actors"
                                textProps={{variant: 'headingMd'}}
                            />
                            <HorizontalGrid columns={2} gap={4}>
                                <VerticalStack gap={4}>
                                    <Text variant="heading2xl" as="h3">6</Text>
                                </VerticalStack>
                            </HorizontalGrid>
                        </VerticalStack>
                    </Box>
                    <Box>
                        <VerticalStack gap={5}>
                            <TitleWithInfo
                                tooltipContent="Blocked Actors"
                                titleText="Blocked Actors"
                                textProps={{variant: 'headingMd'}}
                            />
                            <HorizontalGrid columns={2} gap={4}>
                                <VerticalStack gap={4}>
                                    <Text variant="heading2xl" color="success" as="h3">6</Text>
                                </VerticalStack>
                            </HorizontalGrid>
                        </VerticalStack>
                    </Box>
                    <Box>
                        <VerticalStack gap={5}>
                            <TitleWithInfo
                                tooltipContent="Attack Success Rate"
                                titleText="Attack Success Rate"
                                textProps={{variant: 'headingMd'}}
                            />
                            <HorizontalGrid columns={2} gap={4}>
                                <VerticalStack gap={4}>
                                    <Text variant="heading2xl" color="warning" as="h3">6</Text>
                                </VerticalStack>
                            </HorizontalGrid>
                        </VerticalStack>
                    </Box>
                </HorizontalGrid>
            </Box>
        </Card>
    )
}

ThreatSummary.propTypes = {
    criticalActors: PropTypes.number.isRequired,
    activeActors: PropTypes.number.isRequired,
    blockedActors: PropTypes.number.isRequired,
    attackSuccessRate: PropTypes.number.isRequired,
}
import { Box, VerticalStack, HorizontalStack, Text, Divider, Grid, HorizontalGrid, LegacyCard } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout"
import { ActivityLog } from "./ActivityLog"

export const ActorDetails = ({ actorDetails, setShowActorDetails }) => {
    const ThreatActorHeader = () => {
        return (
            <Box borderColor="border" padding={"4"} paddingBlockStart={"0"} paddingInlineStart={"0"}>
               <VerticalStack gap={"2"}>
                    <Text variant="bodyMd" fontWeight="medium">{actorDetails.id}</Text>
                    <HorizontalStack gap={"2"}>
                        <Text variant="bodySm" color="subdued">Status: Active</Text>
                        <Text variant="bodySm" color="subdued">|</Text>
                        <Text variant="bodySm" color="subdued">Discovered: {actorDetails.discoveredAt}</Text>
                    </HorizontalStack>
               </VerticalStack>
            </Box>
        )
    }

    const overviewComp = (
       <Box padding={"4"}>
            <HorizontalGrid columns={3} gap={"4"}>
                    <VerticalStack gap={"2"}>
                        <Text fontWeight="bold" variant="bodySm">Status</Text>
                        <Text variant="bodyMd">Active</Text>
                    </VerticalStack>
                    <VerticalStack gap={"2"}>
                        <Text fontWeight="bold" variant="bodySm">Discovered</Text>
                        <Text variant="bodyMd">{actorDetails.discoveredAt}</Text>
                    </VerticalStack>
            </HorizontalGrid>
       </Box>
    )

    const activityComp = (
        <ActivityLog activityLog={actorDetails.activity} actorDetails={actorDetails} />
    )

    const components = [
        <ThreatActorHeader />,
        // <Divider />,
        <ActivityLog activityLog={actorDetails.activity} actorDetails={actorDetails} />
    ]
    return (
        <FlyLayout
            title="Threat actor details"
            show={true}
            setShow={setShowActorDetails}
            components={components}
        />
    )
}
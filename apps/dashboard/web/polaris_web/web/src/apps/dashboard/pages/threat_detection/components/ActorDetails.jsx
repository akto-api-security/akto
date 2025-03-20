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
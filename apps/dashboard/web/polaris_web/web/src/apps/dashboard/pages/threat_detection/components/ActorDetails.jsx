import { Box, VerticalStack, HorizontalStack, Text, Divider, Grid, HorizontalGrid, LegacyCard } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout"
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import { ActivityLog } from "./ActivityLog"

export const ActorDetails = ({ actorDetails, setShowActorDetails }) => {
    const ThreatActorHeader = () => {
        return (
            <Box padding={"0"} paddingBlockStart={"0"} borderColor="border">
                <HorizontalStack wrap={false} align="space-between" gap={"6"}>
                    <Text variant="bodyMd" fontWeight="medium">{actorDetails.id}</Text>
                </HorizontalStack>
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

    const overviewTab = {
        id: "overview",
        content: 'Overview',
        component: overviewComp
    }

    const activityTab = {
        id: "activity",
        content: `Activity Log - ${actorDetails.activity.length}`,
        component: activityComp
    }

    const components = [
        <ThreatActorHeader />,
        <Divider />,
        <LayoutWithTabs
            tabs={[overviewTab, activityTab]}
            currTab={() => {}}
        />
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
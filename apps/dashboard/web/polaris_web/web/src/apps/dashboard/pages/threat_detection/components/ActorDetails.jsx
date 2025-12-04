import { useState } from "react"
import { Box, VerticalStack, HorizontalStack, Text, Divider, Grid, HorizontalGrid, LegacyCard, Modal, Button } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout"
import { ActivityLog } from "./ActivityLog";
import Store from "../../../store";
import api from "../api";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";
export const ActorDetails = ({ actorDetails, setShowActorDetails }) => {
    const [ipStatus, setIpStatus] = useState(actorDetails.status || "active")
    const [showModal, setShowModal] = useState(false)

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }
    const handleBlockUnblockIp = async (status) => {
        try {
            const res = await api.modifyThreatActorStatus(actorDetails.latestApiIp, status)
            setIpStatus(status)
            setToast(true, false, "Successfully updated IP status")
            setShowModal(false)
        } catch (error) {
            console.error(error)
            setToast(true, true, "Failed to block IP")
        }
    }

    const ThreatActorHeader = () => {
        return (
            <Box borderColor="border" padding={"4"} paddingBlockStart={"0"} paddingInlineStart={"0"} paddingInlineEnd={"0"}>
               <HorizontalStack align="space-between" blockAlign="center">
                <VerticalStack gap={"2"}>
                        <Text variant="bodyMd" fontWeight="medium">{actorDetails.id}</Text>
                        <HorizontalStack gap={"2"}>
                            <Text variant="bodySm" color="subdued">Status: {ipStatus.charAt(0).toUpperCase() + ipStatus.slice(1)}</Text>
                            <Text variant="bodySm" color="subdued">|</Text>
                            <Text variant="bodySm" color="subdued">Discovered: {actorDetails.discoveredAt}</Text>
                        </HorizontalStack>
                </VerticalStack>
                <Modal
                    activator={<Button destructive={ipStatus.toLowerCase() === "active"} size="slim" onClick={() => setShowModal(!showModal)}>{ipStatus.toLowerCase() === "active" ? "Block IP" : "Unblock IP"}</Button>}
                    open={showModal}
                    onClose={() => setShowModal(false)}
                    primaryAction={
                        {
                            content: ipStatus.toLowerCase() === "active" ? "Block IP" : "Unblock IP",
                            onAction: () => handleBlockUnblockIp(ipStatus.toLowerCase() === "active" ? "blocked" : "active"),
                            disabled: window.IS_AWS_WAF_INTEGRATED === 'false' && window.IS_CLOUDFLARE_WAF_INTEGRATED === 'false',
                        }
                    }
                    title={ipStatus.toLowerCase() === "active" ? "Block IP" : "Unblock IP"}
                >
                    <Modal.Section>
                        <Text variant="bodyMd" color="subdued">
                            {ipStatus.toLowerCase() === "active" ?
                            `Are you sure you want to block this IP - ${actorDetails.latestApiIp}?` :
                            `Are you sure you want to unblock this IP - ${actorDetails.latestApiIp}?`}
                        </Text>
                    </Modal.Section>
                </Modal>
               </HorizontalStack>
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
            title={`${mapLabel("Threat", getDashboardCategory())} actor details`}
            show={true}
            setShow={setShowActorDetails}
            components={components}
        />
    )
}
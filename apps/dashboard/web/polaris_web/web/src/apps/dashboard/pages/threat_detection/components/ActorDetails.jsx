import { useState, useEffect } from "react"
import { Box, VerticalStack, HorizontalStack, Text, Divider, Grid, HorizontalGrid, LegacyCard, Modal, Button, Spinner } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout"
import { ActivityLog } from "./ActivityLog";
import Store from "../../../store";
import api from "../api";
import { getDashboardCategory, mapLabel, isEndpointSecurityCategory } from "../../../../main/labelHelper";
import { usePermissions } from "@/util/permissions";
import AllowedAction from "../../../components/shared/AllowedAction";
export const ActorDetails = ({ actorDetails, setShowActorDetails, onStatusChange }) => {
    const { canCall } = usePermissions();
    const [ipStatus, setIpStatus] = useState(actorDetails.status || "ACTIVE")
    const [showModal, setShowModal] = useState(false)
    const isBlocked = ipStatus === "BLOCKED"
    const [activityLog, setActivityLog] = useState([])
    const [loadingActivity, setLoadingActivity] = useState(true)

    const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }

    useEffect(() => {
        const fetchActivityData = async () => {
            setLoadingActivity(true)
            try {
                const res = await api.fetchThreatsForActor(actorDetails.id)
                setActivityLog(res?.actorActivities || [])
            } catch (error) {
                console.error("Error fetching activity data:", error)
                setToast(true, true, "Failed to load activity data")
                setActivityLog([])
            } finally {
                setLoadingActivity(false)
            }
        }
        fetchActivityData()
    }, [actorDetails.id])

    const handleBlockUnblockIp = async (status) => {
        try {
            const res = await api.modifyThreatActorStatus(actorDetails.latestApiIp, status)
            setIpStatus(status)
            onStatusChange()
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
                            <Text variant="bodySm" color="subdued">Status: {isBlocked ? "Blocked" : "Active"}</Text>
                            <Text variant="bodySm" color="subdued">|</Text>
                            <Text variant="bodySm" color="subdued">Discovered: {actorDetails.discoveredAt}</Text>
                        </HorizontalStack>
                </VerticalStack>
                {!isEndpointSecurityCategory() && (
                    <Modal
                        activator={<AllowedAction allowed={canCall('api/modifyThreatActorStatus')}><Button destructive={!isBlocked} size="slim" onClick={() => setShowModal(!showModal)}>{isBlocked ? "Unblock IP" : "Block IP"}</Button></AllowedAction>}
                        open={showModal}
                        onClose={() => setShowModal(false)}
                        primaryAction={
                            {
                                content: isBlocked ? "Unblock IP" : "Block IP",
                                onAction: () => handleBlockUnblockIp(isBlocked ? "ACTIVE" : "BLOCKED"),
                                disabled: window.IS_AWS_WAF_INTEGRATED === 'false' && window.IS_CLOUDFLARE_WAF_INTEGRATED === 'false',
                            }
                        }
                        title={isBlocked ? "Unblock IP" : "Block IP"}
                    >
                        <Modal.Section>
                            <Text variant="bodyMd" color="subdued">
                                {isBlocked ?
                                `Are you sure you want to unblock this IP - ${actorDetails.latestApiIp}?` :
                                `Are you sure you want to block this IP - ${actorDetails.latestApiIp}?`}
                            </Text>
                        </Modal.Section>
                    </Modal>
                )}
               </HorizontalStack>
            </Box>
        )
    }

    const components = [
        <ThreatActorHeader />,
        <ActivityLog activityLog={activityLog} actorDetails={actorDetails} loading={loadingActivity} />
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
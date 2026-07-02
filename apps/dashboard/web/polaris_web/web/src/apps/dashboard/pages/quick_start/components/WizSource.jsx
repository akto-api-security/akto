import { Badge, Banner, Box, Button, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import api from "../api"
import func from "@/util/func"

function WizSource() {
    const navigate = useNavigate()
    const [hasIntegration, setHasIntegration] = useState(false)
    const [wizImportApiEndpointsJob, setWizImportApiEndpointsJob] = useState(null)
    const [deltaEndpointCount, setDeltaEndpointCount] = useState(null)
    const [isAddingWizTrafficSource, setIsAddingWizTrafficSource] = useState(false)

    async function fetchWizIntegration() {
        try {
            const response = await api.fetchWizIntegration()
            const wizIntegration = response?.wizIntegration
            setHasIntegration(!!wizIntegration)
            if (wizIntegration) {
                setWizImportApiEndpointsJob(response?.wizImportApiEndpointsJob || null)
                setDeltaEndpointCount(wizIntegration.wizImportApiEndpointsJobDeltaEndpointCount ?? null)
            }
        } catch {
            setHasIntegration(false)
        }
    }

    useEffect(() => {
        fetchWizIntegration()
    }, [])

    async function addWizTrafficSource() {
        setIsAddingWizTrafficSource(true)
        try {
            await api.addWizTrafficSource()
            func.setToast(true, false, "Added Wiz traffic source successfully")
            fetchWizIntegration()
        } catch (error) {
            func.setToast(true, true, error?.response?.data?.actionErrors?.[0] || "Failed to add Wiz traffic source.")
        } finally {
            setIsAddingWizTrafficSource(false)
        }
    }

    async function removeWizTrafficSource() {
        func.showConfirmationModal(
            "Are you sure you want to remove the Wiz traffic source? The import job will be stopped and you will need to reconnect to resume syncing.",
            "Remove",
            async () => {
                try {
                    await api.removeWizTrafficSource()
                    func.setToast(true, false, "Wiz traffic source removed successfully")
                    fetchWizIntegration()
                } catch {
                    func.setToast(true, true, "Failed to remove Wiz traffic source")
                }
            }
        )
    }

    const jobStatusToBadgeTone = (status) => {
        switch (status) {
            case 'RUNNING': return 'info'
            case 'SCHEDULED': return 'success'
            case 'COMPLETED': return 'success'
            case 'FAILED': return 'critical'
            default: return 'warning'
        }
    }

    if (!hasIntegration) {
        return (
            <Box paddingBlockStart="4">
                <Banner
                    title="Wiz not integrated"
                    status="warning"
                    action={{ content: 'Go to Wiz settings', onAction: () => navigate("/dashboard/settings/integrations/wiz") }}
                >
                    <p>Integrate Wiz first, then return to this page to connect it as a traffic source.</p>
                </Banner>
            </Box>
        )
    }

    return (
        <Box paddingBlockStart="4">
            <VerticalStack gap={4}>
                {wizImportApiEndpointsJob ? (
                    <VerticalStack gap={3}>
                        <VerticalStack gap={2}>
                            <HorizontalStack gap={2} blockAlign="center">
                                <Text variant="bodyMd" fontWeight="semibold">Import job status</Text>
                                <Badge status={jobStatusToBadgeTone(wizImportApiEndpointsJob.jobStatus)}>
                                    {func.toSentenceCase(wizImportApiEndpointsJob.jobStatus)}
                                </Badge>
                                {wizImportApiEndpointsJob.jobStatus === 'RUNNING' && wizImportApiEndpointsJob.startedAt > 0 && (
                                    <Text tone="subdued">
                                        Running since {func.epochToDateTime(wizImportApiEndpointsJob.startedAt)}
                                        {wizImportApiEndpointsJob.heartbeatAt > 0 && ` - last heartbeat ${func.epochToDateTime(wizImportApiEndpointsJob.heartbeatAt)}`}
                                    </Text>
                                )}
                            </HorizontalStack>
                            <VerticalStack gap={1}>
                                {wizImportApiEndpointsJob.finishedAt > 0 && (
                                    <Text tone="subdued">
                                        Last run: {func.epochToDateTime(wizImportApiEndpointsJob.finishedAt)}
                                        {deltaEndpointCount != null ? ` - ${deltaEndpointCount} endpoints discovered / updated` : ''}
                                    </Text>
                                )}
                                {wizImportApiEndpointsJob.jobStatus === 'SCHEDULED' && wizImportApiEndpointsJob.scheduledAt > 0 && (
                                    <Text tone="subdued">Next run: {func.epochToDateTime(wizImportApiEndpointsJob.scheduledAt)}</Text>
                                )}
                            </VerticalStack>
                        </VerticalStack>
                        <Divider />
                        <Button tone="critical" fullWidth onClick={removeWizTrafficSource}>
                            Remove traffic source
                        </Button>
                    </VerticalStack>
                ) : (
                    <VerticalStack gap={3}>
                        <Text variant="bodyMd">
                            Connect Wiz as a traffic source to start importing your API endpoints into Akto's inventory. Once connected, Akto will periodically sync endpoints from Wiz to keep your inventory up to date.
                        </Text>
                        <Button onClick={addWizTrafficSource} loading={isAddingWizTrafficSource} disabled={isAddingWizTrafficSource}>Connect</Button>
                    </VerticalStack>
                )}
            </VerticalStack>
        </Box>
    )
}

export default WizSource

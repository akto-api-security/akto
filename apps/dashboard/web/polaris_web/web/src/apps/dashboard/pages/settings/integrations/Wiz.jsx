import { Badge, Button, HorizontalStack, LegacyCard, Text, TextField, VerticalStack, Box } from "@shopify/polaris"
import IntegrationsLayout from "./IntegrationsLayout"
import PasswordTextField from "@/apps/dashboard/components/layouts/PasswordTextField"
import { useEffect, useState } from "react"
import settingFunctions from "../module"
import func from "@/util/func"

function Wiz() {

    const [tenantDataCenter, setTenantDataCenter] = useState('')
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [isSaving, setIsSaving] = useState(false)
    const [isRemoveable, setIsRemoveable] = useState(false)

    const [wizImportApiEndpointsJob, setWizImportApiEndpointsJob] = useState(null)
    const [deltaEndpointCount, setDeltaEndpointCount] = useState(null)
    const [isAddingWizTrafficSource, setIsAddingWizTrafficSource] = useState(false)

    const resetFields = () => {
        setTenantDataCenter('')
        setClientId('')
        setClientSecret('')
        setIsRemoveable(false)
        setWizImportApiEndpointsJob(null)
        setDeltaEndpointCount(null)
    }

    async function fetchWizIntegration() {

        try {
            let response = await settingFunctions.fetchWizIntegration()

            const wizIntegration = response?.wizIntegration
            const wizImportApiEndpointsJob = response?.wizImportApiEndpointsJob

            // Check if integration exists (has tenantDataCenter and clientId)
            const hasIntegration = wizIntegration && wizIntegration.tenantDataCenter && wizIntegration.clientId

            if (hasIntegration) {
                setTenantDataCenter(wizIntegration.tenantDataCenter || '')
                setClientId(wizIntegration.clientId || '')
                setClientSecret(null) // Don't show existing secret
                setIsRemoveable(true)
                setWizImportApiEndpointsJob(wizImportApiEndpointsJob || null)
                setDeltaEndpointCount(wizIntegration.wizImportApiEndpointsJobDeltaEndpointCount ?? null)
            } else {
                // No integration exists - reset everything
                resetFields()
            }
        } catch (error) {
            func.setToast(true, true, 'Error fetching Wiz integration:' + error)
            // Reset on error
            resetFields()
        }
    }

    useEffect(() => {
        fetchWizIntegration()
    }, [])

    async function addWizIntegration(){
        if (!tenantDataCenter?.trim() || !clientId?.trim() || !clientSecret?.trim()) {
            func.setToast(true, true, "Please fill all required fields")
            return
        }

        setIsSaving(true)
        try {
            await settingFunctions.addWizIntegration(tenantDataCenter, clientId, clientSecret)
            func.setToast(true, false, "Successfully added Wiz Integration")
            fetchWizIntegration()
        } catch (error) {
            func.setToast(true, true, error?.response?.data?.actionErrors?.[0] || "Failed to add Wiz Integration")
        } finally {
            setIsSaving(false)
        }
    }

    async function removeWizTrafficSource() {
        func.showConfirmationModal(
            "Are you sure you want to remove the Wiz traffic source? The import job will be stopped and you will need to reconnect to resume syncing.",
            "Remove",
            async () => {
                try {
                    await settingFunctions.removeWizTrafficSource()
                    func.setToast(true, false, "Wiz traffic source removed successfully")
                    fetchWizIntegration()
                } catch (error) {
                    func.setToast(true, true, "Failed to remove Wiz traffic source")
                }
            }
        )
    }

    async function addWizTrafficSource() {
        setIsAddingWizTrafficSource(true)
        try {
            await settingFunctions.addWizTrafficSource()
            func.setToast(true, false, "Added Wiz traffic source successfully")
            fetchWizIntegration()
        } catch (error) {
            func.setToast(true, true, error?.response?.data?.actionErrors?.[0] || "Failed to add Wiz traffic source.")
        } finally {
            setIsAddingWizTrafficSource(false)
        }
    }

    async function removeWizIntegration() {
        setIsSaving(true)
        try {
            await settingFunctions.removeWizIntegration()
            func.setToast(true, false, "Successfully removed Wiz Integration")
            resetFields()
        } catch (error) {
            func.setToast(true, true, "Failed to remove Wiz Integration")
        } finally {
            setIsSaving(false)
        }
    }

    const isSaveDisabled = () => {
        return isSaving || !tenantDataCenter?.trim() || !clientId?.trim() ||
               (clientSecret === null ? true : !clientSecret?.trim())
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

    const cardContent = "Seamlessly enhance your API security posture with Akto's Wiz integration. Connect Wiz as a traffic source to periodically import your API endpoints into Akto's inventory, and enrich your Wiz dashboard with API vulnerabilities discovered by Akto."

    const wizCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : 'Save',
                onAction: addWizIntegration,
                disabled: isSaveDisabled(),
                loading: isSaving
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeWizIntegration,
                disabled: !isRemoveable || isSaving
            }]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate Wiz</Text>
            </LegacyCard.Section>

            <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <TextField
                        label="Tenant Data Center"
                        value={tenantDataCenter}
                        helpText="Specify your Wiz regional tenant data center (e.g., us1, us2, eu1, eu2)"
                        placeholder='Tenant Data Center'
                        requiredIndicator
                        onChange={setTenantDataCenter}
                    />
                    <TextField
                        label="Client ID"
                        value={clientId}
                        helpText="Specify the OAuth client ID of your Wiz service account"
                        placeholder='Client ID'
                        requiredIndicator
                        onChange={setClientId}
                    />
                    <PasswordTextField
                        label="Client Secret"
                        helpText="Specify the OAuth client Secret of your Wiz service account"
                        field={clientSecret === null ? '' : clientSecret}
                        onFunc={true}
                        setField={setClientSecret}
                        requiredIndicator
                    />
                </VerticalStack>
            </LegacyCard.Section>

            {isRemoveable && (
                <LegacyCard.Section>
                    <Box borderWidth="1" borderRadius='2' padding={4} borderColor="border-subdued">
                        <VerticalStack gap={4} inlineAlign="start">
                            <Text variant="headingMd">Connect Wiz traffic source</Text>
                            {wizImportApiEndpointsJob ? (
                                <VerticalStack gap={2} inlineAlign="start">
                                    <HorizontalStack gap={2} blockAlign="center">
                                        <Badge tone={jobStatusToBadgeTone(wizImportApiEndpointsJob.jobStatus)}>
                                            {func.toSentenceCase(wizImportApiEndpointsJob.jobStatus)}
                                        </Badge>
                                        {wizImportApiEndpointsJob.jobStatus === 'RUNNING' && wizImportApiEndpointsJob.startedAt > 0 && (
                                            <Text tone="subdued">
                                                since {func.epochToDateTime(wizImportApiEndpointsJob.startedAt)}
                                                {wizImportApiEndpointsJob.heartbeatAt > 0 && ` (last heartbeat: ${func.epochToDateTime(wizImportApiEndpointsJob.heartbeatAt)})`}
                                            </Text>
                                        )}
                                    </HorizontalStack>
                                    {wizImportApiEndpointsJob.jobStatus === 'SCHEDULED' && (
                                        <VerticalStack gap={1}>
                                            {wizImportApiEndpointsJob.finishedAt > 0 && (
                                                <Text tone="subdued">Last run: {func.epochToDateTime(wizImportApiEndpointsJob.finishedAt)}{deltaEndpointCount != null ? ` (${deltaEndpointCount} endpoints discovered / updated)` : ''}</Text>
                                            )}
                                            <Text tone="subdued">Next run: {func.epochToDateTime(wizImportApiEndpointsJob.scheduledAt)}</Text>
                                        </VerticalStack>
                                    )}
                                    <Button tone="critical" variant="plain" onClick={removeWizTrafficSource}>
                                        Remove traffic source
                                    </Button>
                                </VerticalStack>
                            ) : (
                                <Button onClick={addWizTrafficSource} loading={isAddingWizTrafficSource} disabled={isAddingWizTrafficSource}>Connect</Button>
                            )}
                        </VerticalStack>
                    </Box>
                </LegacyCard.Section>
            )}
            
        </LegacyCard>
    )

    return (
        <IntegrationsLayout
            title="Wiz"
            cardContent={cardContent}
            component={wizCard}
            docsUrl="https://docs.akto.io/integrations/wiz"
        />
    )
}

export default Wiz
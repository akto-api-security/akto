import { Button, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import IntegrationsLayout from "./IntegrationsLayout"
import PasswordTextField from "@/apps/dashboard/components/layouts/PasswordTextField"
import { useEffect, useState } from "react"
import settingRequests from "../api"
import func from "@/util/func"

function Imperva() {

    const [apiId, setApiId] = useState('')
    const [apiKey, setApiKey] = useState('')
    const [isSaving, setIsSaving] = useState(false)
    const [isRemoveable, setIsRemoveable] = useState(false)
    const [impervaIntegration, setImpervaIntegration] = useState(null)

    async function fetchImpervaIntegration() {
        const response = await settingRequests.fetchImpervaIntegration()
        const integration = response?.impervaIntegration
        setApiId(integration?.apiId || '')
        setApiKey('')
        setIsRemoveable(!!integration?.apiId)
        setImpervaIntegration(integration || null)
    }

    useEffect(() => {
        fetchImpervaIntegration()
    }, [])

    async function addImpervaIntegration() {
        setIsSaving(true)
        try {
            await settingRequests.addImpervaIntegration(apiId, apiKey)
            func.setToast(true, false, "Successfully added Imperva integration")
            fetchImpervaIntegration()
        } catch (error) {
            func.setToast(true, true, error?.response?.data?.actionErrors?.[0] || "Failed to add Imperva integration")
        } finally {
            setIsSaving(false)
        }
    }

    async function removeImpervaIntegration() {
        await settingRequests.removeImpervaIntegration()
        func.setToast(true, false, "Successfully removed Imperva integration")
        fetchImpervaIntegration()
    }

    async function triggerImpervaImport() {
        try {
            await settingRequests.triggerImpervaImport()
            func.setToast(true, false, "Imperva API import requested. It will start in a few minutes.")
            fetchImpervaIntegration()
        } catch (error) {
            func.setToast(true, true, error?.response?.data?.actionErrors?.[0] || "Failed to request Imperva API import")
        }
    }

    const importStatus = () => {
        const integration = impervaIntegration
        if (integration?.syncRequested) {
            return "Import requested. It will start in a few minutes."
        }
        if (integration?.lastSyncStartTs > (integration?.lastSyncEndTs || 0)) {
            return `Import running since ${func.epochToDateTime(integration.lastSyncStartTs)}.`
        }
        if (integration?.lastSyncEndTs > 0) {
            return `Last import finished at ${func.epochToDateTime(integration.lastSyncEndTs)}.`
        }
        return "No import has run yet."
    }

    const cardContent = "Import your API inventory from Imperva API Security into Akto. Save your Imperva API credentials, then click Import APIs whenever you want to pull the latest inventory."

    const impervaCard = (
        <LegacyCard
            primaryFooterAction={{
                content: 'Save',
                onAction: addImpervaIntegration,
                disabled: isSaving || !apiId?.trim() || !apiKey?.trim(),
                loading: isSaving
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeImpervaIntegration,
                disabled: !isRemoveable || isSaving
            }]}
        >
            <LegacyCard.Section title="Imperva API credentials">
                <VerticalStack gap={"4"}>
                    <TextField
                        label="API ID"
                        value={apiId}
                        placeholder='API ID'
                        requiredIndicator
                        onChange={setApiId}
                    />
                    <PasswordTextField
                        label="API Key"
                        helpText={isRemoveable ? "The saved key is hidden. Enter it again to update the integration." : "Needs read access to Imperva API Security."}
                        field={apiKey}
                        onFunc={true}
                        setField={setApiKey}
                        requiredIndicator
                    />
                </VerticalStack>
            </LegacyCard.Section>

            {isRemoveable && (
                <LegacyCard.Section title="Import">
                    <VerticalStack gap={"2"} inlineAlign="start">
                        <Text tone="subdued">{importStatus()}</Text>
                        <Button onClick={triggerImpervaImport} disabled={impervaIntegration?.syncRequested}>Import APIs</Button>
                    </VerticalStack>
                </LegacyCard.Section>
            )}
        </LegacyCard>
    )

    return (
        <IntegrationsLayout
            title="Imperva"
            cardContent={cardContent}
            component={impervaCard}
            docsUrl="https://docs.akto.io/traffic-connector/manual/imperva"
        />
    )
}

export default Imperva

import { Button, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import IntegrationsLayout from "./IntegrationsLayout"
import PasswordTextField from "@/apps/dashboard/components/layouts/PasswordTextField"
import { useEffect, useState } from "react"
import settingRequests from "../api"
import func from "@/util/func"

function Imperva() {

    const [apiId, setApiId] = useState('')
    const [apiKey, setApiKey] = useState('')
    const [integration, setIntegration] = useState(null)

    async function fetchImpervaIntegration() {
        const response = await settingRequests.fetchImpervaIntegration()
        setIntegration(response?.impervaIntegration || null)
        setApiId(response?.impervaIntegration?.apiId || '')
        setApiKey('')
    }

    useEffect(() => {
        fetchImpervaIntegration()
    }, [])

    async function saveImpervaIntegration() {
        await settingRequests.addImpervaIntegration(apiId, apiKey)
        func.setToast(true, false, "Imperva credentials saved")
        fetchImpervaIntegration()
    }

    async function triggerImpervaImport() {
        await settingRequests.triggerImpervaImport()
        func.setToast(true, false, "Imperva API import requested. It will start in a few minutes.")
        fetchImpervaIntegration()
    }

    let importStatus = "No import has run yet."
    if (integration?.syncRequested && integration?.lastSyncStartTs > (integration?.lastSyncEndTs || 0)) {
        importStatus = `Import running since ${func.epochToDateTime(integration.lastSyncStartTs)}.`
    } else if (integration?.syncRequested) {
        importStatus = "Import requested. It will start shortly."
    } else if (integration?.lastSyncEndTs > 0) {
        importStatus = `Last import finished at ${func.epochToDateTime(integration.lastSyncEndTs)}.`
    }

    const impervaCard = (
        <LegacyCard primaryFooterAction={{content: 'Save', onAction: saveImpervaIntegration, disabled: !apiId?.trim() || !apiKey?.trim()}}>
            <LegacyCard.Section title="Imperva API credentials">
                <VerticalStack gap={"4"}>
                    <TextField label="API ID" value={apiId} onChange={setApiId} requiredIndicator />
                    <PasswordTextField label="API Key" field={apiKey} setField={setApiKey} onFunc={true} requiredIndicator />
                </VerticalStack>
            </LegacyCard.Section>
            {integration && (
                <LegacyCard.Section title="Import">
                    <VerticalStack gap={"2"} inlineAlign="start">
                        <Text tone="subdued">{importStatus}</Text>
                        <Button onClick={triggerImpervaImport} disabled={integration.syncRequested}>Import APIs</Button>
                    </VerticalStack>
                </LegacyCard.Section>
            )}
        </LegacyCard>
    )

    return (
        <IntegrationsLayout
            title="Imperva"
            cardContent="Import your API inventory from Imperva API Security into Akto. Save your Imperva API credentials, then click Import APIs to pull the latest inventory."
            component={impervaCard}
            docsUrl="https://docs.akto.io/traffic-connector/manual/imperva"
        />
    )
}

export default Imperva

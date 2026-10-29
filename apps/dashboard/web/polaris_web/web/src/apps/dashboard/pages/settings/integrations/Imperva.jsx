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
    const [importJob, setImportJob] = useState(null)

    async function fetchImpervaIntegration() {
        const response = await settingRequests.fetchImpervaIntegration()
        setIntegration(response?.impervaIntegration || null)
        setImportJob(response?.importJob || null)
        return response
    }

    useEffect(() => {
        fetchImpervaIntegration().then((response) => setApiId(response?.impervaIntegration?.apiId || ''))
    }, [])

    const importActive = importJob?.jobStatus === 'SCHEDULED' || importJob?.jobStatus === 'RUNNING'

    // refresh the import status while it is in progress
    useEffect(() => {
        if (!importActive) return
        const interval = setInterval(fetchImpervaIntegration, 10000)
        return () => clearInterval(interval)
    }, [importActive])

    async function saveImpervaIntegration() {
        await settingRequests.addImpervaIntegration(apiId, apiKey)
        func.setToast(true, false, "Imperva credentials saved. API import will start shortly.")
        setApiKey('')
        fetchImpervaIntegration()
    }

    async function triggerImpervaImport() {
        await settingRequests.triggerImpervaImport()
        func.setToast(true, false, "Imperva API import will start shortly.")
        fetchImpervaIntegration()
    }

    async function stopImpervaImport() {
        await settingRequests.stopImpervaImport()
        func.setToast(true, false, "Imperva API import stopped")
        fetchImpervaIntegration()
    }

    const importStatus = {
        SCHEDULED: "Import will start shortly.",
        RUNNING: `Import running since ${func.epochToDateTime(importJob?.startedAt)}.`,
        COMPLETED: `Last import finished at ${func.epochToDateTime(importJob?.finishedAt)}.`,
        FAILED: `Last import failed at ${func.epochToDateTime(importJob?.finishedAt)}.`,
        STOPPED: `Last import stopped at ${func.epochToDateTime(importJob?.lastUpdatedAt)}.`,
    }[importJob?.jobStatus] || "No import has run yet."

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
                        {importActive ?
                            <Button onClick={stopImpervaImport}>Stop import</Button> :
                            <Button onClick={triggerImpervaImport}>Import APIs</Button>
                        }
                    </VerticalStack>
                </LegacyCard.Section>
            )}
        </LegacyCard>
    )

    return (
        <IntegrationsLayout
            title="Imperva"
            cardContent="Import your API inventory from Imperva API Security into Akto. Saving your Imperva API credentials starts the import; use Import APIs to pull the latest inventory again."
            component={impervaCard}
            docsUrl="https://docs.akto.io/traffic-connector/manual/imperva"
        />
    )
}

export default Imperva

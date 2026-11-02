import { Button, ButtonGroup, Text, TextField, VerticalStack } from "@shopify/polaris"
import PasswordTextField from "@/apps/dashboard/components/layouts/PasswordTextField"
import { useEffect, useState } from "react"
import settingRequests from "../../settings/api"
import func from "@/util/func"

function ImpervaApiImport() {

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

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect your Imperva API Security account to import your API inventory into Akto. Saving the credentials starts the import; use Import APIs to pull the latest inventory again.
            </Text>
            <VerticalStack gap="3">
                <TextField label="API ID" value={apiId} onChange={setApiId} requiredIndicator />
                <PasswordTextField label="API Key" field={apiKey} setField={setApiKey} onFunc={true} requiredIndicator />
                {integration && <Text color="subdued">{importStatus}</Text>}
                <ButtonGroup>
                    <Button primary onClick={saveImpervaIntegration} disabled={!apiId?.trim() || !apiKey?.trim()}>Save and import</Button>
                    {integration && (importActive ?
                        <Button onClick={stopImpervaImport}>Stop import</Button> :
                        <Button onClick={triggerImpervaImport}>Import APIs</Button>
                    )}
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default ImpervaApiImport

import { Divider, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import IntegrationsLayout from "./IntegrationsLayout"
import PasswordTextField from "@/apps/dashboard/components/layouts/PasswordTextField"
import { useEffect, useState } from "react"
import settingFunctions from "../module"
import func from "@/util/func"

function OpenTelemetry() {

    const [endpoint, setEndpoint] = useState('')
    const [apiKey, setApiKey] = useState('')
    const [headerName, setHeaderName] = useState('')
    const [isSaving, setIsSaving] = useState(false)
    const [isRemoveable, setIsRemoveable] = useState(false)

    const resetFields = () => {
        setEndpoint('')
        setApiKey('')
        setHeaderName('')
        setIsRemoveable(false)
    }

    async function fetchOpenTelemetryIntegration() {
        try {
            let openTelemetryIntegration = await settingFunctions.fetchOpenTelemetryIntegration()

            if (openTelemetryIntegration) {
                setEndpoint(openTelemetryIntegration.endpoint || '')
                setHeaderName(openTelemetryIntegration.headerName || '')
                setApiKey(null) // Don't show existing secret
                setIsRemoveable(true)
            } else {
                resetFields()
            }
        } catch (error) {
            func.setToast(true, true, 'Error fetching OpenTelemetry integration: ' + error)
            resetFields()
        }
    }

    useEffect(() => {
        fetchOpenTelemetryIntegration()
    }, [])

    async function addOpenTelemetryIntegration() {
        if (!endpoint?.trim()) {
            func.setToast(true, true, "Please enter a valid endpoint")
            return
        }
        if (!headerName?.trim()) {
            func.setToast(true, true, "Please enter a valid header name")
            return
        }
        if (!apiKey?.trim()) {
            func.setToast(true, true, "Please enter a valid API key")
            return
        }

        setIsSaving(true)
        try {
            await settingFunctions.addOpenTelemetryIntegration(endpoint, apiKey, headerName)
            func.setToast(true, false, "Successfully added OpenTelemetry Integration")
            fetchOpenTelemetryIntegration()
        } catch (error) {
            func.setToast(true, true, error?.response?.data?.actionErrors?.[0] || "Failed to add OpenTelemetry Integration")
        } finally {
            setIsSaving(false)
        }
    }

    async function removeOpenTelemetryIntegration() {
        setIsSaving(true)
        try {
            await settingFunctions.removeOpenTelemetryIntegration()
            func.setToast(true, false, "Successfully removed OpenTelemetry Integration")
            resetFields()
        } catch (error) {
            func.setToast(true, true, "Failed to remove OpenTelemetry Integration")
        } finally {
            setIsSaving(false)
        }
    }

    const isSaveDisabled = () => {
        return isSaving || !endpoint?.trim() || !headerName?.trim() || (apiKey === null ? true : !apiKey?.trim())
    }

    const cardContent = "Send metrics and heartbeat events from Akto's hybrid modules to an OpenTelemetry endpoint."

    const openTelemetryCard = (
        <LegacyCard
            primaryFooterAction={{
                content: isSaving ? 'Saving...' : 'Save',
                onAction: addOpenTelemetryIntegration,
                disabled: isSaveDisabled(),
                loading: isSaving
            }}
            secondaryFooterActions={[{
                content: 'Remove',
                onAction: removeOpenTelemetryIntegration,
                disabled: !isRemoveable || isSaving
            }]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate OpenTelemetry</Text>
            </LegacyCard.Section>

            <LegacyCard.Section>
                <VerticalStack gap={"4"}>
                    <TextField
                        label="OTLP Endpoint"
                        helpText="Specify the OTLP endpoint URL"
                        value={endpoint}
                        onChange={setEndpoint}
                        requiredIndicator
                    />
                    <TextField
                        label="Header Name"
                        helpText="Specify the HTTP header name used to pass the API key (e.g. api-key, Authorization)."
                        value={headerName}
                        onChange={setHeaderName}
                        requiredIndicator
                    />
                    <PasswordTextField
                        label="API Key"
                        helpText="Specify the API key for authenticating with your OTLP endpoint."
                        field={apiKey === null ? '' : apiKey}
                        onFunc={true}
                        setField={setApiKey}
                        requiredIndicator
                    />
                </VerticalStack>
            </LegacyCard.Section>
            <Divider />
            <br/>
        </LegacyCard>
    )

    return (
        <IntegrationsLayout
            title="OpenTelemetry"
            cardContent={cardContent}
            component={openTelemetryCard}
            docsUrl="https://docs.akto.io/integrations/opentelemetry"
        />
    )
}

export default OpenTelemetry
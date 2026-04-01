import React, { useState, useEffect } from 'react'
import { LegacyCard, Text, TextField, VerticalStack, Checkbox } from '@shopify/polaris';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import settingRequests from '../api';
import func from '@/util/func'

function Datadog() {
    const [apiKey, setApiKey] = useState('');
    const [datadogSite, setDatadogSite] = useState('');
    const [enabled, setEnabled] = useState(false);

    async function fetchIntegration() {
        const resp = await settingRequests.fetchDatadogIntegration()
        const config = resp?.datadogForwarderConfig
        if (config) {
            setApiKey(config.apiKey || '')
            setDatadogSite(config.datadogSite || '')
            setEnabled(config.enabled || false)
        }
    }

    async function addDatadogIntegration() {
        await settingRequests.addDatadogIntegration(apiKey, datadogSite, enabled)
        func.setToast(true, false, "Datadog integration saved successfully")
    }

    async function deleteDatadogIntegration() {
        await settingRequests.deleteDatadogIntegration()
        setApiKey('')
        setDatadogSite('')
        setEnabled(false)
        func.setToast(true, false, "Datadog integration deleted")
    }

    async function testDatadogIntegration() {
        const resp = await settingRequests.testDatadogIntegration(apiKey, datadogSite)
        if (resp?.actionErrors?.length > 0) {
            func.setToast(true, true, resp.actionErrors[0])
        } else {
            func.setToast(true, false, "Test log sent to Datadog successfully")
        }
    }

    useEffect(() => {
        fetchIntegration()
    }, [])

    const DatadogCard = (
        <LegacyCard
            primaryFooterAction={{ content: 'Save', onAction: addDatadogIntegration }}
            secondaryFooterActions={[
                { content: 'Test Connection', onAction: testDatadogIntegration },
                { content: 'Delete', destructive: true, onAction: deleteDatadogIntegration }
            ]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate Datadog</Text>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <VerticalStack gap={"2"}>
                    <PasswordTextField
                        text={apiKey}
                        setField={setApiKey}
                        onFunc={true}
                        field={apiKey}
                        label="Datadog API Key"
                    />
                    <TextField
                        value={datadogSite}
                        onChange={setDatadogSite}
                        label="Datadog Site"
                        placeholder="e.g. datadoghq.com"
                    />
                    <Checkbox
                        label="Enable traffic forwarding to Datadog"
                        checked={enabled}
                        onChange={setEnabled}
                    />
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const cardContent = "Forward all HTTP requests and responses captured by Akto to Datadog as log entries for observability and tracing."

    return (
        <IntegrationsLayout title="Datadog" cardContent={cardContent} component={DatadogCard} docsUrl="" />
    )
}

export default Datadog

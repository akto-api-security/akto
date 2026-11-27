import React, { useState, useEffect } from 'react'
import { LegacyCard, Text, TextField, VerticalStack, Checkbox } from '@shopify/polaris';
import IntegrationsLayout from './IntegrationsLayout';
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import settingRequests from '../api';
import func from '@/util/func'
import { usePermissions, whenAllowed } from '@/util/permissions'

function QRadar() {
    const { canCall } = usePermissions()
    const [url, setUrl] = useState('');
    const [authToken, setAuthToken] = useState('');
    const [enabled, setEnabled] = useState(false);
    const [status, setStatus] = useState(null);

    async function fetchIntegration() {
        const resp = await settingRequests.fetchQRadarIntegration()
        const config = resp?.qradarIntegration
        if (config?.url) {
            setUrl(config.url)
            setEnabled(config.enabled || false)
            setStatus(config)
        }
    }

    async function saveIntegration() {
        await settingRequests.saveQRadarIntegration(url, authToken, enabled)
        func.setToast(true, false, "QRadar integration saved")
        setAuthToken('')
        fetchIntegration()
    }

    async function testIntegration() {
        await settingRequests.testQRadarIntegration(url, authToken)
        func.setToast(true, false, "Test event sent to QRadar")
    }

    useEffect(() => {
        fetchIntegration()
    }, [])

    const failing = status?.lastErrorAt > (status?.lastSuccessAt || 0)

    const QRadarCard = (
        <LegacyCard
            primaryFooterAction={{ content: 'Save', onAction: saveIntegration, ...whenAllowed(canCall('api/saveQRadarIntegration')) }}
            secondaryFooterActions={[
                { content: 'Send test event', onAction: testIntegration, ...whenAllowed(canCall('api/testQRadarIntegration')) }
            ]}
        >
            <LegacyCard.Section>
                <Text variant="headingMd">Integrate IBM QRadar</Text>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <VerticalStack gap={"2"}>
                    <TextField
                        value={url}
                        onChange={setUrl}
                        label="HTTP Receiver URL"
                        placeholder="https://qradar.example.com:12469"
                        helpText='QRadar log source using the HTTP Receiver protocol, with Event Parsing Method "Event Per JSON Array" and JSON Path Expression /"events".'
                    />
                    <PasswordTextField
                        text={authToken}
                        setField={setAuthToken}
                        onFunc={true}
                        field={authToken}
                        label="Authorization header (optional)"
                        helpText={status?.url ? "Leave blank to keep the saved value." : undefined}
                    />
                    <Checkbox
                        label="Forward threat events to QRadar"
                        checked={enabled}
                        onChange={setEnabled}
                    />
                </VerticalStack>
            </LegacyCard.Section>
            {status?.enabled ? (
                <LegacyCard.Section>
                    <VerticalStack gap={"1"}>
                        <Text color="subdued">
                            {status.lastSuccessAt ? `Last sent ${func.prettifyEpoch(status.lastSuccessAt)}` : "Nothing sent yet"}
                            {` · ${status.pendingCount || 0} waiting · ${status.failedCount || 0} failed`}
                        </Text>
                        {failing ? <Text color="critical">{`Failing since ${func.prettifyEpoch(status.lastErrorAt)}: ${status.lastError}`}</Text> : null}
                    </VerticalStack>
                </LegacyCard.Section>
            ) : null}
        </LegacyCard>
    )

    const cardContent = "Forward Akto threat events to IBM QRadar as they are detected. Events are queued and retried until QRadar accepts them."

    return (
        <IntegrationsLayout title="IBM QRadar" cardContent={cardContent} component={QRadarCard} docsUrl="" readOnly={!canCall('api/saveQRadarIntegration')} />
    )
}

export default QRadar

import { Button, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

function CrowdStrikeConnector() {
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [baseUrl, setBaseUrl] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')
    const [recurringIntervalSeconds, setRecurringIntervalSeconds] = useState('3600')
    const [isSaved, setIsSaved] = useState(false)

    useEffect(() => {
        api.fetchCrowdStrikeIntegration().then((res) => {
            if (res && res.crowdStrikeIntegration) {
                const integration = res.crowdStrikeIntegration
                setClientId(integration.clientId || '')
                setBaseUrl(integration.baseUrl || '')
                setDataIngestionUrl(integration.dataIngestionUrl || '')
                setRecurringIntervalSeconds(String(integration.recurringIntervalSeconds || 3600))
                setIsSaved(true)
            }
        }).catch(() => {})
    }, [])

    const handleSave = async () => {
        await api.addCrowdStrikeIntegration(
            clientId,
            clientSecret || null,
            baseUrl || null,
            dataIngestionUrl,
            parseInt(recurringIntervalSeconds) || 3600
        ).then(() => {
            setClientSecret('')
            setIsSaved(true)
            func.setToast(true, false, "CrowdStrike integration saved successfully")
        }).catch(() => {
            func.setToast(true, true, "Failed to save CrowdStrike integration")
        })
    }

    const handleRemove = async () => {
        await api.removeCrowdStrikeIntegration().then(() => {
            setClientId('')
            setClientSecret('')
            setBaseUrl('')
            setDataIngestionUrl('')
            setRecurringIntervalSeconds('3600')
            setIsSaved(false)
            func.setToast(true, false, "CrowdStrike integration removed successfully")
        }).catch(() => {
            func.setToast(true, true, "Failed to remove CrowdStrike integration")
        })
    }

    const isSaveDisabled = !clientId || !dataIngestionUrl || (!clientSecret && !isSaved)

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect CrowdStrike Falcon to Akto to detect AI coding tools (Claude, Cursor, Copilot) running on managed endpoints.
            </Text>
            <VerticalStack gap="2">
                <TextField label="Client ID" value={clientId} onChange={setClientId} requiredIndicator />
                <PasswordTextField label="Client Secret" onFunc={true} setField={setClientSecret} field={clientSecret} requiredIndicator />
                <TextField
                    label="Base URL"
                    value={baseUrl}
                    onChange={setBaseUrl}
                    placeholder="https://api.crowdstrike.com"
                    helpText="Leave empty to use the default CrowdStrike API endpoint"
                />
                <TextField label="Data Ingestion Service URL" value={dataIngestionUrl} onChange={setDataIngestionUrl} requiredIndicator />
                <TextField label="Polling Interval (seconds)" value={recurringIntervalSeconds} onChange={setRecurringIntervalSeconds} />
            </VerticalStack>
            <HorizontalStack align='end' gap="2">
                <Button disabled={!isSaved} onClick={handleRemove} destructive>Remove</Button>
                <Button disabled={isSaveDisabled} onClick={handleSave} primary>Save</Button>
            </HorizontalStack>
        </div>
    )
}

export default CrowdStrikeConnector

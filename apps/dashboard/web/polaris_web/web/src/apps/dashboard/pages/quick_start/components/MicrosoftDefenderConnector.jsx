import { Button, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

function MicrosoftDefenderConnector() {
    const [tenantId, setTenantId] = useState('')
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')
    const [recurringIntervalSeconds, setRecurringIntervalSeconds] = useState('3600')
    const [isSaved, setIsSaved] = useState(false)

    useEffect(() => {
        api.fetchMicrosoftDefenderIntegration().then((res) => {
            if (res && res.microsoftDefenderIntegration) {
                const integration = res.microsoftDefenderIntegration
                setTenantId(integration.tenantId || '')
                setClientId(integration.clientId || '')
                setDataIngestionUrl(integration.dataIngestionUrl || '')
                setRecurringIntervalSeconds(String(integration.recurringIntervalSeconds || 3600))
                setIsSaved(true)
            }
        }).catch(() => {})
    }, [])

    const handleSave = async () => {
        await api.addMicrosoftDefenderIntegration(
            tenantId,
            clientId,
            clientSecret || null,
            dataIngestionUrl,
            parseInt(recurringIntervalSeconds) || 3600
        ).then(() => {
            setClientSecret('')
            setIsSaved(true)
            func.setToast(true, false, "Microsoft Defender integration saved successfully")
        }).catch(() => {
            func.setToast(true, true, "Failed to save Microsoft Defender integration")
        })
    }

    const handleRemove = async () => {
        await api.removeMicrosoftDefenderIntegration().then(() => {
            setTenantId('')
            setClientId('')
            setClientSecret('')
            setDataIngestionUrl('')
            setRecurringIntervalSeconds('3600')
            setIsSaved(false)
            func.setToast(true, false, "Microsoft Defender integration removed successfully")
        }).catch(() => {
            func.setToast(true, true, "Failed to remove Microsoft Defender integration")
        })
    }

    const isSaveDisabled = !tenantId || !clientId || !dataIngestionUrl || (!clientSecret && !isSaved)

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect Microsoft Defender for Endpoint to Akto to detect AI coding tools running inside WSL.
            </Text>
            <VerticalStack gap="2">
                <TextField label="Tenant ID" value={tenantId} onChange={setTenantId} requiredIndicator />
                <TextField label="Client ID" value={clientId} onChange={setClientId} requiredIndicator />
                <PasswordTextField label="Client Secret" onFunc={true} setField={setClientSecret} field={clientSecret} requiredIndicator />
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

export default MicrosoftDefenderConnector

import { Button, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

function DataDogConnector() {
    const [datadogApiKey, setDatadogApiKey] = useState('')
    const [datadogAppKey, setDatadogAppKey] = useState('')
    const [site, setSite] = useState('')

    const primaryAction = async() => {
        await api.saveDataDogConnector(datadogApiKey, datadogAppKey, site).then((res) => {
            func.setToast(true, false, "DataDog connector saved successfully")
        }).catch((err) => {
            func.setToast(true, true, "Failed to save DataDog connector")
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect your Datadog account to Akto to visualize your traces in the dashboard.
            </Text>
            <Text variant='bodyMd'>
                Enter your Datadog API key and app key to connect your Datadog account to Akto.
            </Text>
            <VerticalStack gap="2">
                <PasswordTextField label="Datadog API Key" onFunc={true} setField={setDatadogApiKey} field={datadogApiKey} />
                <PasswordTextField label="Datadog App Key" setField={setDatadogAppKey} onFunc={true} field={datadogAppKey} />
                <TextField label="Datadog Site" value={site} onChange={(value) => setSite(value)} />
            </VerticalStack>
            <HorizontalStack align='end'>
                <Button disabled={datadogApiKey?.length === 0 || datadogAppKey?.length === 0 || site?.length === 0} onClick={primaryAction} primary>Save</Button>
            </HorizontalStack>
        </div>
    )
}

export default DataDogConnector
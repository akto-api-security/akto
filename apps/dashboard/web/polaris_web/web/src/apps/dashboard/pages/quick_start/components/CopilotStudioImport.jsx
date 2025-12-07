import { Box, Button, ButtonGroup, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react'
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';

const CopilotStudioImport = () => {
    const [loading, setLoading] = useState(false)
    const [appInsightsAppId, setAppInsightsAppId] = useState('')
    const [appInsightsApiKey, setAppInsightsApiKey] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')

    const goToDocs = () => {
        window.open("https://docs.akto.io/copilot-studio-import")
    }

    const primaryAction = () => {
        if(appInsightsAppId?.length == 0 || appInsightsAppId == undefined) {
            func.setToast(true, true, "Please enter a valid App Insights App ID.")
            return
        }

        if(appInsightsApiKey?.length == 0 || appInsightsApiKey == undefined) {
            func.setToast(true, true, "Please enter a valid App Insights API Key.")
            return
        }

        if(dataIngestionUrl?.length == 0 || dataIngestionUrl == undefined) {
            func.setToast(true, true, "Please enter a valid Data Ingestion Service URL.")
            return
        }

        setLoading(true)
        api.initiateCopilotStudioImport(appInsightsAppId, appInsightsApiKey, dataIngestionUrl).then((res) => {
            func.setToast(true, false, "Copilot Studio Import initiated successfully. Please check your dashboard for updates.")
        }).catch((err) => {
            console.error("Error initiating Copilot Studio import:", err)
            func.setToast(true, true, "Ensure that you have added the correct App Insights App ID and API Key.")
        }).finally(() => {
            setLoading(false)
            setAppInsightsAppId('')
            setAppInsightsApiKey('')
            setDataIngestionUrl('')
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use our Copilot Studio Import feature to capture traffic from Azure Application Insights and instantly send it to your dashboard for real-time insights.
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField
                    label="App Insights App ID"
                    value={appInsightsAppId}
                    onChange={(value) => setAppInsightsAppId(value)}
                    placeholder='xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
                />

                <PasswordTextField
                    label="App Insights API Key"
                    setField={setAppInsightsApiKey}
                    onFunc={true}
                    field={appInsightsApiKey}
                    placeholder='*******'
                />

                <TextField
                    label="URL for Data Ingestion Service"
                    value={dataIngestionUrl}
                    type='url'
                    onChange={(value) => setDataIngestionUrl(value)}
                    placeholder='https://ingestion.example.com'
                />

                <ButtonGroup>
                    <Button
                        onClick={primaryAction}
                        primary
                        disabled={appInsightsAppId?.length == 0 || appInsightsApiKey?.length == 0 || dataIngestionUrl?.length == 0}
                        loading={loading}
                    >
                        Import
                    </Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default CopilotStudioImport

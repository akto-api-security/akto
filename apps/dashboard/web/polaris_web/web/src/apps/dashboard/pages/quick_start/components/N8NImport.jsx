import { Box, Button, ButtonGroup, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react'
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';

const N8NImport = () => {
    const [loading, setLoading] = useState(false)
    const [apiKey, setApiKey] = useState('')
    const [n8nUrl, setN8nUrl] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')

    const goToDocs = () => {
        window.open("https://docs.akto.io/n8n-import")
    }

    const primaryAction = () => {
        if(n8nUrl?.length == 0 || n8nUrl == undefined) {
            func.setToast(true, true, "Please enter a valid N8N URL.")
            return
        }

        if(dataIngestionUrl?.length == 0 || dataIngestionUrl == undefined) {
            func.setToast(true, true, "Please enter a valid Data Ingestion Service URL.")
            return
        }

        if(apiKey?.length == 0 || apiKey == undefined) {
            func.setToast(true, true, "Please enter a valid API Key.")
            return
        }

        setLoading(true)
        api.initiateN8NImport(n8nUrl, apiKey, dataIngestionUrl, window.location.origin).then((res) => {
            func.setToast(true, false, "N8N Import initiated successfully. Please check your dashboard for updates.")
        }).catch((err) => {
            console.error("Error initiating N8N import:", err)
            func.setToast(true, true, "Ensure that you have added the correct N8N URL and API Key.")
        }).finally(() => {
            setLoading(false)
            setN8nUrl('')
            setApiKey('')
            setDataIngestionUrl('')
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use our N8N Import feature to capture traffic and instantly send it to your dashboard for real-time insights.
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField
                    label="N8N URL"
                    value={n8nUrl}
                    type='url'
                    onChange={(value) => setN8nUrl(value)}
                    placeholder='https://n8n.example.com'
                />

                <PasswordTextField
                    label="N8N API Key"
                    setField={setApiKey}
                    onFunc={true}
                    field={apiKey}
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
                        disabled={n8nUrl?.length == 0 || dataIngestionUrl?.length == 0 || apiKey?.length == 0}
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

export default N8NImport

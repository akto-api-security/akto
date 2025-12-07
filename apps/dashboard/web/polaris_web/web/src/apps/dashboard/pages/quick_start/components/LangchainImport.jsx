import { Box, Button, ButtonGroup, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react'
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';

const LangchainImport = () => {
    const [loading, setLoading] = useState(false)
    const [apiKey, setApiKey] = useState('')
    const [langsmithUrl, setLangsmithUrl] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')

    const goToDocs = () => {
        window.open("https://docs.akto.io/langchain-import")
    }

    const primaryAction = () => {
        if(langsmithUrl?.length == 0 || langsmithUrl == undefined) {
            func.setToast(true, true, "Please enter a valid LangSmith URL.")
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
        api.initiateLangchainImport(langsmithUrl, apiKey, dataIngestionUrl).then((res) => {
            func.setToast(true, false, "Langchain Import initiated successfully. Please check your dashboard for updates.")
        }).catch((err) => {
            console.error("Error initiating Langchain import:", err)
            func.setToast(true, true, "Ensure that you have added the correct LangSmith URL and API Key.")
        }).finally(() => {
            setLoading(false)
            setLangsmithUrl('')
            setApiKey('')
            setDataIngestionUrl('')
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use our Langchain Import feature to capture traffic from LangSmith and instantly send it to your dashboard for real-time insights.
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField
                    label="LangSmith Base URL"
                    value={langsmithUrl}
                    type='url'
                    onChange={(value) => setLangsmithUrl(value)}
                    placeholder='https://api.smith.langchain.com'
                />

                <PasswordTextField
                    label="LangSmith API Key"
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
                        disabled={langsmithUrl?.length == 0 || dataIngestionUrl?.length == 0 || apiKey?.length == 0}
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

export default LangchainImport

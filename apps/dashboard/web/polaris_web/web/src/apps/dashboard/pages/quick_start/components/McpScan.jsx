import { Box, Button, ButtonGroup, Checkbox, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react'
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';

const McpScan = () => {
    const [loading, setLoading] = useState(false)
    const [authKey, setAuthKey] = useState('')
    const [authValue, setAuthValue] = useState('')
    const [serverUrl, setServerUrl] = useState('')
    const [requireAuth, setRequireAuth] = useState(false)

    const goToDocs = () => {
        window.open("https://docs.akto.io/mcp-scan")
    }

    const primaryAction = () => {
        if(serverUrl?.length == 0 || serverUrl == undefined) {
            func.setToast(true, true, "Please enter a valid URL.")
            return
        }

        if(!requireAuth) {
            setAuthKey('')
            setAuthValue('')
        }

        setLoading(true)
        api.initiateMCPScan(serverUrl, authKey, authValue, window.location.origin).then((res) => {
            func.setToast(true, false, "MCP Scan initiated successfully. Please check your dashboard for updates.")
        }).catch((err) => {
            console.error("Error initiating crawler:", err)
            func.setToast(true, true, "Ensure that you have added the correct MCP Server URL.")
        }).finally(() => {
            setLoading(false)
            setServerUrl('')
            setRequireAuth(false)
        })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use our MCP Import feature to capture traffic and instantly send it to your dashboard for real-time insights.
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField label="Enter your MCP Initialize URL" value={serverUrl} type='url' onChange={(value) => setServerUrl(value)} placeholder='https://mcp.example.com' />

                <Checkbox label="This site requires login?" checked={requireAuth} onChange={() => setRequireAuth(!requireAuth)} />

                {
                    requireAuth &&
                    <>
                        <TextField label="Auth Header Key" value={authKey} type='string' onChange={(value) => setAuthKey(value)} placeholder='Authorization' />
                        <PasswordTextField label="Auth Header Value" setField={setAuthValue} onFunc={true} field={authValue} placeholder='*******'/>
                    </>
                } 

                <ButtonGroup>
                    <Button onClick={primaryAction} primary disabled={serverUrl?.length == 0} loading={loading}>Import</Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default McpScan
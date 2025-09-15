import { Box, Button, ButtonGroup, Checkbox, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react'
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';

const McpRecon = () => {
    const [loading, setLoading] = useState(false)
    const [ipRange, setIpRange] = useState('')
    const [authKey, setAuthKey] = useState('')
    const [authValue, setAuthValue] = useState('')
    const [requireAuth, setRequireAuth] = useState(false)

    const goToDocs = () => {
        window.open("https://docs.akto.io")
    }

    const primaryAction = () => {
        if(ipRange?.length == 0 || ipRange == undefined) {
            func.setToast(true, true, "Please enter a valid IP range.")
            return
        }

        // Validate IP range format (basic validation)
        const ipRangePattern = /^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$|^(\d{1,3}\.){3}\d{1,3}-(\d{1,3}\.){3}\d{1,3}$/;
        if (!ipRangePattern.test(ipRange)) {
            func.setToast(true, true, "Please enter a valid IP range format (e.g., 192.168.1.0/24 or 192.168.1.1-192.168.1.255)")
            return
        }

        if(!requireAuth) {
            setAuthKey('')
            setAuthValue('')
        }

        setLoading(true)
        setLoading(false)
        // TODO: uncomment when backend is ready
//         api.initiateMCPRecon(ipRange, authKey, authValue, window.location.origin).then((res) => {
//             func.setToast(true, false, "MCP Recon initiated successfully. Discovering and cataloging MCP servers in the specified IP range.")
//         }).catch((err) => {
//             console.error("Error initiating MCP recon:", err)
//             func.setToast(true, true, "Failed to initiate MCP reconnaissance. Please check your IP range and try again.")
//         }).finally(() => {
//             setLoading(false)
//             setIpRange('')
//             setRequireAuth(false)
//         })
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Use our MCP Recon feature to discover and catalog MCP-compatible servers across your network IP ranges for comprehensive security analysis.
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                <TextField 
                    label="Enter IP Range to Scan" 
                    value={ipRange} 
                    type='text' 
                    onChange={(value) => setIpRange(value)} 
                    placeholder='192.168.1.0/24 or 10.0.0.1-10.0.0.255' 
                    helpText="Enter CIDR notation (e.g., 192.168.1.0/24) or IP range (e.g., 192.168.1.1-192.168.1.255)"
                />

                <ButtonGroup>
                    <Button 
                        onClick={primaryAction} 
                        primary 
                        disabled={ipRange?.length == 0} 
                        loading={loading}
                    >
                        Discover
                    </Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    )
}

export default McpRecon
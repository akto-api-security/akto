import { Box, Button, ButtonGroup, Checkbox, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useEffect, useState } from 'react'
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import Dropdown from '../../../components/layouts/Dropdown';
import testingApi from '../../testing/api'

const AUTH_TYPE_HEADER = 'HEADER'
const AUTH_TYPE_TEST_ROLE = 'TEST_ROLE'

const authTypeOptions = [
    { label: 'Auth header', value: AUTH_TYPE_HEADER },
    { label: 'Test role (OAuth / login flow)', value: AUTH_TYPE_TEST_ROLE },
]

const McpScan = () => {
    const [loading, setLoading] = useState(false)
    const [authKey, setAuthKey] = useState('')
    const [authValue, setAuthValue] = useState('')
    const [serverUrl, setServerUrl] = useState('')
    const [requireAuth, setRequireAuth] = useState(false)
    const [authType, setAuthType] = useState(AUTH_TYPE_HEADER)
    const [testRole, setTestRole] = useState('')
    const [testRolesArr, setTestRolesArr] = useState([])

    const fetchTestRoles = async () => {
        try {
            const testRolesResponse = await testingApi.fetchTestRoles()
            const testRoles = testRolesResponse.testRoles.map(testRole => {
                return {
                    "label": testRole.name,
                    "value": testRole.hexId
                }
            })
            setTestRolesArr(testRoles)
            if (testRoles.length > 0) {
                setTestRole(testRoles[0].value)
            }
        } catch (error) {
        }
    }

    useEffect(() => {
        fetchTestRoles()
    }, [])

    const goToDocs = () => {
        window.open("https://docs.akto.io/mcp-scan")
    }

    const primaryAction = () => {
        if(serverUrl?.length == 0 || serverUrl == undefined) {
            func.setToast(true, true, "Please enter a valid URL.")
            return
        }

        const useHeaderAuth = requireAuth && authType === AUTH_TYPE_HEADER
        const useTestRole = requireAuth && authType === AUTH_TYPE_TEST_ROLE

        if(useTestRole && !testRole) {
            func.setToast(true, true, "Please select a test role.")
            return
        }

        setLoading(true)
        api.initiateMCPScan(serverUrl, useHeaderAuth ? authKey : '', useHeaderAuth ? authValue : '', window.location.origin, useTestRole ? testRole : null).then((res) => {
            func.setToast(true, false, "MCP Scan initiated successfully. Please check your dashboard for updates.")
            setServerUrl('')
            setRequireAuth(false)
            setAuthKey('')
            setAuthValue('')
            setAuthType(AUTH_TYPE_HEADER)
        }).catch((err) => {
            console.error("Error initiating crawler:", err)
            // specific errors (e.g. test role login failure) are already shown by the request handler
            if(!err?.response?.data?.actionErrors?.length) {
                func.setToast(true, true, "Ensure that you have added the correct MCP Server URL.")
            }
        }).finally(() => {
            setLoading(false)
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
                    <Dropdown
                        id="mcp-auth-type"
                        label="Authentication method"
                        menuItems={authTypeOptions}
                        initial={authType}
                        selected={setAuthType}
                    />
                }

                {
                    requireAuth && authType === AUTH_TYPE_HEADER &&
                    <>
                        <TextField label="Auth Header Key" value={authKey} type='string' onChange={(value) => setAuthKey(value)} placeholder='Authorization' />
                        <PasswordTextField label="Auth Header Value" setField={setAuthValue} onFunc={true} field={authValue} placeholder='*******'/>
                    </>
                }

                {
                    requireAuth && authType === AUTH_TYPE_TEST_ROLE && (
                        testRolesArr.length > 0 ?
                        <Dropdown
                            id="mcp-test-role"
                            label="Test Role"
                            menuItems={testRolesArr}
                            initial={testRole}
                            selected={setTestRole}
                            helpText="Auth configured in the role (e.g. OAuth token request or recorded login) is used to fetch a token for the MCP server."
                        /> :
                        <Text variant='bodyMd' color='subdued'>
                            No test roles found. Create one under Testing → Test roles and configure its authentication.
                        </Text>
                    )
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
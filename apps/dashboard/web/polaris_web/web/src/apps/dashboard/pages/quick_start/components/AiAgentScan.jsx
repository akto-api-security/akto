import { Box, Button, ButtonGroup, Checkbox, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import { useState, useEffect } from 'react'
import api from '../api';
import func from "@/util/func"
import Dropdown from '../../../components/layouts/Dropdown';
import testingApi from '../../testing/api'

const AiAgentScan = ({ description = "Import your AI agents, seamlessly in AKTO.", defaultRequestBody = { "model": "llama3.2", "prompt": "Why is the sky blue?" }, docsLink = "https://docs.akto.io/ai-security" }) => {
    const [loading, setLoading] = useState(false)
    const [url, setUrl] = useState('')
    const [useCustomRequestBody, setUseCustomRequestBody] = useState(false)
    const [customRequestBody, setCustomRequestBody] = useState('')
    const [useTestRole, setUseTestRole] = useState(false)
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

    const handleImport = async () => {
        if (!url || url.length === 0) {
            func.setToast(true, true, "Please enter a valid URL.")
            return
        }

        const requestBodyToSend = useCustomRequestBody 
            ? customRequestBody 
            : JSON.stringify(defaultRequestBody)

        setLoading(true)
        try {
            const testRoleToSend = useTestRole ? testRole : null
            await api.importFromUrl(url, testRoleToSend, requestBodyToSend || null)
            func.setToast(true, false, "AI agent imported successfully.")
            // Reset form
            setUrl('')
            setCustomRequestBody('')
            setUseCustomRequestBody(false)
            setUseTestRole(false)
            setTestRole(testRolesArr[0]?.value || '')
        } finally {
            setLoading(false)
        }
    }

    const goToDocs = () => {
        window.open(docsLink, '_blank', 'noopener,noreferrer')
    }

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                {description}
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="4">
                <TextField 
                    label="AI Endpoint URL" 
                    value={url} 
                    onChange={setUrl} 
                    placeholder="https://api.example.com/ai-agent"
                    type="url"
                    helpText="Enter the AI endpoint URL"
                />

                <Checkbox
                    label="Use custom request body"
                    checked={useCustomRequestBody}
                    onChange={setUseCustomRequestBody}
                />

                {useCustomRequestBody && (
                    <TextField 
                        label="Custom Request Body" 
                        value={customRequestBody} 
                        onChange={setCustomRequestBody} 
                        placeholder='{"key": "value"}'
                        multiline={4}
                        helpText="Enter JSON request body"
                    />
                )}

                <Checkbox
                    label="Use test role for authentication"
                    checked={useTestRole}
                    onChange={setUseTestRole}
                />

                {useTestRole && testRolesArr.length > 0 && (
                    <Dropdown
                        label="Test Role"
                        menuItems={testRolesArr}
                        initial={testRole}
                        selected={setTestRole}
                    />
                )}

                <Box paddingBlockStart={2}>
                    <ButtonGroup>
                        <Button 
                            onClick={handleImport} 
                            primary 
                            disabled={!url || url.length === 0} 
                            loading={loading}
                        >
                            Import
                        </Button>
                        <Button onClick={goToDocs}>
                            Go to docs
                        </Button>
                    </ButtonGroup>
                </Box>
            </VerticalStack>
        </div>
    )
}

export default AiAgentScan
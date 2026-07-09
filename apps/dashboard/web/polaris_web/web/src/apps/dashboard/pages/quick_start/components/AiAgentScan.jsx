import { Box, Button, ButtonGroup, Checkbox, Divider, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor } from '@shopify/polaris-icons';
import { useState, useEffect } from 'react'
import api from '../api';
import func from "@/util/func"
import Dropdown from '../../../components/layouts/Dropdown';
import testingApi from '../../testing/api'

const MAX_HEADERS = 5

const AiAgentScan = ({ description = "Import your AI agents, seamlessly in AKTO.", defaultRequestBody = { "model": "llama3.2", "prompt": "Why is the sky blue?" }, docsLink = "https://docs.akto.io/ai-security" }) => {
    const [loading, setLoading] = useState(false)
    const [url, setUrl] = useState('')
    const [collectionName, setCollectionName] = useState('')
    const [useCustomRequestBody, setUseCustomRequestBody] = useState(false)
    const [customRequestBody, setCustomRequestBody] = useState('')
    const [useTestRole, setUseTestRole] = useState(false)
    const [testRole, setTestRole] = useState('')
    const [testRolesArr, setTestRolesArr] = useState([])
    const [headers, setHeaders] = useState([])

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

    function updateHeader(index, field, val) {
        setHeaders(prev => {
            const updated = [...prev]
            updated[index] = { ...updated[index], [field]: val }
            return updated
        })
    }

    function addHeaderRow() {
        if (headers.length < MAX_HEADERS) {
            setHeaders(prev => [...prev, { key: '', value: '' }])
        }
    }

    function removeHeaderRow(index) {
        setHeaders(prev => prev.filter((_, i) => i !== index))
    }

    function headersArrayToObject() {
        const obj = {}
        for (const h of headers) {
            if (h.key && h.key.trim()) {
                obj[h.key.trim()] = h.value || ''
            }
        }
        return Object.keys(obj).length > 0 ? obj : null
    }

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
            const customHeaders = headersArrayToObject()
            const collectionNameToSend = collectionName.trim() || null
            await api.importFromUrl(url, testRoleToSend, requestBodyToSend || null, customHeaders, collectionNameToSend)
            func.setToast(true, false, "AI agent imported successfully.")
            setUrl('')
            setCollectionName('')
            setCustomRequestBody('')
            setUseCustomRequestBody(false)
            setUseTestRole(false)
            setTestRole(testRolesArr[0]?.value || '')
            setHeaders([])
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

                <TextField
                    label="AI Agent name (optional)"
                    value={collectionName}
                    onChange={setCollectionName}
                    placeholder="my-ai-agent"
                    helpText="If not provided, the hostname from the URL will be used"
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

                <VerticalStack gap="2">
                    <Text variant="bodyMd" fontWeight="medium">Custom Headers</Text>
                    {headers.map((header, index) => (
                        <HorizontalStack key={index} gap="2" blockAlign="center">
                            <Box minWidth="200px">
                                <TextField
                                    label=""
                                    labelHidden
                                    placeholder="Header key"
                                    value={header.key}
                                    onChange={(val) => updateHeader(index, 'key', val)}
                                />
                            </Box>
                            <Box minWidth="200px">
                                <TextField
                                    label=""
                                    labelHidden
                                    placeholder="Header value"
                                    value={header.value}
                                    onChange={(val) => updateHeader(index, 'value', val)}
                                />
                            </Box>
                            <Button
                                plain
                                icon={DeleteMinor}
                                onClick={() => removeHeaderRow(index)}
                                accessibilityLabel="Remove header"
                            />
                        </HorizontalStack>
                    ))}
                    {headers.length < MAX_HEADERS && (
                        <Box>
                            <Button plain onClick={addHeaderRow}>+ Add header</Button>
                        </Box>
                    )}
                </VerticalStack>

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
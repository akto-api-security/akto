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
    const [useCustomResponse, setUseCustomResponse] = useState(false)
    const [customResponseBody, setCustomResponseBody] = useState('')
    const [customResponseStatusCode, setCustomResponseStatusCode] = useState('200')
    const [responseHeaders, setResponseHeaders] = useState([])

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

    function updateHeaderRow(setList, index, field, val) {
        setList(prev => {
            const updated = [...prev]
            updated[index] = { ...updated[index], [field]: val }
            return updated
        })
    }

    function addHeaderRow(list, setList) {
        if (list.length < MAX_HEADERS) {
            setList(prev => [...prev, { key: '', value: '' }])
        }
    }

    function removeHeaderRow(setList, index) {
        setList(prev => prev.filter((_, i) => i !== index))
    }

    function headersArrayToObject(list) {
        const obj = {}
        for (const h of list) {
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
            const customHeaders = headersArrayToObject(headers)
            const collectionNameToSend = collectionName.trim() || null
            const customResponseBodyToSend = useCustomResponse ? (customResponseBody || null) : null
            const customResponseStatusCodeToSend = useCustomResponse && customResponseStatusCode
                ? parseInt(customResponseStatusCode, 10)
                : null
            const customResponseHeadersToSend = useCustomResponse ? headersArrayToObject(responseHeaders) : null
            await api.importFromUrl(
                url, testRoleToSend, requestBodyToSend || null, customHeaders, collectionNameToSend,
                customResponseBodyToSend, customResponseStatusCodeToSend, customResponseHeadersToSend
            )
            func.setToast(true, false, "AI agent imported successfully.")
            setUrl('')
            setCollectionName('')
            setCustomRequestBody('')
            setUseCustomRequestBody(false)
            setUseTestRole(false)
            setTestRole(testRolesArr[0]?.value || '')
            setHeaders([])
            setUseCustomResponse(false)
            setCustomResponseBody('')
            setCustomResponseStatusCode('200')
            setResponseHeaders([])
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
                                    onChange={(val) => updateHeaderRow(setHeaders, index, 'key', val)}
                                />
                            </Box>
                            <Box minWidth="200px">
                                <TextField
                                    label=""
                                    labelHidden
                                    placeholder="Header value"
                                    value={header.value}
                                    onChange={(val) => updateHeaderRow(setHeaders, index, 'value', val)}
                                />
                            </Box>
                            <Button
                                plain
                                icon={DeleteMinor}
                                onClick={() => removeHeaderRow(setHeaders, index)}
                                accessibilityLabel="Remove header"
                            />
                        </HorizontalStack>
                    ))}
                    {headers.length < MAX_HEADERS && (
                        <Box>
                            <Button plain onClick={() => addHeaderRow(headers, setHeaders)}>+ Add header</Button>
                        </Box>
                    )}
                </VerticalStack>

                <Checkbox
                    label="Use custom response (skip live call)"
                    checked={useCustomResponse}
                    onChange={setUseCustomResponse}
                    helpText="Provide the response yourself instead of Akto calling the URL — useful when the endpoint isn't reachable from the dashboard."
                />

                {useCustomResponse && (
                    <VerticalStack gap="4">
                        <TextField
                            label="Custom Response Status Code"
                            type="number"
                            value={customResponseStatusCode}
                            onChange={setCustomResponseStatusCode}
                            placeholder="200"
                        />

                        <TextField
                            label="Custom Response Body"
                            value={customResponseBody}
                            onChange={setCustomResponseBody}
                            placeholder='{"key": "value"}'
                            multiline={4}
                            helpText="This response will be saved as-is instead of calling the URL"
                        />

                        <VerticalStack gap="2">
                            <Text variant="bodyMd" fontWeight="medium">Custom Response Headers</Text>
                            {responseHeaders.map((header, index) => (
                                <HorizontalStack key={index} gap="2" blockAlign="center">
                                    <Box minWidth="200px">
                                        <TextField
                                            label=""
                                            labelHidden
                                            placeholder="Header key"
                                            value={header.key}
                                            onChange={(val) => updateHeaderRow(setResponseHeaders, index, 'key', val)}
                                        />
                                    </Box>
                                    <Box minWidth="200px">
                                        <TextField
                                            label=""
                                            labelHidden
                                            placeholder="Header value"
                                            value={header.value}
                                            onChange={(val) => updateHeaderRow(setResponseHeaders, index, 'value', val)}
                                        />
                                    </Box>
                                    <Button
                                        plain
                                        icon={DeleteMinor}
                                        onClick={() => removeHeaderRow(setResponseHeaders, index)}
                                        accessibilityLabel="Remove header"
                                    />
                                </HorizontalStack>
                            ))}
                            {responseHeaders.length < MAX_HEADERS && (
                                <Box>
                                    <Button plain onClick={() => addHeaderRow(responseHeaders, setResponseHeaders)}>+ Add header</Button>
                                </Box>
                            )}
                        </VerticalStack>
                    </VerticalStack>
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
import { Box, Button, ButtonGroup, Checkbox, Divider, HorizontalStack, Select, Text, TextField, VerticalStack } from '@shopify/polaris';
import { DeleteMinor } from '@shopify/polaris-icons';
import { useState } from 'react'
import api from '../api';
import func from "@/util/func"
import TestRoleDropdown from '../../../components/shared/TestRoleDropdown';
import MiniTestingSelector from './MiniTestingSelector';

const MAX_HEADERS = 5
const AGENT_TYPES = [{ label: 'HTTP', value: 'HTTP' }, { label: 'WebSocket', value: 'WEBSOCKET' }]
const METHODS = ['POST', 'GET', 'PUT', 'PATCH', 'DELETE'].map(m => ({ label: m, value: m }))
// a WebSocket handshake is an http(s) request (e.g. copied from Burp), so http(s) maps to ws(s) on the same endpoint
const toWebSocketUrl = (value) => (value || '').trim().replace(/^http(s?):\/\//i, 'ws$1://')

const AiAgentScan = ({ description = "Import your AI agents, seamlessly in AKTO.", defaultRequestBody = { "model": "llama3.2", "prompt": "Why is the sky blue?" }, docsLink = "https://docs.akto.io/ai-security" }) => {
    const [loading, setLoading] = useState(false)
    const [url, setUrl] = useState('')
    const [collectionName, setCollectionName] = useState('')
    const [useCustomRequestBody, setUseCustomRequestBody] = useState(false)
    const [customRequestBody, setCustomRequestBody] = useState('')
    const [useTestRole, setUseTestRole] = useState(false)
    const [testRole, setTestRole] = useState('')
    const [headers, setHeaders] = useState([])
    const [useCustomResponse, setUseCustomResponse] = useState(false)
    const [customResponseBody, setCustomResponseBody] = useState('')
    const [customResponseStatusCode, setCustomResponseStatusCode] = useState('200')
    const [responseHeaders, setResponseHeaders] = useState([])
    const [useMiniTesting, setUseMiniTesting] = useState(false)
    const [miniTestingName, setMiniTestingName] = useState('')
    const [agentType, setAgentType] = useState('HTTP')
    const [method, setMethod] = useState('POST')

    const isWebSocket = agentType === 'WEBSOCKET'
    const hasEvent = isWebSocket && useCustomRequestBody && customRequestBody.trim().length > 0
    // a WebSocket event can be sent to the connection imported earlier under the same agent name
    const canImport = (url && url.length > 0) || (hasEvent && collectionName.trim().length > 0)

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
        if (!canImport) {
            func.setToast(true, true, "Please enter a valid URL.")
            return
        }
        const urlToSend = isWebSocket && url ? toWebSocketUrl(url) : url
        if (isWebSocket && !useCustomResponse && !(useMiniTesting && miniTestingName)) {
            func.setToast(true, true, "WebSocket agents are reached via a mini-testing module, select one.")
            return
        }

        // WebSocket: the body is the event, and none means import only the connection; GET has no body,
        // only POST falls back to the default body
        let requestBodyToSend
        if (isWebSocket) {
            requestBodyToSend = hasEvent ? customRequestBody : null
        } else if (method === 'GET') {
            requestBodyToSend = null
        } else if (method !== 'POST') {
            requestBodyToSend = useCustomRequestBody ? customRequestBody : null
        } else {
            requestBodyToSend = useCustomRequestBody ? customRequestBody : JSON.stringify(defaultRequestBody)
        }

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
            const miniTestingNameToSend = useMiniTesting && !useCustomResponse ? (miniTestingName || null) : null
            await api.importFromUrl(
                urlToSend, testRoleToSend, requestBodyToSend || null, customHeaders, collectionNameToSend,
                customResponseBodyToSend, customResponseStatusCodeToSend, customResponseHeadersToSend,
                miniTestingNameToSend, isWebSocket ? null : method
            )
            func.setToast(true, false, "AI agent imported successfully.")
            setUrl('')
            setCollectionName('')
            setCustomRequestBody('')
            setUseCustomRequestBody(false)
            setUseTestRole(false)
            setTestRole('')
            setHeaders([])
            setUseCustomResponse(false)
            setCustomResponseBody('')
            setCustomResponseStatusCode('200')
            setResponseHeaders([])
            setUseMiniTesting(false)
            setMiniTestingName('')
            setAgentType('HTTP')
            setMethod('POST')
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
                <HorizontalStack gap="4">
                    <Select
                        label="Agent type"
                        options={AGENT_TYPES}
                        value={agentType}
                        onChange={setAgentType}
                    />
                    {!isWebSocket && (
                        <Select
                            label="Method"
                            options={METHODS}
                            value={method}
                            onChange={setMethod}
                        />
                    )}
                </HorizontalStack>

                <TextField
                    label={isWebSocket ? "WebSocket URL" : "AI Endpoint URL"}
                    value={url}
                    onChange={setUrl}
                    placeholder="https://api.example.com/ai-agent"
                    type="url"
                    helpText={isWebSocket
                        ? "Import the URL first (its connection is checked from mini-testing), then send events with the same agent name and no URL"
                        : "Enter the AI endpoint URL"}
                />

                <TextField
                    label="AI Agent name (optional)"
                    value={collectionName}
                    onChange={setCollectionName}
                    placeholder="my-ai-agent"
                    helpText="If not provided, the hostname from the URL will be used"
                />

                {(isWebSocket || method !== 'GET') && (
                    <Checkbox
                        label={isWebSocket ? "Send a WebSocket event" : "Use custom request body"}
                        checked={useCustomRequestBody}
                        onChange={setUseCustomRequestBody}
                    />
                )}

                {(isWebSocket || method !== 'GET') && useCustomRequestBody && (
                    <TextField 
                        label={isWebSocket ? "WebSocket event" : "Custom Request Body"} 
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

                {useTestRole && (
                    <TestRoleDropdown
                        selectedRole={testRole}
                        setSelectedRole={setTestRole}
                        selectFirstByDefault={true}
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

                {!useCustomResponse && (
                    <MiniTestingSelector
                        id="ai-agent-mini-testing"
                        enabled={useMiniTesting}
                        setEnabled={setUseMiniTesting}
                        selected={miniTestingName}
                        setSelected={setMiniTestingName}
                    />
                )}

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
                            disabled={!canImport} 
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
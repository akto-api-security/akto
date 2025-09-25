import React, { useState, useEffect, useCallback } from 'react'
import { 
    VerticalStack, 
    Text, 
    Button, 
    HorizontalStack, 
    Card, 
    Box,
} from '@shopify/polaris'
import SampleDataComponent from '../../../components/shared/SampleDataComponent'

function ConfigurationRequired({ 
    errorMessage, 
    editableJsonData, 
    onJsonChange, 
    onRetry,
    currentUrl
}) {
    const [sampleData, setSampleData] = useState(null)
    const [editorData, setEditorData] = useState(null)

    // Convert request string to proper sample data format
    useEffect(() => {
        if (editableJsonData && typeof editableJsonData === 'string') {
            try {
                const parsedRequest = JSON.parse(editableJsonData)
                
                // Create a complete HTTP sample in the format expected by SampleDataComponent
                const httpSample = {
                    destIp: null,
                    method: parsedRequest.method,
                    requestPayload: JSON.stringify(parsedRequest.body || {}),
                    responsePayload: "{}",
                    ip: null,
                    path: currentUrl || "/",
                    requestHeaders: JSON.stringify(parsedRequest.headers || {}),
                    responseHeaders: "{}",
                    time: Math.floor(Date.now() / 1000).toString(),
                    statusCode: errorMessage.split(" ")[1],
                    akto_account_id: "1000000",
                    direction: null,
                    is_pending: "false"
                }
                
                setSampleData({
                    message: JSON.stringify(httpSample),
                    originalMessage: JSON.stringify(httpSample)
                })
            } catch (error) {
                console.error('Error parsing editableJsonData:', error)
            }
        } 
    }, [editableJsonData, currentUrl, errorMessage])

    const handleEditorDataChange = useCallback((newData) => {
        // Just store the data locally, don't call onJsonChange yet
        setEditorData(newData)
    }, [])

    const parseHttpRequest = (httpText) => {
        const lines = httpText.split('\n')
        const requestLine = lines[0].trim()
        
        // Parse request line: "GET GET /tokens/{cb-token-id} undefined"
        const requestParts = requestLine.split(' ')
        const method = requestParts[0] // First "GET"
        
        // Parse headers
        const headers = {}
        let bodyStartIndex = -1
        
        for (let i = 1; i < lines.length; i++) {
            const line = lines[i].trim()
            if (line === '') {
                bodyStartIndex = i + 1
                break
            }
            
            const colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                const headerName = line.substring(0, colonIndex).trim()
                const headerValue = line.substring(colonIndex + 1).trim()
                headers[headerName] = headerValue
            }
        }
        
        // Parse body (everything after empty line)
        let body = {}
        if (bodyStartIndex > 0 && bodyStartIndex < lines.length) {
            const bodyText = lines.slice(bodyStartIndex).join('\n').trim()
            if (bodyText && bodyText !== '{}') {
                try {
                    body = JSON.parse(bodyText)
                } catch (error) {
                    // If not JSON, treat as form data or plain text
                    body = bodyText
                }
            }
        }
        
        return {
            method,
            headers,
            body
        }
    }

    const handleRetryClick = useCallback(() => {
        if (editorData) {
            try {
                // Parse HTTP format to JSON
                const requestData = parseHttpRequest(editorData)
                onJsonChange(JSON.stringify(requestData))
            } catch (error) {
                console.error('Error parsing HTTP request:', error)
                onJsonChange(editorData)
            }
        }
        onRetry('retry')
    }, [editorData, onJsonChange, onRetry])
    return (
        <VerticalStack gap="4">
            <Card>
                <Box padding="4">
                    <VerticalStack gap="4">
                        <Text variant="headingMd" as="h3">
                            Configuration Required
                        </Text>
                        
                        <Box padding="3" style={{ backgroundColor: "var(--p-color-bg-surface-critical-subdued)" }}>
                            <Text variant="bodyMd" tone="critical">
                                {errorMessage} for {currentUrl}
                            </Text>
                        </Box>
                        {sampleData && (
                            <SampleDataComponent
                                type="request"
                                sampleData={sampleData}
                                minHeight="200px"
                                readOnly={false}
                                showResponse={false}
                                getEditorData={handleEditorDataChange}
                            />
                        )}
                        
                        <HorizontalStack gap="2">
                            <Button primary onClick={handleRetryClick}>
                                Retry
                            </Button>
                            <Button onClick={() => onRetry('discard')}>
                                Discard
                            </Button>
                        </HorizontalStack>
                    </VerticalStack>
                </Box>
            </Card>
        </VerticalStack>
    )
}

export default ConfigurationRequired

import { Modal, TextField, VerticalStack, HorizontalStack, Text, Button, Divider, Box } from "@shopify/polaris"
import { useState, useEffect } from "react"

function GuardrailSchemaModal({ open, onClose, endpoint, initialData, onSave, saving }) {
    const [requestMessageFields, setRequestMessageFields] = useState([])
    const [responseMessageFields, setResponseMessageFields] = useState([])
    const [blockedResponseCode, setBlockedResponseCode] = useState('')
    const [blockedResponseBody, setBlockedResponseBody] = useState('')
    const [blockedResponseContentType, setBlockedResponseContentType] = useState('')

    useEffect(() => {
        const schema = initialData?.guardrailSchema
        setRequestMessageFields(schema?.requestMessageFields || [])
        setResponseMessageFields(schema?.responseMessageFields || [])
        setBlockedResponseCode(schema?.blockedResponseCode ? String(schema.blockedResponseCode) : '')
        setBlockedResponseBody(schema?.blockedResponseBody || '')
        setBlockedResponseContentType(schema?.blockedResponseContentType || '')
    }, [initialData])

    const addField = (setter) => setter(prev => [...prev, { id: Date.now() + Math.random(), fieldPath: '', description: '' }])
    const removeField = (setter, index) => setter(prev => prev.filter((_, i) => i !== index))
    const updateField = (setter, index, key, value) =>
        setter(prev => prev.map((f, i) => i === index ? { ...f, [key]: value } : f))

    const renderFieldList = (fields, setter, label, placeholder) => (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between">
                <Text variant="headingSm">{label}</Text>
                <Button size="slim" onClick={() => addField(setter)}>Add field</Button>
            </HorizontalStack>
            {fields.length === 0 && (
                <Text variant="bodyMd" color="subdued">No fields configured. Click "Add field" to specify JSON paths.</Text>
            )}
            {fields.map((field, idx) => (
                <HorizontalStack key={field.id} gap="2" align="fill">
                    <Box width="45%">
                        <TextField
                            label="JSON path"
                            labelHidden
                            placeholder={placeholder}
                            value={field.fieldPath}
                            onChange={(v) => updateField(setter, idx, 'fieldPath', v)}
                            autoComplete="off"
                        />
                    </Box>
                    <Box width="45%">
                        <TextField
                            label="Description"
                            labelHidden
                            placeholder="Optional description"
                            value={field.description || ''}
                            onChange={(v) => updateField(setter, idx, 'description', v)}
                            autoComplete="off"
                        />
                    </Box>
                    <Button destructive size="slim" onClick={() => removeField(setter, idx)}>
                        Remove
                    </Button>
                </HorizontalStack>
            ))}
        </VerticalStack>
    )

    const handleSave = () => {
        const code = blockedResponseCode ? parseInt(blockedResponseCode, 10) : null
        onSave({
            requestMessageFields,
            responseMessageFields,
            blockedResponseCode: code,
            blockedResponseBody: blockedResponseBody || null,
            blockedResponseContentType: blockedResponseContentType || null,
        })
    }

    return (
        <Modal
            open={open}
            onClose={onClose}
            title={`Configure guardrail schema${endpoint ? ': ' + endpoint : ''}`}
            primaryAction={{ content: 'Save', onAction: handleSave, loading: saving }}
            secondaryActions={[{ content: 'Clear schema', onAction: () => onSave(null), destructive: true }, { content: 'Cancel', onAction: onClose }]}
            large
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {renderFieldList(
                        requestMessageFields,
                        setRequestMessageFields,
                        'Request message fields',
                        'e.g. messages[-1].content'
                    )}
                    <Divider />
                    {renderFieldList(
                        responseMessageFields,
                        setResponseMessageFields,
                        'Response message fields',
                        'e.g. choices[0].message.content'
                    )}
                    <Divider />
                    <TextField
                        label="Blocked response code"
                        type="number"
                        value={blockedResponseCode}
                        onChange={setBlockedResponseCode}
                        placeholder="e.g. 403"
                        autoComplete="off"
                        helpText="HTTP status code returned when guardrail blocks a request"
                    />
                    <TextField
                        label="Blocked response body"
                        multiline={4}
                        value={blockedResponseBody}
                        onChange={setBlockedResponseBody}
                        placeholder='e.g. {"error": "Request blocked by guardrail"}'
                        autoComplete="off"
                    />
                    <TextField
                        label="Blocked response content type"
                        value={blockedResponseContentType}
                        onChange={setBlockedResponseContentType}
                        placeholder="e.g. application/json"
                        autoComplete="off"
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default GuardrailSchemaModal

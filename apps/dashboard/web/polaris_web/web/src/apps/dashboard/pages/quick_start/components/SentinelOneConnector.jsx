import { Box, Button, ChoiceList, Divider, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import { useState, useEffect } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

function SentinelOneConnector() {
    // Config
    const [apiToken, setApiToken] = useState('')
    const [consoleUrl, setConsoleUrl] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')
    const [recurringIntervalSeconds, setRecurringIntervalSeconds] = useState('3600')
    const [isSaved, setIsSaved] = useState(false)

    // Guardrails
    const [guardrailTypes, setGuardrailTypes] = useState([])
    const [selectedGuardrailTypes, setSelectedGuardrailTypes] = useState([])
    const [guardrailEnvVars, setGuardrailEnvVars] = useState({})
    const [executingGuardrails, setExecutingGuardrails] = useState(false)

    useEffect(() => {
        api.fetchSentinelOneIntegration().then((res) => {
            if (res && res.sentinelOneIntegration) {
                const integration = res.sentinelOneIntegration
                setConsoleUrl(integration.consoleUrl || '')
                setDataIngestionUrl(integration.dataIngestionUrl || '')
                setRecurringIntervalSeconds(String(integration.recurringIntervalSeconds || 3600))
                setIsSaved(true)
                // Fetch guardrail types when integration is configured
                fetchGuardrailTypes()
            }
        }).catch(() => {})
    }, [])

    const fetchGuardrailTypes = () => {
        api.getGuardrailTypes().then((res) => {
            setGuardrailTypes(res.guardrailTypes || [])
        }).catch(() => {
            func.setToast(true, true, 'Failed to fetch guardrail types')
        })
    }

    // ── Config ──────────────────────────────────────────────────────────────

    const handleSave = () => {
        api.addSentinelOneIntegration(
            apiToken || null, consoleUrl, dataIngestionUrl,
            parseInt(recurringIntervalSeconds) || 3600
        ).then(() => {
            setApiToken('')
            setIsSaved(true)
            func.setToast(true, false, 'SentinelOne integration saved successfully')
        }).catch(() => func.setToast(true, true, 'Failed to save SentinelOne integration'))
    }

    const handleRemove = () => {
        api.removeSentinelOneIntegration().then(() => {
            setApiToken(''); setConsoleUrl(''); setDataIngestionUrl('')
            setRecurringIntervalSeconds('3600'); setIsSaved(false)
            func.setToast(true, false, 'SentinelOne integration removed successfully')
        }).catch(() => func.setToast(true, true, 'Failed to remove SentinelOne integration'))
    }

    // ── Guardrails ───────────────────────────────────────────────────────────

    const handleSaveGuardrails = () => {
        if (selectedGuardrailTypes.length === 0) {
            func.setToast(true, true, 'Please select at least one guardrail')
            return
        }

        setExecutingGuardrails(true)
        // Call API once with all selected guardrail types
        api.saveGuardrailsConfig(selectedGuardrailTypes, guardrailEnvVars, 'all', [])
            .then((result) => {
                const execution = result.guardrailExecution || {}
                const totalSuccess = execution.successCount || 0
                const totalFail = execution.failCount || 0
                const total = execution.totalCount || 0
                
                func.setToast(true, false, 
                    `Guardrails executed: ${totalSuccess}/${total} successful, ${totalFail} failed`)
            })
            .catch(() => func.setToast(true, true, 'Failed to save and execute guardrails'))
            .finally(() => setExecutingGuardrails(false))
    }

    // ── Derived ──────────────────────────────────────────────────────────────

    const isSaveDisabled = !consoleUrl || !dataIngestionUrl || (!apiToken && !isSaved)

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect SentinelOne to Akto to detect AI coding tools running on managed endpoints.
            </Text>

            <Box paddingBlockStart="3"><Divider /></Box>

            {/* Config */}
            <VerticalStack gap="2">
                <TextField label="SentinelOne Console URL" value={consoleUrl} onChange={setConsoleUrl}
                    placeholder="https://usea1-partners.sentinelone.net" requiredIndicator autoComplete="off" />
                <PasswordTextField label="API Token" onFunc={true} setField={setApiToken} field={apiToken} requiredIndicator />
                <TextField label="Data Ingestion Service URL" value={dataIngestionUrl} onChange={setDataIngestionUrl}
                    requiredIndicator autoComplete="off" />
                <TextField label="Polling Interval (seconds)" value={recurringIntervalSeconds}
                    onChange={setRecurringIntervalSeconds} type="number" autoComplete="off" />
            </VerticalStack>

            <HorizontalStack align='end' gap="2">
                <Button disabled={!isSaved} onClick={handleRemove} destructive>Remove</Button>
                <Button disabled={isSaveDisabled} onClick={handleSave} primary>Save</Button>
            </HorizontalStack>

            {isSaved && (
                <VerticalStack gap="4">
                    <Box paddingBlockStart="1"><Divider /></Box>

                    {/* Guardrails Installation */}
                    <VerticalStack gap="4">
                        <Text variant="headingMd">Guardrails Installation</Text>
                        <Text variant="bodySm" color="subdued">
                            Install security guardrails on your SentinelOne managed endpoints
                        </Text>

                        <VerticalStack gap="3">
                            <Text variant="headingSm">Guardrails</Text>
                            
                            {guardrailTypes.map((guardrail) => {
                                const isSelected = selectedGuardrailTypes.includes(guardrail.type)
                                
                                return (
                                    <VerticalStack gap="2" key={guardrail.type}>
                                        <ChoiceList
                                            choices={[{
                                                label: guardrail.displayName,
                                                value: guardrail.type,
                                                helpText: guardrail.description
                                            }]}
                                            selected={isSelected ? [guardrail.type] : []}
                                            onChange={(selected) => {
                                                if (selected.length > 0) {
                                                    // Add to selection
                                                    if (!selectedGuardrailTypes.includes(guardrail.type)) {
                                                        setSelectedGuardrailTypes([...selectedGuardrailTypes, guardrail.type])
                                                    }
                                                } else {
                                                    // Remove from selection
                                                    setSelectedGuardrailTypes(selectedGuardrailTypes.filter(t => t !== guardrail.type))
                                                }
                                            }}
                                        />
                                        
                                        {isSelected && guardrail.envVars && guardrail.envVars.length > 0 && (
                                            <Box paddingInlineStart="6">
                                                <VerticalStack gap="2">
                                                    {guardrail.envVars.map((envVar, idx) => (
                                                        <TextField
                                                            key={idx}
                                                            label={envVar.label}
                                                            placeholder={envVar.placeholder}
                                                            value={guardrailEnvVars[envVar.name] || ''}
                                                            onChange={(val) => setGuardrailEnvVars({
                                                                ...guardrailEnvVars,
                                                                [envVar.name]: val
                                                            })}
                                                            requiredIndicator={envVar.required === 'true'}
                                                            autoComplete="off"
                                                        />
                                                    ))}
                                                </VerticalStack>
                                            </Box>
                                        )}
                                    </VerticalStack>
                                )
                            })}
                        </VerticalStack>

                        {selectedGuardrailTypes.length > 0 && (
                            <HorizontalStack align="end">
                                <Button
                                    primary
                                    onClick={handleSaveGuardrails}
                                    loading={executingGuardrails}
                                    disabled={executingGuardrails}
                                >
                                    Save & Run Guardrails
                                </Button>
                            </HorizontalStack>
                        )}
                    </VerticalStack>
                </VerticalStack>
            )}
        </div>
    )
}

export default SentinelOneConnector

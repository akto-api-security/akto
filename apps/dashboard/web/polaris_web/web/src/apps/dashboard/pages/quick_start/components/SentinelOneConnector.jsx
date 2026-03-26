import { Box, Button, Checkbox, Combobox, Divider, HorizontalStack, Icon, Listbox, Tag, Text, TextField, VerticalStack } from '@shopify/polaris'
import { SearchMinor } from '@shopify/polaris-icons'
import { useState, useEffect, useMemo, useCallback } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

function SentinelOneConnector() {
    // Config
    const [apiToken, setApiToken] = useState('')
    const [consoleUrl, setConsoleUrl] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')
    const [guardrailsUrl, setGuardrailsUrl] = useState('')
    const [recurringIntervalSeconds, setRecurringIntervalSeconds] = useState('3600')
    const [isSaved, setIsSaved] = useState(false)

    // Guardrails
    const [guardrailTypes, setGuardrailTypes] = useState([])
    const [selectedGuardrailTypes, setSelectedGuardrailTypes] = useState([])
    const [executingGuardrails, setExecutingGuardrails] = useState(false)
    const [envVarValues, setEnvVarValues] = useState({})

    // Device selection
    const [agents, setAgents] = useState([])
    const [runOnAllDevices, setRunOnAllDevices] = useState(true)
    const [selectedAgentIds, setSelectedAgentIds] = useState([])
    const [searchInput, setSearchInput] = useState('')

    const allOptions = useMemo(() =>
        agents.map((agent) => ({
            value: agent.id,
            label: agent.computerName || agent.id,
            os: agent.osName || 'Unknown OS',
        })),
        [agents]
    )

    const filteredOptions = useMemo(() => {
        return allOptions.filter((opt) =>
            opt.label.toLowerCase().includes(searchInput.toLowerCase())
        )
    }, [allOptions, searchInput])

    const toggleDevice = useCallback((id) => {
        setSelectedAgentIds((prev) =>
            prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
        )
    }, [])

    const removeDevice = useCallback((id) => {
        setSelectedAgentIds((prev) => prev.filter((x) => x !== id))
    }, [])

    useEffect(() => {
        // Fetch available guardrail types from backend
        api.getGuardrailTypes().then((res) => {
            if (res && res.guardrailTypes) {
                setGuardrailTypes(res.guardrailTypes)
            }
        }).catch(() => {})

        // Fetch existing integration config
        api.fetchSentinelOneIntegration().then((res) => {
            if (res && res.sentinelOneIntegration) {
                const integration = res.sentinelOneIntegration
                setConsoleUrl(integration.consoleUrl || '')
                setDataIngestionUrl(integration.dataIngestionUrl || '')
                setGuardrailsUrl(integration.guardrailsUrl || '')
                setRecurringIntervalSeconds(String(integration.recurringIntervalSeconds || 3600))
                setIsSaved(true)
                
                // Load saved guardrail configuration
                if (integration.guardrailType && integration.guardrailType.length > 0) {
                    setSelectedGuardrailTypes(integration.guardrailType)
                }
                
                // Load saved env vars
                if (integration.guardrailEnvVars) {
                    setEnvVarValues(integration.guardrailEnvVars)
                }

                // Load saved target mode and agent IDs
                if (integration.guardrailTargetMode) {
                    setRunOnAllDevices(integration.guardrailTargetMode === 'all')
                }
                if (integration.guardrailAgentIds && integration.guardrailAgentIds.length > 0) {
                    setSelectedAgentIds(integration.guardrailAgentIds)
                }

                // Fetch agents list
                api.fetchSentinelOneAgents().then((agentsRes) => {
                    if (agentsRes && agentsRes.agents) {
                        setAgents(agentsRes.agents)
                    }
                }).catch(() => {})
            }
        }).catch(() => {})
    }, [])

    // ── Config ──────────────────────────────────────────────────────────────

    const handleSave = () => {
        api.addSentinelOneIntegration(
            apiToken || null, consoleUrl, dataIngestionUrl, guardrailsUrl,
            parseInt(recurringIntervalSeconds) || 3600
        ).then(() => {
            setApiToken('')
            setIsSaved(true)
            func.setToast(true, false, 'SentinelOne integration saved successfully')
        }).catch(() => func.setToast(true, true, 'Failed to save SentinelOne integration'))
    }

    const handleRemove = () => {
        api.removeSentinelOneIntegration().then(() => {
            setApiToken(''); setConsoleUrl(''); setDataIngestionUrl(''); setGuardrailsUrl('')
            setRecurringIntervalSeconds('3600'); setIsSaved(false)
            func.setToast(true, false, 'SentinelOne integration removed successfully')
        }).catch(() => func.setToast(true, true, 'Failed to remove SentinelOne integration'))
    }

    // ── Guardrails ───────────────────────────────────────────────────────────

    const handleGuardrailToggle = (type) => {
        setSelectedGuardrailTypes(prev => 
            prev.includes(type) ? prev.filter(t => t !== type) : [...prev, type]
        )
    }

    const handleSaveGuardrails = () => {
        if (selectedGuardrailTypes.length === 0) {
            func.setToast(true, true, 'Please select at least one guardrail')
            return
        }

        if (!runOnAllDevices && selectedAgentIds.length === 0) {
            func.setToast(true, true, 'Please select at least one device')
            return
        }

        // AKTO_GUARDRAILS_URL is common for all
        const guardrailEnvVars = {
            'AKTO_GUARDRAILS_URL': guardrailsUrl,
            ...envVarValues
        }

        const targetMode = runOnAllDevices ? 'all' : 'selected'
        const agentIds = runOnAllDevices ? [] : selectedAgentIds

        setExecutingGuardrails(true)
        api.saveGuardrailsConfig(selectedGuardrailTypes, guardrailEnvVars, targetMode, agentIds)
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

    const isSaveDisabled = !consoleUrl || !dataIngestionUrl || !guardrailsUrl || (!apiToken && !isSaved)

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
                <TextField label="Guardrails URL" value={guardrailsUrl} onChange={setGuardrailsUrl}
                    placeholder="https://your-guardrails-endpoint.akto.io" requiredIndicator autoComplete="off"
                    helpText="Common URL for all guardrails (AKTO_GUARDRAILS_URL)" />
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
                            Select and configure security guardrails for your SentinelOne managed endpoints
                        </Text>

                        <VerticalStack gap="3">
                            {guardrailTypes.map((guardrail) => {
                                const isSelected = selectedGuardrailTypes.includes(guardrail.type)
                                const hasEnvVars = guardrail.envVars && guardrail.envVars.length > 0
                                
                                return (
                                    <VerticalStack key={guardrail.type} gap="2">
                                        <Checkbox
                                            label={guardrail.displayName}
                                            checked={isSelected}
                                            onChange={() => handleGuardrailToggle(guardrail.type)}
                                            helpText={guardrail.description}
                                        />
                                        
                                        {isSelected && hasEnvVars && (
                                            <Box paddingInlineStart="6">
                                                <VerticalStack gap="2">
                                                    <Text variant="bodySm" fontWeight="semibold" color="subdued">
                                                        {guardrail.displayName} Configuration:
                                                    </Text>
                                                    {guardrail.envVars.map((envVar) => (
                                                        <TextField
                                                            key={envVar.name}
                                                            label={envVar.label}
                                                            placeholder={envVar.placeholder}
                                                            value={envVarValues[envVar.name] || ''}
                                                            onChange={(value) => setEnvVarValues(prev => ({
                                                                ...prev,
                                                                [envVar.name]: value
                                                            }))}
                                                            requiredIndicator={envVar.required === 'true'}
                                                            autoComplete="off"
                                                            helpText={`${envVar.name} for ${guardrail.displayName}`}
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
                            <VerticalStack gap="3">
                                <Box paddingBlockStart="2">
                                    <VerticalStack gap="2">
                                        <Checkbox
                                            label="Run on all devices"
                                            checked={runOnAllDevices}
                                            onChange={(checked) => {
                                                setRunOnAllDevices(checked)
                                            }}
                                            helpText="Install guardrails on all SentinelOne managed endpoints"
                                        />

                                        {!runOnAllDevices && (
                                            <Box paddingInlineStart="6">
                                                <VerticalStack gap="2">
                                                    <Combobox
                                                        allowMultiple
                                                        activator={
                                                            <Combobox.TextField
                                                                prefix={<Icon source={SearchMinor} />}
                                                                onChange={setSearchInput}
                                                                label="Search devices"
                                                                value={searchInput}
                                                                placeholder="Search and select devices..."
                                                                autoComplete="off"
                                                            />
                                                        }
                                                    >
                                                        {filteredOptions.length > 0 ? (
                                                            <Listbox onSelect={toggleDevice}>
                                                                {filteredOptions.map((opt) => (
                                                                    <Listbox.Option
                                                                        key={opt.value}
                                                                        value={opt.value}
                                                                        selected={selectedAgentIds.includes(opt.value)}
                                                                        accessibilityLabel={opt.label}
                                                                    >
                                                                        {`${opt.label} (${opt.os})`}
                                                                    </Listbox.Option>
                                                                ))}
                                                            </Listbox>
                                                        ) : null}
                                                    </Combobox>
                                                    {selectedAgentIds.length > 0 && (
                                                        <HorizontalStack gap="1" wrap>
                                                            {selectedAgentIds.map((id) => {
                                                                const opt = allOptions.find((o) => o.value === id)
                                                                return (
                                                                    <Tag key={id} onRemove={() => removeDevice(id)}>
                                                                        {opt ? `${opt.label} (${opt.os})` : id}
                                                                    </Tag>
                                                                )
                                                            })}
                                                        </HorizontalStack>
                                                    )}
                                                    <Text variant="bodySm" color="subdued">
                                                        {selectedAgentIds.length} of {agents.length} device(s) selected
                                                    </Text>
                                                </VerticalStack>
                                            </Box>
                                        )}
                                    </VerticalStack>
                                </Box>

                                <HorizontalStack align="end">
                                    <Button
                                        primary
                                        onClick={handleSaveGuardrails}
                                        loading={executingGuardrails}
                                        disabled={executingGuardrails || (!runOnAllDevices && selectedAgentIds.length === 0)}
                                    >
                                        Save & Run Guardrails
                                    </Button>
                                </HorizontalStack>
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </VerticalStack>
            )}
        </div>
    )
}

export default SentinelOneConnector

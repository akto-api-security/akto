import { Box, Button, Checkbox, Combobox, Divider, HorizontalStack, Icon, Listbox, Tag, Text, TextField, VerticalStack } from '@shopify/polaris'
import { SearchMinor } from '@shopify/polaris-icons'
import { useState, useEffect, useMemo, useCallback } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

function CrowdStrikeConnector() {
    // Config
    const [clientId, setClientId] = useState('')
    const [clientSecret, setClientSecret] = useState('')
    const [baseUrl, setBaseUrl] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')
    const [aktoApiToken, setAktoApiToken] = useState('')
    const [recurringIntervalSeconds, setRecurringIntervalSeconds] = useState('21600')
    const [isSaved, setIsSaved] = useState(false)

    // Guardrails
    const [guardrailTypes, setGuardrailTypes] = useState([])
    const [selectedGuardrailTypes, setSelectedGuardrailTypes] = useState([])
    const [executingGuardrails, setExecutingGuardrails] = useState(false)
    const [envVarValues, setEnvVarValues] = useState({})

    // Device selection (guardrails)
    const [devices, setDevices] = useState([])
    const [runOnAllDevices, setRunOnAllDevices] = useState(true)
    const [selectedDeviceIds, setSelectedDeviceIds] = useState([])
    const [searchInput, setSearchInput] = useState('')

    // Device selection (discovery job targeting)
    const [runDiscoveryOnAllDevices, setRunDiscoveryOnAllDevices] = useState(true)
    const [selectedDiscoveryDeviceIds, setSelectedDiscoveryDeviceIds] = useState([])
    const [discoverySearchInput, setDiscoverySearchInput] = useState('')
    const [schedulingDiscovery, setSchedulingDiscovery] = useState(false)

    const allOptions = useMemo(() =>
        devices.map((d) => {
            const os = d.platform || 'Unknown OS'
            const identifier = d.hostname || d.id
            const label = d.username ? `${d.username} — ${identifier}` : identifier
            return { value: d.id, label, os, deviceId: d.id }
        }),
        [devices]
    )

    const filteredOptions = useMemo(() =>
        allOptions.filter((opt) =>
            opt.label.toLowerCase().includes(searchInput.toLowerCase())
        ),
        [allOptions, searchInput]
    )

    const discoveryFilteredOptions = useMemo(() =>
        allOptions.filter((opt) =>
            opt.label.toLowerCase().includes(discoverySearchInput.toLowerCase())
        ),
        [allOptions, discoverySearchInput]
    )

    const toggleDevice = useCallback((id) => {
        setSelectedDeviceIds((prev) =>
            prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
        )
    }, [])

    const removeDevice = useCallback((id) => {
        setSelectedDeviceIds((prev) => prev.filter((x) => x !== id))
    }, [])

    const toggleDiscoveryDevice = useCallback((id) => {
        setSelectedDiscoveryDeviceIds((prev) =>
            prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
        )
    }, [])

    const removeDiscoveryDevice = useCallback((id) => {
        setSelectedDiscoveryDeviceIds((prev) => prev.filter((x) => x !== id))
    }, [])

    useEffect(() => {
        api.fetchCrowdStrikeGuardrailTypes().then((res) => {
            if (res && res.guardrailTypes) setGuardrailTypes(res.guardrailTypes)
        }).catch(() => {})

        api.fetchCrowdStrikeIntegration().then((res) => {
            if (res && res.crowdStrikeIntegration) {
                const integration = res.crowdStrikeIntegration
                setClientId(integration.clientId || '')
                setBaseUrl(integration.baseUrl || '')
                setDataIngestionUrl(integration.dataIngestionUrl || '')
                setRecurringIntervalSeconds(String(integration.recurringIntervalSeconds || 21600))
                setIsSaved(true)

                api.fetchCrowdStrikeDevices().then((devRes) => {
                    if (devRes && devRes.devices) setDevices(devRes.devices)
                }).catch(() => {})
            }
        }).catch(() => {})
    }, [])

    // ── Config ───────────────────────────────────────────────────────────────

    const handleSave = () => {
        api.addCrowdStrikeIntegration(
            clientId, clientSecret || null, baseUrl || null,
            dataIngestionUrl, aktoApiToken || null, parseInt(recurringIntervalSeconds) || 21600
        ).then(() => {
            setClientSecret('')
            setAktoApiToken('')
            setIsSaved(true)
            func.setToast(true, false, 'CrowdStrike integration saved successfully')
        }).catch(() => func.setToast(true, true, 'Failed to save CrowdStrike integration'))
    }

    const handleRemove = () => {
        api.removeCrowdStrikeIntegration().then(() => {
            setClientId(''); setClientSecret(''); setBaseUrl('')
            setDataIngestionUrl(''); setAktoApiToken(''); setRecurringIntervalSeconds('21600'); setIsSaved(false)
            func.setToast(true, false, 'CrowdStrike integration removed successfully')
        }).catch(() => func.setToast(true, true, 'Failed to remove CrowdStrike integration'))
    }

    // ── Guardrails ───────────────────────────────────────────────────────────

    const handleGuardrailToggle = (type) => {
        setSelectedGuardrailTypes((prev) =>
            prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]
        )
    }

    const handleSaveGuardrails = () => {
        if (selectedGuardrailTypes.length === 0) {
            func.setToast(true, true, 'Please select at least one guardrail')
            return
        }
        if (!runOnAllDevices && selectedDeviceIds.length === 0) {
            func.setToast(true, true, 'Please select at least one device')
            return
        }

        const guardrailEnvVars = { ...envVarValues }
        const targetMode = runOnAllDevices ? 'all' : 'select'
        const deviceIds = runOnAllDevices ? [] : selectedDeviceIds

        setExecutingGuardrails(true)
        api.saveCrowdStrikeGuardrailsConfig(selectedGuardrailTypes, guardrailEnvVars, targetMode, deviceIds)
            .then((result) => {
                const execution = result.guardrailExecution || {}
                const s = execution.successCount || 0
                const d = execution.dispatchedCount || 0
                const f = execution.failCount || 0
                const t = execution.totalCount || 0
                const dispatched = s + d
                func.setToast(true, f > 0, `Guardrails: ${dispatched}/${t} dispatched${f > 0 ? `, ${f} failed` : ''}`)
            })
            .catch(() => func.setToast(true, true, 'Failed to save and execute guardrails'))
            .finally(() => setExecutingGuardrails(false))
    }

    // ── Discovery job targeting ──────────────────────────────────────────────

    const handleScheduleDiscovery = () => {
        if (!runDiscoveryOnAllDevices && selectedDiscoveryDeviceIds.length === 0) {
            func.setToast(true, true, 'Please select at least one device')
            return
        }

        const targetMode = runDiscoveryOnAllDevices ? 'all' : 'select'
        const deviceIds = runDiscoveryOnAllDevices ? [] : selectedDiscoveryDeviceIds

        setSchedulingDiscovery(true)
        api.scheduleCrowdStrikeDiscovery(targetMode, deviceIds)
            .then(() => {
                func.setToast(true, false, 'CrowdStrike discovery scheduled successfully')
            })
            .catch(() => func.setToast(true, true, 'Failed to schedule CrowdStrike discovery'))
            .finally(() => setSchedulingDiscovery(false))
    }

    const isSaveDisabled = !clientId || !dataIngestionUrl || (!clientSecret && !isSaved) || (!aktoApiToken && !isSaved)

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Connect CrowdStrike Falcon to Akto to detect AI coding tools (Claude, Cursor, Copilot) running on managed endpoints.
            </Text>

            <Box paddingBlockStart="3"><Divider /></Box>

            {/* Config */}
            <VerticalStack gap="2">
                <TextField label="Client ID" value={clientId} onChange={setClientId} requiredIndicator autoComplete="off" />
                <PasswordTextField label="Client Secret" onFunc={true} setField={setClientSecret} field={clientSecret} requiredIndicator />
                <TextField
                    label="Base URL"
                    value={baseUrl}
                    onChange={setBaseUrl}
                    placeholder="https://api.crowdstrike.com"
                    helpText="Leave empty to use the default CrowdStrike API endpoint"
                    autoComplete="off"
                />
                <TextField
                    label="Data Ingestion Service URL"
                    value={dataIngestionUrl}
                    onChange={setDataIngestionUrl}
                    requiredIndicator
                    autoComplete="off"
                    helpText="Common URL for data ingestion and guardrails (AKTO_DATA_INGESTION_URL)"
                />
                <PasswordTextField
                    label="Akto API Token"
                    onFunc={true}
                    setField={setAktoApiToken}
                    field={aktoApiToken}
                    requiredIndicator={!isSaved}
                    helpText="Common token used by all guardrails hook installs (AKTO_API_TOKEN)"
                />
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

                    <VerticalStack gap="4">
                        <Text variant="headingMd">Discovery Targeting</Text>
                        <Text variant="bodySm" color="subdued">
                            Choose which devices the recurring discovery job (AI agent, MCP config, and skills scan) runs against. This is read-only — it does not modify anything on the endpoint.
                        </Text>

                        <Checkbox
                            label="Run on all devices"
                            checked={runDiscoveryOnAllDevices}
                            onChange={(checked) => setRunDiscoveryOnAllDevices(checked)}
                            helpText="Run discovery on all CrowdStrike Falcon managed endpoints"
                        />
                        {!runDiscoveryOnAllDevices && (
                            <Box paddingInlineStart="6">
                                <VerticalStack gap="2">
                                    <Combobox
                                        allowMultiple
                                        activator={
                                            <Combobox.TextField
                                                prefix={<Icon source={SearchMinor} />}
                                                onChange={setDiscoverySearchInput}
                                                label="Search devices"
                                                value={discoverySearchInput}
                                                placeholder="Search by username or hostname..."
                                                autoComplete="off"
                                            />
                                        }
                                    >
                                        {discoveryFilteredOptions.length > 0 ? (
                                            <Listbox onSelect={toggleDiscoveryDevice}>
                                                {discoveryFilteredOptions.map((opt) => (
                                                    <Listbox.Option
                                                        key={opt.value}
                                                        value={opt.value}
                                                        selected={selectedDiscoveryDeviceIds.includes(opt.value)}
                                                        accessibilityLabel={opt.label}
                                                    >
                                                        {`${opt.label} (${opt.os}) [${opt.deviceId}]`}
                                                    </Listbox.Option>
                                                ))}
                                            </Listbox>
                                        ) : null}
                                    </Combobox>
                                    {selectedDiscoveryDeviceIds.length > 0 && (
                                        <HorizontalStack gap="1" wrap>
                                            {selectedDiscoveryDeviceIds.map((id) => {
                                                const opt = allOptions.find((o) => o.value === id)
                                                return (
                                                    <Tag key={id} onRemove={() => removeDiscoveryDevice(id)}>
                                                        {opt ? `${opt.label} (${opt.os})` : id}
                                                    </Tag>
                                                )
                                            })}
                                        </HorizontalStack>
                                    )}
                                    <Text variant="bodySm" color="subdued">
                                        {selectedDiscoveryDeviceIds.length} of {devices.length} device(s) selected
                                    </Text>
                                </VerticalStack>
                            </Box>
                        )}

                        <HorizontalStack align="end">
                            <Button
                                primary
                                onClick={handleScheduleDiscovery}
                                loading={schedulingDiscovery}
                                disabled={schedulingDiscovery || (!runDiscoveryOnAllDevices && selectedDiscoveryDeviceIds.length === 0)}
                            >
                                Run Discovery
                            </Button>
                        </HorizontalStack>
                    </VerticalStack>

                    <Box paddingBlockStart="1"><Divider /></Box>

                    <VerticalStack gap="4">
                        <Text variant="headingMd">Guardrails Installation</Text>
                        <Text variant="bodySm" color="subdued">
                            Select and configure security guardrails for your CrowdStrike Falcon managed endpoints
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
                                                            onChange={(value) => setEnvVarValues((prev) => ({ ...prev, [envVar.name]: value }))}
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
                                            onChange={(checked) => setRunOnAllDevices(checked)}
                                            helpText="Install guardrails on all CrowdStrike Falcon managed endpoints"
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
                                                                placeholder="Search by username or hostname..."
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
                                                                        selected={selectedDeviceIds.includes(opt.value)}
                                                                        accessibilityLabel={opt.label}
                                                                    >
                                                                        {`${opt.label} (${opt.os}) [${opt.deviceId}]`}
                                                                    </Listbox.Option>
                                                                ))}
                                                            </Listbox>
                                                        ) : null}
                                                    </Combobox>
                                                    {selectedDeviceIds.length > 0 && (
                                                        <HorizontalStack gap="1" wrap>
                                                            {selectedDeviceIds.map((id) => {
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
                                                        {selectedDeviceIds.length} of {devices.length} device(s) selected
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
                                        disabled={executingGuardrails || (!runOnAllDevices && selectedDeviceIds.length === 0)}
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

export default CrowdStrikeConnector

import { Autocomplete, Badge, Box, Button, Divider, DropZone, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import { useState, useEffect, useCallback } from 'react'
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

    // Agents
    const [agents, setAgents] = useState([])
    const [loadingAgents, setLoadingAgents] = useState(false)

    // Remote Scripts
    const [uploadScriptName, setUploadScriptName] = useState('')
    const [uploadScriptContent, setUploadScriptContent] = useState('')
    const [uploadFileName, setUploadFileName] = useState('')
    const [uploadingScript, setUploadingScript] = useState(false)
    const [viewingScript, setViewingScript] = useState(null)
    const [executeScriptId, setExecuteScriptId] = useState('')
    const [selectedAgents, setSelectedAgents] = useState([])  // Array of {id, computerName}
    const [inputValue, setInputValue] = useState('')
    const [executeInputParams, setExecuteInputParams] = useState('')
    const [executingScript, setExecutingScript] = useState(false)
    const [executeTaskId, setExecuteTaskId] = useState('')
    const [taskStatuses, setTaskStatuses] = useState([])
    const [loadingTaskStatus, setLoadingTaskStatus] = useState(false)

    useEffect(() => {
        api.fetchSentinelOneIntegration().then((res) => {
            if (res && res.sentinelOneIntegration) {
                const integration = res.sentinelOneIntegration
                setConsoleUrl(integration.consoleUrl || '')
                setDataIngestionUrl(integration.dataIngestionUrl || '')
                setRecurringIntervalSeconds(String(integration.recurringIntervalSeconds || 3600))
                setIsSaved(true)
                // Fetch agents when integration is configured
                fetchAgents()
            }
        }).catch(() => {})
    }, [])

    const fetchAgents = () => {
        setLoadingAgents(true)
        api.fetchSentinelOneAgents().then((res) => {
            setAgents(res.agents || [])
        }).catch(() => {
            func.setToast(true, true, 'Failed to fetch agents')
        }).finally(() => setLoadingAgents(false))
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

    // ── Remote Scripts ───────────────────────────────────────────────────────

    const handleUploadScript = () => {
        if (!uploadScriptName.trim() || !uploadScriptContent.trim()) return
        setUploadingScript(true)
        api.uploadSentinelOneRemoteScript(uploadScriptName, uploadScriptContent, null).then((res) => {
            const id = res.scriptId || ''
            func.setToast(true, false, `Script uploaded${id ? ` (ID: ${id})` : ''}`)
            setUploadScriptName('')
            setUploadScriptContent('')
            setUploadFileName('')
            if (id) setExecuteScriptId(id)
        }).catch(() => func.setToast(true, true, 'Failed to upload script.'))
        .finally(() => setUploadingScript(false))
    }

    const handleExecuteScript = () => {
        const agentIdArray = selectedAgents.map(a => a.id)
        if (!executeScriptId || agentIdArray.length === 0) return
        setExecutingScript(true)
        setExecuteTaskId('')
        api.executeSentinelOneRemoteScript(
            executeScriptId, agentIdArray,
            `Akto: ${executeScriptId}`,
            executeInputParams || null
        ).then((res) => {
            setExecuteTaskId(res.parentTaskId || '')
            func.setToast(true, false, `Script dispatched${res.parentTaskId ? ` (task: ${res.parentTaskId})` : ''}`)
        }).catch(() => func.setToast(true, true, 'Failed to execute script.'))
        .finally(() => setExecutingScript(false))
    }

    const handleCheckTaskStatus = () => {
        if (!executeTaskId) return
        setLoadingTaskStatus(true)
        api.getSentinelOneScriptTaskStatus(executeTaskId).then((res) => {
            setTaskStatuses(res.remoteScripts || [])
        }).catch(() => func.setToast(true, true, 'Failed to fetch task status.'))
        .finally(() => setLoadingTaskStatus(false))
    }

    const handleViewScript = (id) => {
        api.getSentinelOneRemoteScriptContent(id).then((res) => {
            setViewingScript({ id, content: res.scriptContent || '' })
        }).catch(() => func.setToast(true, true, 'Failed to fetch script content.'))
    }

    // ── Derived ──────────────────────────────────────────────────────────────

    const isSaveDisabled = !consoleUrl || !dataIngestionUrl || (!apiToken && !isSaved)
    
    // Agent autocomplete options
    const agentOptions = agents.map(a => ({
        value: a.id,
        label: `${a.computerName} (${a.osName || 'Unknown OS'})`
    }))
    
    const updateAgentSelection = useCallback((selected) => {
        const newSelection = selected.map(id => {
            const agent = agents.find(a => a.id === id)
            return { id, computerName: agent?.computerName || id }
        })
        setSelectedAgents(newSelection)
    }, [agents])
    
    const removeAgent = (idToRemove) => {
        setSelectedAgents(selectedAgents.filter(a => a.id !== idToRemove))
    }

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

                    {/* Remote Scripts */}
                    <VerticalStack gap="4">

                        {/* Upload */}
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Upload Script</Text>
                            <Text variant="bodySm" color="subdued">
                                .sh for macOS/Linux · .ps1 for Windows
                            </Text>
                            <DropZone onDrop={(_d, accepted) => {
                                const file = accepted[0]
                                if (!file) return
                                setUploadFileName(file.name)
                                setUploadScriptName(file.name.replace(/\.[^.]+$/, ''))
                                const reader = new FileReader()
                                reader.onload = (ev) => setUploadScriptContent(ev.target.result)
                                reader.readAsText(file)
                            }} accept=".sh,.bash,.zsh,.ps1,.py" type="file">
                                {uploadFileName ? (
                                    <div style={{ padding: '8px 12px' }}>
                                        <Text>{uploadFileName}</Text>
                                    </div>
                                ) : (
                                    <DropZone.FileUpload actionTitle="Choose script file" actionHint="Accepts .sh (macOS/Linux), .ps1 (Windows)" />
                                )}
                            </DropZone>
                            {uploadFileName && (
                                <TextField
                                    label="Script name"
                                    value={uploadScriptName}
                                    onChange={setUploadScriptName}
                                    autoComplete="off"
                                    helpText="Auto-filled from filename — edit if needed"
                                />
                            )}
                            <HorizontalStack align="end" gap="2">
                                {uploadFileName && (
                                    <Button plain onClick={() => { setUploadFileName(''); setUploadScriptName(''); setUploadScriptContent('') }}>
                                        Remove
                                    </Button>
                                )}
                                <Button primary onClick={handleUploadScript}
                                    loading={uploadingScript}
                                    disabled={!uploadScriptName.trim() || !uploadScriptContent.trim() || uploadingScript}>
                                    Upload to Library
                                </Button>
                            </HorizontalStack>
                        </VerticalStack>

                        <Divider />

                        {/* Execute */}
                        <VerticalStack gap="3">
                            <Text variant="headingSm">Execute Script</Text>
                            <TextField
                                label="Script ID"
                                value={executeScriptId}
                                onChange={(v) => { setExecuteScriptId(v); setViewingScript(null) }}
                                placeholder="Script ID (auto-filled after upload)"
                                autoComplete="off"
                                connectedRight={
                                    <Button onClick={() => executeScriptId && handleViewScript(executeScriptId)} disabled={!executeScriptId}>
                                        View
                                    </Button>
                                }
                            />

                            {viewingScript && viewingScript.id === executeScriptId && (
                                <VerticalStack gap="2">
                                    <HorizontalStack align="space-between" blockAlign="center">
                                        <Text variant="bodySm" fontWeight="semibold">Script content</Text>
                                        <Button size="slim" onClick={() => setViewingScript(null)}>Close</Button>
                                    </HorizontalStack>
                                    <div style={{ background: 'var(--p-surface-subdued)', borderRadius: '4px', padding: '12px', overflowX: 'auto' }}>
                                        <pre style={{ margin: 0, fontSize: '12px', fontFamily: 'monospace', whiteSpace: 'pre-wrap' }}>
                                            {viewingScript.content}
                                        </pre>
                                    </div>
                                </VerticalStack>
                            )}

                            <VerticalStack gap="2">
                                <Text variant="bodySm" fontWeight="semibold">Select Agents</Text>
                                {loadingAgents ? (
                                    <Text variant="bodySm" color="subdued">Loading agents...</Text>
                                ) : agents.length === 0 ? (
                                    <Text variant="bodySm" color="subdued">No agents found. <Button plain onClick={fetchAgents}>Refresh</Button></Text>
                                ) : (
                                    <Autocomplete
                                        allowMultiple
                                        options={agentOptions}
                                        selected={selectedAgents.map(a => a.id)}
                                        onSelect={updateAgentSelection}
                                        textField={<Autocomplete.TextField
                                            onChange={setInputValue}
                                            value={inputValue}
                                            placeholder="Search agents by computer name"
                                            autoComplete="off"
                                        />}
                                    />
                                )}
                                {selectedAgents.length > 0 && (
                                    <HorizontalStack gap="2" wrap>
                                        {selectedAgents.map(agent => (
                                            <Badge key={agent.id} onDismiss={() => removeAgent(agent.id)}>
                                                {agent.computerName}
                                            </Badge>
                                        ))}
                                    </HorizontalStack>
                                )}
                                <Text variant="bodySm" color="subdued">
                                    {selectedAgents.length > 0 ? `${selectedAgents.length} agent(s) selected` : 'Select agents to execute script on'}
                                </Text>
                            </VerticalStack>

                            <TextField
                                label="Input parameters (optional)"
                                value={executeInputParams}
                                onChange={setExecuteInputParams}
                                placeholder="e.g. AKTO_URL=https://..."
                                autoComplete="off"
                            />

                            <HorizontalStack align="end" gap="2">
                                {executeTaskId && (
                                    <Button onClick={handleCheckTaskStatus} loading={loadingTaskStatus}>
                                        Check Status
                                    </Button>
                                )}
                                <Button primary onClick={handleExecuteScript}
                                    loading={executingScript}
                                    disabled={!executeScriptId || selectedAgents.length === 0 || executingScript}>
                                    Run on {selectedAgents.length > 0 ? `${selectedAgents.length} Device(s)` : 'Selected Devices'}
                                </Button>
                            </HorizontalStack>

                            {taskStatuses.length > 0 && (
                                <VerticalStack gap="2">
                                    <Text variant="headingSm">Task Status</Text>
                                    {taskStatuses.map((t, i) => (
                                        <HorizontalStack key={i} gap="2" blockAlign="center">
                                            <Text variant="bodySm" fontWeight="medium">{t.agentComputerName || '—'}</Text>
                                            <Badge status={t.status === 'completed' ? 'success' : t.status === 'failed' ? 'critical' : 'info'}>
                                                {t.status || 'unknown'}
                                            </Badge>
                                            {t.detailedStatus && <Text variant="bodySm" color="subdued">{t.detailedStatus}</Text>}
                                        </HorizontalStack>
                                    ))}
                                </VerticalStack>
                            )}
                        </VerticalStack>
                    </VerticalStack>
                </VerticalStack>
            )}
        </div>
    )
}

export default SentinelOneConnector

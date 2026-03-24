import { Badge, Box, Button, Combobox, Divider, DropZone, HorizontalStack, Icon, Listbox, Select, Spinner, Tag, Text, TextField, VerticalStack } from '@shopify/polaris'
import { ClipboardMinor, CollectionsMajor, SearchMinor } from '@shopify/polaris-icons'
import { useCallback, useMemo, useState, useEffect } from 'react'
import PasswordTextField from '../../../components/layouts/PasswordTextField'
import api from '../api'
import func from '@/util/func'

const DEFAULT_SEARCH_TERMS = ['Claude', 'Cursor']

const LOOKBACK_OPTIONS = [
    { label: 'Last 1 hour', value: '1' },
    { label: 'Last 6 hours', value: '6' },
    { label: 'Last 24 hours', value: '24' },
    { label: 'Last 48 hours', value: '48' },
    { label: 'Last 7 days', value: '168' },
]

// Group SDL result rows by device (tries common field names SentinelOne uses)
function groupSdlByDevice(rows) {
    const deviceField = ['endpointName', 'agentComputerName', 'computerName', 'hostname', 'srcIp']
        .find((f) => rows.length > 0 && rows[0][f] != null) || null
    if (!deviceField) return { 'All Devices': rows }
    return rows.reduce((acc, row) => {
        const key = row[deviceField] || 'Unknown Device'
        if (!acc[key]) acc[key] = []
        acc[key].push(row)
        return acc
    }, {})
}

function SentinelOneConnector() {
    const [activeTab, setActiveTab] = useState('apps')

    // Config
    const [apiToken, setApiToken] = useState('')
    const [consoleUrl, setConsoleUrl] = useState('')
    const [dataIngestionUrl, setDataIngestionUrl] = useState('')
    const [recurringIntervalSeconds, setRecurringIntervalSeconds] = useState('3600')
    const [isSaved, setIsSaved] = useState(false)

    // Shared: what tools to search for (drives both apps filter AND SDL query)
    const [searchTerms, setSearchTerms] = useState(DEFAULT_SEARCH_TERMS)
    const [termInput, setTermInput] = useState('')

    // Apps tab
    const [agents, setAgents] = useState([])
    const [loadingAgents, setLoadingAgents] = useState(false)
    const [selectedAgentIds, setSelectedAgentIds] = useState([])
    const [agentSearch, setAgentSearch] = useState('')
    const [allApps, setAllApps] = useState([])
    const [loadingApps, setLoadingApps] = useState(false)

    // SDL tab
    const [sdlLookback, setSdlLookback] = useState('24')
    const [sdlRunning, setSdlRunning] = useState(false)
    const [sdlResults, setSdlResults] = useState(null)

    // Remote Scripts tab
    const [remoteScripts, setRemoteScripts] = useState([])
    const [loadingScripts, setLoadingScripts] = useState(false)
    const [uploadScriptName, setUploadScriptName] = useState('')
    const [uploadScriptContent, setUploadScriptContent] = useState('')
    const [uploadFileName, setUploadFileName] = useState('')
    const [uploadingScript, setUploadingScript] = useState(false)
    const [viewingScript, setViewingScript] = useState(null)
    const [executeScriptId, setExecuteScriptId] = useState('')
    const [executeAgentIds, setExecuteAgentIds] = useState([])
    const [executeAgentSearch, setExecuteAgentSearch] = useState('')
    const [executeInputParams, setExecuteInputParams] = useState('')
    const [executingScript, setExecutingScript] = useState(false)
    const [executeTaskId, setExecuteTaskId] = useState('')
    const [taskStatuses, setTaskStatuses] = useState([])
    const [loadingTaskStatus, setLoadingTaskStatus] = useState(false)
    const [agentName, setAgentName] = useState('')
    const [savingAppsCollections, setSavingAppsCollections] = useState(false)
    const [savingSdlCollections, setSavingSdlCollections] = useState(false)

    useEffect(() => {
        api.fetchSentinelOneIntegration().then((res) => {
            if (res && res.sentinelOneIntegration) {
                const integration = res.sentinelOneIntegration
                setConsoleUrl(integration.consoleUrl || '')
                setDataIngestionUrl(integration.dataIngestionUrl || '')
                setRecurringIntervalSeconds(String(integration.recurringIntervalSeconds || 3600))
                setIsSaved(true)
                // Auto-fetch agents once integration is confirmed
                setLoadingAgents(true)
                api.fetchSentinelOneAgents().then((r) => {
                    if (r && r.agents) setAgents(r.agents)
                }).catch(() => {}).finally(() => setLoadingAgents(false))
            }
        }).catch(() => {})
    }, [])

    // ── Config ────────────────────────────────────────────────────────────────

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
            setAgents([]); setAllApps([]); setSelectedAgentIds([])
            setSearchTerms(DEFAULT_SEARCH_TERMS); setSdlResults(null)
            func.setToast(true, false, 'SentinelOne integration removed successfully')
        }).catch(() => func.setToast(true, true, 'Failed to remove SentinelOne integration'))
    }

    const handleGenerateToken = () => {
        api.generateSentinelOneApiToken()
            .then(() => func.setToast(true, false, 'API token regenerated successfully'))
            .catch(() => func.setToast(true, true, 'Failed to regenerate API token'))
    }

    // ── Search term management ────────────────────────────────────────────────

    const addTerm = (value) => {
        const trimmed = (value || termInput).trim()
        if (!trimmed || searchTerms.includes(trimmed)) { setTermInput(''); return }
        setSearchTerms((prev) => [...prev, trimmed])
        setTermInput('')
    }

    const removeTerm = useCallback((term) => {
        setSearchTerms((prev) => prev.filter((t) => t !== term))
    }, [])

    // ── Apps tab ─────────────────────────────────────────────────────────────

    const handleFetchAgents = () => {
        setLoadingAgents(true)
        setAgents([]); setAllApps([]); setSelectedAgentIds([])
        api.fetchSentinelOneAgents().then((res) => {
            if (res && res.agents) setAgents(res.agents)
        }).catch(() => {
            func.setToast(true, true, 'Failed to fetch devices. Ensure SentinelOne integration is configured.')
        }).finally(() => setLoadingAgents(false))
    }

    const fetchAppsForAgents = useCallback((ids) => {
        setLoadingApps(true)
        setAllApps([])
        api.fetchSentinelOneAgentApplications(ids && ids.length > 0 ? ids : null).then((res) => {
            setAllApps((res && res.agentApplications) ? res.agentApplications : [])
        }).catch(() => func.setToast(true, true, 'Failed to fetch applications.'))
        .finally(() => setLoadingApps(false))
    }, [])

    const allAgentOptions = useMemo(() =>
        agents.map((a) => ({ value: a.id, label: a.computerName || a.id, os: a.osName || '' })),
        [agents]
    )

    const filteredAgentOptions = useMemo(() => {
        if (!agentSearch) return allAgentOptions
        const re = new RegExp(agentSearch, 'i')
        return allAgentOptions.filter((o) => `${o.label} ${o.os}`.match(re))
    }, [allAgentOptions, agentSearch])

    const toggleAgent = useCallback((id) => {
        setSelectedAgentIds((prev) => {
            const next = prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id]
            fetchAppsForAgents(next.length > 0 ? next : null)
            return next
        })
        setAgentSearch('')
    }, [fetchAppsForAgents])

    const removeAgent = useCallback((id) => {
        setSelectedAgentIds((prev) => {
            const next = prev.filter((v) => v !== id)
            fetchAppsForAgents(next.length > 0 ? next : null)
            return next
        })
    }, [fetchAppsForAgents])

    const filteredResults = useMemo(() => {
        if (searchTerms.length === 0) return allApps
        return allApps.filter((app) =>
            searchTerms.some((t) => (app.name || '').toLowerCase().includes(t.toLowerCase()))
        )
    }, [allApps, searchTerms])

    const resultsByDevice = useMemo(() => {
        const map = {}
        filteredResults.forEach((app) => {
            const key = app.computerName || app.agentId || 'Unknown Device'
            if (!map[key]) map[key] = []
            map[key].push(app)
        })
        return map
    }, [filteredResults])

    // ── SDL tab ───────────────────────────────────────────────────────────────

    // ── Remote Scripts tab ────────────────────────────────────────────────────

    const handleListScripts = () => {
        setLoadingScripts(true)
        api.listSentinelOneRemoteScripts().then((res) => {
            setRemoteScripts(res.remoteScripts || [])
        }).catch(() => func.setToast(true, true, 'Failed to fetch remote scripts.'))
        .finally(() => setLoadingScripts(false))
    }

    const handleUploadScript = () => {
        if (!uploadScriptName.trim() || !uploadScriptContent.trim()) return
        setUploadingScript(true)
        api.uploadSentinelOneRemoteScript(uploadScriptName, uploadScriptContent, null).then((res) => {
            const id = res.scriptId || ''
            func.setToast(true, false, `Script uploaded${id ? ` (ID: ${id})` : ''} — select it below to run`)
            setUploadScriptName('')
            setUploadScriptContent('')
            setUploadFileName('')
            // Refresh library then auto-select the newly uploaded script
            handleListScripts()
            if (id) { setExecuteScriptId(id); setActiveTab('scripts') }
        }).catch(() => func.setToast(true, true, 'Failed to upload script.'))
        .finally(() => setUploadingScript(false))
    }

    const handleExecuteScript = () => {
        if (!executeScriptId || executeAgentIds.length === 0) return
        setExecutingScript(true)
        setExecuteTaskId('')
        api.executeSentinelOneRemoteScript(
            executeScriptId, executeAgentIds,
            `Akto: ${remoteScripts.find(s => s.id === executeScriptId)?.scriptName || executeScriptId}`,
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

    const toggleExecuteAgent = useCallback((id) => {
        setExecuteAgentIds((prev) => prev.includes(id) ? prev.filter(v => v !== id) : [...prev, id])
        setExecuteAgentSearch('')
    }, [])

    const filteredExecuteAgentOptions = useMemo(() => {
        if (!executeAgentSearch) return allAgentOptions
        const re = new RegExp(executeAgentSearch, 'i')
        return allAgentOptions.filter(o => `${o.label} ${o.os}`.match(re))
    }, [allAgentOptions, executeAgentSearch])

    const handleViewScript = (id, name) => {
        api.getSentinelOneRemoteScriptContent(id).then((res) => {
            setViewingScript({ id, name, content: res.scriptContent || '' })
        }).catch(() => func.setToast(true, true, 'Failed to fetch script content.'))
    }

    // ── SDL tab ───────────────────────────────────────────────────────────────

    const handleRunSdl = () => {
        setSdlRunning(true)
        setSdlResults(null)
        api.runSentinelOneSdlQuery(searchTerms, parseInt(sdlLookback)).then((res) => {
            setSdlResults(res.sdlResults || [])
            func.setToast(true, false, `Query returned ${(res.sdlResults || []).length} result(s)`)
        }).catch((err) => {
            func.setToast(true, true, err?.response?.data?.actionErrors?.[0] || 'SDL query failed.')
        }).finally(() => setSdlRunning(false))
    }

    const handleSaveAppsAsCollections = () => {
        if (!agentName.trim() || filteredResults.length === 0) return
        setSavingAppsCollections(true)
        api.ingestSentinelOneAgentApplications(filteredResults, agentName.trim()).then(() => {
            func.setToast(true, false, `Saved ${filteredResults.length} app(s) as collections`)
        }).catch(() => {
            func.setToast(true, true, 'Failed to save applications as collections')
        }).finally(() => setSavingAppsCollections(false))
    }

    const handleSaveSdlAsCollections = () => {
        if (!agentName.trim() || !processedSdlResults || processedSdlResults.length === 0) return
        setSavingSdlCollections(true)
        api.ingestSentinelOneSdlEvents(processedSdlResults, agentName.trim()).then(() => {
            func.setToast(true, false, `Saved ${processedSdlResults.length} event(s) as collections grouped by device`)
        }).catch(() => {
            func.setToast(true, true, 'Failed to save SDL events as collections')
        }).finally(() => setSavingSdlCollections(false))
    }

    const copyToClipboard = (text) => {
        navigator.clipboard.writeText(text)
        func.setToast(true, false, 'Copied to clipboard')
    }

    // ── Derived ───────────────────────────────────────────────────────────────

    const isSaveDisabled = !consoleUrl || !dataIngestionUrl || (!apiToken && !isSaved)

    const PROCESS_COLUMNS = useMemo(() => ['srcProcName', 'srcProcCmdLine', 'srcProcUser', 'endpointName',
        'srcProcStartTime', 'srcProcPid', 'objectType', 'eventType', 'agentComputerName', 'computerName'], [])

    const processedSdlResults = useMemo(() => {
        if (!sdlResults) return null
        return sdlResults
            .filter(r => !r.objectType || r.objectType === 'process')
            .map(r => {
                const clean = {}
                Object.entries(r).forEach(([k, v]) => {
                    if (typeof v === 'string') {
                        try { clean[k] = new TextDecoder('utf-8').decode(Uint8Array.from(v, c => c.charCodeAt(0))) }
                        catch { clean[k] = v }
                    } else {
                        clean[k] = v
                    }
                })
                return clean
            })
    }, [sdlResults])

    const sdlGrouped = processedSdlResults && processedSdlResults.length > 0 ? groupSdlByDevice(processedSdlResults) : null

    const sdlColumns = useMemo(() => {
        if (!processedSdlResults || processedSdlResults.length === 0) return []
        const allKeys = Object.keys(processedSdlResults[0])
        const preferred = PROCESS_COLUMNS.filter(c => allKeys.includes(c) && processedSdlResults.some(r => r[c] != null && r[c] !== '' && r[c] !== '—'))
        return preferred.length > 0 ? preferred : allKeys.slice(0, 8)
    }, [processedSdlResults, PROCESS_COLUMNS])

    const selectedAgentTags = selectedAgentIds.map((id) => {
        const opt = allAgentOptions.find((o) => o.value === id)
        return <Tag key={id} onRemove={() => removeAgent(id)}>{opt ? opt.label : id}</Tag>
    })

    const searchTermTags = searchTerms.map((t) => (
        <Tag key={t} onRemove={() => removeTerm(t)}>{t}</Tag>
    ))

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
                {isSaved && <Button onClick={handleGenerateToken}>Regenerate Token</Button>}
                <Button disabled={isSaveDisabled} onClick={handleSave} primary>Save</Button>
            </HorizontalStack>

            {isSaved && (
                <VerticalStack gap="4">
                    <Box paddingBlockStart="1"><Divider /></Box>

                    {/* Shared: what to search for */}
                    <VerticalStack gap="3">
                        <VerticalStack gap="1">
                            <Text variant="headingSm">What are you looking for?</Text>
                            <Text variant="bodySm" color="subdued">
                                Add tool names to detect. These drive both the app inventory check and process activity queries.
                            </Text>
                        </VerticalStack>

                        <HorizontalStack gap="2" blockAlign="end">
                            <div style={{ flex: 1 }}>
                                <TextField
                                    label="Tool name" labelHidden
                                    value={termInput}
                                    onChange={setTermInput}
                                    placeholder="e.g. Copilot, Windsurf, openclaw..."
                                    autoComplete="off"
                                    onKeyPress={(e) => { if (e.key === 'Enter') addTerm() }}
                                    connectedRight={
                                        <Button onClick={() => addTerm()} disabled={!termInput.trim()}>Add</Button>
                                    }
                                />
                            </div>
                        </HorizontalStack>

                        {searchTermTags.length > 0 && (
                            <HorizontalStack gap="1" wrap>{searchTermTags}</HorizontalStack>
                        )}
                    </VerticalStack>

                    <Divider />

                    {/* Tabs */}
                    <HorizontalStack gap="2">
                        <Button pressed={activeTab === 'apps'} onClick={() => setActiveTab('apps')}>
                            Installed Apps
                        </Button>
                        <Button pressed={activeTab === 'sdl'} onClick={() => setActiveTab('sdl')}>
                            Process Activity
                        </Button>
                        <Button pressed={activeTab === 'scripts'} onClick={() => setActiveTab('scripts')}>
                            Remote Scripts
                        </Button>
                    </HorizontalStack>

                    {/* ── Installed Apps ── */}
                    {activeTab === 'apps' && (
                        <VerticalStack gap="4">
                            <VerticalStack gap="3">
                                <HorizontalStack align="space-between" blockAlign="center">
                                    <VerticalStack gap="1">
                                        <Text variant="headingSm">Select Devices</Text>
                                        <Text variant="bodySm" color="subdued">
                                            Fetch managed endpoints, then optionally scope to specific ones.
                                        </Text>
                                    </VerticalStack>
                                    <Button onClick={handleFetchAgents} loading={loadingAgents} size="slim">
                                        {agents.length > 0 ? 'Refresh' : 'Fetch Devices'}
                                    </Button>
                                </HorizontalStack>

                                {agents.length > 0 && (
                                    <VerticalStack gap="2">
                                        <Combobox allowMultiple activator={
                                            <Combobox.TextField
                                                prefix={<Icon source={SearchMinor} />}
                                                onChange={setAgentSearch}
                                                label="Select devices" labelHidden
                                                value={agentSearch}
                                                placeholder={`Search ${agents.length} device(s)...`}
                                                autoComplete="off"
                                            />
                                        }>
                                            {filteredAgentOptions.length > 0 && (
                                                <Listbox onSelect={toggleAgent}>
                                                    {filteredAgentOptions.map((opt) => (
                                                        <Listbox.Option
                                                            key={opt.value}
                                                            value={opt.value}
                                                            selected={selectedAgentIds.includes(opt.value)}
                                                            accessibilityLabel={opt.label}
                                                        >
                                                            {`${opt.label}${opt.os ? ` (${opt.os})` : ''}`}
                                                        </Listbox.Option>
                                                    ))}
                                                </Listbox>
                                            )}
                                        </Combobox>

                                        {selectedAgentTags.length > 0 ? (
                                            <HorizontalStack gap="1" wrap>{selectedAgentTags}</HorizontalStack>
                                        ) : (
                                            <Text variant="bodySm" color="subdued">
                                                No device selected — all {agents.length} device(s) included.
                                            </Text>
                                        )}
                                    </VerticalStack>
                                )}
                            </VerticalStack>

                            {(loadingApps || allApps.length > 0) && <Divider />}

                            {loadingApps && (
                                <HorizontalStack gap="2" blockAlign="center">
                                    <Spinner size="small" />
                                    <Text color="subdued">Fetching installed applications...</Text>
                                </HorizontalStack>
                            )}

                            {!loadingApps && allApps.length > 0 && (
                                <VerticalStack gap="3">
                                    <HorizontalStack align="space-between" blockAlign="center">
                                        <Text variant="headingSm">Results</Text>
                                        <Badge status={filteredResults.length > 0 ? 'success' : 'subdued'}>
                                            {filteredResults.length} install{filteredResults.length !== 1 ? 's' : ''} found
                                        </Badge>
                                    </HorizontalStack>

                                    {filteredResults.length === 0 ? (
                                        <Text color="subdued">
                                            No matching installs found. Add more tool names above or broaden your search.
                                        </Text>
                                    ) : (
                                        <>
                                            <VerticalStack gap="3">
                                                <TextField
                                                    label="Agent name (ai-agent tag)"
                                                    value={agentName}
                                                    onChange={setAgentName}
                                                    placeholder="e.g. claude, cursor"
                                                    autoComplete="off"
                                                />
                                                <HorizontalStack align="end">
                                                    <Button primary icon={CollectionsMajor} onClick={handleSaveAppsAsCollections}
                                                        loading={savingAppsCollections}
                                                        disabled={!agentName.trim() || savingAppsCollections}>
                                                        Save as Collections
                                                    </Button>
                                                </HorizontalStack>
                                            </VerticalStack>
                                            {Object.entries(resultsByDevice).map(([deviceName, apps]) => (
                                            <VerticalStack key={deviceName} gap="2">
                                                <HorizontalStack gap="2" blockAlign="center">
                                                    <Text variant="bodyMd" fontWeight="semibold">{deviceName}</Text>
                                                    <Badge>{apps.length} app{apps.length !== 1 ? 's' : ''}</Badge>
                                                </HorizontalStack>
                                                <Box paddingInlineStart="4">
                                                    <VerticalStack gap="1">
                                                        {apps.map((app, i) => (
                                                            <HorizontalStack key={i} gap="3" blockAlign="center">
                                                                <Text variant="bodySm" fontWeight="medium">{app.name || '—'}</Text>
                                                                {app.version && <Text variant="bodySm" color="subdued">v{app.version}</Text>}
                                                                {app.publisher && <Text variant="bodySm" color="subdued">{app.publisher}</Text>}
                                                            </HorizontalStack>
                                                        ))}
                                                    </VerticalStack>
                                                </Box>
                                            </VerticalStack>
                                        ))}
                                        </>
                                    )}
                                </VerticalStack>
                            )}
                        </VerticalStack>
                    )}

                    {/* ── Remote Scripts ── */}
                    {activeTab === 'scripts' && (
                        <VerticalStack gap="4">

                            {/* Upload section */}
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

                            {/* Library + Run section */}
                            <VerticalStack gap="3">
                                <HorizontalStack align="space-between" blockAlign="center">
                                    <Text variant="headingSm">Script Library</Text>
                                    <Button onClick={handleListScripts} loading={loadingScripts} size="slim">
                                        {remoteScripts.length > 0 ? 'Refresh' : 'Load Scripts'}
                                    </Button>
                                </HorizontalStack>

                                {viewingScript && (
                                    <VerticalStack gap="2">
                                        <HorizontalStack align="space-between" blockAlign="center">
                                            <Text variant="bodyMd" fontWeight="semibold">{viewingScript.name}</Text>
                                            <Button size="slim" onClick={() => setViewingScript(null)}>Close</Button>
                                        </HorizontalStack>
                                        <div style={{ background: 'var(--p-surface-subdued)', borderRadius: '4px', padding: '12px', overflowX: 'auto' }}>
                                            <pre style={{ margin: 0, fontSize: '12px', fontFamily: 'monospace', whiteSpace: 'pre-wrap' }}>
                                                {viewingScript.content}
                                            </pre>
                                        </div>
                                    </VerticalStack>
                                )}

                                {remoteScripts.length > 0 && (
                                    <VerticalStack gap="2">
                                        {remoteScripts.map((script) => (
                                            <HorizontalStack key={script.id} align="space-between" blockAlign="center">
                                                <VerticalStack gap="0">
                                                    <Text variant="bodySm" fontWeight="medium">{script.scriptName || script.name || script.id}</Text>
                                                    <Text variant="bodySm" color="subdued">{[].concat(script.osTypes || script.osType || []).join(', ')}</Text>
                                                </VerticalStack>
                                                <HorizontalStack gap="2">
                                                    <Button size="slim" onClick={() => handleViewScript(script.id, script.scriptName || script.name)}>
                                                        View
                                                    </Button>
                                                    <Button size="slim" primary={executeScriptId === script.id}
                                                        onClick={() => setExecuteScriptId(prev => prev === script.id ? '' : script.id)}>
                                                        {executeScriptId === script.id ? 'Selected' : 'Select'}
                                                    </Button>
                                                </HorizontalStack>
                                            </HorizontalStack>
                                        ))}
                                    </VerticalStack>
                                )}
                            </VerticalStack>

                            {/* Run on devices section — shown when a script is selected */}
                            {executeScriptId && (
                                <>
                                    <Divider />
                                    <VerticalStack gap="3">
                                        <Text variant="headingSm">Run on Devices</Text>
                                        {agents.length === 0 ? (
                                            <Text variant="bodySm" color="subdued">
                                                No devices loaded — go to Installed Apps tab and click Fetch Devices first.
                                            </Text>
                                        ) : (
                                            <VerticalStack gap="2">
                                                <Combobox allowMultiple activator={
                                                    <Combobox.TextField
                                                        prefix={<Icon source={SearchMinor} />}
                                                        onChange={setExecuteAgentSearch}
                                                        label="Select devices" labelHidden
                                                        value={executeAgentSearch}
                                                        placeholder={`Search ${agents.length} device(s)...`}
                                                        autoComplete="off"
                                                    />
                                                }>
                                                    {filteredExecuteAgentOptions.length > 0 && (
                                                        <Listbox onSelect={toggleExecuteAgent}>
                                                            {filteredExecuteAgentOptions.map(opt => (
                                                                <Listbox.Option
                                                                    key={opt.value}
                                                                    value={opt.value}
                                                                    selected={executeAgentIds.includes(opt.value)}
                                                                    accessibilityLabel={opt.label}
                                                                >
                                                                    {`${opt.label}${opt.os ? ` (${opt.os})` : ''}`}
                                                                </Listbox.Option>
                                                            ))}
                                                        </Listbox>
                                                    )}
                                                </Combobox>
                                                {executeAgentIds.length > 0 && (
                                                    <HorizontalStack gap="1" wrap>
                                                        {executeAgentIds.map(id => {
                                                            const opt = allAgentOptions.find(o => o.value === id)
                                                            return (
                                                                <Tag key={id} onRemove={() => setExecuteAgentIds(prev => prev.filter(v => v !== id))}>
                                                                    {opt ? opt.label : id}
                                                                </Tag>
                                                            )
                                                        })}
                                                    </HorizontalStack>
                                                )}
                                            </VerticalStack>
                                        )}
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
                                                disabled={executeAgentIds.length === 0 || executingScript}>
                                                Run on {executeAgentIds.length > 0 ? `${executeAgentIds.length} Device(s)` : 'Selected Devices'}
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
                                </>
                            )}
                        </VerticalStack>
                    )}

                    {/* ── Process Activity (SDL) ── */}
                    {activeTab === 'sdl' && (
                        <VerticalStack gap="4">
                            <VerticalStack gap="1">
                                <Text variant="headingSm">Deep Visibility Query</Text>
                                <Text variant="bodySm" color="subdued">
                                    Searches process commands and names across all endpoints. Uses the search terms you added above.
                                </Text>
                            </VerticalStack>

                            {searchTerms.length > 0 && (
                                <Box background="bg-subdued" padding="3" borderRadius="2">
                                    <Text variant="bodySm" color="subdued">
                                        Will search for: <strong>{searchTerms.map(t => `"${t}"`).join(', ')}</strong> in processCmd and processName
                                    </Text>
                                </Box>
                            )}

                            <Select
                                label="Lookback window"
                                options={LOOKBACK_OPTIONS}
                                value={sdlLookback}
                                onChange={setSdlLookback}
                            />

                            <HorizontalStack align="end">
                                <Button primary onClick={handleRunSdl}
                                    loading={sdlRunning}
                                    disabled={searchTerms.length === 0 || sdlRunning}>
                                    Run Query
                                </Button>
                            </HorizontalStack>

                            {sdlRunning && (
                                <HorizontalStack gap="2" blockAlign="center">
                                    <Spinner size="small" />
                                    <Text color="subdued">Running SDL query, polling for results...</Text>
                                </HorizontalStack>
                            )}

                            {sdlResults !== null && (
                                <VerticalStack gap="3">
                                    <HorizontalStack align="space-between" blockAlign="center">
                                        <Text variant="headingSm">Results</Text>
                                        <Badge status={sdlResults.length > 0 ? 'success' : 'subdued'}>
                                            {sdlResults.length} event{sdlResults.length !== 1 ? 's' : ''} found
                                        </Badge>
                                    </HorizontalStack>

                                    {sdlResults.length === 0 ? (
                                        <Text color="subdued">
                                            No events found. Try broadening the query or increasing the lookback window.
                                        </Text>
                                    ) : (
                                        <VerticalStack gap="4">
                                            <VerticalStack gap="3">
                                                <HorizontalStack align="space-between" blockAlign="center">
                                                    <TextField
                                                        label="Agent name (ai-agent tag)"
                                                        value={agentName}
                                                        onChange={setAgentName}
                                                        placeholder="e.g. claude, cursor"
                                                        autoComplete="off"
                                                    />
                                                    <HorizontalStack gap="2">
                                                        <Button plain icon={ClipboardMinor}
                                                            onClick={() => copyToClipboard(JSON.stringify(processedSdlResults, null, 2))}>
                                                            Export as JSON
                                                        </Button>
                                                        <Button primary icon={CollectionsMajor} onClick={handleSaveSdlAsCollections}
                                                            loading={savingSdlCollections}
                                                            disabled={!agentName.trim() || savingSdlCollections}>
                                                            Save as Collections
                                                        </Button>
                                                    </HorizontalStack>
                                                </HorizontalStack>
                                            </VerticalStack>
                                            {Object.entries(sdlGrouped).map(([device, rows]) => (
                                                <VerticalStack key={device} gap="2">
                                                    <HorizontalStack gap="2" blockAlign="center">
                                                        <Text variant="bodyMd" fontWeight="semibold">{device}</Text>
                                                        <Badge>{rows.length} event{rows.length !== 1 ? 's' : ''}</Badge>
                                                    </HorizontalStack>
                                                    <div style={{ overflowX: 'auto' }}>
                                                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                                                            <thead>
                                                                <tr>
                                                                    {sdlColumns.map((col) => (
                                                                        <th key={col} style={{ textAlign: 'left', padding: '6px 10px', borderBottom: '1px solid var(--p-border-subdued)', whiteSpace: 'nowrap', fontWeight: 600 }}>
                                                                            {col}
                                                                        </th>
                                                                    ))}
                                                                </tr>
                                                            </thead>
                                                            <tbody>
                                                                {rows.map((row, i) => (
                                                                    <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'var(--p-surface-subdued)' }}>
                                                                        {sdlColumns.map((col) => (
                                                                            <td key={col} style={{ padding: '6px 10px', borderBottom: '1px solid var(--p-border-subdued)', whiteSpace: 'nowrap', maxWidth: '260px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                                                                {row[col] != null ? String(row[col]) : '—'}
                                                                            </td>
                                                                        ))}
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                </VerticalStack>
                                            ))}
                                        </VerticalStack>
                                    )}
                                </VerticalStack>
                            )}
                        </VerticalStack>
                    )}
                </VerticalStack>
            )}
        </div>
    )
}

export default SentinelOneConnector

import { Badge, Button, Combobox, DropZone, HorizontalStack, Icon, Listbox, Select, Spinner, Tag, Text, TextField, Tooltip, VerticalStack } from '@shopify/polaris'
import { ClipboardMinor, InfoMinor, SearchMinor } from '@shopify/polaris-icons'
import { useCallback, useEffect, useMemo, useState } from 'react'
import api from '../api'
import func from '@/util/func'

function MicrosoftDefenderRunQueriesConnector() {
    const [activeTab, setActiveTab] = useState('liveResponse')
    const [devices, setDevices] = useState([])
    const [selectedDeviceIds, setSelectedDeviceIds] = useState([])
    const [searchInput, setSearchInput] = useState('')
    const [scriptContent, setScriptContent] = useState('')
    const [scriptName, setScriptName] = useState('')
    const [scriptUploaded, setScriptUploaded] = useState(false)
    const [loadingDevices, setLoadingDevices] = useState(false)
    const [uploading, setUploading] = useState(false)
    const [running, setRunning] = useState(false)
    const [results, setResults] = useState(null)
    const [libraryScripts, setLibraryScripts] = useState([])
    const [loadingLibrary, setLoadingLibrary] = useState(false)
    const [useLibraryScript, setUseLibraryScript] = useState(false)
    const [selectedLibraryScript, setSelectedLibraryScript] = useState('')
    const [scriptParameters, setScriptParameters] = useState('')
    const [kqlQuery, setKqlQuery] = useState('')
    const [kqlTimeRangeDays, setKqlTimeRangeDays] = useState('1')
    const [kqlRunning, setKqlRunning] = useState(false)
    const [kqlResults, setKqlResults] = useState(null)

    useEffect(() => {
        setLoadingDevices(true)
        api.fetchDefenderDevices().then((res) => {
            if (res && res.devices) setDevices(res.devices)
        }).catch(() => {
            func.setToast(true, true, "Failed to fetch devices. Ensure Microsoft Defender integration is configured.")
        }).finally(() => setLoadingDevices(false))

        setLoadingLibrary(true)
        api.listDefenderLibraryScripts().then((res) => {
            if (res && res.libraryScripts) setLibraryScripts(res.libraryScripts)
        }).catch(() => {}).finally(() => setLoadingLibrary(false))
    }, [])

    const allOptions = useMemo(() =>
        devices.map((d) => ({
            value: d.id,
            label: d.computerDnsName || d.id,
            os: d.osPlatform || 'Unknown',
        })),
        [devices]
    )

    const filteredOptions = useMemo(() => {
        if (!searchInput) return allOptions
        const re = new RegExp(searchInput, 'i')
        return allOptions.filter((o) => `${o.label} ${o.os}`.match(re))
    }, [allOptions, searchInput])

    const toggleDevice = useCallback((id) => {
        setSelectedDeviceIds((prev) =>
            prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id]
        )
        setSearchInput('')
    }, [])

    const removeDevice = useCallback((id) => {
        setSelectedDeviceIds((prev) => prev.filter((v) => v !== id))
    }, [])

    const handleDropZoneDrop = useCallback((_drop, accepted) => {
        const file = accepted[0]
        if (!file) return
        setScriptName(file.name)
        setScriptUploaded(false)
        const reader = new FileReader()
        reader.onload = (e) => setScriptContent(e.target.result)
        reader.readAsText(file)
    }, [])

    const handleUploadScript = async () => {
        setUploading(true)
        api.uploadDefenderScript(scriptContent, scriptName).then(() => {
            setScriptUploaded(true)
            func.setToast(true, false, `Script '${scriptName}' uploaded to Defender library.`)
        }).catch(() => {
            func.setToast(true, true, "Failed to upload script to Defender library.")
        }).finally(() => setUploading(false))
    }

    const activeScriptName = useLibraryScript ? selectedLibraryScript : scriptName
    const canRun = selectedDeviceIds.length > 0 && !!activeScriptName && (useLibraryScript || scriptUploaded) && !running

    const handleRun = () => {
        setRunning(true)
        setResults(null)
        api.runDefenderLiveResponse(selectedDeviceIds, activeScriptName, scriptParameters || null).then((res) => {
            setResults(res.liveResponseResults || [])
            func.setToast(true, false, "Live response completed.")
        }).catch(() => {
            func.setToast(true, true, "Failed to run live response.")
        }).finally(() => setRunning(false))
    }

    const handleKqlRun = () => {
        setKqlRunning(true)
        setKqlResults(null)
        api.runDefenderKqlQuery(kqlQuery, parseInt(kqlTimeRangeDays)).then((res) => {
            setKqlResults(res.kqlResults || [])
            func.setToast(true, false, `Query returned ${(res.kqlResults || []).length} result(s).`)
        }).catch((err) => {
            func.setToast(true, true, err?.response?.data?.actionErrors?.[0] || "Query is invalid or could not be executed. Please check your KQL syntax and try again.")
        }).finally(() => setKqlRunning(false))
    }

    const copyToClipboard = (text) => {
        navigator.clipboard.writeText(text)
        func.setToast(true, false, "Copied to clipboard.")
    }

    const selectedTags = selectedDeviceIds.map((id) => {
        const opt = allOptions.find((o) => o.value === id)
        return (
            <Tag key={id} onRemove={() => removeDevice(id)}>
                {opt ? `${opt.label} (${opt.os})` : id}
            </Tag>
        )
    })

    const statusColor = (status) => {
        if (!status) return 'default'
        if (status === 'Succeeded') return 'success'
        if (status === 'Failed' || status === 'error') return 'critical'
        if (status === 'TimedOut') return 'warning'
        return 'info'
    }

    const libraryOptions = [
        { label: 'Select a script from library...', value: '' },
        ...libraryScripts.map((s) => ({ label: s.fileName, value: s.fileName }))
    ]

    const timeRangeOptions = [
        { label: 'Last 1 day', value: '1' },
        { label: 'Last 3 days', value: '3' },
        { label: 'Last 7 days', value: '7' },
        { label: 'Last 14 days', value: '14' },
        { label: 'Last 30 days', value: '30' },
    ]

    const kqlColumns = kqlResults && kqlResults.length > 0 ? Object.keys(kqlResults[0]) : []

    return (
        <div className='card-items'>
            <VerticalStack gap="4">
                <HorizontalStack gap="2">
                    <Button pressed={activeTab === 'liveResponse'} onClick={() => setActiveTab('liveResponse')}>
                        Live Response
                    </Button>
                    <Button pressed={activeTab === 'kql'} onClick={() => setActiveTab('kql')}>
                        KQL Query
                    </Button>
                </HorizontalStack>

                {activeTab === 'liveResponse' && (
                    <VerticalStack gap="4">
                        <Text variant='bodyMd' color="subdued">
                            Select devices, choose or upload a script, then run it sequentially on each selected device.
                        </Text>

                        {/* Device selector */}
                        <VerticalStack gap="2">
                            <Text variant='headingSm'>Select Devices</Text>
                            {loadingDevices ? (
                                <HorizontalStack gap="2">
                                    <Spinner size="small" />
                                    <Text>Loading devices...</Text>
                                </HorizontalStack>
                            ) : (
                                <VerticalStack gap="2">
                                    <Combobox
                                        allowMultiple
                                        activator={
                                            <Combobox.TextField
                                                prefix={<Icon source={SearchMinor} />}
                                                onChange={setSearchInput}
                                                label="Search devices"
                                                labelHidden
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
                                                        selected={selectedDeviceIds.includes(opt.value)}
                                                        accessibilityLabel={opt.label}
                                                    >
                                                        {`${opt.label} (${opt.os})`}
                                                    </Listbox.Option>
                                                ))}
                                            </Listbox>
                                        ) : null}
                                    </Combobox>
                                    {selectedTags.length > 0 && (
                                        <VerticalStack gap="1">
                                            <HorizontalStack gap="1" wrap>
                                                {selectedTags}
                                            </HorizontalStack>
                                            {selectedDeviceIds.map((id) => {
                                                const opt = allOptions.find((o) => o.value === id)
                                                return (
                                                    <HorizontalStack key={id} gap="1" blockAlign="center">
                                                        <Text variant="bodySm" color="subdued">{opt ? opt.label : id}:</Text>
                                                        <Text variant="bodySm" color="subdued">{id}</Text>
                                                        <Tooltip content="Copy device ID" dismissOnMouseOut>
                                                            <span onClick={() => copyToClipboard(id)} style={{ cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
                                                                <Icon source={ClipboardMinor} color="subdued" />
                                                            </span>
                                                        </Tooltip>
                                                    </HorizontalStack>
                                                )
                                            })}
                                        </VerticalStack>
                                    )}
                                </VerticalStack>
                            )}
                        </VerticalStack>

                        {/* Script selection */}
                        <VerticalStack gap="2">
                            <Text variant='headingSm'>Script</Text>
                            <HorizontalStack gap="2">
                                <Button pressed={!useLibraryScript} onClick={() => setUseLibraryScript(false)}>
                                    Upload new script
                                </Button>
                                <Button pressed={useLibraryScript} onClick={() => setUseLibraryScript(true)}>
                                    Use existing library script
                                </Button>
                            </HorizontalStack>

                            {useLibraryScript ? (
                                loadingLibrary ? (
                                    <HorizontalStack gap="2">
                                        <Spinner size="small" />
                                        <Text>Loading library scripts...</Text>
                                    </HorizontalStack>
                                ) : (
                                    <Select
                                        label="Library script"
                                        labelHidden
                                        options={libraryOptions}
                                        value={selectedLibraryScript}
                                        onChange={setSelectedLibraryScript}
                                    />
                                )
                            ) : (
                                <VerticalStack gap="2">
                                    <Text variant='bodySm' color="subdued">Note: .sh for macOS/Linux · .ps1 for Windows</Text>
                                    <DropZone onDrop={handleDropZoneDrop} accept=".ps1,.sh,.bat" type="file">
                                        {scriptName ? (
                                            <div style={{ padding: '8px 12px' }}>
                                                <HorizontalStack gap="2" blockAlign="center">
                                                    <Text>{scriptName}</Text>
                                                    {scriptUploaded && <Badge status="success">Uploaded</Badge>}
                                                </HorizontalStack>
                                            </div>
                                        ) : (
                                            <DropZone.FileUpload actionTitle="Upload script" actionHint="Accepts .ps1 (Windows), .sh (macOS/Linux), .bat" />
                                        )}
                                    </DropZone>
                                    {scriptName && (
                                        <HorizontalStack gap="2">
                                            <Button plain onClick={() => { setScriptContent(''); setScriptName(''); setScriptUploaded(false) }}>
                                                Remove
                                            </Button>
                                            <Button onClick={handleUploadScript} loading={uploading} disabled={scriptUploaded || uploading}>
                                                {scriptUploaded ? 'Uploaded' : 'Upload to Library'}
                                            </Button>
                                        </HorizontalStack>
                                    )}
                                </VerticalStack>
                            )}
                        </VerticalStack>

                        {/* Script parameters */}
                        <TextField
                            label={
                                <HorizontalStack gap="1">
                                    <Text>Script Parameters (optional)</Text>
                                    <Tooltip
                                        content={`Passed as -parameters on Windows (.ps1) or as environment args on Linux/macOS (.sh). Example: run update_openclaw_simple.ps1 -parameters "AKTO_PROXY_URL=https://example.ngrok-free.dev/v1 OPENAI_API_KEY=sk-... MODEL_ID=claude-sonnet-4-6"`}
                                        dismissOnMouseOut
                                        width="wide"
                                    >
                                        <Icon source={InfoMinor} color="subdued" />
                                    </Tooltip>
                                </HorizontalStack>
                            }
                            value={scriptParameters}
                            onChange={setScriptParameters}
                            placeholder="e.g. AKTO_PROXY_URL=https://example.ngrok-free.dev/v1 OPENAI_API_KEY=sk-... MODEL_ID=claude-sonnet-4-6"
                            autoComplete="off"
                        />

                        {running && (
                            <HorizontalStack gap="2" blockAlign="center">
                                <Spinner size="small" />
                                <Text>Running live response on {selectedDeviceIds.length} device(s), polling for completion...</Text>
                            </HorizontalStack>
                        )}
                        {results && results.length > 0 && (
                            <VerticalStack gap="2">
                                <Text variant='headingSm'>Results</Text>
                                {results.map((r, i) => {
                                    const opt = allOptions.find((o) => o.value === r.deviceId)
                                    const label = opt ? `${opt.label} (${opt.os})` : r.deviceId
                                    return (
                                        <HorizontalStack key={i} gap="2" blockAlign="center">
                                            <Text variant='bodySm'>{label}</Text>
                                            <Badge status={statusColor(r.status)}>{r.status || 'unknown'}</Badge>
                                            {r.error && <Text variant='bodySm' color="critical">{r.error}</Text>}
                                        </HorizontalStack>
                                    )
                                })}
                            </VerticalStack>
                        )}

                        <HorizontalStack align='end'>
                            <Button primary onClick={handleRun} loading={running} disabled={!canRun}>
                                Run on Selected Devices
                            </Button>
                        </HorizontalStack>
                    </VerticalStack>
                )}

                {activeTab === 'kql' && (
                    <VerticalStack gap="4">
                        <Text variant='bodyMd' color="subdued">
                            Run custom KQL queries against Microsoft Defender Advanced Hunting. A time filter is appended automatically unless your query already uses <code>ago(</code>.
                        </Text>

                        <Select
                            label="Time range"
                            options={timeRangeOptions}
                            value={kqlTimeRangeDays}
                            onChange={setKqlTimeRangeDays}
                        />

                        <TextField
                            label="KQL Query"
                            value={kqlQuery}
                            onChange={setKqlQuery}
                            multiline={6}
                            monospaced
                            placeholder={"DeviceNetworkEvents\n| where RemotePort == 443\n| project DeviceName, RemoteIP, RemoteUrl\n| limit 100"}
                            autoComplete="off"
                        />

                        <HorizontalStack align='end'>
                            <Button primary onClick={handleKqlRun} loading={kqlRunning} disabled={!kqlQuery.trim() || kqlRunning}>
                                Run Query
                            </Button>
                        </HorizontalStack>

                        {kqlRunning && (
                            <HorizontalStack gap="2" blockAlign="center">
                                <Spinner size="small" />
                                <Text>Running query...</Text>
                            </HorizontalStack>
                        )}

                        {kqlResults && kqlResults.length === 0 && (
                            <Text color="subdued">No results returned.</Text>
                        )}

                        {kqlResults && kqlResults.length > 0 && (
                            <VerticalStack gap="2">
                                <HorizontalStack align="space-between" blockAlign="center">
                                    <Text variant='headingSm'>Results ({kqlResults.length} rows)</Text>
                                    <Button plain icon={ClipboardMinor} onClick={() => copyToClipboard(JSON.stringify(kqlResults, null, 2))}>
                                        Copy JSON
                                    </Button>
                                </HorizontalStack>
                                <div style={{ overflowX: 'auto' }}>
                                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                                        <thead>
                                            <tr>
                                                {kqlColumns.map((col) => (
                                                    <th key={col} style={{ textAlign: 'left', padding: '6px 10px', borderBottom: '1px solid var(--p-border-subdued)', whiteSpace: 'nowrap', fontWeight: 600 }}>
                                                        {col}
                                                    </th>
                                                ))}
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {kqlResults.map((row, i) => (
                                                <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'var(--p-surface-subdued)' }}>
                                                    {kqlColumns.map((col) => (
                                                        <td key={col} style={{ padding: '6px 10px', borderBottom: '1px solid var(--p-border-subdued)', whiteSpace: 'nowrap', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                                            {row[col] != null ? String(row[col]) : '—'}
                                                        </td>
                                                    ))}
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </VerticalStack>
                        )}
                    </VerticalStack>
                )}
            </VerticalStack>
        </div>
    )
}

export default MicrosoftDefenderRunQueriesConnector

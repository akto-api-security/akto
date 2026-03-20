import { Badge, Button, Combobox, DropZone, HorizontalStack, Icon, Listbox, Spinner, Tag, Text, VerticalStack } from '@shopify/polaris'
import { SearchMinor } from '@shopify/polaris-icons'
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import api from '../api'
import func from '@/util/func'

function MicrosoftDefenderRunQueriesConnector() {
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

    useEffect(() => {
        setLoadingDevices(true)
        api.fetchDefenderDevices().then((res) => {
            if (res && res.devices) setDevices(res.devices)
        }).catch(() => {
            func.setToast(true, true, "Failed to fetch devices. Ensure Microsoft Defender integration is configured.")
        }).finally(() => setLoadingDevices(false))
    }, [])

    const allOptions = useMemo(() =>
        devices.map((d) => ({
            value: d.id,
            label: `${d.computerDnsName || d.id} (${d.osPlatform || 'unknown'})`
        })),
        [devices]
    )

    const filteredOptions = useMemo(() => {
        if (!searchInput) return allOptions
        const re = new RegExp(searchInput, 'i')
        return allOptions.filter((o) => o.label.match(re))
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

    const handleRun = async () => {
        setRunning(true)
        setResults(null)
        api.runDefenderLiveResponse(selectedDeviceIds, scriptName).then((res) => {
            setResults(res.liveResponseResults || [])
            func.setToast(true, false, "Live response completed.")
        }).catch(() => {
            func.setToast(true, true, "Failed to run live response.")
        }).finally(() => setRunning(false))
    }

    const selectedTags = selectedDeviceIds.map((id) => {
        const opt = allOptions.find((o) => o.value === id)
        return (
            <Tag key={id} onRemove={() => removeDevice(id)}>
                {opt ? opt.label : id}
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

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                Select devices, upload a script to the Defender Live Response library, then run it sequentially on each selected device.
            </Text>
            <VerticalStack gap="4">

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
                                                {opt.label}
                                            </Listbox.Option>
                                        ))}
                                    </Listbox>
                                ) : null}
                            </Combobox>
                            {selectedTags.length > 0 && (
                                <HorizontalStack gap="1" wrap>
                                    {selectedTags}
                                </HorizontalStack>
                            )}
                        </VerticalStack>
                    )}
                </VerticalStack>

                {/* Script upload */}
                <VerticalStack gap="2">
                    <Text variant='headingSm'>Upload Script to Defender Library</Text>
                    <DropZone onDrop={handleDropZoneDrop} accept=".ps1,.sh,.bat" type="file">
                        {scriptName ? (
                            <div style={{ padding: '8px 12px' }}>
                                <HorizontalStack gap="2" blockAlign="center">
                                    <Text>{scriptName}</Text>
                                    {scriptUploaded && <Badge status="success">Uploaded</Badge>}
                                </HorizontalStack>
                            </div>
                        ) : (
                            <DropZone.FileUpload actionTitle="Upload script" actionHint="Accepts .ps1, .sh, .bat" />
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

                {/* Results */}
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
                            const label = opt ? opt.label : r.deviceId
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
                    <Button
                        primary
                        onClick={handleRun}
                        loading={running}
                        disabled={selectedDeviceIds.length === 0 || !scriptUploaded || running}
                    >
                        Run on Selected Devices
                    </Button>
                </HorizontalStack>
            </VerticalStack>
        </div>
    )
}

export default MicrosoftDefenderRunQueriesConnector

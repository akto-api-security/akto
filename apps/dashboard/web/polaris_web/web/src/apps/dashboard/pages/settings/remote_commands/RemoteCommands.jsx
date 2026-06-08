import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Badge, Button, Text, Modal, TextField,
    VerticalStack, HorizontalStack, LegacyCard,
    Spinner, Tag, Checkbox, Tooltip, Banner, Box, RadioButton
} from '@shopify/polaris'
import { RefreshMajor } from '@shopify/polaris-icons'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import settingRequests from '../api'
import func from '@/util/func'

const MODULE_TYPE_ENDPOINT_SHIELD = 'MCP_ENDPOINT_SHIELD'

const resourceName = { singular: 'command', plural: 'commands' }

const HEADERS = [
    { title: 'Command',  text: 'Command',  value: 'commandLine' },
    { title: 'Target',   text: 'Target',   value: 'targetText' },
    { title: 'Created',  text: 'Created',  value: 'createdAtFormatted' },
    { title: 'Progress', text: 'Progress', value: 'progressText' },
    { title: 'Status',   text: 'Status',   value: 'statusBadge' },
]

function commandStatusBadge(status) {
    if (status === 'ACTIVE') return <Badge tone="info">ACTIVE</Badge>
    if (status === 'EXPIRED') return <Badge tone="new">EXPIRED</Badge>
    return <Badge tone="new">CANCELLED</Badge>
}

function formatTarget(targetType, targetDeviceIds) {
    if (targetType === 'ALL') return 'All devices'
    const count = targetDeviceIds?.length || 0
    return `${count} device${count !== 1 ? 's' : ''}`
}

function formatCommandLine(command, args) {
    const full = [command, ...(args || [])].join(' ')
    return full.length > 60 ? full.substring(0, 60) + '...' : full
}

// ─── Device helpers ──────────────────────────────────────────────────────────

// lastHeartbeatReceived is epoch seconds (Java int field)
function deduplicateDevices(moduleInfos) {
    const map = {}
    for (const m of (moduleInfos || [])) {
        const dId = m.additionalData?.deviceId
        if (!dId) continue
        const heartbeat = m.lastHeartbeatReceived || 0
        if (!map[dId] || heartbeat > map[dId].lastHeartbeat) {
            map[dId] = { id: dId, label: m.name || dId, lastHeartbeat: heartbeat }
        }
    }
    return Object.values(map)
}

function uniqueDeviceCount(moduleInfos) {
    const ids = new Set()
    for (const m of (moduleInfos || [])) {
        if (m.additionalData?.deviceId) ids.add(m.additionalData.deviceId)
    }
    return ids.size
}

// ─── New Command Modal ────────────────────────────────────────────────────────

function NewCommandModal({ onClose, onSuccess }) {
    const [step, setStep] = useState('form')
    const [command, setCommand] = useState('')
    const [argsInput, setArgsInput] = useState('')
    const [args, setArgs] = useState([])
    const [timeoutSec, setTimeoutSec] = useState('60')
    const [expiresIn, setExpiresIn] = useState('3600')
    const [targetType, setTargetType] = useState('ALL')
    const [devices, setDevices] = useState([])
    const [selectedDeviceIds, setSelectedDeviceIds] = useState([])
    const [deviceSearch, setDeviceSearch] = useState('')
    const [loadingDevices, setLoadingDevices] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [totalDevices, setTotalDevices] = useState(0)

    useEffect(() => {
        settingRequests.fetchModuleInfo({ moduleType: MODULE_TYPE_ENDPOINT_SHIELD })
            .then(res => setTotalDevices(uniqueDeviceCount(res?.moduleInfos)))
            .catch(() => {})
    }, [])

    useEffect(() => {
        if (targetType !== 'SELECTED') return
        setLoadingDevices(true)
        settingRequests.fetchModuleInfo({ moduleType: MODULE_TYPE_ENDPOINT_SHIELD })
            .then(res => {
                const mods = res?.moduleInfos || []
                setTotalDevices(uniqueDeviceCount(mods))
                setDevices(deduplicateDevices(mods))
            })
            .catch(() => {})
            .finally(() => setLoadingDevices(false))
    }, [targetType])

    const commandError = !command.trim()
        ? 'Command is required'
        : /[/\\]/.test(command)
            ? 'Command cannot contain path separators'
            : null
    const timeoutError = !timeoutSec || isNaN(timeoutSec) || +timeoutSec < 1 || +timeoutSec > 300
        ? 'Must be between 1 and 300' : null
    const expiresError = !expiresIn || isNaN(expiresIn) || +expiresIn < 60 || +expiresIn > 86400
        ? 'Must be between 60 and 86400' : null
    const targetError = targetType === 'SELECTED' && selectedDeviceIds.length === 0
        ? 'Select at least one device' : null

    const isFormValid = !commandError && !timeoutError && !expiresError && !targetError

    function addArg() {
        const trimmed = argsInput.trim()
        if (!trimmed || args.length >= 50 || trimmed.length > 1024) return
        setArgs(prev => [...prev, trimmed])
        setArgsInput('')
    }

    async function submitCommand() {
        setSubmitting(true)
        try {
            const res = await settingRequests.queueEndpointRemoteCommand(
                command.trim(), args, +timeoutSec, +expiresIn,
                targetType, targetType === 'SELECTED' ? selectedDeviceIds : []
            )
            if (res?.error) return
            onSuccess({
                commandId: res?.commandId || null,
                command: command.trim(), args, targetType,
                targetDeviceIds: targetType === 'SELECTED' ? selectedDeviceIds : [],
                createdAt: Date.now(), status: 'ACTIVE',
                executionSummary: { total: 0, pending: 0, running: 0, completed: 0, failed: 0 },
            })
            onClose()
            func.setToast(true, false, 'Command queued successfully.')
        } catch {
            // response interceptor already shows error toasts for 4xx/5xx
        } finally {
            setSubmitting(false)
        }
    }

    const filteredDevices = deviceSearch.trim()
        ? devices.filter(d => d.label.toLowerCase().includes(deviceSearch.toLowerCase()))
        : devices

    if (step === 'confirm') {
        return (
            <Modal
                open onClose={onClose}
                title="New Remote Command"
                primaryAction={{ content: 'Confirm & Run', onAction: submitCommand, loading: submitting, destructive: true }}
                secondaryActions={[{ content: 'Go back', onAction: () => setStep('form') }]}
            >
                <Modal.Section>
                    <Banner tone="warning">
                        <VerticalStack gap="1">
                            <Text>This will run on all {totalDevices} registered devices.</Text>
                            <Text>Devices that are offline or do not poll before the expiry window will not execute the command.</Text>
                        </VerticalStack>
                    </Banner>
                </Modal.Section>
            </Modal>
        )
    }

    return (
        <Modal
            open onClose={onClose}
            title="New Remote Command"
            primaryAction={{
                content: 'Run Command',
                onAction: () => {
                    if (!isFormValid) return
                    if (targetType === 'ALL') { setStep('confirm'); return }
                    submitCommand()
                },
                disabled: !isFormValid,
                loading: submitting,
            }}
            secondaryActions={[{ content: 'Cancel', onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="5">
                    <TextField
                        label="Command"
                        value={command}
                        onChange={setCommand}
                        placeholder="ls"
                        error={command ? commandError : null}
                        autoComplete="off"
                    />

                    <VerticalStack gap="2">
                        <Text variant="bodyMd" fontWeight="medium">Arguments</Text>
                        <HorizontalStack gap="2" wrap>
                            {args.map((arg, i) => (
                                <Tag key={i} onRemove={() => setArgs(prev => prev.filter((_, j) => j !== i))}>
                                    {arg}
                                </Tag>
                            ))}
                        </HorizontalStack>
                        <HorizontalStack gap="2" blockAlign="center">
                            <Box minWidth="200px">
                                <TextField
                                    value={argsInput}
                                    onChange={setArgsInput}
                                    placeholder="e.g. -la"
                                    autoComplete="off"
                                    onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addArg() } }}
                                    label="" labelHidden
                                />
                            </Box>
                            <Button onClick={addArg} disabled={!argsInput.trim() || args.length >= 50}>
                                + Add argument
                            </Button>
                        </HorizontalStack>
                    </VerticalStack>

                    <HorizontalStack gap="4" wrap>
                        <Box minWidth="160px">
                            <TextField
                                label="Timeout (seconds)" type="number"
                                value={timeoutSec} onChange={setTimeoutSec}
                                min={1} max={300}
                                error={timeoutSec && timeoutError}
                                helpText="1 - 300; default 60"
                                autoComplete="off"
                            />
                        </Box>
                        <Box minWidth="160px">
                            <TextField
                                label="Expires in (seconds)" type="number"
                                value={expiresIn} onChange={setExpiresIn}
                                min={60} max={86400}
                                error={expiresIn && expiresError}
                                helpText="60 - 86400; default 3600"
                                autoComplete="off"
                            />
                        </Box>
                    </HorizontalStack>

                    <VerticalStack gap="3">
                        <Text variant="bodyMd" fontWeight="medium">Target</Text>
                        <RadioButton
                            label={`All devices (${totalDevices} registered)`}
                            checked={targetType === 'ALL'}
                            id="rc-target-all" name="rc-target"
                            onChange={() => setTargetType('ALL')}
                        />
                        <RadioButton
                            label="Selected devices"
                            checked={targetType === 'SELECTED'}
                            id="rc-target-selected" name="rc-target"
                            onChange={() => setTargetType('SELECTED')}
                        />
                        {targetType === 'SELECTED' && (
                            <Box paddingInlineStart="6">
                                <VerticalStack gap="3">
                                    <TextField
                                        placeholder="Search devices..."
                                        value={deviceSearch} onChange={setDeviceSearch}
                                        autoComplete="off" label="" labelHidden
                                    />
                                    {loadingDevices ? (
                                        <HorizontalStack gap="2">
                                            <Spinner size="small" />
                                            <Text color="subdued">Loading devices...</Text>
                                        </HorizontalStack>
                                    ) : filteredDevices.length === 0 ? (
                                        <Text color="subdued">No devices found.</Text>
                                    ) : (
                                        <VerticalStack gap="2">
                                            {filteredDevices.map(d => (
                                                <Checkbox
                                                    key={d.id}
                                                    label={
                                                        <HorizontalStack gap="2">
                                                            <Text>{d.label}</Text>
                                                            {d.lastHeartbeat > 0 && (
                                                                <Text color="subdued" variant="bodySm">
                                                                    last seen {func.prettifyEpoch(d.lastHeartbeat)}
                                                                </Text>
                                                            )}
                                                        </HorizontalStack>
                                                    }
                                                    checked={selectedDeviceIds.includes(d.id)}
                                                    onChange={checked =>
                                                        setSelectedDeviceIds(prev =>
                                                            checked ? [...prev, d.id] : prev.filter(id => id !== d.id)
                                                        )
                                                    }
                                                />
                                            ))}
                                        </VerticalStack>
                                    )}
                                    {targetError && <Text tone="critical" variant="bodySm">{targetError}</Text>}
                                </VerticalStack>
                            </Box>
                        )}
                    </VerticalStack>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

// ─── Remote Commands List (Level 1) ──────────────────────────────────────────

function RemoteCommands() {
    const navigate = useNavigate()
    const [commands, setCommands] = useState([])
    const [loading, setLoading] = useState(true)
    const [refreshing, setRefreshing] = useState(false)
    const [tableVersion, setTableVersion] = useState(0)
    const [showModal, setShowModal] = useState(false)
    const pollRef = useRef(null)

    async function loadCommands() {
        try {
            const res = await settingRequests.fetchEndpointRemoteCommandList(50)
            setCommands(res?.commands || [])
            setTableVersion(v => v + 1)
        } catch {
        } finally {
            setLoading(false)
        }
    }

    async function handleRefresh() {
        setRefreshing(true)
        try {
            await loadCommands()
        } finally {
            setRefreshing(false)
        }
    }

    useEffect(() => {
        loadCommands()
        return () => clearInterval(pollRef.current)
    }, [])

    useEffect(() => {
        clearInterval(pollRef.current)
        const needsPoll = commands.some(c =>
            (c.executionSummary?.pending || 0) > 0 || (c.executionSummary?.running || 0) > 0
        )
        if (needsPoll) pollRef.current = setInterval(loadCommands, 10000)
        return () => clearInterval(pollRef.current)
    }, [commands])

    function handleNewCommand(cmd) {
        setCommands(prev => [cmd, ...prev])
    }

    const tableData = commands.map(cmd => ({
        id: cmd.commandId || String(Math.random()),
        commandLine: formatCommandLine(cmd.command, cmd.args),
        targetText: formatTarget(cmd.targetType, cmd.targetDeviceIds),
        createdAtFormatted: cmd.createdAt
            ? func.prettifyEpoch(Math.floor(cmd.createdAt / 1000))
            : '--',
        progressText: `${cmd.executionSummary?.completed || 0} / ${cmd.executionSummary?.total || 0}`,
        statusBadge: commandStatusBadge(cmd.status),
        _commandId: cmd.commandId,
    }))

    const table = (
        <VerticalStack gap="1">
            <HorizontalStack align="end">
                <Button icon={RefreshMajor} plain onClick={handleRefresh} loading={refreshing} accessibilityLabel="Refresh" />
            </HorizontalStack>
            <GithubSimpleTable
                key={`rc-${tableVersion}`}
                data={tableData}
                headers={HEADERS}
                headings={HEADERS}
                resourceName={resourceName}
                selectable={false}
                useNewRow={true}
                condensedHeight={true}
                loading={loading}
                onRowClick={row => row._commandId && navigate(`/dashboard/settings/remote-commands/${row._commandId}`)}
                rowClickable={true}
                hideQueryField={true}
            />
        </VerticalStack>
    )

    return (
        <>
            <PageWithMultipleCards
                title="Remote Commands"
                isFirstPage={true}
                primaryAction={{ content: '+ New Command', onAction: () => setShowModal(true) }}
                components={[table]}
            />
            {showModal && (
                <NewCommandModal
                    onClose={() => setShowModal(false)}
                    onSuccess={handleNewCommand}
                />
            )}
        </>
    )
}

export default RemoteCommands

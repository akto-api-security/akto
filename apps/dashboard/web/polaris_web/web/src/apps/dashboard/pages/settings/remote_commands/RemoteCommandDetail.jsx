import { useState, useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
    Badge, Button, Text, LegacyCard,
    VerticalStack, HorizontalStack,
    Spinner, Banner, Box, Tooltip, Divider
} from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import settingRequests from '../api'
import func from '@/util/func'

// ─── Constants ────────────────────────────────────────────────────────────────

const EXEC_RESOURCE = { singular: 'execution', plural: 'executions' }

const EXEC_HEADERS = [
    { title: 'Device',   text: 'Device',   value: 'deviceIdText' },
    { title: 'Status',   text: 'Status',   value: 'statusComp' },
    { title: 'Exit',     text: 'Exit',     value: 'exitText' },
    { title: 'Duration', text: 'Duration', value: 'durationText' },
    { title: 'Updated',  text: 'Updated',  value: 'updatedText' },
]

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatTarget(targetType, targetDeviceIds) {
    if (targetType === 'ALL') return 'All devices'
    const count = targetDeviceIds?.length || 0
    return `${count} device${count !== 1 ? 's' : ''}`
}

function executionStatusBadge(exec) {
    const { status, exitCode, errorReason } = exec
    if (status === 'PENDING') return <Badge tone="attention">PENDING</Badge>
    if (status === 'RUNNING') {
        return (
            <HorizontalStack gap="2" blockAlign="center">
                <Spinner size="small" />
                <Badge tone="info">RUNNING</Badge>
            </HorizontalStack>
        )
    }
    if (status === 'COMPLETED') {
        return exitCode === 0
            ? <Badge tone="success">COMPLETED</Badge>
            : <Badge tone="warning">COMPLETED (exit {exitCode})</Badge>
    }
    if (status === 'FAILED') {
        if (errorReason === 'expired' || errorReason === 'cancelled')
            return <Badge tone="new">FAILED — {errorReason}</Badge>
        return <Badge tone="critical">FAILED — {errorReason || 'unknown'}</Badge>
    }
    return <Badge>{status}</Badge>
}

// ─── Output display ───────────────────────────────────────────────────────────

function OutputBox({ label, content }) {
    const [expanded, setExpanded] = useState(false)
    const lines = content ? content.split('\n') : []
    const hasContent = content && content.trim().length > 0
    const isTruncated = hasContent && (
        content.endsWith('\n[output truncated]') || content.endsWith('\n[truncated by server]')
    )

    function handleCopy() {
        navigator.clipboard.writeText(content || '').catch(() => {})
        func.setToast(true, false, `${label} copied.`)
    }

    return (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between" blockAlign="center">
                <Text variant="bodySm" fontWeight="semibold">{label}</Text>
                {hasContent && <Button plain size="slim" onClick={handleCopy}>Copy</Button>}
            </HorizontalStack>
            {isTruncated && <Banner tone="warning">Output truncated at 512 KB</Banner>}
            <Box background="bg-surface-secondary" padding="3" borderRadius="2">
                {!hasContent ? (
                    <Text color="subdued" variant="bodySm">(empty)</Text>
                ) : (
                    <VerticalStack gap="2">
                        <pre style={{
                            fontFamily: 'monospace', fontSize: '12px', margin: 0,
                            whiteSpace: 'pre-wrap', wordBreak: 'break-all',
                            background: '#1a1a1a', color: '#e5e5e5',
                            padding: '8px', borderRadius: '4px',
                        }}>
                            {(expanded ? lines : lines.slice(0, 50)).join('\n')}
                        </pre>
                        {lines.length > 50 && !expanded && (
                            <Button plain onClick={() => setExpanded(true)}>
                                Show all {lines.length} lines
                            </Button>
                        )}
                    </VerticalStack>
                )}
            </Box>
        </VerticalStack>
    )
}

function ExpandedOutput({ exec }) {
    const { status, exitCode, errorReason, stdout, stderr } = exec
    const bothEmpty = !(stdout && stdout.trim()) && !(stderr && stderr.trim())

    if (status === 'FAILED' && (errorReason === 'expired' || errorReason === 'cancelled')) {
        const msg = errorReason === 'cancelled'
            ? 'Command was cancelled before this device polled. The command was never executed on this device.'
            : 'Command expired before this device polled. The command was never executed on this device.'
        return (
            <Box padding="4" background="bg-surface-secondary">
                <Text color="subdued">{msg}</Text>
            </Box>
        )
    }

    let explanation = null
    if (status === 'FAILED') {
        if (errorReason === 'exec_error')
            explanation = 'Process could not be started. Verify the command exists on this device and the agent has execute permission.'
        else if (errorReason === 'timeout')
            explanation = 'Process was killed after the command timeout.'
        else if (errorReason === 'presumed_dead_agent')
            explanation = 'Agent did not report back within the expected window. The command may or may not have run. Check the device directly.'
    }

    if (status === 'COMPLETED' && bothEmpty) {
        return (
            <Box padding="4">
                <Text color="subdued">No output</Text>
            </Box>
        )
    }

    return (
        <Box padding="4" background="bg-surface-secondary">
            <VerticalStack gap="4">
                {explanation && <Text color="subdued">{explanation}</Text>}
                {!(explanation && bothEmpty) && (
                    <HorizontalStack gap="4" wrap={false} align="start">
                        <Box width="50%"><OutputBox label="STDOUT" content={stdout} /></Box>
                        <Box width="50%"><OutputBox label="STDERR" content={stderr} /></Box>
                    </HorizontalStack>
                )}
            </VerticalStack>
        </Box>
    )
}

// ─── Remote Command Detail (Level 2) ─────────────────────────────────────────

function RemoteCommandDetail() {
    const { commandId } = useParams()
    const navigate = useNavigate()
    const [command, setCommand] = useState(null)
    const [executions, setExecutions] = useState([])
    const [loading, setLoading] = useState(true)
    const [cancelling, setCancelling] = useState(false)
    const pollRef = useRef(null)

    async function loadData() {
        try {
            const [listRes, execRes] = await Promise.all([
                settingRequests.fetchEndpointRemoteCommandList(100),
                settingRequests.fetchEndpointRemoteCommandExecutions(commandId, null, 50),
            ])
            const found = (listRes?.commands || []).find(c => c.commandId === commandId)
            if (found) setCommand(found)
            setExecutions(execRes?.executions || [])
        } catch {
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadData()
        return () => clearInterval(pollRef.current)
    }, [commandId])

    useEffect(() => {
        clearInterval(pollRef.current)
        const needsPoll = executions.some(e => e.status === 'PENDING' || e.status === 'RUNNING')
        if (needsPoll) pollRef.current = setInterval(loadData, 5000)
        return () => clearInterval(pollRef.current)
    }, [executions])

    async function handleCancel() {
        setCancelling(true)
        try {
            await settingRequests.cancelEndpointRemoteCommand(commandId)
            setCommand(prev => prev ? { ...prev, status: 'CANCELLED' } : prev)
            func.setToast(true, false, 'Command cancelled.')
        } catch {
            func.setToast(true, true, 'Failed to cancel command.')
        } finally {
            setCancelling(false)
        }
    }

    const summary = command?.executionSummary || {}
    const cmdText = command ? [command.command, ...(command.args || [])].join(' ') : commandId
    const createdAtText = command?.createdAt ? func.prettifyEpoch(Math.floor(command.createdAt / 1000)) : ''
    const createdAtFull = command?.createdAt ? new Date(command.createdAt).toLocaleString() : ''

    const expiresText = (() => {
        if (command?.status !== 'ACTIVE') return ''
        if (!command?.expirySeconds || !command?.createdAt) return ''
        const expiresAt = command.createdAt + command.expirySeconds * 1000
        const diffMin = Math.floor((expiresAt - Date.now()) / 60000)
        if (diffMin <= 0) return ''
        return diffMin >= 60
            ? `Expires in ${Math.floor(diffMin / 60)}h ${diffMin % 60}m`
            : `Expires in ${diffMin}m`
    })()

    // ── Header card ──────────────────────────────────────────────────────────
    const headerCard = (
        <LegacyCard key="header">
            <LegacyCard.Section>
                <HorizontalStack align="space-between" blockAlign="start" wrap={false}>
                    <VerticalStack gap="2">
                        <Text variant="headingMd">
                            <span style={{ fontFamily: 'monospace' }}>{cmdText}</span>
                        </Text>
                        <HorizontalStack gap="2" wrap>
                            <Text color="subdued" variant="bodySm">{formatTarget(command?.targetType, command?.targetDeviceIds)}</Text>
                            {command?.createdBy && <>
                                <Text color="subdued" variant="bodySm">·</Text>
                                <Text color="subdued" variant="bodySm">Created by {command.createdBy}</Text>
                            </>}
                            {createdAtText && <>
                                <Text color="subdued" variant="bodySm">·</Text>
                                <Tooltip content={createdAtFull} dismissOnMouseOut>
                                    <Text color="subdued" variant="bodySm">{createdAtText}</Text>
                                </Tooltip>
                            </>}
                            {expiresText && <>
                                <Text color="subdued" variant="bodySm">·</Text>
                                <Text color="subdued" variant="bodySm">{expiresText}</Text>
                            </>}
                        </HorizontalStack>
                        <HorizontalStack gap="3" wrap>
                            <Text variant="bodySm">{summary.completed || 0} completed</Text>
                            <Text color="subdued" variant="bodySm">·</Text>
                            <Text variant="bodySm">{summary.pending || 0} pending</Text>
                            <Text color="subdued" variant="bodySm">·</Text>
                            <Text variant="bodySm">{summary.running || 0} running</Text>
                            <Text color="subdued" variant="bodySm">·</Text>
                            <Text variant="bodySm">{summary.failed || 0} failed</Text>
                        </HorizontalStack>
                    </VerticalStack>
                    <HorizontalStack gap="2" blockAlign="center">
                        {command?.status === 'CANCELLED' && (
                            <Text color="subdued" variant="bodySm">Cancelled</Text>
                        )}
                        {command?.status === 'EXPIRED' && (
                            <Text color="subdued" variant="bodySm">Expired</Text>
                        )}
                        {command?.status === 'ACTIVE' && (
                            <Button destructive onClick={handleCancel} loading={cancelling} size="slim">
                                Cancel Command
                            </Button>
                        )}
                    </HorizontalStack>
                </HorizontalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    // ── Execution table ──────────────────────────────────────────────────────
    const execTableData = executions.map(exec => {
        const canExpand = exec.status === 'COMPLETED' || exec.status === 'FAILED'
        return {
            id: exec.execId || String(Math.random()),
            name: exec.execId || String(Math.random()),  // collapsible state key
            deviceIdText: exec.deviceId || '—',
            statusComp: executionStatusBadge(exec),
            exitText: exec.exitCode != null ? String(exec.exitCode) : '—',
            durationText: exec.durationMs != null ? `${exec.durationMs}ms` : '—',
            updatedText: exec.executedAt ? func.prettifyEpoch(Math.floor(exec.executedAt / 1000)) : '—',
            collapsibleRow: canExpand ? <ExpandedOutput exec={exec} /> : null,
        }
    })

    const execTable = (
        <GithubSimpleTable
            key={`exec-${executions.length}`}
            data={execTableData}
            headers={EXEC_HEADERS}
            headings={EXEC_HEADERS}
            resourceName={EXEC_RESOURCE}
            selectable={false}
            useNewRow={true}
            condensedHeight={true}
            loading={loading}
            hideQueryField={true}
            rowClickable={true}
        />
    )

    return (
        <PageWithMultipleCards
            title="Remote Commands"
            backUrl="/dashboard/settings/remote-commands"
            components={[headerCard, execTable]}
        />
    )
}

export default RemoteCommandDetail

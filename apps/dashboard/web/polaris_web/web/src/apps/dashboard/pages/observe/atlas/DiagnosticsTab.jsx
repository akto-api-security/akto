import { ActionList, Badge, Banner, Box, Button, Collapsible, HorizontalStack, List, Popover, Spinner, Text, TextField, VerticalStack } from "@shopify/polaris"
import { ChevronDownMinor, ChevronRightMinor, ExportMinor, RefreshMinor } from "@shopify/polaris-icons"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import func from "@/util/func"
import settingRequests from "../../settings/api"

// Diagnostic reports for one device: taken by the installer on every deploy, by the
// agent once it has set itself up, and on demand (Run diagnostics queues an
// akto-diagnose remote command for this device). The backend keeps the newest 20.

const SOURCE_LABELS = {
    'install': 'Install',
    'verify-failed': 'Install — agent never reported',
    'pkg-failed': 'Install — package failed',
    'post-provision': 'Agent setup',
    'on-demand': 'On demand',
    'manual': 'Manual',
}
const sourceLabel = (source) => SOURCE_LABELS[source] || source || 'Unknown'

// The outcome of the install (or agent setup) the report was taken for. The
// verdict describes the device, which can be healthy after a failed install
// (the previous version is still in place), so both are shown. Outcomes that
// say nothing beyond the source (postinstall, hooks-ok, …) get no badge.
const OUTCOME_BADGES = {
    'installed': { status: 'success', label: 'Install succeeded' },
    'failed': { status: 'critical', label: 'Install failed' },
    'pkg-install-failed': { status: 'critical', label: 'Package install failed' },
    'agent-not-running': { status: 'critical', label: 'Install failed — agent not running' },
    'agent-running-no-report': { status: 'critical', label: 'Install failed — agent never reported' },
    'hooks-failed': { status: 'critical', label: 'Hook install failed' },
}
const outcomeBadge = (outcome) => {
    if (!outcome) return null
    if (OUTCOME_BADGES[outcome]) return OUTCOME_BADGES[outcome]
    return /fail|missing|not-running|no-report/.test(outcome) ? { status: 'critical', label: `Install failed (${outcome})` } : null
}
const OutcomeBadge = ({ diagnostic }) => {
    const b = outcomeBadge(diagnostic?.outcome)
    return b ? <Badge status={b.status}>{b.label}</Badge> : null
}

const PROFILE_LABELS = { full: 'Full report', quick: 'Quick check' }
const RUN_POLL_MS = 5000
const ACTIVE_RUN_STATES = ['waiting', 'running']

// Why a run did not produce a report, in words an admin can act on.
const runFailureText = (run) => {
    const err = run.error || ''
    switch (run.errorReason) {
        case 'disabled_by_config': return 'On-demand diagnostics are disabled on this device (ENABLE_REMOTE_DIAGNOSTICS=false).'
        case 'timeout': return 'The diagnostic did not finish within 4 minutes.'
        case 'presumed_dead_agent': return 'The agent stopped responding while running the diagnostic.'
        case 'not_supported':
        case 'invalid_args': return err || 'This device cannot run on-demand diagnostics.'
        case 'exec_error':
            // An agent from before akto-diagnose looks it up on PATH.
            if (err.includes('akto-diagnose')) return 'This agent version does not support on-demand diagnostics. Update the agent and try again.'
            return err || 'The diagnostic could not be started on the device.'
        default: return err || 'The diagnostic failed on the device.'
    }
}

const PRE_STYLE = {
    fontFamily: 'monospace', fontSize: '12px', margin: 0,
    whiteSpace: 'pre-wrap', wordBreak: 'break-all',
    background: '#1a1a1a', color: '#e5e5e5',
    padding: '8px', borderRadius: '4px', maxHeight: '420px', overflow: 'auto',
}

const SEPARATOR = /^={20,}\s*$/
const ERROR_LINE = /^\s{0,2}❌/
const WARNING_LINE = /^\s{0,2}⚠️/

// Split a diagnose_mac.sh report into its sections. Each section is printed as
//   ====…====
//     TITLE
//   ====…====
// Anything before the first one is the report's header.
export const splitReportSections = (report) => {
    const lines = (report || '').split('\n')
    const sections = []
    let current = { title: 'Overview', lines: [] }
    for (let i = 0; i < lines.length; i++) {
        if (SEPARATOR.test(lines[i]) && i + 2 < lines.length && SEPARATOR.test(lines[i + 2]) && lines[i + 1].trim()) {
            if (current.lines.some(l => l.trim())) sections.push(current)
            current = { title: lines[i + 1].trim(), lines: [] }
            i += 2
            continue
        }
        current.lines.push(lines[i])
    }
    if (current.lines.some(l => l.trim())) sections.push(current)
    return sections.map(s => ({
        ...s,
        errors: s.lines.filter(l => ERROR_LINE.test(l)).length,
        warnings: s.lines.filter(l => WARNING_LINE.test(l)).length,
    }))
}

const VerdictBadge = ({ diagnostic }) => {
    if (!diagnostic?.verdict) return <Badge>No verdict</Badge>
    return diagnostic.healthy
        ? <Badge status="success">Device healthy</Badge>
        : <Badge status="critical">Device unhealthy</Badge>
}

const verdictText = (verdict) => (verdict || '').replace(/^[A-Z ]+—\s*/, '')
const verdictTitle = (verdict) => (verdict || '').split('—')[0].trim()

function ReportSection({ section, query, defaultOpen }) {
    const [open, setOpen] = useState(defaultOpen)
    useEffect(() => { setOpen(defaultOpen) }, [defaultOpen])
    const shown = query
        ? section.lines.filter(l => l.toLowerCase().includes(query.toLowerCase()))
        : section.lines
    if (query && shown.length === 0) return null
    return (
        <Box borderColor="border-subdued" borderWidth="1" borderRadius="2" padding="2">
            <VerticalStack gap="2">
                <Button plain monochrome removeUnderline fullWidth textAlign="left"
                    icon={open ? ChevronDownMinor : ChevronRightMinor}
                    onClick={() => setOpen(o => !o)}>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="bodyMd" fontWeight="semibold">{section.title}</Text>
                        {section.errors > 0 && <Badge status="critical">{`${section.errors} error${section.errors > 1 ? 's' : ''}`}</Badge>}
                        {section.warnings > 0 && <Badge status="warning">{`${section.warnings} warning${section.warnings > 1 ? 's' : ''}`}</Badge>}
                    </HorizontalStack>
                </Button>
                <Collapsible open={open} id={`diag-section-${section.title}`}>
                    <pre style={PRE_STYLE}>{shown.join('\n')}</pre>
                </Collapsible>
            </VerticalStack>
        </Box>
    )
}

function ReportViewer({ diagnostic, onBack }) {
    const [query, setQuery] = useState('')
    const sections = useMemo(() => splitReportSections(diagnostic.report), [diagnostic.report])
    const truncated = (diagnostic.report || '').includes('[truncated by server')

    const download = () => {
        const blob = new Blob([diagnostic.report || ''], { type: 'text/plain' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `akto-diagnostic-${diagnostic.deviceId || 'device'}-${diagnostic.createdAt || ''}.txt`
        a.click()
        URL.revokeObjectURL(url)
    }
    const copy = () => {
        navigator.clipboard.writeText(diagnostic.report || '').catch(() => {})
        func.setToast(true, false, 'Report copied')
    }

    return (
        <VerticalStack gap="3">
            <HorizontalStack align="space-between" blockAlign="center">
                <Button plain onClick={onBack}>← All reports</Button>
                <HorizontalStack gap="2">
                    <Button size="slim" onClick={copy}>Copy</Button>
                    <Button size="slim" icon={ExportMinor} onClick={download}>Download</Button>
                </HorizontalStack>
            </HorizontalStack>
            <SummaryCard diagnostic={diagnostic} />
            {truncated && <Banner status="warning">This report was larger than 512 KB and was truncated.</Banner>}
            <TextField label="Search the report" labelHidden placeholder="Search the report"
                value={query} onChange={setQuery} clearButton onClearButtonClick={() => setQuery('')} autoComplete="off" />
            {sections.map((s, i) => (
                <ReportSection key={`${s.title}-${i}`} section={s} query={query}
                    defaultOpen={!!query || s.errors > 0} />
            ))}
        </VerticalStack>
    )
}

function SummaryCard({ diagnostic, onOpen }) {
    const findings = diagnostic.findings || []
    return (
        <Box background="bg-surface-secondary" padding="4" borderRadius="2">
            <VerticalStack gap="2">
                <HorizontalStack align="space-between" blockAlign="center">
                    <HorizontalStack gap="2" blockAlign="center">
                        <OutcomeBadge diagnostic={diagnostic} />
                        <VerdictBadge diagnostic={diagnostic} />
                        <Text variant="headingSm" as="h3">{verdictTitle(diagnostic.verdict) || 'Diagnostic report'}</Text>
                    </HorizontalStack>
                    {onOpen && <Button size="slim" onClick={onOpen}>Open report</Button>}
                </HorizontalStack>
                {verdictText(diagnostic.verdict) && <Text as="p" variant="bodySm">{verdictText(diagnostic.verdict)}</Text>}
                {findings.length > 0 && (
                    <List type="bullet">
                        {findings.slice(0, 5).map((f, i) => <List.Item key={i}>{f}</List.Item>)}
                    </List>
                )}
                <Text as="p" variant="bodySm" color="subdued">
                    {[
                        sourceLabel(diagnostic.source),
                        diagnostic.requestedBy ? `by ${diagnostic.requestedBy}` : null,
                        func.prettifyEpoch(diagnostic.createdAt),
                        diagnostic.agentVersion ? `agent ${diagnostic.agentVersion}` : null,
                    ].filter(Boolean).join(' · ')}
                </Text>
            </VerticalStack>
        </Box>
    )
}

function RunRow({ run, onCancel, cancelling }) {
    const profile = PROFILE_LABELS[run.profile] || run.profile
    const meta = [profile, run.requestedBy ? `by ${run.requestedBy}` : null, func.prettifyEpoch(Math.floor((run.createdAt || 0) / 1000))]
        .filter(Boolean).join(' · ')
    let icon, title, detail
    switch (run.state) {
        case 'waiting':
            icon = <Spinner size="small" />
            title = 'Waiting for the device'
            detail = `Devices check in every 15 seconds. If this one is offline the run expires at ${new Date(run.expiresAt).toLocaleTimeString()}.`
            break
        case 'running':
            icon = <Spinner size="small" />
            title = 'Running on the device'
            detail = run.profile === 'quick' ? 'A quick check takes a few seconds.' : 'A full report takes about a minute.'
            break
        case 'expired':
            icon = <Badge status="warning">Expired</Badge>
            title = 'The device never picked it up'
            detail = 'It did not check in within an hour — it may be offline, or its agent is not running.'
            break
        case 'cancelled':
            icon = <Badge>Cancelled</Badge>
            title = 'Cancelled'
            break
        case 'completed':
            icon = <Badge status="attention">Not stored</Badge>
            title = 'Finished, but the report was not stored'
            detail = 'The backend does not support stored diagnostics yet; the output is in the Remote Commands history.'
            break
        default:
            icon = <Badge status="critical">Failed</Badge>
            title = 'Diagnostic failed'
            detail = runFailureText(run)
    }
    return (
        <Box padding="3" borderColor="border-subdued" borderWidth="1" borderRadius="2">
            <HorizontalStack align="space-between" blockAlign="start" wrap={false} gap="3">
                <HorizontalStack gap="3" blockAlign="start" wrap={false}>
                    <Box paddingBlockStart="05">{icon}</Box>
                    <VerticalStack gap="1">
                        <Text variant="bodyMd" fontWeight="semibold">{title}</Text>
                        {detail && <Text variant="bodySm" color="subdued">{detail}</Text>}
                        <Text variant="bodySm" color="subdued">{meta}</Text>
                    </VerticalStack>
                </HorizontalStack>
                {run.state === 'waiting' && (
                    <Button size="slim" onClick={() => onCancel(run.commandId)} loading={cancelling}>Cancel</Button>
                )}
            </HorizontalStack>
        </Box>
    )
}

function DiagnosticsTab({ agent }) {
    const [loading, setLoading] = useState(false)
    const [diagnostics, setDiagnostics] = useState([])
    const [opened, setOpened] = useState(null) // full report being viewed
    const [openingId, setOpeningId] = useState(null)
    const [runs, setRuns] = useState([])
    const [runMenuOpen, setRunMenuOpen] = useState(false)
    const [starting, setStarting] = useState(false)
    const [cancellingId, setCancellingId] = useState(null)
    const deviceId = agent?.deviceId
    const deviceRef = useRef(deviceId)
    deviceRef.current = deviceId

    const loadRuns = useCallback(async () => {
        if (!deviceId) return
        try {
            const res = await settingRequests.fetchEndpointShieldDiagnosticRuns(deviceId)
            if (deviceRef.current === deviceId) setRuns(res?.runs || [])
        } catch (e) {
            // Runs are secondary; the report list still renders.
        }
    }, [deviceId])

    const load = useCallback(async () => {
        if (!deviceId) return
        setLoading(true)
        try {
            const [res] = await Promise.all([
                settingRequests.fetchEndpointShieldDiagnostics(deviceId),
                loadRuns(),
            ])
            if (deviceRef.current === deviceId) setDiagnostics(res?.diagnostics || [])
        } catch (e) {
            setDiagnostics([])
        } finally {
            setLoading(false)
        }
    }, [deviceId, loadRuns])

    const wasActive = useRef(false)
    useEffect(() => { setOpened(null); setRuns([]); wasActive.current = false; load() }, [load])

    // While a run is waiting or running, poll it; when it finishes, reload the
    // reports so the new one appears at the top.
    const hasActiveRun = runs.some(r => ACTIVE_RUN_STATES.includes(r.state))
    useEffect(() => {
        if (!hasActiveRun) {
            if (wasActive.current) load()
            wasActive.current = false
            return
        }
        wasActive.current = true
        const t = setInterval(loadRuns, RUN_POLL_MS)
        return () => clearInterval(t)
    }, [hasActiveRun, loadRuns, load])

    const startRun = useCallback(async (profile) => {
        setRunMenuOpen(false)
        setStarting(true)
        try {
            await settingRequests.runEndpointShieldDiagnostic(deviceId, profile)
            await loadRuns()
        } catch (e) {
            func.setToast(true, true, 'Could not start diagnostics')
        } finally {
            setStarting(false)
        }
    }, [deviceId, loadRuns])

    const cancelRun = useCallback(async (commandId) => {
        setCancellingId(commandId)
        try {
            await settingRequests.cancelEndpointShieldDiagnosticRun(commandId)
            await loadRuns()
        } finally {
            setCancellingId(null)
        }
    }, [loadRuns])

    const open = useCallback(async (id) => {
        setOpeningId(id)
        try {
            const res = await settingRequests.fetchEndpointShieldDiagnostic(id)
            if (res?.diagnostic) setOpened(res.diagnostic)
        } finally {
            setOpeningId(null)
        }
    }, [])

    if (opened) {
        return <Box paddingBlockStart="4"><ReportViewer diagnostic={opened} onBack={() => setOpened(null)} /></Box>
    }

    const [latest, ...older] = diagnostics
    // Runs worth showing: in flight, or ended without a report since the latest
    // one (a completed run with a stored report is simply that report).
    const latestMs = (latest?.createdAt || 0) * 1000
    const shownRuns = runs.filter(r =>
        ACTIVE_RUN_STATES.includes(r.state) || (r.createdAt > latestMs && !(r.state === 'completed' && r.diagnosticId))
    ).slice(0, 3)
    return (
        <Box paddingBlockStart="4">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingSm" as="h3">Diagnostics</Text>
                    <HorizontalStack gap="2">
                        <Button size="slim" icon={RefreshMinor} onClick={load} disabled={loading} accessibilityLabel="Refresh" />
                        <Popover
                            active={runMenuOpen}
                            onClose={() => setRunMenuOpen(false)}
                            activator={
                                <Button size="slim" primary disclosure loading={starting} disabled={hasActiveRun || !deviceId}
                                    onClick={() => setRunMenuOpen(o => !o)}>
                                    Run diagnostics
                                </Button>
                            }
                        >
                            <ActionList actionRole="menuitem" items={[
                                { content: 'Full report', helpText: 'Everything — about a minute', onAction: () => startRun('full') },
                                { content: 'Quick check', helpText: 'Verdict, services and backend reachability — seconds', onAction: () => startRun('quick') },
                            ]} />
                        </Popover>
                    </HorizontalStack>
                </HorizontalStack>
                {shownRuns.map(r => (
                    <RunRow key={r.commandId} run={r} onCancel={cancelRun} cancelling={cancellingId === r.commandId} />
                ))}
                {loading && diagnostics.length === 0 ? (
                    <Box padding="6"><HorizontalStack align="center"><Spinner size="small" /></HorizontalStack></Box>
                ) : !latest ? (
                    <Box padding="6">
                        <Text as="p" color="subdued" alignment="center">
                            No diagnostic reports yet. A report is taken automatically on every install and when the agent sets itself up, or run one now with Run diagnostics.
                        </Text>
                    </Box>
                ) : (
                    <VerticalStack gap="3">
                        <SummaryCard diagnostic={latest} onOpen={() => open(latest.id)} />
                        {older.length > 0 && (
                            <VerticalStack gap="1">
                                <Text variant="bodySm" color="subdued">Earlier reports</Text>
                                {older.map(d => (
                                    <Box key={d.id} padding="2" borderColor="border-subdued" borderBlockEndWidth="1">
                                        <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                                            <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                                                <VerdictBadge diagnostic={d} />
                                                <OutcomeBadge diagnostic={d} />
                                                <VerticalStack gap="0">
                                                    <Text variant="bodySm" fontWeight="medium">{sourceLabel(d.source)}</Text>
                                                    <Text variant="bodySm" color="subdued">
                                                        {[func.prettifyEpoch(d.createdAt), d.errorCount ? `${d.errorCount} error${d.errorCount > 1 ? 's' : ''}` : null].filter(Boolean).join(' · ')}
                                                    </Text>
                                                </VerticalStack>
                                            </HorizontalStack>
                                            <Button plain loading={openingId === d.id} onClick={() => open(d.id)}>View</Button>
                                        </HorizontalStack>
                                    </Box>
                                ))}
                            </VerticalStack>
                        )}
                    </VerticalStack>
                )}
            </VerticalStack>
        </Box>
    )
}

export default DiagnosticsTab

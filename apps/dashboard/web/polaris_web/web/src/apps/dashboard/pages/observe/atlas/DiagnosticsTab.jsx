import { Badge, Banner, Box, Button, Collapsible, HorizontalStack, List, Spinner, Text, TextField, VerticalStack } from "@shopify/polaris"
import { ChevronDownMinor, ChevronRightMinor, ExportMinor, RefreshMinor } from "@shopify/polaris-icons"
import { useCallback, useEffect, useMemo, useState } from "react"
import func from "@/util/func"
import settingRequests from "../../settings/api"

// Diagnostic reports for one device: taken by the installer on every deploy, by the
// agent once it has set itself up, and on demand. The backend keeps the newest 20.

const SOURCE_LABELS = {
    'install': 'Install',
    'verify-failed': 'Install — agent never reported',
    'pkg-failed': 'Install — package failed',
    'post-provision': 'Agent setup',
    'on-demand': 'On demand',
    'manual': 'Manual',
}
const sourceLabel = (source) => SOURCE_LABELS[source] || source || 'Unknown'

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
        ? <Badge status="success">Healthy</Badge>
        : <Badge status="critical">Unhealthy</Badge>
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

function DiagnosticsTab({ agent }) {
    const [loading, setLoading] = useState(false)
    const [diagnostics, setDiagnostics] = useState([])
    const [opened, setOpened] = useState(null) // full report being viewed
    const [openingId, setOpeningId] = useState(null)
    const deviceId = agent?.deviceId

    const load = useCallback(async () => {
        if (!deviceId) return
        setLoading(true)
        try {
            const res = await settingRequests.fetchEndpointShieldDiagnostics(deviceId)
            setDiagnostics(res?.diagnostics || [])
        } catch (e) {
            setDiagnostics([])
        } finally {
            setLoading(false)
        }
    }, [deviceId])

    useEffect(() => { setOpened(null); load() }, [load])

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
    return (
        <Box paddingBlockStart="4">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingSm" as="h3">Diagnostics</Text>
                    <Button size="slim" icon={RefreshMinor} onClick={load} disabled={loading} accessibilityLabel="Refresh" />
                </HorizontalStack>
                {loading && diagnostics.length === 0 ? (
                    <Box padding="6"><HorizontalStack align="center"><Spinner size="small" /></HorizontalStack></Box>
                ) : !latest ? (
                    <Box padding="6">
                        <Text as="p" color="subdued" alignment="center">
                            No diagnostic reports yet. A report is taken automatically on every install and when the agent sets itself up.
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

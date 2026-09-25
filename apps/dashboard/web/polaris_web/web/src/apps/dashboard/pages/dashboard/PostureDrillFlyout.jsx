import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Badge, Banner, Box, Button, Card, DataTable, Divider, HorizontalStack, Spinner, Text, VerticalStack,
} from '@shopify/polaris'
import AgenticFlyoutShell from '../observe/agentic/AgenticFlyoutShell'
import FlyoutBreadcrumb from '../observe/agentic/FlyoutBreadcrumb'
import AgGridTable from '../../components/tables/AgGridTable'
import SpinnerCentered from '../../components/progress/SpinnerCentered'
import MarkdownViewer from '@/apps/dashboard/components/shared/MarkdownViewer'
import CustomProgressBar from './new_components/CustomProgressBar'
import SmoothAreaChart from './new_components/SmoothChart'
import { DELTA_TONE_TO_COLOR, DummyDataOverlay, formatDelta, GapHint, riskBand, RiskScoreRing } from './new_components/PostureShared'
import { DUMMY_RISK_SCORE_TREND } from './securityPostureDummyData'
import dashboardApi from './api'
import func from '@/util/func'

// How many times to re-poll a PENDING narrative before giving up silently (no more network
// calls, but the "Generating summary…" line stays as-is rather than flipping to an error state —
// a slow LLM call isn't a failure). 5 tries * 3s = 15s, generous for the one-shot renderer call
// InsightNarrativeHandler makes.
const MAX_NARRATIVE_POLLS = 5
const NARRATIVE_POLL_INTERVAL_MS = 3000

// Must match PostureService.DRILL_RISK_SCORE on the backend — the one drill whose root level
// (path="") renders the rich composite/sub-score/"what moved" view below instead of the generic
// AgGridTable every other drill/level uses (see PostureDrillResult#riskScoreBreakdown's own
// javadoc for why).
const DRILL_RISK_SCORE = 'riskScoreBreakdown'

// Same relative-time rendering LLMCellRenderers.jsx's TimeCell uses, minus its /1000 — every
// drill's "detectedAt" column (see PostureService#fetchDrill's row builders) is already epoch
// seconds, not epoch millis.
function DetectedAtCell({ value }) {
    return <Text variant="bodySm">{func.prettifyEpoch(value || 0)}</Text>
}

// ─── Stats row ────────────────────────────────────────────────────────────────
// Same "big number over a subdued label" stat-tile language OverviewContent (SessionFlyout) uses
// — a row total is always shown (real, not from the backend's own optional summary[]), plus
// whatever InsightResult.Metric rows the drill sends.

function DrillStats({ drill }) {
    const stats = [
        { key: '__total', label: 'Rows', value: (drill.total ?? 0).toLocaleString() },
        ...(drill.summary || []).map((m) => ({ key: m.key, label: m.label, value: m.formatted })),
    ]
    return (
        <HorizontalStack gap="6">
            {stats.map((s) => (
                <VerticalStack gap="1" key={s.key}>
                    <Text variant="headingLg" as="p">{s.value}</Text>
                    <Text variant="bodySm" color="subdued">{s.label}</Text>
                </VerticalStack>
            ))}
        </HorizontalStack>
    )
}

// ─── AI summary ───────────────────────────────────────────────────────────────
// Same box/label language InsightDetailView.jsx's own "Analysis" section already uses for the
// identical OK/PENDING/UNAVAILABLE + markdown/concern/impact/remediation contract
// PostureDrillNarrativeService now sends for every drill level, not just insights.
function DrillNarrative({ drill }) {
    const status = drill.narrativeStatus
    if (status === 'UNAVAILABLE') return null
    const hasSummary = drill.narrativeConcern || drill.narrativeImpact || drill.narrativeRemediation
    return (
        <Box background="bg-surface-secondary" padding="4" borderRadius="2">
            <VerticalStack gap="4">
                <VerticalStack gap="1">
                    <Text variant="bodySm" fontWeight="semibold" color="subdued">AI summary</Text>
                    {status === 'PENDING' ? (
                        <HorizontalStack gap="2" blockAlign="center">
                            <Spinner size="small" accessibilityLabel="Generating AI summary" />
                            <Text variant="bodyMd" color="subdued">Generating summary…</Text>
                        </HorizontalStack>
                    ) : (
                        <MarkdownViewer markdown={drill.narrativeMarkdown} noPadding />
                    )}
                </VerticalStack>
                {status === 'OK' && hasSummary && (
                    <VerticalStack gap="3">
                        {drill.narrativeConcern && (
                            <VerticalStack gap="1">
                                <Text variant="bodySm" fontWeight="semibold" color="subdued">Concern</Text>
                                <Text variant="bodyMd">{drill.narrativeConcern}</Text>
                            </VerticalStack>
                        )}
                        {drill.narrativeImpact && (
                            <VerticalStack gap="1">
                                <Text variant="bodySm" fontWeight="semibold" color="subdued">Impact</Text>
                                <Text variant="bodyMd">{drill.narrativeImpact}</Text>
                            </VerticalStack>
                        )}
                        {drill.narrativeRemediation && (
                            <VerticalStack gap="1">
                                <Text variant="bodySm" fontWeight="semibold" color="subdued">Remediation</Text>
                                <Text variant="bodyMd">{drill.narrativeRemediation}</Text>
                            </VerticalStack>
                        )}
                    </VerticalStack>
                )}
            </VerticalStack>
        </Box>
    )
}

// ─── Risk score breakdown (DRILL_RISK_SCORE's root level) ──────────────────────
// The former standalone "Risk score breakdown" FlyLayout — relocated here wholesale so it renders
// as this drill's own root level (real breadcrumb/URL/AI-summary, same as every other panel)
// instead of a separate flyout/action. Each sub-score row navigates to its own level-2 detail
// table (path=subScore.id) — see PostureService#fetchRiskScoreDrill's own javadoc for what each
// id delegates to.

// What's driving each sub-score — one line per subScore.id, built from the matching breakdown
// field RiskScoreCalculator.computeBreakdown adds (see its own javadoc for why the shape differs
// per sub-score: shadow AI/vendor risk are device-count snapshots, DLP mirrors threat activity's
// per-device diff, compliance gaps groups by policy since an uncovered event isn't one device's).
function subScoreDetailLines(subScore, kpi) {
    switch (subScore.id) {
        case 'shadowAiExposure': {
            const rows = kpi.shadowAiTopServices || []
            if (rows.length === 0) return null
            return rows.map((r) => `${r.service} (${r.status.toLowerCase()}, ${r.deviceCount} device${r.deviceCount === 1 ? '' : 's'})`).join(' · ')
        }
        case 'dlpIncidents': {
            const rows = kpi.dlpDeviceMovements || []
            if (rows.length === 0) return null
            return rows.map((r) => `${r.username || r.deviceId} (${r.impactPoints > 0 ? '+' : ''}${r.impactPoints} pts)`).join(' · ')
        }
        case 'vendorRisk': {
            const table = kpi.vendorTable || []
            const topUnapproved = kpi.vendorRiskTopUnapproved || []
            const lines = []
            if (table.length > 0) {
                lines.push(table.slice(0, 2).map((v) => `${v.vendor} (${v.approved ? 'approved' : 'unapproved'}, ${v.count})`).join(' · '))
            }
            if (topUnapproved.length > 0) {
                lines.push('Top unapproved by device: ' + topUnapproved.map((v) => `${v.vendor} (${v.deviceCount} device${v.deviceCount === 1 ? '' : 's'})`).join(' · '))
            }
            return lines.length > 0 ? lines : null
        }
        case 'complianceGaps': {
            const rows = kpi.complianceGapsByPolicy || []
            if (rows.length === 0) return null
            return rows.map((r) => `${r.policy} (${r.count})`).join(' · ')
        }
        default:
            return null
    }
}

// One row of the risk score breakdown — a sub-score's weight, its bar, and its value or (when
// null) the same "why is this missing" hint every other gap uses. Clickable through to that
// sub-score's own level-2 detail table.
function RiskScoreSubScoreRow({ subScore, kpi, onClick }) {
    const hasValue = subScore.value !== null && subScore.value !== undefined
    const band = riskBand(subScore.value)
    const detail = subScoreDetailLines(subScore, kpi)
    const detailLines = Array.isArray(detail) ? detail : (detail ? [detail] : [])
    return (
        <div onClick={onClick} style={{ cursor: onClick ? 'pointer' : 'default' }}>
            <VerticalStack gap="2">
                <HorizontalStack align="space-between" blockAlign="center" gap={"2"}>
                    <Box width='200px' maxWidth='200px'>
                        <VerticalStack gap="05">
                            <HorizontalStack gap="1" blockAlign="center" align="start">
                                <Text variant="bodyMd" fontWeight="semibold">{subScore.label}</Text>
                                <GapHint gaps={subScore.dataGaps} />
                            </HorizontalStack>
                            <Text variant="bodySm" color="subdued">{subScore.weight}% of composite</Text>
                        </VerticalStack>
                    </Box>
                    <Box width='540px'>
                        <CustomProgressBar progress={hasValue ? subScore.value : 0} topColor={band ? band.color : '#9ca3af'} height={"8px"}/>
                    </Box>
                    <Box width='120px' maxWidth='120px'>
                        <HorizontalStack align="end">
                            <Text variant="bodyMd" fontWeight="semibold">
                                {hasValue ? `${subScore.value} / 100` : 'Not computed'}
                            </Text>
                        </HorizontalStack>
                    </Box>
                </HorizontalStack>

                {detailLines.length > 0 && (
                    <Box paddingBlockStart="1">
                        <VerticalStack gap="05">
                            {detailLines.map((line, i) => (
                                <Text key={i} variant="bodySm" color="subdued">{line}</Text>
                            ))}
                        </VerticalStack>
                    </Box>
                )}
            </VerticalStack>
        </div>
    )
}

// Composite trend — needs posture_score_history, which doesn't exist yet (see
// PostureService.GAP_POSTURE_HISTORY), so illustrative-only and blurred, same convention as every
// other backend-less panel on this page.
function RiskScoreTrendSection() {
    const latest = DUMMY_RISK_SCORE_TREND[DUMMY_RISK_SCORE_TREND.length - 1]
    const body = (
        <VerticalStack gap="4">
            <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                <RiskScoreRing value={latest} size={90} showValue />
                <VerticalStack gap="2">
                    <Text variant="bodySm" color="subdued">Composite trend · last 12 weeks</Text>
                    <SmoothAreaChart tickPositions={DUMMY_RISK_SCORE_TREND} color="#7C5CFC" height="60" width="260" />
                </VerticalStack>
            </HorizontalStack>
            <HorizontalStack gap="2">
                {['30 days', '90 days', '365 days'].map((label, i) => (
                    <Badge key={label} status={i === 0 ? 'info' : undefined}>{label}</Badge>
                ))}
            </HorizontalStack>
        </VerticalStack>
    )
    return <DummyDataOverlay panelId="riskScoreTrend">{body}</DummyDataOverlay>
}

// Supporting "who/what drove it" text for one whatMoved category row — pulled from the
// breakdown's own top-2 device/policy lists (kpi.threatActivityMovements/dlpDeviceMovements/
// complianceGapsByPolicy). Shadow AI exposure and Vendor risk never appear here: they aren't
// time-windowed (see RiskScoreCalculator's addWhatMovedRow comment), so they cannot show up in
// kpi.whatMoved in the first place.
function movedRowDetail(category, kpi) {
    if (category === 'Threat activity' || category === 'DLP incidents') {
        const rows = (category === 'Threat activity' ? kpi.threatActivityMovements : kpi.dlpDeviceMovements) || []
        return rows.map((r) => `${r.username || r.deviceId} (${r.diff > 0 ? '+' : ''}${r.diff})`).join(', ')
    }
    if (category === 'Compliance gaps') {
        return (kpi.complianceGapsByPolicy || []).map((r) => `${r.policy} (${r.count})`).join(', ')
    }
    return ''
}

// Real, not illustrative — one row per sub-score that actually moved between this window and the
// immediately preceding one (RiskScoreCalculator#compute's whatMoved). Each row's points are
// computed the exact same way the composite's own delta is (this sub-score's weight over the
// composite's coveredWeight, times its own current-minus-prior) — summing every row here reproduces
// the composite delta exactly, not approximately, because it's that same weighted-average formula
// decomposed back into its terms.
function RiskScoreAnnotationsSection({ kpi }) {
    const rows = (kpi.whatMoved || []).slice().sort((a, b) => Math.abs(b.impactPoints) - Math.abs(a.impactPoints))

    const tableRows = rows.map((row) => [
        row.category,
        movedRowDetail(row.category, kpi),
        <Text variant="bodyMd" fontWeight="semibold" color={row.impactPoints > 0 ? 'critical' : 'success'}>
            {row.impactPoints > 0 ? `+${row.impactPoints}` : row.impactPoints} pts
        </Text>,
    ])

    return (
        <VerticalStack gap="3">
            <VerticalStack gap="1">
                <Text variant="headingSm">What moved the score</Text>
                <Text variant="bodySm" color="subdued">
                    Each sub-score's own contribution to this period's change — adds up to the delta above
                </Text>
            </VerticalStack>
            {rows.length === 0 ? (
                <Text variant="bodySm" color="subdued">
                    No prior-window comparison available, or nothing changed this period.
                </Text>
            ) : (
                <Card padding="0">
                    <DataTable
                        columnContentTypes={['text', 'text', 'numeric']}
                        headings={['Category', 'Detail', 'Impact']}
                        rows={tableRows}
                        hideScrollIndicator
                        increasedTableDensity
                    />
                </Card>
            )}
        </VerticalStack>
    )
}

// Root-level body for DRILL_RISK_SCORE — the composite ring/value/delta, the always-blurred trend,
// each sub-score (clickable through to its own level-2 table), and "what moved the score". `kpi`
// is the risk score KPI tile's own value/delta/dataGaps/footnote merged with this fetch's
// `riskScoreBreakdown` payload (see PostureDrillResult#riskScoreBreakdown's own javadoc).
function RiskScoreRootBody({ kpi, onSubScoreClick }) {
    if (!kpi) return null
    const band = riskBand(kpi.value)
    const historyGap = (kpi.dataGaps || []).find((g) => g.source === 'POSTURE_HISTORY')
    const deltaText = formatDelta(kpi)

    return (
        <Box overflowY="scroll" padding="4">
            <VerticalStack gap="5">
                <VerticalStack gap="2">
                    <HorizontalStack gap="3" blockAlign="center">
                        <RiskScoreRing value={kpi.value} size={56} />
                        <VerticalStack gap="1">
                            <HorizontalStack gap="3" blockAlign="center">
                                <Text variant="heading2xl">
                                    {kpi.value !== null && kpi.value !== undefined ? `${kpi.value} / 100` : 'Not computed yet'}
                                </Text>
                                {band && (
                                    <Badge status={band.tone === 'critical' ? 'critical' : band.tone === 'warning' ? 'warning' : 'success'}>
                                        {band.label}
                                    </Badge>
                                )}
                            </HorizontalStack>
                            {deltaText && (
                                <Text variant="bodySm" fontWeight="semibold" color={DELTA_TONE_TO_COLOR[kpi.deltaTone] || 'subdued'}>
                                    {deltaText}
                                </Text>
                            )}
                        </VerticalStack>
                    </HorizontalStack>
                    <Text variant="bodySm" color="subdued">
                        Composite of five weighted sub-scores. Lower is better.
                        {kpi.footnote ? ` ${kpi.footnote}.` : ''}
                    </Text>
                </VerticalStack>

                <RiskScoreTrendSection />

                <VerticalStack gap="4">
                    <VerticalStack gap="3">
                        {(kpi.subScores || []).map((s) => (
                            <RiskScoreSubScoreRow key={s.id} subScore={s} kpi={kpi} onClick={() => onSubScoreClick(s.id)} />
                        ))}
                    </VerticalStack>
                    {historyGap && (
                        <Text variant="bodySm" color="subdued">{historyGap.impact}</Text>
                    )}
                </VerticalStack>

                <RiskScoreAnnotationsSection kpi={kpi} />
            </VerticalStack>
        </Box>
    )
}

// ─── PostureDrillFlyout ───────────────────────────────────────────────────────
// One flyout every posture panel opens (Shadow AI tools, What data is leaving, Enforcement
// funnel, Vendor risk, Framework readiness) — see PostureService#fetchDrill's own javadoc for the
// drillId/path/level contract this mirrors 1:1. Same shell/breadcrumb language as SessionFlyout /
// AgenticAssetFlyout (AgenticFlyoutShell + FlyoutBreadcrumb), rather than this page's own
// FlyLayout, for visual consistency with the rest of the app's drilldown flyouts.
//
// `drillState` ({ drillId, path } | null) and its URL sync are owned by the parent page, not this
// component — SecurityPosture.jsx mirrors it to `?drill=&path=` so a drilldown link is shareable
// and reloadable. This component only renders whatever level `drillState` currently points to and
// asks the parent (via onNavigate) to move to a different level — a breadcrumb click or a row
// click when the current level is drillable.
function PostureDrillFlyout({ drillState, onNavigate, onClose, riskScoreKpi, startTimestamp, endTimestamp }) {
    const navigate = useNavigate()
    const show = !!drillState
    const [drill, setDrill] = useState(null)
    const [loading, setLoading] = useState(false)
    // DRILL_RISK_SCORE's root level renders the rich composite/sub-score view (RiskScoreRootBody)
    // instead of the generic AgGridTable every other drill/level uses — the former standalone
    // "Risk score breakdown" FlyLayout, relocated here wholesale.
    const isRiskScoreRoot = drillState?.drillId === DRILL_RISK_SCORE && !drillState?.path
    const mergedRiskScoreKpi = useMemo(() => (
        riskScoreKpi ? { ...riskScoreKpi, ...(drill?.riskScoreBreakdown || {}) } : null
    ), [riskScoreKpi, drill?.riskScoreBreakdown])

    // The initial fetch (below) already returns page 1's rows alongside the title/columns/
    // breadcrumb/ctas/gaps. AgGridTable's own SSRM datasource fires its own getRows() the moment
    // it mounts, which would otherwise re-fetch that exact same page over the network a second
    // time — this cache lets its first call (skip===0, same drillId/path) reuse what's already in
    // hand instead. Consumed once; every other fetch (pagination, a later remount) goes to the
    // network as normal.
    const firstPageCache = useRef(null)
    // Reset whenever the level changes (new drillId/path) — see the polling effect below.
    const narrativePollCount = useRef(0)

    useEffect(() => {
        setDrill(null)
        firstPageCache.current = null
        narrativePollCount.current = 0
        if (!drillState) return
        let cancelled = false

        async function load() {
            setLoading(true)
            try {
                const resp = await dashboardApi.fetchPostureDrill(
                    drillState.drillId, drillState.path, startTimestamp, endTimestamp, 0, 20)
                if (cancelled) return
                setDrill(resp || null)
                firstPageCache.current = resp
                    ? { drillId: drillState.drillId, path: drillState.path, rows: resp.rows || [], total: resp.total || 0 }
                    : null
            } catch (error) {
                console.error('Error fetching posture drill:', error)
                if (!cancelled) setDrill(null)
            } finally {
                if (!cancelled) setLoading(false)
            }
        }

        load()
        return () => { cancelled = true }
    }, [drillState, startTimestamp, endTimestamp])

    // Every ancestor's own {path, label} comes back from the backend on every fetch (see
    // PostureDrillResult.breadcrumb's own javadoc) — a reload from a deep-linked URL renders the
    // full trail with no extra round trips to reconstruct it.
    const breadcrumbItems = useMemo(() => (drill?.breadcrumb || []).map((b, i, arr) => ({
        label: b.label,
        onClick: i === arr.length - 1 ? undefined
            : () => onNavigate({ drillId: drillState.drillId, path: b.path }),
    })), [drill?.breadcrumb, drillState, onNavigate])

    const columnDefs = useMemo(() => (drill?.columns || []).map((c) => ({
        field: c.field,
        headerName: c.headerName,
        flex: 1,
        minWidth: 130,
        cellStyle: { display: 'flex', alignItems: 'center' },
        ...(c.field === 'detectedAt' ? { cellRenderer: DetectedAtCell } : {}),
    })), [drill?.columns])

    const onServerFetch = useCallback(({ skip, limit }) => {
        const cached = firstPageCache.current
        if (skip === 0 && cached && cached.drillId === drillState.drillId && cached.path === drillState.path) {
            firstPageCache.current = null
            return Promise.resolve({ value: cached.rows, total: cached.total })
        }
        return dashboardApi.fetchPostureDrill(
            drillState.drillId, drillState.path, startTimestamp, endTimestamp, skip, limit || 20
        ).then((resp) => ({ value: resp?.rows || [], total: resp?.total || 0 }))
    }, [drillState?.drillId, drillState?.path, startTimestamp, endTimestamp])

    const handleRowClicked = useCallback((e) => {
        if (!drill?.drillable || e?.data?.id === undefined || e?.data?.id === null) return
        const nextPath = drillState.path ? `${drillState.path}/${e.data.id}` : String(e.data.id)
        onNavigate({ drillId: drillState.drillId, path: nextPath })
    }, [drill?.drillable, drillState, onNavigate])

    const handleSubScoreClick = useCallback((subScoreId) => {
        if (!drillState) return
        onNavigate({ drillId: drillState.drillId, path: subScoreId })
    }, [drillState, onNavigate])

    // AI summary is generated in the background (PostureDrillNarrativeService) — a PENDING status
    // means the initial fetch above hit a cache miss and the backend kicked off generation without
    // waiting on it. Re-fetching the same drillId/path a few seconds later picks up the finished
    // prose once it's cached; only the narrative fields are merged in, so this never disturbs the
    // table's own SSRM-managed rows/pagination.
    useEffect(() => {
        if (!drill || drill.narrativeStatus !== 'PENDING' || !drillState) return
        if (narrativePollCount.current >= MAX_NARRATIVE_POLLS) return
        let cancelled = false
        const timer = setTimeout(async () => {
            narrativePollCount.current += 1
            try {
                const resp = await dashboardApi.fetchPostureDrill(
                    drillState.drillId, drillState.path, startTimestamp, endTimestamp, 0, 20)
                if (cancelled || !resp) return
                setDrill((prev) => (prev ? {
                    ...prev,
                    narrativeStatus: resp.narrativeStatus,
                    narrativeMarkdown: resp.narrativeMarkdown,
                    narrativeConcern: resp.narrativeConcern,
                    narrativeImpact: resp.narrativeImpact,
                    narrativeRemediation: resp.narrativeRemediation,
                } : prev))
            } catch (error) {
                console.error('Error polling posture drill narrative:', error)
            }
        }, NARRATIVE_POLL_INTERVAL_MS)
        return () => { cancelled = true; clearTimeout(timer) }
    }, [drill, drillState, startTimestamp, endTimestamp])

    return (
        <AgenticFlyoutShell
            show={show}
            width={760}
            header={
                <FlyoutBreadcrumb
                    items={breadcrumbItems}
                    onClose={onClose}
                    subtitle={drillState?.path ? drill?.title : null}
                />
            }
        >
            {/* Plain flex divs all the way down to the grid, deliberately not Polaris VerticalStack
                — AgGridTable's domLayout="normal" needs an unbroken pixel-height chain to size
                itself against, and VerticalStack doesn't forward flex-grow the way Box/a div does
                (see AgenticFlyoutShell's own reliance on Box forwarding raw style for the same
                reason). Same structure DevicesTab/SessionTracesContent already use. */}
            <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
                {/* AgenticFlyoutShell keeps rendering children through its own close transition, so
                    `drillState` can already be null here for a render or two after the close button
                    is clicked, before the effect above catches up and resets `drill` to null too —
                    guard on both, not just `drill`, or drillState.drillId below throws. */}
                {!drill || !drillState || loading ? (
                    <SpinnerCentered height="200px" />
                ) : (
                    <>
                        <Box padding="4" paddingBlockEnd="0">
                            <HorizontalStack align="space-between" blockAlign="start">
                                {!isRiskScoreRoot && <DrillStats drill={drill} />}
                                {(drill.ctas || []).length > 0 && (
                                    <HorizontalStack gap="2">
                                        {drill.ctas.map((cta) => (
                                            <Button key={cta.id} size="slim" onClick={() => navigate(cta.route)}>{cta.label}</Button>
                                        ))}
                                    </HorizontalStack>
                                )}
                            </HorizontalStack>
                            {(drill.dataGaps || []).length > 0 && (
                                <Box paddingBlockStart="3">
                                    <VerticalStack gap="2">
                                        {drill.dataGaps.map((g, i) => (
                                            <Banner key={i} status="info">{g.impact}</Banner>
                                        ))}
                                    </VerticalStack>
                                </Box>
                            )}
                        </Box>
                        <Box paddingBlockStart="4"><Divider /></Box>
                        <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
                            {isRiskScoreRoot ? (
                                <RiskScoreRootBody kpi={mergedRiskScoreKpi} onSubScoreClick={handleSubScoreClick} />
                            ) : (
                                <AgGridTable
                                    key={`${drillState.drillId}:${drillState.path || ''}`}
                                    columnDefs={columnDefs}
                                    defaultColDef={{ sortable: false, resizable: true, filter: false }}
                                    onServerFetch={onServerFetch}
                                    serverSideRowModel
                                    onRowClicked={drill.drillable ? handleRowClicked : undefined}
                                    getRowStyle={drill.drillable ? () => ({ cursor: 'pointer' }) : undefined}
                                    noOuterBorder
                                    domLayout="normal"
                                    paginationPageSize={20}
                                    hidePageSizeSelector
                                    filterStateUrl={`security-posture-drill/${drillState?.drillId || ''}/${drillState?.path || ''}`}
                                    sideBar={false}
                                />
                            )}
                        </div>
                        {drill.narrativeStatus !== 'UNAVAILABLE' && (
                            <Box paddingBlockStart="3">
                                <DrillNarrative drill={drill} />
                            </Box>
                        )}
                    </>
                )}
            </div>
        </AgenticFlyoutShell>
    )
}

export default PostureDrillFlyout

import { useEffect, useReducer, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Badge, Box, Card, DataTable, HorizontalGrid, HorizontalStack, Icon, Popover, Text, Tooltip, VerticalStack,
} from '@shopify/polaris'
import { CircleInformationMajor } from '@shopify/polaris-icons'
import { produce } from 'immer'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import FlyLayout from '../../components/layouts/FlyLayout'
import CardWithHeader from './new_components/CardWithHeader'
import CustomProgressBar from './new_components/CustomProgressBar'
import DonutChart from '../../components/shared/DonutChart'
import SmoothAreaChart from './new_components/SmoothChart'
import StackedAreaChart from '../../components/charts/StackedAreaChart'
import StackedChart from '../../components/charts/StackedChart'
import { SeverityBadge } from '../observe/agentic/AgenticCellRenderers'
import InsightsFlyout from '../observe/agentic/insights/InsightsFlyout'
import { INSIGHT_GROUP } from '../observe/agentic/insights/insightsHelpers'
import dashboardApi from './api'
import func from '@/util/func'
import values from '@/util/values'
import SpinnerCentered from '../../components/progress/SpinnerCentered'
import {
    PANEL_EMPTY_STATE_COPY, DUMMY_SHADOW_AI_TREND, DUMMY_DATA_LEAVING, DUMMY_ENFORCEMENT_FUNNEL,
    DUMMY_ATTACK_ATTEMPTS, DUMMY_FRAMEWORK_READINESS, DUMMY_ADOPTION_GAP, DUMMY_VENDOR_RISK_BUBBLE,
    DUMMY_RISK_SCORE_TREND,
} from './securityPostureDummyData'

// KPI ids — must match PostureService.KPI_* on the backend.
const KPI_RISK_SCORE = 'riskScore'
const KPI_CRITICAL_ALERTS = 'criticalAlerts'
const KPI_MONITORING_COVERAGE = 'monitoringCoverage'
const KPI_SENSITIVE_INCIDENTS = 'sensitiveDataIncidents'

const DELTA_TONE_TO_COLOR = {
    critical: 'critical',
    success: 'success',
    neutral: 'subdued',
}

// Donut/segment colors — matches the palette other posture cards (ComplianceAtRisksCard,
// GuardrailCoverageCard) already use, so a "data type" or "policy mode" reads the same tone
// wherever it shows up on the page.
const SEGMENT_COLORS = ['#dc2626', '#ea580c', '#ca8a04', '#3b82f6', '#7c3aed', '#9ca3af']

// Mirrors InsightService.severityRank on the backend (CRITICAL first, missing/unrecognized
// last) — needed here because "Act now" merges two already-sorted lists (discovery, guardrail)
// into one and has to re-establish a single worst-first order across both.
const SEVERITY_RANK = { CRITICAL: 1, HIGH: 2, MEDIUM: 3, LOW: 4 }
function severityRank(severity) {
    return SEVERITY_RANK[String(severity || '').toUpperCase()] || 5
}

// Same thresholds for the composite's band badge and a sub-score row's bar color — these scores
// are "higher is worse", so red/amber/green reads the same way at either level. A first-pass
// banding (not something the backend sends), easy to retune once real accounts show where the
// bands should actually sit.
function riskBand(value) {
    if (value === null || value === undefined) return null
    if (value >= 67) return { label: 'Elevated', tone: 'critical', color: '#dc2626' }
    if (value >= 34) return { label: 'Moderate', tone: 'warning', color: '#ca8a04' }
    return { label: 'Good', tone: 'success', color: '#16a34a' }
}

// A 0-100 score as a partial ring, colored by riskBand — reuses DonutChart (already used by
// DataLeavingCard) rather than a new charting primitive. showValue draws "68"/"of 100" centered
// in the ring (the flyout's larger ring); the compact KPI tile version omits it since the value
// is already printed next to the ring.
function RiskScoreRing({ value, size, showValue }) {
    const band = riskBand(value)
    const filled = value === null || value === undefined ? 0 : value
    const ringData = {
        Score: { text: filled, color: band ? band.color : '#9ca3af' },
        Remaining: { text: Math.max(0, 100 - filled), color: '#E4E5E7' },
    }
    return (
        <DonutChart
            data={ringData}
            size={size}
            pieInnerSize="75%"
            title={showValue ? String(filled) : undefined}
            subtitle={showValue ? 'of 100' : undefined}
        />
    )
}

function formatValue(kpi) {
    if (kpi.value === null || kpi.value === undefined) return '—'
    if (kpi.unit === 'percent') return `${kpi.value}%`
    return kpi.value.toLocaleString()
}

function formatDelta(kpi) {
    if (kpi.delta === null || kpi.delta === undefined) return null
    const sign = kpi.delta > 0 ? '+' : ''
    if (kpi.deltaKind === 'percent') return `${sign}${kpi.delta}%`
    return `${sign}${kpi.delta}`
}

// ── Blurred placeholder state ──────────────────────────────────────────────────────
//
// When a panel genuinely has no data (not "zero, confirmed" — no data at all), showing a bare
// "no data" box reads as broken. Instead: render the SAME chart component with static,
// illustrative numbers (never real account data — see securityPostureDummyData.js), blurred, and
// let a click open a Popover explaining why. This is a placeholder for copy the product side
// still owns — PANEL_EMPTY_STATE_COPY is a stub map, not final text.
function DummyDataOverlay({ panelId, children }) {
    const [active, setActive] = useState(false)
    return (
        <div style={{ position: 'relative' }}>
            <div style={{ filter: 'blur(6px)', pointerEvents: 'none', userSelect: 'none' }} aria-hidden="true">
                {children}
            </div>
            <Popover
                active={active}
                onClose={() => setActive(false)}
                activator={
                    <div
                        onClick={() => setActive(true)}
                        style={{ position: 'absolute', inset: 0, cursor: 'pointer' }}
                    />
                }
            >
                <Box padding="4" maxWidth="260px">
                    <Text variant="bodyMd">{PANEL_EMPTY_STATE_COPY[panelId] || 'Not available yet.'}</Text>
                </Box>
            </Popover>
        </div>
    )
}

// A data gap (dataGaps[0] on any panel/KPI the backend sends) — one shared renderer so a reader
// sees the same "why is this empty / approximate" affordance everywhere on the page rather than
// each panel inventing its own.
function GapHint({ gaps }) {
    if (!gaps || gaps.length === 0) return null
    return (
        <Tooltip content={gaps.map((g) => g.impact).join(' ')}>
            <Icon source={CircleInformationMajor} color="subdued" />
        </Tooltip>
    )
}

// One KPI tile. Every figure here can degrade to "no data yet" instead of a bare zero — the
// backend attaches a dataGaps[] entry whenever it does, and this is the one place that renders
// that gap so every card gets it identically rather than each card inventing its own empty state.
function KpiTile({ kpi, onOpen, forceClickable }) {
    const hasValue = kpi.value !== null && kpi.value !== undefined
    const deltaText = formatDelta(kpi)
    // Every other KPI opens by navigating to kpi.route; the risk score has no route (item 10 in
    // the build plan gave it a flyout instead of a page), so it needs to be clickable without one.
    const clickable = hasValue && (forceClickable || !!kpi.route)
    // Only the composite risk score is a 0-100 score with a band color — the other three KPIs
    // (counts/percentages) have no ring to show.
    const showRing = kpi.id === KPI_RISK_SCORE && hasValue

    const valueColumn = (
        <VerticalStack gap="1">
            {hasValue ? (
                <Text variant="heading2xl">{formatValue(kpi)}</Text>
            ) : (
                <Text variant="heading2xl" color="subdued">Not computed yet</Text>
            )}

            {deltaText && (
                <Text variant="bodySm" fontWeight="semibold" color={DELTA_TONE_TO_COLOR[kpi.deltaTone] || 'subdued'}>
                    {deltaText}
                </Text>
            )}
        </VerticalStack>
    )

    return (
        <Card>
            <Box
                padding="4"
                onClick={clickable ? () => onOpen(kpi) : undefined}
                style={clickable ? { cursor: 'pointer' } : undefined}
            >
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="bodySm" fontWeight="semibold" color="subdued">{kpi.label}</Text>
                        <GapHint gaps={kpi.dataGaps} />
                    </HorizontalStack>

                    {showRing ? (
                        <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                            <RiskScoreRing value={kpi.value} size={48} />
                            {valueColumn}
                        </HorizontalStack>
                    ) : valueColumn}

                    {kpi.footnote && (
                        <Text variant="bodySm" color="subdued">{kpi.footnote}</Text>
                    )}
                </VerticalStack>
            </Box>
        </Card>
    )
}

// A stub tile for a posture area not built yet — the design's own convention (every unmapped
// drilldown falls back to a "Not built yet" panel) applied to the summary row, so the page never
// silently omits a card the design expects to see.
function ComingSoonTile({ label }) {
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="bodySm" fontWeight="semibold" color="subdued">{label}</Text>
                        <Badge status="new">Coming soon</Badge>
                    </HorizontalStack>
                    <Text variant="heading2xl" color="subdued">—</Text>
                </VerticalStack>
            </Box>
        </Card>
    )
}

// "Framework readiness" — no backend for this yet at all, so it's always the blurred dummy
// content (see DUMMY_FRAMEWORK_READINESS). Each row's tick mark is this quarter's target,
// positioned absolutely over the same CustomProgressBar every other bar on this page uses.
function FrameworkReadinessCard() {
    const body = (
        <VerticalStack gap="3">
            {DUMMY_FRAMEWORK_READINESS.map((row) => (
                <VerticalStack key={row.id} gap="1">
                    <HorizontalStack align="space-between">
                        <Text variant="bodyMd">{row.label}</Text>
                        <Text variant="bodyMd" fontWeight="semibold">{row.value}%</Text>
                    </HorizontalStack>
                    <div style={{ position: 'relative' }}>
                        <CustomProgressBar progress={row.value} topColor={row.color} height={"10px"}/>
                        <div style={{
                            position: 'absolute', top: 0, bottom: 0, left: `${row.target}%`,
                            width: '2px', background: '#1f2937',
                        }} />
                    </div>
                </VerticalStack>
            ))}
            <Text variant="bodySm" color="subdued">Markers show the target for this quarter</Text>
        </VerticalStack>
    )
    return (
        <CardWithHeader title="Framework readiness" hasData={true} minHeight="220px">
            <DummyDataOverlay panelId="frameworkReadiness">{body}</DummyDataOverlay>
        </CardWithHeader>
    )
}

// "Adoption gap by department" — no backend yet, always blurred dummy content. Each row is a
// two-dot track (shadow-use position, approved-use position) with the shadow-share % at right.
function AdoptionGapCard() {
    const body = (
        <VerticalStack gap="4">
            {DUMMY_ADOPTION_GAP.map((row) => (
                <HorizontalStack key={row.department} align="space-between" blockAlign="center" gap="3">
                    <Box width="110px"><Text variant="bodyMd">{row.department}</Text></Box>
                    <Box style={{ flex: 1, position: 'relative', height: '16px' }}>
                        <div style={{ position: 'absolute', top: '50%', left: 0, right: 0, height: '2px', background: '#e5e7eb' }} />
                        <div style={{
                            position: 'absolute', top: '50%', left: `${row.shadowPct}%`,
                            transform: 'translate(-50%, -50%)', width: 10, height: 10,
                            borderRadius: '50%', background: '#dc2626',
                        }} />
                        <div style={{
                            position: 'absolute', top: '50%', left: `${row.approvedPct}%`,
                            transform: 'translate(-50%, -50%)', width: 10, height: 10,
                            borderRadius: '50%', background: '#16a34a',
                        }} />
                    </Box>
                    <Box width="46px"><Text color="critical" fontWeight="semibold">{row.shadowSharePct}%</Text></Box>
                </HorizontalStack>
            ))}
            <HorizontalStack gap="4" blockAlign="center">
                <HorizontalStack gap="1" blockAlign="center">
                    <div style={{ width: 10, height: 10, borderRadius: '50%', background: '#dc2626' }} />
                    <Text variant="bodySm" color="subdued">Shadow use</Text>
                </HorizontalStack>
                <HorizontalStack gap="1" blockAlign="center">
                    <div style={{ width: 10, height: 10, borderRadius: '50%', background: '#16a34a' }} />
                    <Text variant="bodySm" color="subdued">Approved use</Text>
                </HorizontalStack>
            </HorizontalStack>
        </VerticalStack>
    )
    return (
        <CardWithHeader title="Adoption gap by department" hasData={true} minHeight="220px">
            <DummyDataOverlay panelId="adoptionGap">{body}</DummyDataOverlay>
        </CardWithHeader>
    )
}

// "Vendor risk vs. exposure" — no backend yet, always blurred dummy content. A plain
// absolutely-positioned scatter (no chart library needed — it's static and always blurred),
// with the "act first" zone shaded top-left, matching the design's bubble-chart reference.
function VendorRiskBubbleCard() {
    const body = (
        <VerticalStack gap="2">
            <div style={{ position: 'relative', height: '180px', border: '1px solid #e5e7eb', borderRadius: '4px' }}>
                <div style={{ position: 'absolute', top: 0, left: 0, right: '55%', height: '45%', background: 'rgba(220,38,38,0.08)' }} />
                {DUMMY_VENDOR_RISK_BUBBLE.map((p) => (
                    <div key={p.id} style={{
                        position: 'absolute', left: `${p.x}%`, top: `${100 - p.y}%`,
                        transform: 'translate(-50%, -50%)', width: 14, height: 14, borderRadius: '50%',
                        background: p.actFirst ? '#dc2626' : (p.y > 50 ? '#16a34a' : '#ca8a04'),
                    }} />
                ))}
            </div>
            <HorizontalStack align="space-between">
                <Text variant="bodySm" color="subdued">Employees exposed, left to right</Text>
                <Text variant="bodySm" color="critical">Above the line: act first</Text>
            </HorizontalStack>
        </VerticalStack>
    )
    return (
        <CardWithHeader title="Vendor risk vs. exposure" hasData={true} minHeight="220px">
            <DummyDataOverlay panelId="vendorRiskExposure">{body}</DummyDataOverlay>
        </CardWithHeader>
    )
}

// "Shadow AI is outgrowing what you've approved" — 12-week sanctioned vs. unsanctioned trend.
function ShadowAiTrendCard({ panel, onOpen }) {
    if (!panel) return null
    const hasData = (panel.series || []).some((s) => s.data && s.data.length > 0)
    const effective = hasData ? panel : DUMMY_SHADOW_AI_TREND
    const data = (effective.series || []).map((s) => ({
        name: s.name,
        data: s.data,
        color: s.name === 'Sanctioned' ? '#16a34a' : '#dc2626',
    }))

    const body = (
        <VerticalStack gap="2">
            <HorizontalStack gap="4">
                <Text variant="bodyMd">
                    <Text as="span" fontWeight="semibold" color="critical">{effective.currentUnsanctioned}</Text> unsanctioned
                </Text>
                <Text variant="bodyMd">
                    <Text as="span" fontWeight="semibold" color="success">{effective.currentSanctioned}</Text> sanctioned
                </Text>
            </HorizontalStack>
            <div onClick={hasData ? () => onOpen(panel) : undefined} style={{ cursor: hasData && panel.route ? 'pointer' : 'default' }}>
                <StackedAreaChart
                    height={220}
                    backgroundColor="#ffffff"
                    data={data}
                    yAxisTitle="Share of tools"
                    showGridLines={false}
                    exportingDisabled={true}
                />
            </div>
        </VerticalStack>
    )

    return (
        <CardWithHeader
            title="Shadow AI is outgrowing what you've approved"
            tooltipContent={panel.dataGaps?.[0]?.impact}
            hasData={true}
            minHeight="220px"
        >
            {hasData ? body : <DummyDataOverlay panelId="shadowAiTrend">{body}</DummyDataOverlay>}
        </CardWithHeader>
    )
}

// "What data is leaving" — donut of PII-detecting-policy matches by policy/data-type name.
// Same donut+legend layout ViolationsPage.jsx's DonutCard uses — a side-by-side chart and count
// list, rather than CustomPieChart's stacked-below-the-donut labels.
function ChartLegend({ items }) {
    return (
        <VerticalStack gap="2">
            {items.map(({ label, color, count }) => (
                <HorizontalStack key={label} gap="2" blockAlign="center">
                    <Box style={{ width: 10, height: 10, borderRadius: '50%', background: color, flexShrink: 0 }} />
                    <Text variant="bodyMd" color="subdued">{label}</Text>
                    <Text variant="bodyMd" fontWeight="semibold">{count.toLocaleString()}</Text>
                </HorizontalStack>
            ))}
        </VerticalStack>
    )
}

function DataLeavingCard({ panel, onOpen }) {
    if (!panel) return null
    const hasData = panel.total > 0
    const effective = hasData ? panel : DUMMY_DATA_LEAVING
    const graphData = {}
    ;(effective.segments || []).forEach((seg, i) => {
        graphData[seg.label] = {
            text: seg.count,
            color: SEGMENT_COLORS[i % SEGMENT_COLORS.length],
            filterValue: seg.filterId || seg.label,
        }
    })
    const legendItems = Object.entries(graphData).map(([label, { text, color }]) => ({ label, color, count: text }))

    const body = (
        <HorizontalStack gap="4" blockAlign="center" wrap={false}>
            <DonutChart
                data={graphData}
                title=""
                size={150}
                pieInnerSize="55%"
                onSegmentClick={hasData ? () => onOpen(panel) : undefined}
            />
            <ChartLegend items={legendItems} />
        </HorizontalStack>
    )

    return (
        <CardWithHeader
            title="What data is leaving"
            tooltipContent={panel.dataGaps?.[0]?.impact}
            hasData={true}
            minHeight="220px"
        >
            {hasData ? body : <DummyDataOverlay panelId="dataLeaving">{body}</DummyDataOverlay>}
        </CardWithHeader>
    )
}

const FUNNEL_STAGE_COLORS = {
    hardBlocked: '#8771F6',
    warnedOnly: '#B6B0FE',
    warningOverridden: '#F24122',
}

// Enforcement funnel — matched → hard-blocked → warned-only → warning-overridden. "Matched" is
// against the inspected-actions total (all gateway-inspected traffic); the other three are
// against "matched" itself, since they're policy-behaviour splits of that same population.
// The real "inspected actions" denominator (isAtlasTraffic search total) isn't reliable yet —
// a separate, known, in-progress fix on the ES/SearchClient side. Until then, fall back to this
// fixed placeholder for DISPLAY ONLY when the backend's own figure is missing/zero, so the
// "matched" bar's percent still reads as plausible rather than a misleading 100%/0%. The real
// matched/hard-blocked/warned/overridden counts are untouched — only this one denominator is
// substituted.
const FALLBACK_INSPECTED_ACTIONS = 100000

function EnforcementFunnelCard({ panel, onOpen }) {
    if (!panel) return null
    const stages = panel.stages || []
    const matched = stages.find((s) => s.id === 'matched')
    const hasData = matched && matched.count > 0
    const effective = hasData ? panel : DUMMY_ENFORCEMENT_FUNNEL
    const effectiveStages = effective.stages || []
    const displayInspectedActions = hasData
        ? (effective.inspectedActions || FALLBACK_INSPECTED_ACTIONS)
        : effective.inspectedActions
    const matchedDisplayPercent = hasData && displayInspectedActions > 0
        ? Math.round((matched.count / displayInspectedActions) * 1000) / 10
        : null

    const body = (
        <VerticalStack gap="3">
            {effectiveStages.map((stage) => (
                <Box key={stage.id} onClick={hasData ? () => onOpen(panel) : undefined} style={{ cursor: hasData && panel.route ? 'pointer' : 'default' }}>
                    <VerticalStack gap="1">
                        <HorizontalStack align="space-between">
                            <Text variant="bodyMd">{stage.label}</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{(stage.count ?? 0).toLocaleString()}</Text>
                        </HorizontalStack>
                        <CustomProgressBar
                            height={"12px"}
                            progress={(stage.id === 'matched' && matchedDisplayPercent != null) ? matchedDisplayPercent : (stage.percentOfMatched ?? 0)}
                            topColor={FUNNEL_STAGE_COLORS[stage.id] || '#6D3BEF'}
                        />
                        {stage.id === 'matched' && displayInspectedActions != null && (
                            <Text variant="bodySm" color="subdued">
                                {matchedDisplayPercent != null ? matchedDisplayPercent : stage.percentOfMatched}% of {displayInspectedActions.toLocaleString()} inspected actions
                            </Text>
                        )}
                    </VerticalStack>
                </Box>
            ))}
            {hasData && panel.dataGaps && panel.dataGaps.length > 0 && (
                <Text variant="bodySm" color="subdued">
                    {panel.dataGaps[0].impact}
                </Text>
            )}
        </VerticalStack>
    )

    return (
        <CardWithHeader
            title="Enforcement funnel"
            tooltipContent={panel.dataGaps?.map((g) => g.impact).join(' ')}
            hasData={true}
            minHeight="220px"
        >
            {hasData ? body : <DummyDataOverlay panelId="enforcementFunnel">{body}</DummyDataOverlay>}
        </CardWithHeader>
    )
}

// "Attack attempts" — weekly malicious-event counts, Blocked vs. Got through. "Got through" is
// always 0 today (see PostureService.attackAttemptsTrend's own gap note) — rendered anyway so
// the legend/series shape is already correct for when that data exists.
function AttackAttemptsCard({ panel, onOpen }) {
    if (!panel) return null
    const hasData = (panel.currentTotal ?? 0) > 0
    const effective = hasData ? panel : DUMMY_ATTACK_ATTEMPTS
    const data = (effective.series || []).map((s) => ({
        name: s.name,
        data: s.data,
        color: s.name === 'Got through' ? '#dc2626' : '#7dd3ea',
    }))

    const body = (
        <VerticalStack gap="2">
            <HorizontalStack gap="1" blockAlign="baseline">
                <Text variant="heading2xl">{(effective.currentTotal ?? 0).toLocaleString()}</Text>
                <Text variant="bodyMd" color="subdued">
                    this week · {(effective.currentBlocked ?? 0).toLocaleString()} blocked
                </Text>
            </HorizontalStack>
            <div onClick={hasData ? () => onOpen(panel) : undefined} style={{ cursor: hasData && panel.route ? 'pointer' : 'default' }}>
                <StackedChart
                    type="column"
                    height={180}
                    backgroundColor="#ffffff"
                    data={data}
                    yAxisTitle="Events"
                    showGridLines={false}
                    noGap={false}
                    width={20}
                    exportingDisabled={true}
                />
            </div>
        </VerticalStack>
    )

    return (
        <CardWithHeader
            title="Attack attempts"
            tooltipContent={panel.dataGaps?.map((g) => g.impact).join(' ')}
            hasData={true}
            minHeight="220px"
        >
            {hasData ? body : <DummyDataOverlay panelId="attackAttempts">{body}</DummyDataOverlay>}
        </CardWithHeader>
    )
}

// "Act now" — top 3 discovery + top 3 guardrail insights, merged into one worst-first list.
// Reuses the Insights feature's own data and severity styling wholesale rather than a parallel
// summarization; the source label is what tells the two groups apart once they're merged.
function ActNowRow({ insight, onOpen }) {
    return (
        <Box borderBlockEndWidth="1" borderColor="border">
            <Box
                onClick={() => onOpen(insight)}
                style={{ cursor: 'pointer', borderRadius: '4px', padding: '8px' }}
            >
                <VerticalStack gap="1">
                    <HorizontalStack gap={"2"}>
                        {insight.severity && <SeverityBadge severity={insight.severity} useDot={true}/>}
                        <Text variant="bodyMd" fontWeight="semibold">{insight.title}</Text>
                    </HorizontalStack>
                    <Text variant="bodySm" color="subdued">{insight.headline}</Text>
                </VerticalStack>
            </Box>
        </Box>
    )
}

function ActNowCard({ actNow, onOpenInsight }) {
    if (!actNow) return null
    const discovery = actNow.discovery || []
    const guardrail = actNow.guardrail || []

    const merged = [
        ...discovery.map((i) => ({ ...i, group: INSIGHT_GROUP.ATLAS_DISCOVERY })),
        ...guardrail.map((i) => ({ ...i, group: INSIGHT_GROUP.GUARDRAIL_VIOLATIONS })),
    ].sort((a, b) => severityRank(a.severity) - severityRank(b.severity))

    if (merged.length === 0) {
        return (
            <CardWithHeader title="Act now" hasData={false} emptyMessage="Nothing needs attention right now." minHeight="160px" />
        )
    }

    return (
        <CardWithHeader title="Act now" hasData={true} minHeight="160px">
            <VerticalStack gap="2">
                {merged.map((i) => (
                    <ActNowRow key={i.insightId} insight={i} onOpen={() => onOpenInsight(i.insightId, i.group)} />
                ))}
            </VerticalStack>
        </CardWithHeader>
    )
}

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

// One row of the risk score drilldown — a sub-score's weight, its bar, and its value or (when
// null) the same "why is this missing" hint every other gap on this page uses.
function RiskScoreSubScoreRow({ subScore, kpi }) {
    const hasValue = subScore.value !== null && subScore.value !== undefined
    const band = riskBand(subScore.value)
    const detail = subScoreDetailLines(subScore, kpi)
    const detailLines = Array.isArray(detail) ? detail : (detail ? [detail] : [])
    return (
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
    )
}

// Composite trend + "what moved the score" — both need posture_score_history, which doesn't
// exist yet (see PostureService.GAP_POSTURE_HISTORY / the build plan's item 10), so both are
// illustrative-only and blurred, same convention as every other backend-less panel on this page.
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
    return (
        <Box key="trend" padding="4">
            <DummyDataOverlay panelId="riskScoreTrend">{body}</DummyDataOverlay>
        </Box>
    )
}

// One row from any of the 5 breakdowns into a common {category, item, detail, impactPoints}
// shape — each breakdown has a different subject (a device for threat activity/DLP, a service for
// shadow AI, a vendor for vendor risk, a policy for compliance gaps), so there's no single field
// name to read the "item"/"detail" off; this is the one place that knows all 5 shapes.
function formatMovedRow(row) {
    if (row.deviceId !== undefined) {
        return { ...row, item: row.username || row.deviceId, detail: `${row.prior} → ${row.current}` }
    }
    if (row.service !== undefined) {
        return { ...row, item: row.service, detail: `${row.status.toLowerCase()} · ${row.deviceCount} device${row.deviceCount === 1 ? '' : 's'}` }
    }
    if (row.vendor !== undefined) {
        return { ...row, item: row.vendor, detail: `${row.deviceCount} device${row.deviceCount === 1 ? '' : 's'}` }
    }
    return { ...row, item: row.policy, detail: `${row.count} event${row.count === 1 ? '' : 's'}` }
}

// Real, not illustrative — top 2 from each of the 5 sub-score breakdowns (RiskScoreCalculator's
// threatActivityDeviceMovements/dlpDeviceMovements/shadowAiTopUnapprovedServices/
// vendorRiskTopUnapprovedDevices/complianceGapsByPolicy), combined into one table. impactPoints is
// each row's share of its own category's total (movement for threat activity/DLP, snapshot count
// for the other three), scaled by that sub-score's actual weight (30/25/20/15/10) — bounded by
// that weight, so it reads against the composite's 0-100 scale instead of as a raw, unbounded count.
function RiskScoreAnnotationsSection({ kpi, loading }) {
    const rows = [
        ...(kpi.shadowAiTopServices || []),
        ...(kpi.dlpDeviceMovements || []),
        ...(kpi.vendorRiskTopUnapproved || []),
        ...(kpi.complianceGapsByPolicy || []),
        ...(kpi.threatActivityMovements || []),
    ]
        .map(formatMovedRow)
        .sort((a, b) => Math.abs(b.impactPoints) - Math.abs(a.impactPoints))

    const tableRows = rows.map((row) => [
        row.category,
        row.item,
        row.detail,
        <Text variant="bodyMd" fontWeight="semibold" color={row.impactPoints > 0 ? 'critical' : 'success'}>
            {row.impactPoints > 0 ? `+${row.impactPoints}` : row.impactPoints} pts
        </Text>,
    ])

    return (
        <Box key="annotations" padding="4">
            <VerticalStack gap="3">
                <VerticalStack gap="1">
                    <Text variant="headingSm">What moved the score</Text>
                    <Text variant="bodySm" color="subdued">
                        Top items by weighted impact this period, across every sub-score
                    </Text>
                </VerticalStack>
                {loading ? (
                    <SpinnerCentered />
                ) : rows.length === 0 ? (
                    <Text variant="bodySm" color="subdued">
                        No prior-window comparison available, or nothing changed this period.
                    </Text>
                ) : (
                    <Box maxWidth="620px">
                        <Card padding="0">
                            <DataTable
                                columnContentTypes={['text', 'text', 'text', 'numeric']}
                                headings={['Category', 'Item', 'Detail', 'Impact']}
                                rows={tableRows}
                                hideScrollIndicator
                                increasedTableDensity
                            />
                        </Card>
                    </Box>
                )}
            </VerticalStack>
        </Box>
    )
}

// "Risk score breakdown" flyout body — the drilldown for the composite KPI tile. Shows the real
// composite (with its now-real week-over-week delta) and the five weighted sub-scores, plus the
// two always-blurred illustrative sections above.
function RiskScoreFlyoutBody({ kpi, breakdownLoading }) {
    if (!kpi) return null
    const band = riskBand(kpi.value)
    const historyGap = (kpi.dataGaps || []).find((g) => g.source === 'POSTURE_HISTORY')
    const deltaText = formatDelta(kpi)

    return [
        <Box key="summary" padding="4">
            <VerticalStack gap="2">
                <HorizontalStack gap="3" blockAlign="center">
                    <RiskScoreRing value={kpi.value} size={56} />
                    <VerticalStack gap="1">
                        <HorizontalStack gap="3" blockAlign="center">
                            <Text variant="heading2xl">{kpi.value !== null && kpi.value !== undefined ? `${kpi.value} / 100` : 'Not computed yet'}</Text>
                            {band && <Badge status={band.tone === 'critical' ? 'critical' : band.tone === 'warning' ? 'warning' : 'success'}>{band.label}</Badge>}
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
        </Box>,
        RiskScoreTrendSection(),
        <Box key="subScores" padding="4">
            {breakdownLoading ? (
                <SpinnerCentered />
            ) : (
                <VerticalStack gap="4">
                    <VerticalStack gap={"3"}>
                        {(kpi.subScores || []).map((s) => (
                            <RiskScoreSubScoreRow key={s.id} subScore={s} kpi={kpi} />
                        ))}
                    </VerticalStack>
                    {historyGap && (
                        <Text variant="bodySm" color="subdued">{historyGap.impact}</Text>
                    )}
                </VerticalStack>
            )}
        </Box>,
        RiskScoreAnnotationsSection({ kpi, loading: breakdownLoading }),
    ]
}

function SecurityPosture() {
    const navigate = useNavigate()
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[3] // "Last 30 days" — same default EndpointPosture uses
    )
    const [pageData, setPageData] = useState({})
    const [loading, setLoading] = useState(true)
    const [flyout, setFlyout] = useState(null) // { insightId, group } | null
    const [riskScoreFlyoutOpen, setRiskScoreFlyoutOpen] = useState(false)
    const [riskScoreBreakdown, setRiskScoreBreakdown] = useState(null)
    const [riskScoreBreakdownLoading, setRiskScoreBreakdownLoading] = useState(false)

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000)

    useEffect(() => {
        let cancelled = false

        async function load() {
            setLoading(true)
            try {
                const startTimestamp = getTimeEpoch('since')
                const endTimestamp = getTimeEpoch('until')
                const resp = await dashboardApi.fetchPostureSummary(startTimestamp, endTimestamp)
                if (!cancelled) setPageData(resp || {})
            } catch (error) {
                console.error('Error fetching posture summary:', error)
                if (!cancelled) {
                    setPageData({})
                    setLoading(false)
                }
            } finally {
                if (!cancelled) setLoading(false)
            }
        }

        load()
        return () => { cancelled = true }
    }, [currDateRange])

    // The flyout's own detail (sub-scores + vendor table) — fetched only once the flyout is
    // actually opened, not as part of the page's main load above. Re-fetches if the date range
    // changes while it's open, same as every other panel on this page.
    useEffect(() => {
        if (!riskScoreFlyoutOpen) return
        let cancelled = false

        async function loadBreakdown() {
            setRiskScoreBreakdownLoading(true)
            try {
                const startTimestamp = getTimeEpoch('since')
                const endTimestamp = getTimeEpoch('until')
                const resp = await dashboardApi.fetchRiskScoreBreakdown(startTimestamp, endTimestamp)
                if (!cancelled) setRiskScoreBreakdown(resp || {})
            } catch (error) {
                console.error('Error fetching risk score breakdown:', error)
                if (!cancelled) setRiskScoreBreakdown({})
            } finally {
                if (!cancelled) setRiskScoreBreakdownLoading(false)
            }
        }

        loadBreakdown()
        return () => { cancelled = true }
    }, [riskScoreFlyoutOpen, currDateRange])

    const kpis = pageData.kpis || []
    const kpiById = (id) => kpis.find((k) => k.id === id)

    const openKpi = (kpi) => {
        if (!kpi.route) return
        navigate(kpi.route, { state: kpi.linkParams })
    }

    const openPanel = (panel) => {
        if (!panel || !panel.route) return
        navigate(panel.route)
    }

    const openInsight = (insightId, group) => setFlyout({ insightId, group })

    const kpiRow = (
        <HorizontalGrid columns={4} gap="2">
            {[KPI_RISK_SCORE, KPI_CRITICAL_ALERTS, KPI_MONITORING_COVERAGE, KPI_SENSITIVE_INCIDENTS].map((id) => {
                const kpi = kpiById(id)
                if (!kpi) return <ComingSoonTile key={id} label={id} />
                // The risk score has no route — it opens its own breakdown flyout instead of
                // navigating away, so it needs a different onOpen/clickability than the rest.
                if (id === KPI_RISK_SCORE) {
                    return <KpiTile key={id} kpi={kpi} onOpen={() => setRiskScoreFlyoutOpen(true)} forceClickable />
                }
                return <KpiTile key={id} kpi={kpi} onOpen={openKpi} />
            })}
        </HorizontalGrid>
    )

    // Shadow AI trend gets more width than the data-leaving donut (3:2), not an even split — a
    // ratio, so plain flex rather than Polaris's equal-width HorizontalGrid.
    const shadowAndDataLeavingRow = (
        <div style={{ display: 'flex', gap: '16px', alignItems: 'stretch' }}>
            <div style={{ flex: 3, minWidth: 0 }}><ShadowAiTrendCard panel={pageData.shadowAiTrend} onOpen={openPanel} /></div>
            <div style={{ flex: 2, minWidth: 0 }}><DataLeavingCard panel={pageData.dataLeaving} onOpen={openPanel} /></div>
        </div>
    )

    const funnelAttackVendorRow = (
        <HorizontalGrid columns={3} gap="3">
            <EnforcementFunnelCard panel={pageData.enforcementFunnel} onOpen={openPanel} />
            <AttackAttemptsCard panel={pageData.attackAttempts} onOpen={openPanel} />
            <VendorRiskBubbleCard />
        </HorizontalGrid>
    )

    const frameworkAndAdoptionRow = (
        <HorizontalGrid columns={2} gap="4">
            <FrameworkReadinessCard />
            <AdoptionGapCard />
        </HorizontalGrid>
    )

    const mainColumn = (
        <VerticalStack gap="4">
            {kpiRow}
            {shadowAndDataLeavingRow}
            {funnelAttackVendorRow}
            {frameworkAndAdoptionRow}
        </VerticalStack>
    )

    // "Biggest movers" has no backend/component yet (unlike everything else on this page, which
    // is at minimum wired to a real or dummy panel) — a plain ComingSoonTile stub here rather
    // than fabricating numbers, since this pass is a layout rearrangement, not a new feature.
    const rightRail = (
        <VerticalStack gap="3">
            <ActNowCard actNow={pageData.actNow} onOpenInsight={openInsight} />
            <ComingSoonTile label="Biggest movers" />
        </VerticalStack>
    )

    const pageBody = (
        <div style={{ display: 'flex', gap: '16px'}}>
            <div style={{ flex: 5, minWidth: 0 }}>{mainColumn}</div>
            <div style={{ flex: 2, minWidth: 0 }}>{rightRail}</div>
        </div>
    )

    const pageComponents = [
        <Box key="body">{pageBody}</Box>,
    ]

    return (
        <Box>
            {loading ? (
                <Box padding="8">
                    <SpinnerCentered />
                </Box>
            ) : (
                <>
                    <PageWithMultipleCards
                        title={<Text variant="headingLg">AI security posture</Text>}
                        isFirstPage={true}
                        components={pageComponents}
                        primaryAction={
                            <DateRangeFilter
                                initialDispatch={currDateRange}
                                dispatch={(dateObj) => dispatchCurrDateRange({
                                    type: 'update', period: dateObj.period, title: dateObj.title, alias: dateObj.alias,
                                })}
                            />
                        }
                    />
                    {flyout && (
                        <InsightsFlyout
                            show={!!flyout}
                            onClose={() => setFlyout(null)}
                            startTimestamp={getTimeEpoch('since')}
                            endTimestamp={getTimeEpoch('until')}
                            initialInsightId={flyout.insightId}
                            group={flyout.group}
                        />
                    )}
                    <FlyLayout
                        title="Risk score breakdown"
                        show={riskScoreFlyoutOpen}
                        setShow={setRiskScoreFlyoutOpen}
                        components={RiskScoreFlyoutBody({
                            kpi: kpiById(KPI_RISK_SCORE) ? { ...kpiById(KPI_RISK_SCORE), ...riskScoreBreakdown } : null,
                            breakdownLoading: riskScoreBreakdownLoading,
                        }) || []}
                        showDivider
                    />
                </>
            )}
        </Box>
    )
}

export default SecurityPosture

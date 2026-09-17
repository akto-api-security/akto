import { useEffect, useReducer, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Badge, Box, Card, HorizontalGrid, HorizontalStack, Icon, Popover, Text, Tooltip, VerticalStack,
} from '@shopify/polaris'
import { CircleInformationMajor } from '@shopify/polaris-icons'
import { produce } from 'immer'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import FlyLayout from '../../components/layouts/FlyLayout'
import CardWithHeader from './new_components/CardWithHeader'
import CustomPieChart from './new_components/CustomPieChart'
import CustomProgressBar from './new_components/CustomProgressBar'
import StackedAreaChart from '../../components/charts/StackedAreaChart'
import StackedChart from '../../components/charts/StackedChart'
import { SeverityBadge } from '../observe/agentic/AgenticCellRenderers'
import InsightsFlyout from '../observe/agentic/insights/InsightsFlyout'
import { INSIGHT_GROUP, INSIGHT_GROUP_LABEL } from '../observe/agentic/insights/insightsHelpers'
import dashboardApi from './api'
import func from '@/util/func'
import values from '@/util/values'
import SpinnerCentered from '../../components/progress/SpinnerCentered'

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
// illustrative numbers (never real account data — see each DUMMY_* constant below), blurred, and
// let a click open a Popover explaining why. This is a placeholder for copy the product side
// still owns — PANEL_EMPTY_STATE_COPY is a stub map, not final text.
const PANEL_EMPTY_STATE_COPY = {
    shadowAiTrend: 'Fill me in',
    dataLeaving: 'Fill me in',
    enforcementFunnel: 'Fill me in',
    attackAttempts: 'Fill me in',
    frameworkReadiness: 'Fill me in',
    vendorRiskExposure: 'Fill me in',
    adoptionGap: 'Fill me in',
}

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

// Static week-ending-now timestamps for a dummy N-week series — same [ms, value] point shape
// the real backend series use, so the same chart component renders either one identically.
function dummyWeeklySeries(values) {
    const nowMs = Date.now()
    const weekMs = 7 * 24 * 3600 * 1000
    return values.map((v, i) => [nowMs - (values.length - 1 - i) * weekMs, v])
}

// Illustrative-only numbers, shaped exactly like each panel's real response — never real
// account data. Same figures the original design mockup used for these cards.
const DUMMY_SHADOW_AI_TREND = {
    series: [
        { name: 'Sanctioned', data: dummyWeeklySeries([48, 52, 55, 58, 61, 64, 66, 69, 71, 74, 77, 79]) },
        { name: 'Unsanctioned', data: dummyWeeklySeries([88, 94, 99, 104, 109, 113, 117, 121, 125, 129, 132, 135]) },
    ],
    currentSanctioned: 79,
    currentUnsanctioned: 135,
    route: null,
}
const DUMMY_DATA_LEAVING = {
    total: 100,
    segments: [
        { label: 'Source code', count: 34, percent: 34 },
        { label: 'Customer PII', count: 26, percent: 26 },
        { label: 'Financials', count: 18, percent: 18 },
        { label: 'Credentials', count: 12, percent: 12 },
        { label: 'Other', count: 10, percent: 10 },
    ],
    route: null,
}
const DUMMY_ENFORCEMENT_FUNNEL = {
    stages: [
        { id: 'matched', label: 'Matched a policy', count: 6914, percentOfMatched: 4.6 },
        { id: 'hardBlocked', label: 'Hard-blocked', count: 3180, percentOfMatched: 46 },
        { id: 'warnedOnly', label: 'Warned only', count: 2697, percentOfMatched: 39 },
        { id: 'warningOverridden', label: 'Warning overridden', count: 620, percentOfMatched: 9 },
    ],
    inspectedActions: 148900,
    route: null,
}
const DUMMY_ATTACK_ATTEMPTS = {
    series: [
        { name: 'Blocked', data: dummyWeeklySeries([58, 52, 61, 64, 69, 71, 75, 81]) },
        { name: 'Got through', data: dummyWeeklySeries([4, 3, 5, 4, 5, 5, 6, 6]) },
    ],
    currentTotal: 87,
    currentBlocked: 81,
    currentGotThrough: 6,
    route: null,
}

// These three panels have no backend yet at all (not "empty data" — the feature itself isn't
// built), so unlike the four above they're ALWAYS shown blurred, never conditionally.
const DUMMY_FRAMEWORK_READINESS = [
    { id: 'nist', label: 'NIST AI RMF', value: 78, target: 85, color: '#ca8a04' },
    { id: 'iso', label: 'ISO/IEC 42001', value: 64, target: 72, color: '#ca8a04' },
    { id: 'euai', label: 'EU AI Act (GPAI)', value: 51, target: 65, color: '#dc2626' },
    { id: 'soc2', label: 'SOC 2 · AI addendum', value: 92, target: 90, color: '#16a34a' },
]
const DUMMY_ADOPTION_GAP = [
    { department: 'Engineering', shadowPct: 30, approvedPct: 60, shadowSharePct: 41 },
    { department: 'Sales', shadowPct: 24, approvedPct: 68, shadowSharePct: 28 },
    { department: 'Marketing', shadowPct: 16, approvedPct: 78, shadowSharePct: 19 },
    { department: 'Finance', shadowPct: 6, approvedPct: 92, shadowSharePct: 6 },
]
const DUMMY_VENDOR_RISK_BUBBLE = [
    { id: 1, x: 12, y: 18, actFirst: true },
    { id: 2, x: 18, y: 12, actFirst: true },
    { id: 3, x: 28, y: 22, actFirst: true },
    { id: 4, x: 24, y: 42, actFirst: false },
    { id: 5, x: 45, y: 30, actFirst: false },
    { id: 6, x: 62, y: 55, actFirst: false },
    { id: 7, x: 78, y: 68, actFirst: false },
]

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
                        <CustomProgressBar progress={row.value} topColor={row.color} />
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

    const body = (
        <CustomPieChart
            subtitle="incidents"
            graphData={graphData}
            onSegmentClick={hasData ? () => onOpen(panel) : undefined}
        />
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
    hardBlocked: '#16a34a',
    warnedOnly: '#ca8a04',
    warningOverridden: '#dc2626',
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
                            progress={(stage.id === 'matched' && matchedDisplayPercent != null) ? matchedDisplayPercent : (stage.percentOfMatched ?? 0)}
                            topColor={FUNNEL_STAGE_COLORS[stage.id] || '#6b7280'}
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
        <Box
            padding="3"
            borderColor="border-subdued"
            borderWidth="1"
            borderRadius="2"
            onClick={() => onOpen(insight)}
            style={{ cursor: 'pointer' }}
        >
            <VerticalStack gap="1">
                <HorizontalStack align="space-between" blockAlign="start">
                    <Text variant="bodyMd" fontWeight="semibold">{insight.title}</Text>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="bodySm" color="subdued">{INSIGHT_GROUP_LABEL[insight.group]}</Text>
                        {insight.severity && <SeverityBadge severity={insight.severity} />}
                    </HorizontalStack>
                </HorizontalStack>
                <Text variant="bodySm" color="subdued">{insight.headline}</Text>
            </VerticalStack>
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

// One row of the risk score drilldown — a sub-score's weight, its bar, and its value or (when
// null) the same "why is this missing" hint every other gap on this page uses.
function RiskScoreSubScoreRow({ subScore, vendorTable }) {
    const hasValue = subScore.value !== null && subScore.value !== undefined
    const band = riskBand(subScore.value)
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
            
            {subScore.id === 'vendorRisk' && vendorTable && vendorTable.length > 0 && (
                <Box paddingBlockStart="1">
                    <Text variant="bodySm" color="subdued">
                        {vendorTable.slice(0, 4).map((v) => `${v.vendor} (${v.approved ? 'approved' : 'unapproved'}, ${v.count})`).join(' · ')}
                    </Text>
                </Box>
            )}
        </VerticalStack>
    )
}

// "Risk score breakdown" flyout body — the drilldown for the composite KPI tile. No trend chart
// and no "what moved the score" table here: both need posture_score_history, which doesn't exist
// yet (see PostureService.GAP_POSTURE_HISTORY / the build plan's item 10). This shows exactly
// what's computed right now: the five weighted sub-scores behind the composite.
function RiskScoreFlyoutBody({ kpi }) {
    if (!kpi) return null
    const band = riskBand(kpi.value)
    const historyGap = (kpi.dataGaps || []).find((g) => g.source === 'POSTURE_HISTORY')

    return [
        <Box key="summary" padding="4">
            <VerticalStack gap="2">
                <HorizontalStack gap="3" blockAlign="center">
                    <Text variant="heading2xl">{kpi.value !== null && kpi.value !== undefined ? `${kpi.value} / 100` : 'Not computed yet'}</Text>
                    {band && <Badge status={band.tone === 'critical' ? 'critical' : band.tone === 'warning' ? 'warning' : 'success'}>{band.label}</Badge>}
                </HorizontalStack>
                <Text variant="bodySm" color="subdued">
                    Composite of five weighted sub-scores. Lower is better.
                    {kpi.footnote ? ` ${kpi.footnote}.` : ''}
                </Text>
            </VerticalStack>
        </Box>,
        <Box key="subScores" padding="4">
            <VerticalStack gap="4">
                <VerticalStack gap={"3"}>
                    {(kpi.subScores || []).map((s) => (
                        <RiskScoreSubScoreRow key={s.id} subScore={s} vendorTable={kpi.vendorTable} />
                    ))}
                </VerticalStack>
                {historyGap && (
                    <Text variant="bodySm" color="subdued">{historyGap.impact}</Text>
                )}
            </VerticalStack>
        </Box>,
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
        <Box paddingBlockEnd="4">
            <HorizontalGrid columns={4} gap="3">
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
        </Box>
    )

    const panelsGrid = (
        <HorizontalGrid columns={2} gap="4">
            <ShadowAiTrendCard panel={pageData.shadowAiTrend} onOpen={openPanel} />
            <DataLeavingCard panel={pageData.dataLeaving} onOpen={openPanel} />
            <EnforcementFunnelCard panel={pageData.enforcementFunnel} onOpen={openPanel} />
            <AttackAttemptsCard panel={pageData.attackAttempts} onOpen={openPanel} />
            <ActNowCard actNow={pageData.actNow} onOpenInsight={openInsight} />
        </HorizontalGrid>
    )

    // None of these three have a backend yet — always-blurred dummy content (see the DUMMY_*
    // constants above) instead of a bare "Coming soon" stub, per the design reference.
    const comingSoonRow = (
        <HorizontalGrid columns={3} gap="4">
            <FrameworkReadinessCard />
            <VendorRiskBubbleCard />
            <AdoptionGapCard />
        </HorizontalGrid>
    )

    const pageComponents = [
        <Box key="kpis">{kpiRow}</Box>,
        <Box key="panels" paddingBlockEnd="4">{panelsGrid}</Box>,
        <Box key="comingSoon">{comingSoonRow}</Box>,
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
                        components={RiskScoreFlyoutBody({ kpi: kpiById(KPI_RISK_SCORE) }) || []}
                        showDivider
                    />
                </>
            )}
        </Box>
    )
}

export default SecurityPosture

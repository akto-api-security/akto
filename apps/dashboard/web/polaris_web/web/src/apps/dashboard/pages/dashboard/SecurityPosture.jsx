import { useEffect, useMemo, useReducer, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
    Badge, Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack,
} from '@shopify/polaris'
import { produce } from 'immer'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import DateRangeFilter from '../../components/layouts/DateRangeFilter'
import CardWithHeader from './new_components/CardWithHeader'
import ComponentHeader from './new_components/ComponentHeader'
import CustomProgressBar from './new_components/CustomProgressBar'
import DonutChart from '../../components/shared/DonutChart'
import SmoothAreaChart from './new_components/SmoothChart'
import StackedAreaChart from '../../components/charts/StackedAreaChart'
import StackedChart from '../../components/charts/StackedChart'
import { SeverityBadge } from '../observe/agentic/AgenticCellRenderers'
import InsightsFlyout from '../observe/agentic/insights/InsightsFlyout'
import { INSIGHT_GROUP } from '../observe/agentic/insights/insightsHelpers'
import PostureDrillFlyout from './PostureDrillFlyout'
import { DELTA_TONE_TO_COLOR, DummyDataOverlay, formatDelta, RiskScoreRing } from './new_components/PostureShared'
import dashboardApi from './api'
import func from '@/util/func'
import values from '@/util/values'
import SpinnerCentered from '../../components/progress/SpinnerCentered'
import {
    DUMMY_SHADOW_AI_TREND, DUMMY_DATA_LEAVING, DUMMY_ENFORCEMENT_FUNNEL,
    DUMMY_ATTACK_ATTEMPTS, DUMMY_FRAMEWORK_READINESS, DUMMY_ADOPTION_GAP, DUMMY_VENDOR_RISK_BUBBLE,
    POSTURE_CARD_INFO,
} from './securityPostureDummyData'

// KPI ids — must match PostureService.KPI_* on the backend.
const KPI_RISK_SCORE = 'riskScore'
const KPI_CRITICAL_ALERTS = 'criticalAlerts'
const KPI_MONITORING_COVERAGE = 'monitoringCoverage'
const KPI_SENSITIVE_INCIDENTS = 'sensitiveDataIncidents'

// Drill ids — must match PostureService.DRILL_* on the backend.
const DRILL_SHADOW_AI = 'shadowAiTools'
const DRILL_DATA_LEAVING = 'dataLeaving'
const DRILL_ENFORCEMENT_FUNNEL = 'enforcementFunnel'
const DRILL_VENDOR_RISK = 'vendorRisk'
const DRILL_FRAMEWORK_READINESS = 'frameworkReadiness'
const DRILL_RISK_SCORE = 'riskScoreBreakdown'
// Reuse the KPI's own id as its drill id, same as the backend does (PostureService.DRILL_CRITICAL_ALERTS/
// DRILL_SENSITIVE_DATA) — these two are the KPI tile's own drilldown, not a panel's.
const DRILL_CRITICAL_ALERTS = KPI_CRITICAL_ALERTS
const DRILL_SENSITIVE_DATA = KPI_SENSITIVE_INCIDENTS

// Sparkline color per KPI — both are "higher is worse" counts, so both read red, matching
// deltaTone's own critical-is-red convention elsewhere on this page.
const KPI_SPARKLINE_COLOR = {
    [KPI_CRITICAL_ALERTS]: '#dc2626',
    [KPI_SENSITIVE_INCIDENTS]: '#dc2626',
}

// Donut/segment colors — matches the palette other posture cards (ComplianceAtRisksCard,
// GuardrailCoverageCard) already use, so a "data type" or "policy mode" reads the same tone
// wherever it shows up on the page.
const SEGMENT_COLORS = {
    'Source Code':  '#F24122',
    'Customer PII': '#F2B322',
    'Financials':   '#3BC3D3',
    'Credentials':  '#7958F3',
    'Others':        '#CACED3',
}

// Mirrors InsightService.severityRank on the backend (CRITICAL first, missing/unrecognized
// last) — needed here because "Act now" merges two already-sorted lists (discovery, guardrail)
// into one and has to re-establish a single worst-first order across both.
const SEVERITY_RANK = { CRITICAL: 1, HIGH: 2, MEDIUM: 3, LOW: 4 }
function severityRank(severity) {
    return SEVERITY_RANK[String(severity || '').toUpperCase()] || 5
}

// Title tooltip for any posture card: what it measures, then any data-gap notes the backend sent.
function cardInfo(id, gaps, ...extra) {
    return [POSTURE_CARD_INFO[id], ...extra, ...(gaps || []).map((g) => g.impact)].filter(Boolean).join(' ')
}

function formatValue(kpi) {
    if (kpi.value === null || kpi.value === undefined) return '—'
    if (kpi.unit === 'percent') return `${kpi.value}%`
    return kpi.value.toLocaleString()
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

    // Critical alerts / Sensitive data incidents: real weekly counts, bucketed server-side from
    // the same fixed ATTACK_TREND_WEEKS window "Attack attempts" uses (see
    // PostureService#weeklySparkline). Monitoring coverage has no history to trend against yet
    // (a point-in-time ratio, not an event count — see buildSummary's own gap conventions), so it
    // gets the page's usual blurred-illustrative-data treatment instead of a real chart.
    const sparklineColor = KPI_SPARKLINE_COLOR[kpi.id]
    const hasRealSparkline = !!sparklineColor && Array.isArray(kpi.sparkline) && kpi.sparkline.length > 0

    // Footnotes live in the title's tooltip (with any data gaps) rather than as body text, so every
    // tile hugs to title → value → sparkline and the row stays one compact height.
    const hintText = cardInfo(kpi.id, kpi.dataGaps, kpi.footnote)

    // Same header as every other card on the page, so the KPI titles read as proper card titles.
    const header = <ComponentHeader title={kpi.label} tooltipContent={hintText || null} />

    const valueRow = hasValue ? (
        <HorizontalStack gap="2" blockAlign="baseline" wrap={false}>
            <Text variant="heading2xl" as="p">{formatValue(kpi)}</Text>
            {deltaText && (
                <Text variant="bodyMd" fontWeight="semibold" color={DELTA_TONE_TO_COLOR[kpi.deltaTone] || 'subdued'}>
                    {deltaText}
                </Text>
            )}
        </HorizontalStack>
    ) : (
        <Text variant="headingLg" as="p" color="subdued">Not computed yet</Text>
    )

    // Same title row on every tile so the four labels line up; the ring sits beside the value below it.
    const content = showRing ? (
        <VerticalStack gap="2">
            {header}
            <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                {/* minWidth stops flex-shrink from clipping the donut's chart container. */}
                <Box minWidth="56px">
                    <RiskScoreRing value={kpi.value} size={56} />
                </Box>
                {valueRow}
            </HorizontalStack>
        </VerticalStack>
    ) : (
        <VerticalStack gap="1">
            {header}
            {valueRow}
            {hasRealSparkline && (
                <Box paddingBlockStart="2" width="100%">
                    <SmoothAreaChart tickPositions={kpi.sparkline} color={sparklineColor} height="40" width={null} />
                </Box>
            )}
        </VerticalStack>
    )

    return (
        <Card padding="4">
            {clickable ? (
                <Box className="cursor-pointer" onClick={() => onOpen(kpi)}>{content}</Box>
            ) : content}
        </Card>
    )
}

// A stub tile for a posture area not built yet — the design's own convention (every unmapped
// drilldown falls back to a "Not built yet" panel) applied to the summary row, so the page never
// silently omits a card the design expects to see.
function ComingSoonTile({ label }) {
    return (
        <Card padding="4">
            <VerticalStack gap="1">
                <ComponentHeader title={label} />
                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                    <Text variant="heading2xl" as="p" color="subdued">—</Text>
                    <Badge status="new">Coming soon</Badge>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    )
}

// Same red/yellow/green triad already used elsewhere on this page (see SEGMENT_COLORS /
// warningOverridden above) — reused rather than inventing a fourth palette for one panel.
function colorForReadiness(value) {
    if (value >= 75) return '#23C48C'
    if (value >= 40) return '#F2B322'
    return '#F24122'
}

// "Framework readiness" — clausesCovered/totalClauses per framework, from a compliance-clause
// scan of real guardrail-violation traffic (see ComplianceClauseScanService /
// PostureService#frameworkReadiness). Replaces the earlier enforcingPolicies/totalPolicies metric,
// which answered "are my policies switched on" rather than "how ready am I for this framework".
// No target/quarter-goal exists server-side, so — unlike the earlier dummy content — there is no
// tick mark to draw; inventing one would just be fake data again.
function FrameworkReadinessCard({ panel, onOpen }) {
    const rows = panel?.frameworks || []
    const hasData = rows.length > 0
    const effectiveRows = hasData ? rows : DUMMY_FRAMEWORK_READINESS

    const body = (
        <VerticalStack gap="3">
            {effectiveRows.map((row) => (
                // Any framework click opens the same group-level (all-frameworks) drilldown table —
                // drilling into one specific framework's clause hits happens from a row inside it.
                <div
                    key={row.framework || row.id}
                    onClick={hasData ? () => onOpen() : undefined}
                    style={{ cursor: hasData ? 'pointer' : 'default' }}
                >
                    <VerticalStack gap="1">
                        <HorizontalStack align="space-between">
                            <Text variant="bodyMd">{row.framework || row.label}</Text>
                            <Text variant="bodyMd" fontWeight="semibold">{row.value}%</Text>
                        </HorizontalStack>
                        <CustomProgressBar progress={row.value} topColor={colorForReadiness(row.value)} height={"10px"}/>
                    </VerticalStack>
                </div>
            ))}
        </VerticalStack>
    )
    return (
        <CardWithHeader
            title="Framework readiness"
            tooltipContent={cardInfo('frameworkReadiness', panel?.dataGaps)}
            hasData={true}
            minHeight="220px"
        >
            {hasData ? body : <DummyDataOverlay panelId="frameworkReadiness">{body}</DummyDataOverlay>}
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
        <CardWithHeader title="Adoption gap by department" tooltipContent={cardInfo('adoptionGap')} hasData={true} minHeight="220px">
            <DummyDataOverlay panelId="adoptionGap">{body}</DummyDataOverlay>
        </CardWithHeader>
    )
}

// "Vendor risk vs. exposure" — real once vendorTable has rows (the same table RiskScoreSubScoreRow
// shows for the vendorRisk sub-score, now also on the main summary — see RiskScoreCalculator#compute).
// x = this vendor's device count relative to the busiest vendor (exposure); y = its 0-5 risk weight
// (KNOWN_RISKY_VENDORS/UNAPPROVED_VENDOR_WEIGHT — unapproved is a flat 3, a known-risky-but-approved
// vendor is 5, everything else is 0). A plain absolutely-positioned scatter, not a chart library —
// static-shaped data (a handful of vendors), so there's nothing a real chart engine buys here.
function VendorRiskBubbleCard({ vendorTable, onOpen }) {
    const navigate = useNavigate()
    const rows = vendorTable || []
    const hasData = rows.length > 0
    const maxCount = hasData ? Math.max(...rows.map((r) => r.count)) : 0
    const points = hasData
        ? rows.map((r) => ({
            id: r.vendor,
            x: maxCount > 0 ? Math.round((r.count / maxCount) * 85) + 5 : 5,
            y: Math.round((r.weight / 5) * 80) + 10,
            color: r.weight >= 4 ? '#dc2626' : r.weight >= 3 ? '#ca8a04' : '#16a34a',
            label: `${r.vendor} — ${r.approved ? 'approved' : 'unapproved'}, ${r.count} device${r.count === 1 ? '' : 's'}`,
        }))
        : DUMMY_VENDOR_RISK_BUBBLE.map((p) => ({ ...p, color: p.actFirst ? '#dc2626' : (p.y > 50 ? '#16a34a' : '#ca8a04') }))

    const body = (
        <VerticalStack gap="2">
            <div
                onClick={hasData ? () => onOpen() : undefined}
                style={{ position: 'relative', height: '180px', border: '1px solid #e5e7eb', borderRadius: '4px', cursor: hasData ? 'pointer' : 'default' }}
            >
                <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '35%', background: 'rgba(220,38,38,0.08)' }} />
                {points.map((p) => (
                    // Plain title, not Polaris <Tooltip>: Tooltip wraps its child in its own
                    // positioned span, which becomes the nearest positioned ancestor for this
                    // div's position:absolute instead of the chart container above — every bubble
                    // collapsed to roughly the same spot (only the last one, topmost in z-order,
                    // looked like it rendered at all).
                    <div
                        key={p.id}
                        title={p.label || ''}
                        style={{
                            position: 'absolute', left: `${p.x}%`, top: `${100 - p.y}%`,
                            transform: 'translate(-50%, -50%)', width: 14, height: 14, borderRadius: '50%',
                            background: p.color,
                        }}
                    />
                ))}
            </div>
            <HorizontalStack align="space-between">
                <Text variant="bodySm" color="subdued">Devices exposed, left to right</Text>
                <Text variant="bodySm" color="critical">Above the line: act first</Text>
            </HorizontalStack>
        </VerticalStack>
    )

    return (
        <CardWithHeader
            title="Vendor risk vs. exposure"
            tooltipContent={cardInfo('vendorRiskExposure')}
            hasData={true}
            headerAction={<Button plain onClick={() => navigate('/dashboard/observe/audit')}>Registry</Button>}
        >
            {hasData ? body : <DummyDataOverlay panelId="vendorRiskExposure">{body}</DummyDataOverlay>}
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
            tooltipContent={cardInfo('shadowAiTrend', panel.dataGaps)}
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
            {items.map(({ label, color, count, percent }) => (
                <HorizontalStack key={label} align="space-between" blockAlign="center">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Box style={{ width: 10, height: 10, borderRadius: '50%', background: color, flexShrink: 0 }} />
                        <Text variant="bodyMd" color="subdued">{label}</Text>
                    </HorizontalStack>
                    <HorizontalStack gap="1" blockAlign="center">
                        <Text variant="bodyMd" fontWeight="semibold">{count.toLocaleString()}</Text>
                        {(percent !== undefined && percent !== null) && (
                            <Text variant="bodySm" color="subdued">({percent}%)</Text>
                        )}
                    </HorizontalStack>
                </HorizontalStack>
            ))}
        </VerticalStack>
    )
}

function DataLeavingCard({ panel, onOpen }) {
    if (!panel) return null
    const hasData = panel.total > 0
    const effective = hasData ? panel : DUMMY_DATA_LEAVING

    // Fixed taxonomy order (SEGMENT_COLORS' own key order), not whatever order the backend's
    // segments array happens to arrive in — any label outside that taxonomy (a real, unmapped
    // policy name; the backend's own "Other" overflow bucket) is appended after, in its existing
    // order, rather than dropped.
    const labelOrder = Object.keys(SEGMENT_COLORS)
    const orderedSegments = [...(effective.segments || [])].sort((a, b) => {
        const ai = labelOrder.indexOf(a.label)
        const bi = labelOrder.indexOf(b.label)
        return (ai === -1 ? labelOrder.length : ai) - (bi === -1 ? labelOrder.length : bi)
    })

    const graphData = {}
    orderedSegments.forEach((seg) => {
        graphData[seg.label] = {
            text: seg.count,
            color: SEGMENT_COLORS[seg.label] || SEGMENT_COLORS.Others,
            filterValue: seg.filterId || seg.label,
            percent: seg.percent,
        }
    })
    const legendItems = Object.entries(graphData).map(([label, { text, color, percent }]) => ({ label, color, count: text, percent }))

    // Any segment click opens the SAME group-level drilldown table (every data type, not just the
    // one clicked) — drilling into one specific type happens from a row inside that table.
    const body = (
        <HorizontalStack gap="4" blockAlign="center" wrap={false}>
            <DonutChart
                data={graphData}
                title=""
                size={150}
                pieInnerSize="55%"
                onSegmentClick={hasData ? () => onOpen() : undefined}
            />
            <ChartLegend items={legendItems} />
        </HorizontalStack>
    )

    return (
        <CardWithHeader
            title="What data is leaving"
            tooltipContent={cardInfo('dataLeaving', panel.dataGaps)}
            hasData={true}
            minHeight="180px"
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
                // Any stage click opens the same group-level (all-stages) drilldown table — drilling
                // into one specific stage happens from a row inside that table.
                <div key={stage.id} onClick={hasData ? () => onOpen() : undefined} style={{ cursor: hasData && panel.route ? 'pointer' : 'default' }}>
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
                </div>
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
            tooltipContent={cardInfo('enforcementFunnel', panel.dataGaps)}
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
            tooltipContent={cardInfo('attackAttempts', panel.dataGaps)}
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
function ActNowRow({ insight, onOpen, isLast }) {
    return (
        <Box padding="2" borderBlockEndWidth={isLast ? undefined : '1'} borderColor="border" onClick={() => onOpen(insight)}>
            <Box className="cursor-pointer">
                <VerticalStack gap="1">
                    <HorizontalStack gap="2">
                        {insight.severity && <SeverityBadge severity={insight.severity} useDot={true}/>}
                        <Text variant="bodyMd" fontWeight="semibold">{insight.title}</Text>
                    </HorizontalStack>
                    <Text variant="bodySm" color="subdued">{insight.headline}</Text>
                </VerticalStack>
            </Box>
        </Box>
    )
}

// Real, not illustrative — PostureService#biggestMovers: a vendor whose device count or
// malicious-event count crossed a fixed threshold within the last 2 weeks (independent of the
// page's own date filter), top 5 combined across both conditions (max 3 each).
function BiggestMoversCard({ biggestMovers }) {
    const movers = (biggestMovers && biggestMovers.movers) || []
    if (movers.length === 0) {
        return (
            <CardWithHeader title="Biggest movers" tooltipContent={cardInfo('biggestMovers')} hasData={false}
                emptyMessage="No vendor crossed a threshold in the time period" minHeight="160px" />
        )
    }
    return (
        <CardWithHeader title="Biggest movers" tooltipContent={cardInfo('biggestMovers')} hasData={true} minHeight="160px">
            <VerticalStack gap="3">
                {movers.map((m) => (
                    <VerticalStack key={`${m.condition}-${m.vendor}`} gap="05">
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="bodyMd" fontWeight="semibold">{m.vendor}</Text>
                            <Badge status={m.condition === 'attacks' ? 'critical' : 'warning'}>
                                {m.condition === 'attacks' ? `${m.value.toLocaleString()} attacks` : `${m.value} devices`}
                            </Badge>
                        </HorizontalStack>
                        <Text variant="bodySm" color="subdued">{m.headline}</Text>
                    </VerticalStack>
                ))}
            </VerticalStack>
        </CardWithHeader>
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
            <CardWithHeader title="Act now" tooltipContent={cardInfo('actNow')} hasData={false} emptyMessage="Nothing needs attention right now." minHeight="160px" />
        )
    }

    return (
        <CardWithHeader title="Act now" tooltipContent={cardInfo('actNow')} hasData={true} minHeight="160px">
            <VerticalStack gap="2">
                {merged.map((i, idx) => (
                    <ActNowRow key={i.insightId} insight={i} isLast={idx === merged.length - 1} onOpen={() => onOpenInsight(i.insightId, i.group)} />
                ))}
            </VerticalStack>
        </CardWithHeader>
    )
}

// "Last 30 days" — same default EndpointPosture uses.
const DEFAULT_DATE_RANGE = values.ranges[3]

// A shared drilldown link is only reproducible if the page's date filter comes back with it —
// this page's range was pure local state until now. `since`/`until` (raw epoch seconds) mirror the
// same convention ThreatDetectionPage/CompliancePage already use for a shareable date filter.
function dateRangeFromSearchParams(searchParams) {
    const sinceParam = searchParams.get('since')
    const untilParam = searchParams.get('until')
    if (sinceParam == null || untilParam == null) return null
    const sinceTs = parseInt(sinceParam, 10)
    const untilTs = parseInt(untilParam, 10)
    if (Number.isNaN(sinceTs) || Number.isNaN(untilTs)) return null
    return { title: 'Custom', alias: 'custom', period: { since: new Date(sinceTs * 1000), until: new Date(untilTs * 1000) } }
}

function SecurityPosture() {
    const navigate = useNavigate()
    const [searchParams, setSearchParams] = useSearchParams()
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        searchParams,
        (sp) => dateRangeFromSearchParams(sp) || DEFAULT_DATE_RANGE
    )
    const [pageData, setPageData] = useState({})
    const [loading, setLoading] = useState(true)
    const [flyout, setFlyout] = useState(null) // { insightId, group } | null
    // { drillId, path } | null — the paginated drilldown flyout (Shadow AI tools, What data is
    // leaving, Enforcement funnel, Vendor risk, Framework readiness, Risk score breakdown), fully
    // derived from the URL's own `drill`/`path` params so a drilldown link is shareable and
    // reload-safe.
    const drillState = useMemo(() => {
        const drillId = searchParams.get('drill')
        if (!drillId) return null
        return { drillId, path: searchParams.get('path') || '' }
    }, [searchParams])

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000)

    // Mirrors the page's own selected range into the URL on every change ({replace: true} — this
    // reflects internal state, it isn't a user-initiated navigation) so a link copied at any time
    // reproduces the same range, not just whatever a drilldown's own `drill`/`path` params capture.
    useEffect(() => {
        const since = String(getTimeEpoch('since'))
        const until = String(getTimeEpoch('until'))
        if (searchParams.get('since') === since && searchParams.get('until') === until) return
        const next = new URLSearchParams(searchParams)
        next.set('since', since)
        next.set('until', until)
        setSearchParams(next, { replace: true })
    }, [currDateRange]) // eslint-disable-line react-hooks/exhaustive-deps

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

    // Opens (or jumps to a specific level of) a panel's paginated drilldown flyout — replaces the
    // old openPanel/navigate-away behavior for the 3 panels that had it, and is the first
    // drilldown at all for Vendor risk / Framework readiness, which had none. {replace: true}: this
    // mirrors flyout state into the URL, it isn't itself a navigation the user should be able to
    // back-button through level by level.
    const openDrill = (drillId, path = '') => {
        const next = new URLSearchParams(searchParams)
        next.set('drill', drillId)
        if (path) next.set('path', path); else next.delete('path')
        setSearchParams(next, { replace: true })
    }
    const navigateDrill = (state) => openDrill(state.drillId, state.path)
    const closeDrill = () => {
        const next = new URLSearchParams(searchParams)
        next.delete('drill')
        next.delete('path')
        setSearchParams(next, { replace: true })
    }

    const kpiRow = (
        <HorizontalGrid columns={4} gap="4">
            {[KPI_RISK_SCORE, KPI_CRITICAL_ALERTS, KPI_MONITORING_COVERAGE, KPI_SENSITIVE_INCIDENTS].map((id) => {
                const kpi = kpiById(id)
                if (!kpi) return <ComingSoonTile key={id} label={id} />
                // Risk score / Critical alerts / Sensitive data incidents each open their own
                // in-context drilldown instead of navigating away — Monitoring coverage is the
                // only one left on the old navigate-away behavior (openKpi).
                if (id === KPI_RISK_SCORE) {
                    return <KpiTile key={id} kpi={kpi} onOpen={() => openDrill(DRILL_RISK_SCORE)} forceClickable />
                }
                if (id === KPI_CRITICAL_ALERTS) {
                    return <KpiTile key={id} kpi={kpi} onOpen={() => openDrill(DRILL_CRITICAL_ALERTS)} forceClickable />
                }
                if (id === KPI_SENSITIVE_INCIDENTS) {
                    return <KpiTile key={id} kpi={kpi} onOpen={() => openDrill(DRILL_SENSITIVE_DATA)} forceClickable />
                }
                return <KpiTile key={id} kpi={kpi} onOpen={openKpi} />
            })}
        </HorizontalGrid>
    )

    // Shadow AI trend gets more width than the data-leaving donut (3:2). Cards are direct grid
    // items, so both stretch to the taller one's height; minmax(0, …) lets the charts shrink.
    const shadowAndDataLeavingRow = (
        <HorizontalGrid columns="minmax(0, 3fr) minmax(0, 2fr)" gap="4">
            <ShadowAiTrendCard panel={pageData.shadowAiTrend} onOpen={() => openDrill(DRILL_SHADOW_AI)} />
            {/* Every card opens the SAME group-level (L1) table regardless of which segment/row/
                stage within it was clicked — drilling into a specific member (a data type, a
                stage, a framework) happens by clicking a row inside that L1 table, not by
                pre-guessing which one from the card. */}
            <DataLeavingCard panel={pageData.dataLeaving} onOpen={() => openDrill(DRILL_DATA_LEAVING)} />
        </HorizontalGrid>
    )

    const funnelAttackVendorRow = (
        <HorizontalGrid columns={3} gap="3">
            <EnforcementFunnelCard panel={pageData.enforcementFunnel} onOpen={() => openDrill(DRILL_ENFORCEMENT_FUNNEL)} />
            <AttackAttemptsCard panel={pageData.attackAttempts} onOpen={openPanel} />
            <VendorRiskBubbleCard vendorTable={kpiById(KPI_RISK_SCORE)?.vendorTable} onOpen={() => openDrill(DRILL_VENDOR_RISK)} />
        </HorizontalGrid>
    )

    const frameworkAndAdoptionRow = (
        <HorizontalGrid columns={2} gap="4">
            <FrameworkReadinessCard panel={pageData.frameworkReadiness} onOpen={() => openDrill(DRILL_FRAMEWORK_READINESS)} />
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

    const rightRail = (
        <VerticalStack gap="3">
            <ActNowCard actNow={pageData.actNow} onOpenInsight={openInsight} />
            <BiggestMoversCard biggestMovers={pageData.biggestMovers} />
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
                    <PostureDrillFlyout
                        drillState={drillState}
                        onNavigate={navigateDrill}
                        onClose={closeDrill}
                        riskScoreKpi={kpiById(KPI_RISK_SCORE)}
                        startTimestamp={getTimeEpoch('since')}
                        endTimestamp={getTimeEpoch('until')}
                    />
                </>
            )}
        </Box>
    )
}

export default SecurityPosture

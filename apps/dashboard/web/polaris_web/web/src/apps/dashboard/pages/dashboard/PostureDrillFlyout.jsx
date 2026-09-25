import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Badge, Banner, Box, Button, Card, DataTable, Divider, HorizontalGrid, HorizontalStack, Icon, Scrollable, Spinner, Text, VerticalStack,
} from '@shopify/polaris'
import { ChevronRightMinor } from '@shopify/polaris-icons'
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
// drill row field named one of these (see PostureService#fetchDrill/ProfileBuilder's own row
// builders) is already epoch seconds, not epoch millis, and must never render as a bare number —
// this was the "AI summary/table shows raw seconds" bug this level's build fixed.
const EPOCH_FIELDS = new Set(['detectedAt', 'firstSeen', 'lastSeen', 'lastScannedAt', 'timestamp'])

// A CTA's own `params` (e.g. Critical alerts' "View all" -> {severity: "CRITICAL"}) has to land
// as a URL query param, not router `state` — the destination pages this app already has (e.g.
// ThreatDetectionPage.jsx's own severity filter) read their own pre-filters off `searchParams`,
// never off `location.state`. Appending here, once, is what makes a CTA's `params` do anything at
// all — passing them as `state` would have silently gone nowhere on arrival.
function ctaHref(cta) {
    if (!cta.params) return cta.route
    const qs = new URLSearchParams(cta.params).toString()
    if (!qs) return cta.route
    return cta.route + (cta.route.includes('?') ? '&' : '?') + qs
}

function EpochCell({ value }) {
    return <Text variant="bodySm">{func.prettifyEpoch(value || 0)}</Text>
}

// ─── Stats row ────────────────────────────────────────────────────────────────
// Same "big number over a subdued label" stat-tile language OverviewContent (SessionFlyout) uses
// — a row total is always shown (real, not from the backend's own optional summary[]), plus
// whatever InsightResult.Metric rows the drill sends.

function DrillStats({ drill }) {
    const stats = (drill.summary || []).map((m) => ({ key: m.key, label: m.label, value: m.formatted }))
    // The table's first column names what each row is, so the list gets a real title ("Tools")
    // with the count beside it, rather than a generic "Rows" total.
    const entity = drill.columns?.[0]?.headerName
    const title = !entity ? 'Results'
        : entity.endsWith('s') ? entity
        : /[^aeiou]y$/i.test(entity) ? `${entity.slice(0, -1)}ies` : `${entity}s`
    return (
        <VerticalStack gap="3">
            <HorizontalStack gap="2" blockAlign="center">
                <Text variant="headingMd" as="h3">{title}</Text>
                <Badge>{(drill.total ?? 0).toLocaleString()}</Badge>
            </HorizontalStack>
            {stats.length > 0 && (
                <HorizontalStack gap="6">
                    {stats.map((s) => (
                        <VerticalStack gap="1" key={s.key}>
                            <Text variant="headingLg" as="p">{s.value}</Text>
                            <Text variant="bodySm" color="subdued">{s.label}</Text>
                        </VerticalStack>
                    ))}
                </HorizontalStack>
            )}
        </VerticalStack>
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
                    <Text variant="headingSm">AI summary</Text>
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
                                <Text variant="headingXs">Concern</Text>
                                <Text variant="bodyMd">{drill.narrativeConcern}</Text>
                            </VerticalStack>
                        )}
                        {drill.narrativeImpact && (
                            <VerticalStack gap="1">
                                <Text variant="headingXs">Impact</Text>
                                <Text variant="bodyMd">{drill.narrativeImpact}</Text>
                            </VerticalStack>
                        )}
                        {drill.narrativeRemediation && (
                            <VerticalStack gap="1">
                                <Text variant="headingXs">Remediation</Text>
                                <Text variant="bodyMd">{drill.narrativeRemediation}</Text>
                            </VerticalStack>
                        )}
                    </VerticalStack>
                )}
            </VerticalStack>
        </Box>
    )
}

// ─── Entity profile (risk-score breakdown's own 3rd level) ─────────────────────
// One page per sub-score's L2 row (a tool/device/vendor/framework) — a header with a risk badge
// and CTAs, an optional coaching notice, stat tiles, a facts card, and one or more sections (a
// timeline or a table). See PostureDrillResult#layout's own javadoc; every section's rows are
// already capped server-side (PostureService.PROFILE_SECTION_CAP), so this renders them plainly
// rather than through AgGridTable's own SSRM pagination.

const SEVERITY_DOT_COLOR = { CRITICAL: '#D82C0D', HIGH: '#EF8A15', MEDIUM: '#EEC200', LOW: '#8C9196' }
const STATUS_BADGE_STATUS = { Met: 'success', Partial: 'attention', Gap: 'critical' }

function ProfileHeader({ drill, onCtaClick }) {
    const ctas = drill.ctas || []
    return (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between" blockAlign="start" wrap={false}>
                <HorizontalStack gap="3" blockAlign="center">
                    <Text variant="headingXl" as="h2">{drill.title}</Text>
                    {drill.badge && <Badge status={drill.badge.tone}>{drill.badge.label}</Badge>}
                </HorizontalStack>
                {ctas.length > 0 && (
                    <HorizontalStack gap="2">
                        {ctas.map((cta, i) => (
                            <Button key={cta.id} size="slim" primary={i === ctas.length - 1}
                                onClick={() => onCtaClick(cta)}>
                                {cta.label}
                            </Button>
                        ))}
                    </HorizontalStack>
                )}
            </HorizontalStack>
            {drill.subtitle && <Text variant="bodyMd" color="subdued">{drill.subtitle}</Text>}
        </VerticalStack>
    )
}

function ProfileFacts({ facts }) {
    if (!facts || facts.length === 0) return null
    return (
        <HorizontalGrid columns={3} gap="4">
                {facts.map((f, i) => (
                    <VerticalStack gap="1" key={i}>
                        <Text variant="bodySm" color="subdued">{f.label}</Text>
                        <Text variant="bodyMd" fontWeight="semibold" color={f.tone === 'critical' ? 'critical' : undefined}>
                            {f.value}
                        </Text>
                    </VerticalStack>
                ))}
        </HorizontalGrid>
    )
}

function ProfileTimeline({ section }) {
    const rows = section.rows || []
    return (
                <VerticalStack gap="4">
                    <VerticalStack gap="05">
                        <Text variant="headingSm">{section.title}</Text>
                        {section.subtitle && <Text variant="bodySm" color="subdued">{section.subtitle}</Text>}
                    </VerticalStack>
                    {rows.length === 0 ? (
                        <Text variant="bodySm" color="subdued">Nothing recorded in this window.</Text>
                    ) : (
                        // time | rail | content. Grid cells stretch to the row's height, so the rail's
                        // border runs from under the dot to the next row — rows have no gap, the
                        // content's bottom padding is the spacing, which keeps the line unbroken.
                        <VerticalStack gap="0">
                            {rows.map((r, i) => (
                                <HorizontalGrid key={i} columns="96px 8px minmax(0, 1fr)" gap="3">
                                    <Text variant="bodySm" color="subdued" alignment="end">{func.prettifyEpoch(r.timestamp || 0)}</Text>
                                    <Box position="relative">
                                        <Box paddingBlockStart="1">
                                            <Box className="agentic-dot" style={{ '--dot-color': SEVERITY_DOT_COLOR[String(r.severity || '').toUpperCase()] || '#8C9196' }} />
                                        </Box>
                                        {i < rows.length - 1 && (
                                            <Box position="absolute" insetBlockStart="4" insetBlockEnd="0" width="4px" borderInlineEndWidth="1" borderColor="border-subdued" />
                                        )}
                                    </Box>
                                    <Box paddingBlockEnd="5">
                                        <VerticalStack gap="05">
                                            <Text variant="bodyMd" fontWeight="semibold">{r.title}</Text>
                                            {r.detail && <Text variant="bodySm" color="subdued">{r.detail}</Text>}
                                        </VerticalStack>
                                    </Box>
                                </HorizontalGrid>
                            ))}
                        </VerticalStack>
                    )}
                    {section.total > rows.length && (
                        <Text variant="bodySm" color="subdued">Showing {rows.length} of {section.total}.</Text>
                    )}
                </VerticalStack>
    )
}

function ProfileTable({ section }) {
    const columns = section.columns || []
    const rows = section.rows || []
    const headings = columns.map((c) => c.headerName)
    const tableRows = rows.map((r) => columns.map((c) => {
        const value = r[c.field]
        if (EPOCH_FIELDS.has(c.field)) return func.prettifyEpoch(value || 0)
        if ((c.field === 'status' || c.field === 'result') && STATUS_BADGE_STATUS[value]) {
            return <Badge status={STATUS_BADGE_STATUS[value]}>{value}</Badge>
        }
        return value === null || value === undefined || value === '' ? '-' : String(value)
    }))
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <VerticalStack gap="05">
                    <Text variant="headingSm">{section.title}</Text>
                    {section.subtitle && <Text variant="bodySm" color="subdued">{section.subtitle}</Text>}
                </VerticalStack>
            </Box>
            {rows.length === 0 ? (
                <Box padding="4" paddingBlockStart="0">
                    <Text variant="bodySm" color="subdued">Nothing to show in this window.</Text>
                </Box>
            ) : (
                <DataTable
                    columnContentTypes={columns.map(() => 'text')}
                    headings={headings}
                    rows={tableRows}
                    hideScrollIndicator
                    increasedTableDensity
                />
            )}
            {section.total > rows.length && (
                <Box padding="3">
                    <Text variant="bodySm" color="subdued">Showing {rows.length} of {section.total}.</Text>
                </Box>
            )}
        </Card>
    )
}

function DrillProfileBody({ drill, onCtaClick }) {
    // Summary metrics repeat what the subtitle already says, so they join the facts card (skipping
    // any the facts already carry) instead of rendering as a third copy of the same numbers.
    const facts = drill.facts || []
    const factLabels = new Set(facts.map((f) => f.label))
    const mergedFacts = [
        ...(drill.summary || []).filter((m) => !factLabels.has(m.label)).map((m) => ({ label: m.label, value: m.formatted })),
        ...facts,
    ]
    return (
        <Scrollable shadow style={{ flex: 1, minHeight: 0 }}>
        <Box padding="4">
            <VerticalStack gap="4">
                <ProfileHeader drill={drill} onCtaClick={onCtaClick} />
                {drill.notice && <Banner status="info">{drill.notice}</Banner>}
                {(drill.dataGaps || []).map((g, i) => <Banner key={i} status="info">{g.impact}</Banner>)}
                <ProfileFacts facts={mergedFacts} />
                {(drill.sections || []).map((s) => (
                    <VerticalStack key={s.id} gap="4">
                        <Divider />
                        {s.kind === 'timeline' ? <ProfileTimeline section={s} /> : <ProfileTable section={s} />}
                    </VerticalStack>
                ))}
            </VerticalStack>
        </Box>
        </Scrollable>
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
    // This sub-score's share of the composite's change (same row "What moved the score" lists).
    const moved = (kpi.whatMoved || []).find((m) => m.category === subScore.label)
    return (
        <Box className="agentic-clickable-row" onClick={onClick}>
            <VerticalStack gap="2">
                <HorizontalGrid columns="180px minmax(0, 1fr) 96px 20px" gap="4" alignItems="center">
                    <VerticalStack gap="05">
                        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                            <Text variant="bodyMd" fontWeight="medium">{subScore.label}</Text>
                            <GapHint gaps={subScore.dataGaps} />
                        </HorizontalStack>
                        <Text variant="bodySm" color="subdued">{subScore.weight}% of composite</Text>
                    </VerticalStack>
                    <CustomProgressBar progress={hasValue ? subScore.value : 0} topColor={band ? band.color : '#9ca3af'} height={"8px"}/>
                    <VerticalStack gap="05" inlineAlign="end">
                        {hasValue ? (
                            <Text variant="bodyMd" fontWeight="semibold">{subScore.value} / 100</Text>
                        ) : (
                            <Text variant="bodySm" color="subdued">Not computed</Text>
                        )}
                        {moved && (
                            <Text variant="bodySm" color={moved.impactPoints > 0 ? 'critical' : 'success'}>
                                {moved.impactPoints > 0 ? '+' : ''}{moved.impactPoints} pts
                            </Text>
                        )}
                    </VerticalStack>
                    <Icon source={ChevronRightMinor} color="subdued" />
                </HorizontalGrid>
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
        </Box>
    )
}

// Composite trend — needs posture_score_history, which doesn't exist yet (see
// PostureService.GAP_POSTURE_HISTORY), so illustrative-only and blurred, same convention as every
// other backend-less panel on this page.
function RiskScoreTrendSection() {
    const body = (
        <VerticalStack gap="4">
            <VerticalStack gap="2">
                <Text variant="bodySm" color="subdued">Composite trend · last 12 weeks</Text>
                <SmoothAreaChart tickPositions={DUMMY_RISK_SCORE_TREND} color="#7C5CFC" height="60" width={null} />
            </VerticalStack>
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

// Same category strings RiskScoreCalculator#addWhatMovedRow writes -> the sub-score id
// openDrill/onSubScoreClick already navigates to (see RiskScoreSubScoreRow's own onClick a few
// rows above this table) — lets a "what moved the score" row jump to that sub-score's own L2
// breakdown, the closest "similar screen" this table's own aggregate category can resolve to
// (the detail text names specific people, but not the raw deviceId an L3 profile link needs).
// Shadow AI exposure / Vendor risk never appear here in the first place (see this file's own
// comment above), so they're intentionally absent from this map too.
const WHAT_MOVED_CATEGORY_TO_SUB_SCORE_ID = {
    'DLP incidents': 'dlpIncidents',
    'Compliance gaps': 'complianceGaps',
    'Threat activity': 'threatActivity',
}

// Real, not illustrative — one row per sub-score that actually moved between this window and the
// immediately preceding one (RiskScoreCalculator#compute's whatMoved). Each row's points are
// computed the exact same way the composite's own delta is (this sub-score's weight over the
// composite's coveredWeight, times its own current-minus-prior) — summing every row here reproduces
// the composite delta exactly, not approximately, because it's that same weighted-average formula
// decomposed back into its terms.
function RiskScoreAnnotationsSection({ kpi, onSubScoreClick }) {
    const rows = (kpi.whatMoved || []).slice().sort((a, b) => Math.abs(b.impactPoints) - Math.abs(a.impactPoints))

    const tableRows = rows.map((row) => {
        const subScoreId = WHAT_MOVED_CATEGORY_TO_SUB_SCORE_ID[row.category]
        const onClick = subScoreId ? () => onSubScoreClick(subScoreId) : undefined
        const cellStyle = onClick ? { cursor: 'pointer' } : undefined
        return [
            <div onClick={onClick} style={cellStyle}>{row.category}</div>,
            <div onClick={onClick} style={cellStyle}>{movedRowDetail(row.category, kpi)}</div>,
            <div onClick={onClick} style={cellStyle}>
                <Text variant="bodyMd" fontWeight="semibold" color={row.impactPoints > 0 ? 'critical' : 'success'}>
                    {row.impactPoints > 0 ? `+${row.impactPoints}` : row.impactPoints} pts
                </Text>
            </div>,
        ]
    })

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
        <Scrollable shadow style={{ flex: 1, minHeight: 0 }}>
        <Box padding="4">
            <VerticalStack gap="5">
                {/* Grid, not HorizontalStack: settings.css forces `.Polaris-HorizontalStack { align-items: center !important }`
                    globally, which would override blockAlign="start". The fixed track also keeps the donut from clipping. */}
                <HorizontalGrid columns="96px minmax(0, 1fr)" gap="5" alignItems="start">
                    <RiskScoreRing value={kpi.value} size={96} showValue />
                    <VerticalStack gap="3">
                        <VerticalStack gap="1">
                            <HorizontalStack gap="2" blockAlign="center">
                                {band && (
                                    <Badge status={band.tone === 'critical' ? 'critical' : band.tone === 'warning' ? 'warning' : 'success'}>
                                        {band.label}
                                    </Badge>
                                )}
                                {deltaText && (
                                    <HorizontalStack gap="1">
                                        <Text variant="bodySm" fontWeight="semibold" color={DELTA_TONE_TO_COLOR[kpi.deltaTone] || 'subdued'}>
                                            {deltaText}
                                        </Text>
                                        <Text variant="bodySm" color="subdued">vs previous period</Text>
                                    </HorizontalStack>
                                )}
                            </HorizontalStack>
                            <Text variant="bodySm" color="subdued">
                                Composite of five weighted sub-scores. Lower is better.
                                {kpi.footnote ? ` ${kpi.footnote}.` : ''}
                            </Text>
                        </VerticalStack>
                        <RiskScoreTrendSection />
                        {historyGap && <Text variant="bodySm" color="subdued">{historyGap.impact}</Text>}
                    </VerticalStack>
                </HorizontalGrid>

                {/* Rows carry their own padding + hover (agentic-clickable-row), so no stack gap here. */}
                <VerticalStack gap="0">
                    {(kpi.subScores || []).map((s) => (
                        <VerticalStack key={s.id} gap="0">
                            <Divider />
                            <RiskScoreSubScoreRow subScore={s} kpi={kpi} onClick={() => onSubScoreClick(s.id)} />
                        </VerticalStack>
                    ))}
                    <Divider />
                </VerticalStack>

                <RiskScoreAnnotationsSection kpi={kpi} onSubScoreClick={onSubScoreClick} />
            </VerticalStack>
        </Box>
        </Scrollable>
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
    // Risk-score breakdown's own 3rd level (one entity — a tool/device/vendor/framework) — see
    // PostureDrillResult#layout's own javadoc. Renders DrillProfileBody instead of AgGridTable,
    // with its own header/stats/facts replacing the generic top-of-flyout DrillStats/ctas row.
    const isProfileLayout = drill?.layout === 'profile'
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
    const breadcrumbItems = useMemo(() => {
        const trail = (drill?.breadcrumb || []).map((b, i, arr) => ({
            label: b.label,
            onClick: i === arr.length - 1 ? undefined
                : () => onNavigate({ drillId: drillState.drillId, path: b.path }),
        }))
        // Every drill's own trail starts at its panel root ("Shadow AI tools", "Risk score
        // breakdown", ...) — this leading crumb is the one thing every level shares: the page this
        // flyout sits over. Not part of PostureDrillResult.breadcrumb itself (that's a backend
        // concept scoped to one drillId's own levels; "close the flyout" is a frontend-only action).
        return [{ label: 'Security posture', onClick: onClose }, ...trail]
    }, [drill?.breadcrumb, drillState, onNavigate, onClose])

    const columnDefs = useMemo(() => (drill?.columns || []).map((c) => ({
        field: c.field,
        headerName: c.headerName,
        flex: 1,
        minWidth: 130,
        cellStyle: { display: 'flex', alignItems: 'center' },
        ...(EPOCH_FIELDS.has(c.field) ? { cellRenderer: EpochCell } : {}),
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

    const ctas = drill?.ctas || []
    const dataGaps = drill?.dataGaps || []
    // The risk score root has no stats row, so without CTAs/gaps the top strip would render as an
    // empty padded band with a stray divider under the header.
    const hasTopContent = !isRiskScoreRoot || ctas.length > 0 || dataGaps.length > 0
    const narrativeSection = drill && drill.narrativeStatus !== 'UNAVAILABLE' ? (
        <>
            <Divider />
            {/* Same pane as the breakdown above, so the two halves split the flyout evenly and each scrolls on its own. */}
            <Scrollable shadow style={{ flex: 1, minHeight: 0 }}>
                <DrillNarrative drill={drill} />
            </Scrollable>
        </>
    ) : null

    return (
        <AgenticFlyoutShell
            show={show}
            width={760}
            header={
                <FlyoutBreadcrumb
                    items={breadcrumbItems}
                    onClose={onClose}
                    // Only when it adds something — on sub-score levels the title is the last crumb already.
                    subtitle={drillState?.path && drill && drill.title !== breadcrumbItems[breadcrumbItems.length - 1]?.label ? drill.title : null}
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
                        {!isProfileLayout && hasTopContent && (
                            <>
                                <Box padding="4" paddingBlockEnd="0">
                                    <HorizontalStack align="space-between" blockAlign="start">
                                        {!isRiskScoreRoot && <DrillStats drill={drill} />}
                                        {ctas.length > 0 && (
                                            <HorizontalStack gap="2">
                                                {ctas.map((cta) => (
                                                    <Button key={cta.id} size="slim" onClick={() => navigate(ctaHref(cta))}>{cta.label}</Button>
                                                ))}
                                            </HorizontalStack>
                                        )}
                                    </HorizontalStack>
                                    {dataGaps.length > 0 && (
                                        <Box paddingBlockStart="3">
                                            <VerticalStack gap="2">
                                                {dataGaps.map((g, i) => (
                                                    <Banner key={i} status="info">{g.impact}</Banner>
                                                ))}
                                            </VerticalStack>
                                        </Box>
                                    )}
                                </Box>
                                <Box paddingBlockStart="4"><Divider /></Box>
                            </>
                        )}
                        <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
                            {isRiskScoreRoot ? (
                                <RiskScoreRootBody kpi={mergedRiskScoreKpi} onSubScoreClick={handleSubScoreClick} />
                            ) : isProfileLayout ? (
                                <DrillProfileBody drill={drill} onCtaClick={(cta) => navigate(ctaHref(cta))} />
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
                        {narrativeSection}
                    </>
                )}
            </div>
        </AgenticFlyoutShell>
    )
}

export default PostureDrillFlyout

import { useState } from 'react'
import { Badge, Box, Card, HorizontalStack, Icon, Popover, ProgressBar, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import { CircleInformationMajor } from '@shopify/polaris-icons'

// Generic idioms shared by both posture pages, adapted from the same pattern
// SecurityPosture.jsx already established (KpiTile/ComingSoonTile/GapHint/DummyDataOverlay) —
// the shape of this page's own KPI/panel objects differs slightly (footnote/secondaryFootnote/
// linkLabel instead of route/dataGaps-only), so these are new functions following the same idiom
// rather than literal re-exports of the Atlas-specific ones.

export const TONE_TEXT_COLOR = {
    critical: 'critical',
    warning: 'warning',
    success: 'success',
    subdued: 'subdued',
}

// Polaris's ProgressBar `color` prop uses a different vocabulary (no "warning") than Badge's
// `status`/our own TONE_TEXT_COLOR — this bridges a KPI's own tone to it.
const TONE_PROGRESS_COLOR = {
    critical: 'critical',
    warning: 'highlight',
    success: 'success',
}

export function formatValue(value, unit) {
    if (value === null || value === undefined) return '—'
    if (unit === 'percent') return `${value}%`
    return value.toLocaleString()
}

export function formatDelta(delta, unit) {
    if (delta === null || delta === undefined) return null
    const sign = delta > 0 ? '+' : ''
    return `${sign}${delta}${unit === 'percent' ? '%' : ''}`
}

// Higher-is-worse 0-100 score -> a red/amber/green tone. First-pass thresholds, easy to retune
// once real accounts show where they should sit — same convention SecurityPosture.jsx's own
// riskBand uses for its composite score.
export function riskBand(value) {
    if (value === null || value === undefined) return null
    if (value >= 67) return { tone: 'critical' }
    if (value >= 34) return { tone: 'warning' }
    return { tone: 'success' }
}

// A data gap (dataGaps[] on any KPI/panel a real backend phase sends) — one shared renderer so a
// reader sees the same "why is this empty/approximate" affordance everywhere on the page.
export function GapHint({ gaps }) {
    if (!gaps || gaps.length === 0) return null
    return (
        <Tooltip content={gaps.map((g) => (typeof g === 'string' ? g : g.impact)).join(' ')}>
            <Box><Icon source={CircleInformationMajor} color="subdued" /></Box>
        </Tooltip>
    )
}

export const POSTURE_EMPTY_STATE_COPY = {
    dangerousPaths: 'Dangerous execution path detection needs a new graph-tracing engine that doesn’t exist yet — tracked as a follow-up phase.',
    identityCredential: 'Akto doesn’t yet capture how an agent authenticates to its own downstream tools (credential type, rotation, sharing) — tracked separately.',
    runtimeAnomaly: 'Volume-anomaly detection needs a new per-agent invocation counter that doesn’t exist yet — tracked as a follow-up phase.',
    permissionVisibility: 'Permission visibility needs the agent permission resolver, which isn’t wired up yet.',
    identityAccess: 'Identity & access needs the agent permission/identity resolvers, which aren’t wired up yet.',
    changesPlaceholder: 'This change type needs a resolver that isn’t wired up yet.',
}

// When a panel genuinely has no data yet (not "zero, confirmed"), render the SAME component with
// static illustrative numbers, blurred, and let a click open a Popover explaining why — rather
// than a bare "no data" box. Reused verbatim from SecurityPosture.jsx's own DummyDataOverlay idiom.
export function DummyDataOverlay({ panelId, copy, children }) {
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
                    <div onClick={() => setActive(true)} style={{ position: 'absolute', inset: 0, cursor: 'pointer' }} />
                }
            >
                <Box padding="4" maxWidth="280px">
                    <Text variant="bodyMd">{copy || POSTURE_EMPTY_STATE_COPY[panelId] || 'Not available yet.'}</Text>
                </Box>
            </Popover>
        </div>
    )
}

// One KPI tile — Card + label + big value + optional secondary line + optional delta + optional
// link. A KPI arrives with `status: "COMING_SOON"` instead of a value when its resolver isn't
// wired up yet — callers render ComingSoonTile in that case rather than this component.
export function KpiTile({ kpi, icon, onOpenLink }) {
    const hasValue = kpi.value !== null && kpi.value !== undefined
    const deltaText = formatDelta(kpi.delta, kpi.deltaUnit)
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <HorizontalStack gap="2" blockAlign="center">
                            {icon && <Box><Icon source={icon} color="subdued" /></Box>}
                            <Text variant="bodySm" fontWeight="semibold" color="subdued">{kpi.label.toUpperCase()}</Text>
                        </HorizontalStack>
                        <GapHint gaps={kpi.dataGaps} />
                    </HorizontalStack>
                    <VerticalStack gap="1">
                        {hasValue ? (
                            <Text variant="heading2xl">{formatValue(kpi.value, kpi.unit)}</Text>
                        ) : (
                            <Text variant="heading2xl" color="subdued">Not computed yet</Text>
                        )}
                        {deltaText && (
                            <Text variant="bodySm" fontWeight="semibold" color={TONE_TEXT_COLOR[kpi.deltaTone] || 'subdued'}>
                                {deltaText}
                            </Text>
                        )}
                    </VerticalStack>
                    {hasValue && kpi.unit === 'percent' && (
                        <ProgressBar progress={kpi.value} size="small" color={TONE_PROGRESS_COLOR[kpi.tone] || 'primary'} />
                    )}
                    {kpi.footnote && <Text variant="bodySm" color="subdued">{kpi.footnote}</Text>}
                    {kpi.secondaryFootnote && (
                        <Text variant="bodySm" color={TONE_TEXT_COLOR[kpi.secondaryTone] || 'subdued'}>{kpi.secondaryFootnote}</Text>
                    )}
                    {kpi.linkLabel && (
                        <Box onClick={() => onOpenLink && onOpenLink(kpi)} style={{ cursor: 'pointer' }}>
                            <Text variant="bodySm" fontWeight="semibold" color="interactive">{kpi.linkLabel} →</Text>
                        </Box>
                    )}
                </VerticalStack>
            </Box>
        </Card>
    )
}

// A stub tile for a KPI not wired up yet — matches SecurityPosture.jsx's own ComingSoonTile
// convention exactly, so the page never silently omits a card the design expects to see.
export function ComingSoonTile({ label }) {
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="bodySm" fontWeight="semibold" color="subdued">{label.toUpperCase()}</Text>
                        <Badge status="new">Coming soon</Badge>
                    </HorizontalStack>
                    <Text variant="heading2xl" color="subdued">—</Text>
                </VerticalStack>
            </Box>
        </Card>
    )
}

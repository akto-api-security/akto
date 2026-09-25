import { useState } from 'react'
import { Box, Icon, Popover, Text, Tooltip } from '@shopify/polaris'
import { CircleInformationMajor } from '@shopify/polaris-icons'
import DonutChart from '../../../components/shared/DonutChart'
import { PANEL_EMPTY_STATE_COPY } from '../securityPostureDummyData'

// Shared between SecurityPosture.jsx's own cards and PostureDrillFlyout's risk-score breakdown
// root view (which used to live in SecurityPosture.jsx as its own standalone FlyLayout, before
// the risk score KPI's drilldown was folded into the same generic drill mechanism as the other 5
// panels) — extracted here instead of duplicated across both files.

// A data gap (dataGaps[0] on any panel/KPI the backend sends) — one shared renderer so a reader
// sees the same "why is this empty / approximate" affordance everywhere rather than each panel
// inventing its own.
export function GapHint({ gaps }) {
    if (!gaps || gaps.length === 0) return null
    return (
        <Tooltip content={gaps.map((g) => g.impact).join(' ')}>
            <Icon source={CircleInformationMajor} color="subdued" />
        </Tooltip>
    )
}

// When a panel genuinely has no data (not "zero, confirmed" — no data at all), showing a bare
// "no data" box reads as broken. Instead: render the SAME chart component with static,
// illustrative numbers (never real account data — see securityPostureDummyData.js), blurred, and
// let a click open a Popover explaining why. This is a placeholder for copy the product side
// still owns — PANEL_EMPTY_STATE_COPY is a stub map, not final text.
export function DummyDataOverlay({ panelId, children }) {
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

// Same thresholds for the composite's band badge and a sub-score row's bar color — these scores
// are "higher is worse", so red/amber/green reads the same way at either level. A first-pass
// banding (not something the backend sends), easy to retune once real accounts show where the
// bands should actually sit.
export function riskBand(value) {
    if (value === null || value === undefined) return null
    if (value >= 67) return { label: 'Elevated', tone: 'critical', color: '#dc2626' }
    if (value >= 34) return { label: 'Moderate', tone: 'warning', color: '#ca8a04' }
    return { label: 'Good', tone: 'success', color: '#16a34a' }
}

export const DELTA_TONE_TO_COLOR = {
    critical: 'critical',
    success: 'success',
    neutral: 'subdued',
}

export function formatDelta(kpi) {
    if (kpi.delta === null || kpi.delta === undefined) return null
    const sign = kpi.delta > 0 ? '+' : ''
    if (kpi.deltaKind === 'percent') return `${sign}${kpi.delta}%`
    return `${sign}${kpi.delta}`
}

// A 0-100 score as a partial ring, colored by riskBand — reuses DonutChart rather than a new
// charting primitive. showValue draws "68"/"of 100" centered in the ring (the breakdown view's
// larger ring); the compact KPI tile version omits it since the value is already printed next to
// the ring.
export function RiskScoreRing({ value, size, showValue }) {
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

import { Badge, Box, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { ArrowDownMinor, ArrowUpMinor } from '@shopify/polaris-icons'
import { TONE_TEXT_COLOR } from '../../agenticPostureShared'

const TONE_STROKE_VAR = {
    critical: 'var(--p-color-icon-critical)',
    warning: 'var(--p-color-icon-warning)',
    success: 'var(--p-color-icon-success)',
}

// A small inline-SVG sparkline (same technique the original mockup uses, no charting library) —
// sample/illustrative points for now, since there's no real weekly posture-score-history model
// yet to back a real multi-point trend (see the posture plan). Rendered plainly like the rest of
// the page's sample data, not hidden behind a placeholder.
function Sparkline({ points, tone }) {
    if (!points || points.length < 2) return null
    const w = 140
    const h = 56
    const pad = 4
    const min = Math.min(...points)
    const max = Math.max(...points)
    const range = max - min || 1
    const coords = points.map((p, i) => {
        const x = pad + (i / (points.length - 1)) * (w - pad * 2)
        const y = h - pad - ((p - min) / range) * (h - pad * 2)
        return [x, y]
    })
    const last = coords[coords.length - 1]
    const stroke = TONE_STROKE_VAR[tone] || 'var(--p-color-icon-subdued)'
    return (
        <svg viewBox={`0 0 ${w} ${h}`} width={w} height={h} aria-hidden="true">
            <polyline
                points={coords.map(([x, y]) => `${x},${y}`).join(' ')}
                fill="none"
                stroke={stroke}
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <circle cx={last[0]} cy={last[1]} r="3" fill={stroke} />
        </svg>
    )
}

// Fleet-wide composite score hero — number + band badge + week-over-week delta, plus a sparkline
// of sample weekly trend points (see Sparkline above for why those are illustrative, not real).
function PostureScoreCard({ postureScore }) {
    if (!postureScore) return null
    const { value, bandLabel, bandTone, delta, deltaTone, deltaDrivenBy, trend } = postureScore
    const deltaPositive = delta > 0
    return (
        <Card>
            {/* height:100%+flex column, same technique CardWithHeader's useFlexContent option
                already uses elsewhere in this app, so this card's content fills the full height
                the KPI grid's two rows give it (see the flex wrapper in ArgusPosture.jsx) instead
                of clumping at the top with dead space below. */}
            <Box padding="4" style={{ height: '100%' }}>
                <VerticalStack gap="4" style={{ height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="bodySm" fontWeight="semibold" color="subdued">POSTURE SCORE</Text>
                        {bandLabel && <Badge status={bandTone}>{bandLabel}</Badge>}
                    </HorizontalStack>
                    <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                        <HorizontalStack gap="2" blockAlign="baseline" wrap={false}>
                            <Text variant="heading3xl">{value}</Text>
                            <Text variant="headingMd" color="subdued">/100</Text>
                        </HorizontalStack>
                        <Sparkline points={trend} tone={bandTone} />
                    </HorizontalStack>
                    {delta !== null && delta !== undefined && (
                        <HorizontalStack gap="1" blockAlign="center">
                            <Box><Icon source={deltaPositive ? ArrowUpMinor : ArrowDownMinor} color={TONE_TEXT_COLOR[deltaTone] || 'subdued'} /></Box>
                            <Text variant="bodyMd" fontWeight="semibold" color={TONE_TEXT_COLOR[deltaTone] || 'subdued'}>
                                {deltaPositive ? '+' : ''}{delta} pts vs last week
                            </Text>
                            {deltaDrivenBy && <Text variant="bodyMd" color="subdued">· driven by {deltaDrivenBy}</Text>}
                        </HorizontalStack>
                    )}
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default PostureScoreCard

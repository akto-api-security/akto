import { Badge, Box, Button, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { ArrowDownMinor, ArrowUpMinor } from '@shopify/polaris-icons'
import { GapHint, BAND_LABEL_FOR_TONE, TONE_TEXT_COLOR, riskBand } from '../../agenticPostureShared'

const TONE_STROKE_VAR = {
    critical: 'var(--p-color-icon-critical)',
    warning: 'var(--p-color-icon-warning)',
    success: 'var(--p-color-icon-success)',
}

// Real trend line from AgenticPostureScoreHistory; renders nothing with fewer than 2 points.
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

// Fleet-wide posture score hero; shows "Not computed yet" instead of falling back to mock data.
function PostureScoreCard({ postureScore, onOpenBreakdown }) {
    if (!postureScore) return null
    const { value, agentsScored, agentsWithNoSignal, dataGaps, trend, delta, deltaTone } = postureScore
    const hasValue = value !== null && value !== undefined
    const hasDelta = delta !== null && delta !== undefined
    const deltaPositive = delta > 0
    const band = hasValue ? riskBand(value) : null
    const bandTone = band ? band.tone : null
    const bandLabel = bandTone ? BAND_LABEL_FOR_TONE[bandTone] : null
    const clickable = hasValue && !!onOpenBreakdown

    return (
        <Card>
            <Box padding="4" onClick={clickable ? onOpenBreakdown : undefined}
                style={{ height: '100%', cursor: clickable ? 'pointer' : undefined }}>
                <VerticalStack gap="4" style={{ height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                    <HorizontalStack align="space-between" blockAlign="center">
                        <HorizontalStack gap="1" blockAlign="center">
                            <Text variant="bodySm" fontWeight="semibold" color="subdued">POSTURE SCORE</Text>
                            <GapHint gaps={dataGaps} />
                        </HorizontalStack>
                        {bandLabel && <Badge status={bandTone}>{bandLabel}</Badge>}
                    </HorizontalStack>
                    {hasValue ? (
                        <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                            <HorizontalStack gap="2" blockAlign="baseline" wrap={false}>
                                <Text variant="heading3xl">{Math.round(value)}</Text>
                                <Text variant="headingMd" color="subdued">/100</Text>
                            </HorizontalStack>
                            <Sparkline points={trend} tone={bandTone} />
                        </HorizontalStack>
                    ) : (
                        <Text variant="heading2xl" color="subdued">Not computed yet</Text>
                    )}
                    {hasDelta && (
                        <HorizontalStack gap="1" blockAlign="center">
                            <Box><Icon source={deltaPositive ? ArrowUpMinor : ArrowDownMinor} color={TONE_TEXT_COLOR[deltaTone] || 'subdued'} /></Box>
                            <Text variant="bodyMd" fontWeight="semibold" color={TONE_TEXT_COLOR[deltaTone] || 'subdued'}>
                                {deltaPositive ? '+' : ''}{delta} pts vs last week
                            </Text>
                        </HorizontalStack>
                    )}
                    {hasValue && agentsScored > 0 && (
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="bodySm" color="subdued">
                                {agentsWithNoSignal > 0
                                    ? `Based on ${agentsScored - agentsWithNoSignal} of ${agentsScored} agents`
                                    : `Based on ${agentsScored} agent${agentsScored === 1 ? '' : 's'}`}
                            </Text>
                            {clickable && <Button plain>How is this calculated?</Button>}
                        </HorizontalStack>
                    )}
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default PostureScoreCard

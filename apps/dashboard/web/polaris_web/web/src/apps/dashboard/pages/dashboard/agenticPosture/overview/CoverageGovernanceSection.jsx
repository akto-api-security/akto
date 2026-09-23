import { Box, Card, HorizontalGrid, HorizontalStack, ProgressBar, Text, VerticalStack } from '@shopify/polaris'
import './CoverageGovernanceSection.css'

// bad < 70, average 70-84, good >= 85 — first-pass thresholds, easy to retune once real accounts
// show where they should sit (same spirit as riskBand's own thresholds in agenticPostureShared).
function rating(value) {
    if (value >= 85) return 'good'
    if (value >= 70) return 'average'
    return 'bad'
}

const RATING_COLOR = { good: 'success', average: 'highlight', bad: 'critical' }

// One metric row — label, percentage, real Polaris ProgressBar (not the app's hand-styled
// CustomProgressBar, see the posture plan's component audit). Colored red/orange/green by
// bad/average/good — "average" needs a small scoped CSS override since Polaris's ProgressBar has
// no orange option of its own (see CoverageGovernanceSection.css).
function MetricRow({ metric }) {
    const metricRating = rating(metric.value)
    return (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between" blockAlign="baseline">
                <Text variant="bodyMd">{metric.label}</Text>
                <Text variant="bodyMd" fontWeight="semibold">{metric.value}%</Text>
            </HorizontalStack>
            <div className={metricRating === 'average' ? 'coverage-progress-average' : undefined}>
                <ProgressBar progress={metric.value} size="small" color={RATING_COLOR[metricRating]} />
            </div>
        </VerticalStack>
    )
}

// Every metric is sample data right now — rendered plainly, "permission visibility" included,
// since there's no real-vs-not-yet-wired distinction to draw yet (see the posture plan).
function CoverageGovernanceSection({ coverageGovernance }) {
    const metrics = coverageGovernance || []
    return (
        <Card>
            <Box padding="4">
                <HorizontalGrid columns={3} gap="4">
                    {metrics.map((metric) => {
                        const display = metric.status === 'COMING_SOON' ? { ...metric, ...metric.illustrative } : metric
                        return <MetricRow key={metric.id} metric={display} />
                    })}
                </HorizontalGrid>
            </Box>
        </Card>
    )
}

export default CoverageGovernanceSection

import { Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import CustomProgressBar from '../../new_components/CustomProgressBar'
import { DummyDataOverlay } from '../../agenticPostureShared'

// Same red/amber/green triad SecurityPosture.jsx's own Framework readiness card uses
// (colorForReadiness) — kept local rather than imported since Atlas/Argus don't share a
// components folder (see agenticPostureShared.jsx's own note on why).
function colorForReadiness(value) {
    if (value >= 75) return '#23C48C'
    if (value >= 40) return '#F2B322'
    return '#F24122'
}

function FrameworkRow({ row }) {
    return (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between">
                <Text variant="bodyMd">{row.framework}</Text>
                <Text variant="bodyMd" fontWeight="semibold">{row.value}%</Text>
            </HorizontalStack>
            <CustomProgressBar progress={row.value} topColor={colorForReadiness(row.value)} height="10px" />
        </VerticalStack>
    )
}

// Shown blurred (via DummyDataOverlay) when there are no matching violations yet — illustrative
// only, same idiom as SecurityPosture.jsx's own DUMMY_FRAMEWORK_READINESS.
const DUMMY_FRAMEWORK_READINESS = [
    { framework: 'OWASP LLM', value: 10 },
    { framework: 'NIST AI Risk Management Framework', value: 25 },
]

// Computed live (server-side) from real prompt-injection/harmful-category violations matched
// against a fixed control->framework map — not an LLM scan, so there's nothing to "run" or go
// stale here, unlike Atlas's own Framework Readiness card.
function FrameworkReadinessSection({ frameworkReadiness }) {
    const rows = frameworkReadiness?.frameworks || []
    const hasData = rows.length > 0
    const effectiveRows = hasData ? rows : DUMMY_FRAMEWORK_READINESS

    const body = (
        <Card>
            <Box padding="4">
                <VerticalStack gap="3">
                    {effectiveRows.map((row) => (
                        <FrameworkRow key={row.framework} row={row} />
                    ))}
                </VerticalStack>
            </Box>
        </Card>
    )

    if (!hasData) {
        return (
            <DummyDataOverlay
                panelId="frameworkReadiness"
                copy="No prompt-injection or harmful-category violations in this window yet."
            >
                {body}
            </DummyDataOverlay>
        )
    }
    return body
}

export default FrameworkReadinessSection

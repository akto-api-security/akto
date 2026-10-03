import { Box, Card, HorizontalGrid, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import { CircleAlertMajor } from '@shopify/polaris-icons'

const SCAN_TYPE_LABEL = {
    ONE_TIME: 'Self scan',
    RECURRING: 'Scheduled scan',
    CONTINUOUS_TESTING: 'Continuous scan',
    CI_CD: 'CI/CD scan',
}

function StatTile({ label, value }) {
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="1">
                    <Text variant="bodySm" color="subdued">{label}</Text>
                    <Text variant="heading2xl">{value}</Text>
                </VerticalStack>
            </Box>
        </Card>
    )
}

// Invocation counts + "last scanned" are real (the last-scanned fact reuses existing red-team
// scan-run metadata). The volume-anomaly card is illustrative — no per-agent invocation-volume
// counter exists yet to detect a real spike (see the posture plan's Phase 4).
function RuntimeActivitySection({ runtimeActivity }) {
    if (!runtimeActivity) return null
    const scanType = runtimeActivity.lastScanned ? SCAN_TYPE_LABEL[runtimeActivity.lastScanned.type] || runtimeActivity.lastScanned.type : null
    return (
        <VerticalStack gap="4">
            <HorizontalGrid columns={3} gap="3">
                <StatTile label="Invocations, 30d" value={runtimeActivity.invocations30d?.toLocaleString()} />
                <StatTile label={runtimeActivity.actionsIssuedLabel || 'Actions issued'} value={runtimeActivity.actionsIssued?.toLocaleString()} />
                <StatTile label={runtimeActivity.largestActionLabel || 'Largest single action'} value={runtimeActivity.largestAction} />
            </HorizontalGrid>
            {scanType && (
                <Text variant="bodySm" color="subdued">
                    Last scanned · {scanType} · {runtimeActivity.lastScanned.when}
                </Text>
            )}
            {runtimeActivity.anomaly && (
                <Card>
                    <Box padding="4">
                        <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                            <Box><Icon source={CircleAlertMajor} color="critical" /></Box>
                            <VerticalStack gap="0">
                                <Text variant="bodyMd" fontWeight="semibold">{runtimeActivity.anomaly.illustrative?.headline}</Text>
                                <Text variant="bodySm" color="subdued">{runtimeActivity.anomaly.illustrative?.detail}</Text>
                            </VerticalStack>
                        </HorizontalStack>
                    </Box>
                </Card>
            )}
        </VerticalStack>
    )
}

export default RuntimeActivitySection

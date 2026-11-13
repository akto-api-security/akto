import { Box, Card, HorizontalStack, ProgressBar, Text, VerticalStack } from '@shopify/polaris'

const percent = (part, total) => (total > 0 ? Math.round((part / total) * 100) : 0)

function Share({ label, value, total, note }) {
    return (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between" blockAlign="center">
                <Text variant="bodyMd">{label}</Text>
                <Text variant="bodyMd" fontWeight="semibold">{note ?? `${value.toLocaleString()} of ${total.toLocaleString()}`}</Text>
            </HorizontalStack>
            <ProgressBar progress={percent(value, total)} size="small" color="primary" animated={false} />
        </VerticalStack>
    )
}

// Where the assets are found (the source of their traffic) and how many of them have a guardrail policy.
export function PlatformsCard({ platforms }) {
    return (
        <Card padding="4">
            <VerticalStack gap="3">
                <Text variant="headingMd" as="h2">Assets by platform</Text>
                {platforms.length === 0 && <Text variant="bodyMd" color="subdued">No assets found yet.</Text>}
                {platforms.map((platform) => (
                    <Share
                        key={platform.name}
                        label={platform.name}
                        value={platform.withPolicy}
                        total={platform.total}
                        note={`${platform.total.toLocaleString()} · ${platform.withPolicy.toLocaleString()} with a policy`}
                    />
                ))}
            </VerticalStack>
        </Card>
    )
}

// How much of what was found is actually seen and guarded.
export function CoverageCard({ coverage }) {
    const { assets, withTraffic, withPolicy } = coverage
    return (
        <Card padding="4">
            <VerticalStack gap="3">
                <Text variant="headingMd" as="h2">Coverage</Text>
                <Share label="Seen in traffic in the last 30 days" value={withTraffic} total={assets} />
                <Share label="Covered by a guardrail policy" value={withPolicy} total={assets} />
                <Box>
                    <Text variant="bodySm" color="subdued">
                        Assets with no recent traffic were found from configuration only. Add the Argus sensor to see how they behave.
                    </Text>
                </Box>
            </VerticalStack>
        </Card>
    )
}

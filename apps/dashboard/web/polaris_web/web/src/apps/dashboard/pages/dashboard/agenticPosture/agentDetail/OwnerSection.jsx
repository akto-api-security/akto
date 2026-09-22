import { Avatar, Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'

function initialsOf(name) {
    if (!name) return '?'
    return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
}

// Owner name/team are real (the existing agent-owner tag); Slack channel/on-call have no data
// source anywhere in the codebase today (verified) — an explicit gap, not a "Coming soon" phase,
// since closing it needs a new integration or manual-tagging feature outside this plan's scope.
function OwnerSection({ owner }) {
    if (!owner) return null
    const hasOwner = !!owner.name && owner.name !== 'Owner not mapped'
    return (
        <Card>
            <Box padding="4">
                <HorizontalStack gap="4" blockAlign="center" wrap>
                    <Avatar initials={initialsOf(owner.name)} name={owner.name || 'Unknown'} />
                    <VerticalStack gap="0">
                        <Text variant="bodyMd" fontWeight="semibold" color={hasOwner ? undefined : 'subdued'}>
                            {owner.name || 'Owner not mapped'}
                        </Text>
                        {owner.team && <Text variant="bodySm" color="subdued">{owner.team}</Text>}
                    </VerticalStack>
                    {owner.slack && <Text variant="bodySm" color="subdued">Slack · {owner.slack}</Text>}
                    {owner.onCall && <Text variant="bodySm" color="subdued">On-call · {owner.onCall}</Text>}
                    {!owner.slack && !owner.onCall && (
                        <Text variant="bodySm" color="subdued">Slack channel / on-call rotation — not available (no integration configured)</Text>
                    )}
                </HorizontalStack>
            </Box>
        </Card>
    )
}

export default OwnerSection

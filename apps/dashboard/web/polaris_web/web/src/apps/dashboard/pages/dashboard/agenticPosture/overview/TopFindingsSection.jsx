import { Box, Card, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'

// One finding — severity + title, an "Open agent page" link when the finding resolves cleanly to
// a known agent, metadata row, and the two-part Why it matters / Remediation prose the mockup uses.
function FindingCard({ finding, onOpenAgent }) {
    return (
        <Box paddingBlockStart="4" paddingBlockEnd="4" borderBlockEndWidth="1" borderColor="border">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="start">
                    <HorizontalStack gap="2" blockAlign="center">
                        <SeverityBadge severity={finding.severity} />
                        <Text variant="bodyMd" fontWeight="semibold">{finding.title}</Text>
                    </HorizontalStack>
                    {finding.groupKey && (
                        <Link onClick={() => onOpenAgent(finding.groupKey)} removeUnderline>
                            Open agent page →
                        </Link>
                    )}
                </HorizontalStack>
                <HorizontalStack gap="4">
                    {finding.groupKey && <Text variant="bodySm" color="subdued">Agent · {finding.groupKey}</Text>}
                    {finding.environment && <Text variant="bodySm" color="subdued">Environment · {finding.environment}</Text>}
                    {finding.owner && <Text variant="bodySm" color="subdued">Owner · {finding.owner}</Text>}
                    {finding.resource && <Text variant="bodySm" color="subdued">Resource · {finding.resource}</Text>}
                </HorizontalStack>
                {finding.whyItMatters && (
                    <Text variant="bodySm" color="subdued">
                        <Text as="span" fontWeight="semibold">Why it matters — </Text>
                        {finding.whyItMatters}
                    </Text>
                )}
                {finding.remediation && (
                    <Text variant="bodySm" color="subdued">
                        <Text as="span" fontWeight="semibold">Remediation — </Text>
                        {finding.remediation}
                    </Text>
                )}
            </VerticalStack>
        </Box>
    )
}

function TopFindingsSection({ topFindings, onOpenAgent }) {
    const findings = topFindings || []
    if (findings.length === 0) {
        return (
            <Card>
                <Box padding="4">
                    <Text variant="bodyMd" color="subdued" alignment="center">No findings in this window.</Text>
                </Box>
            </Card>
        )
    }
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="0">
                    {findings.map((finding) => (
                        <FindingCard key={finding.id} finding={finding} onOpenAgent={onOpenAgent} />
                    ))}
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default TopFindingsSection

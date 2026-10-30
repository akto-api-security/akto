import { Box, Card, HorizontalGrid, Link, Text } from "@shopify/polaris";
import { OverviewCardHeader, OverviewListRow } from "./OverviewCard";

const NOISY_IGNORE_PCT = 40;
const COLUMNS = "minmax(0, 1.3fr) minmax(0, 1fr) 80px 90px";

export default function PolicyViolationsCard({ policies, onPolicyClick, onTune }) {
    const max = Math.max(1, ...policies.map((p) => p.count));
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader title="Violations by policy" subtitle="High ignore rates usually mean a policy needs tuning" />
            </Box>
            <OverviewListRow>
                <HorizontalGrid columns={COLUMNS} gap="2">
                    <Text variant="bodySm" color="subdued">Policy</Text>
                    <Text variant="bodySm" color="subdued">Violations</Text>
                    <Text variant="bodySm" color="subdued">Ignored</Text>
                    <span />
                </HorizontalGrid>
            </OverviewListRow>
            {policies.map((policy) => {
                const noisy = policy.ignoredPct >= NOISY_IGNORE_PCT;
                return (
                    <OverviewListRow key={policy.name}>
                        <HorizontalGrid columns={COLUMNS} gap="2" alignItems="center">
                            <Link monochrome removeUnderline onClick={() => onPolicyClick(policy)}>
                                <Text variant="bodyMd" as="span" truncate>{policy.name}</Text>
                            </Link>
                            <HorizontalGrid columns="1fr 36px" gap="2" alignItems="center">
                                <Box background="bg-inset" borderRadius="1" minHeight="6px">
                                    <Box background="bg-magic-strong" borderRadius="1" minHeight="6px" width={`${Math.round((policy.count / max) * 100)}%`} />
                                </Box>
                                <Text variant="bodyMd" alignment="end">{policy.count}</Text>
                            </HorizontalGrid>
                            <Text variant="bodyMd" color={noisy ? "warning" : "subdued"} fontWeight={noisy ? "semibold" : "regular"}>{`${policy.ignoredPct}%`}</Text>
                            {noisy ? <Link onClick={() => onTune(policy)}>Tune policy</Link> : <span />}
                        </HorizontalGrid>
                    </OverviewListRow>
                );
            })}
        </Card>
    );
}

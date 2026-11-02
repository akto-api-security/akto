import { Banner, Box, Card, HorizontalStack, Link, Text, VerticalStack } from "@shopify/polaris";
import { OverviewCardHeader } from "./OverviewCard";

const BAR_COLOR = {
    OPEN: "bg-inverse-active",
    IN_REVIEW: "bg-info-strong",
    ASSIGNED: "bg-magic-strong",
    TICKETED: "bg-interactive",
    IGNORED: "bg-strong",
    RESOLVED: "bg-success-strong",
};

export default function TriageStatusCard({ triage, periodDays, onStatusClick, onAssign }) {
    const max = Math.max(1, ...triage.statuses.map((s) => s.count));
    const stale = triage.staleCriticalUnassigned;
    const hours = triage.staleAfterHours;
    return (
        <Card>
            <VerticalStack gap="3">
                <OverviewCardHeader title="Triage status" subtitle={`All violations in the last ${periodDays} days`} />
                {triage.statuses.map((status) => (
                    <div key={status.key} onClick={() => onStatusClick(status)} style={{ cursor: "pointer" }}>
                        <VerticalStack gap="1">
                            <HorizontalStack align="space-between">
                                <Text variant="bodyMd">{status.label}</Text>
                                <Text variant="bodyMd" fontWeight="semibold">{status.count}</Text>
                            </HorizontalStack>
                            <Box background="bg-inset" borderRadius="1" minHeight="6px">
                                <Box background={BAR_COLOR[status.key]} borderRadius="1" minHeight="6px" width={`${Math.round((status.count / max) * 100)}%`} />
                            </Box>
                        </VerticalStack>
                    </div>
                ))}
                {stale > 0 && (
                    <Banner status="warning">
                        {`${stale} critical ${stale === 1 ? "violation has" : "violations have"} no owner and ${stale === 1 ? "is" : "are"} older than ${hours} ${hours === 1 ? "hour" : "hours"}. `}
                        <Link onClick={onAssign}>Assign now</Link>
                    </Banner>
                )}
            </VerticalStack>
        </Card>
    );
}

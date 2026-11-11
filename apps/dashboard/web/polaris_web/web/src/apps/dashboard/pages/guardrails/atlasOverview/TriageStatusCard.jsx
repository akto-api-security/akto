import { Box, Card, HorizontalStack, Icon, Link, Text, VerticalStack } from "@shopify/polaris";
import { DiamondAlertMajor } from "@shopify/polaris-icons";
import { OverviewCardHeader } from "./OverviewCard";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";

const BAR_COLOR = {
    OPEN: "bg-inverse-active",
    IN_REVIEW: "bg-info-strong",
    ASSIGNED: "bg-magic-strong",
    TICKETED: "bg-interactive",
    IGNORED: "bg-strong",
    RESOLVED: "bg-success-strong",
};

export default function TriageStatusCard({ triage, periodDays, loading, onStatusClick, onAssign }) {
    if (loading) return <Card><VerticalStack gap="3"><OverviewCardHeader title="Triage status" /><SpinnerCentered height="200px" /></VerticalStack></Card>;

    const max = Math.max(1, ...triage.statuses.map((s) => s.count));
    const stale = triage.staleCriticalUnassigned;
    const hours = triage.staleAfterHours;
    return (
        <Card>
            <VerticalStack gap="3">
                <OverviewCardHeader title="Triage status" subtitle={periodDays ? `All violations in the last ${periodDays} ${periodDays === 1 ? "day" : "days"}` : "All violations"} />
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
                    <Box background="bg-caution-subdued" padding="3" borderRadius="2">
                        <HorizontalStack gap="2" blockAlign="start" wrap={false}>
                            <Box>
                                <Icon source={DiamondAlertMajor} color="warning" />
                            </Box>
                            <Text variant="bodyMd" as="p">
                                <Text as="span" fontWeight="semibold">{stale}</Text>
                                {` critical ${stale === 1 ? "violation has" : "violations have"} no owner and ${stale === 1 ? "is" : "are"} older than `}
                                <Text as="span" fontWeight="semibold">{`${hours} ${hours === 1 ? "hour" : "hours"}`}</Text>
                                {". "}
                                <Text as="span" fontWeight="semibold">
                                    <Link onClick={onAssign} removeUnderline>Assign now</Link>
                                </Text>
                            </Text>
                        </HorizontalStack>
                    </Box>
                )}
            </VerticalStack>
        </Card>
    );
}

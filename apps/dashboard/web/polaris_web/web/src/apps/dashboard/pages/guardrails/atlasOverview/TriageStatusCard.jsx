import { Box, HorizontalStack, Link, Text, VerticalStack } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import CalloutStrip from "@/apps/dashboard/components/shared/CalloutStrip";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { formatCount } from "./atlasOverviewUtils";

const BAR_COLOR = {
    OPEN: "bg-inverse-active",
    IN_REVIEW: "bg-info-strong",
    ASSIGNED: "bg-magic-strong",
    TICKETED: "bg-interactive",
    IGNORED: "bg-strong",
    RESOLVED: "bg-success-strong",
};

export default function TriageStatusCard({ triage, onStatusClick, onAssign }) {
    const statuses = triage?.statuses || [];
    const max = Math.max(1, ...statuses.map((s) => s.count));
    const stale = triage?.staleCriticalUnassigned || 0;
    const hours = triage?.staleAfterHours;
    const periodDays = triage?.periodDays;
    return (
        <CardWithHeader
            title="Triage status"
            tooltipContent={periodDays ? `All violations in the last ${periodDays} ${periodDays === 1 ? "day" : "days"}` : "All violations"}
            hasData
        >
            {triage === null ? <SpinnerCentered height="200px" /> : (
                <VerticalStack gap="3">
                    {statuses.map((status) => (
                        <div key={status.key} onClick={() => onStatusClick(status)} style={{ cursor: "pointer" }}>
                            <VerticalStack gap="1">
                                <HorizontalStack align="space-between">
                                    <Text variant="bodyMd">{status.label}</Text>
                                    <Text variant="bodyMd" fontWeight="semibold">{formatCount(status.count)}</Text>
                                </HorizontalStack>
                                <Box background="bg-inset" borderRadius="1" minHeight="6px">
                                    <Box background={BAR_COLOR[status.key]} borderRadius="1" minHeight="6px" width={`${Math.round((status.count / max) * 100)}%`} />
                                </Box>
                            </VerticalStack>
                        </div>
                    ))}
                    {stale > 0 && (
                        <CalloutStrip tone="warning">
                            <Text as="span" fontWeight="semibold">{formatCount(stale)}</Text>
                            {` critical ${stale === 1 ? "violation has" : "violations have"} no owner and ${stale === 1 ? "is" : "are"} older than `}
                            <Text as="span" fontWeight="semibold">{`${hours} ${hours === 1 ? "hour" : "hours"}`}</Text>
                            {". "}
                            <Text as="span" fontWeight="semibold">
                                <Link onClick={onAssign} removeUnderline>Assign now</Link>
                            </Text>
                        </CalloutStrip>
                    )}
                </VerticalStack>
            )}
        </CardWithHeader>
    );
}

import { Box, HorizontalStack, Link, Text, VerticalStack } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import CalloutStrip from "@/apps/dashboard/components/shared/CalloutStrip";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { OverviewLoadError } from "./OverviewCard";
import { formatCount } from "./atlasOverviewUtils";
import { ACTIVITY_TAB, CRITICAL_SEVERITY } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";

const BAR_COLOR = {
    OPEN: "bg-critical-strong",
    IN_REVIEW: "bg-caution-strong",
    ASSIGNED: "bg-info-strong",
    TICKETED: "bg-magic-strong",
    IGNORED: "bg-inverse-active",
};
const STATUS_EVENT_STATUS = {
    OPEN: ACTIVITY_TAB.ACTIVE,
    IN_REVIEW: ACTIVITY_TAB.UNDER_REVIEW,
    IGNORED: ACTIVITY_TAB.IGNORED,
};

export default function TriageStatusCard({ triage, onOpenActivity }) {
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
            {triage === null ? <SpinnerCentered height="200px" /> : triage?.failed ? <OverviewLoadError /> : (
                <VerticalStack gap="5">
                    {statuses.map((status) => (
                        <div key={status.key} onClick={() => onOpenActivity({ eventStatus: STATUS_EVENT_STATUS[status.key] })} style={{ cursor: "pointer" }}>
                            <VerticalStack gap="1">
                                <HorizontalStack align="space-between">
                                    <Text variant="bodyMd">{status.label}</Text>
                                    <Text variant="bodyMd" fontWeight="semibold">{formatCount(status.count)}</Text>
                                </HorizontalStack>
                                <Box background="bg-inset" borderRadius="1" minHeight="6px">
                                    <Box background={BAR_COLOR[status.key]} borderRadius="1" minHeight="6px" width={status.count > 0 ? `max(6px, ${(status.count / max) * 100}%)` : "0"} />
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
                                <Link onClick={() => onOpenActivity({ eventStatus: ACTIVITY_TAB.ACTIVE, severity: CRITICAL_SEVERITY })} removeUnderline>Assign now</Link>
                            </Text>
                        </CalloutStrip>
                    )}
                </VerticalStack>
            )}
        </CardWithHeader>
    );
}

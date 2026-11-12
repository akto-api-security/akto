import { Box, HorizontalStack, Link, Text, VerticalStack } from "@shopify/polaris";
import CalloutStrip from "@/apps/dashboard/components/shared/CalloutStrip";
import DiscoveryCard from "../../observe/atlas/discovery/shared/DiscoveryCard";
import { LOAD_STATE } from "../../observe/atlas/discovery/constants";
import observeFunc from "../../observe/transform";

const BAR_COLOR = {
    OPEN: "bg-inverse-active",
    IN_REVIEW: "bg-info-strong",
    ASSIGNED: "bg-magic-strong",
    TICKETED: "bg-interactive",
    IGNORED: "bg-strong",
    RESOLVED: "bg-success-strong",
};

export default function TriageStatusCard({ triage, periodDays, loading, onStatusClick, onAssign }) {
    const max = Math.max(1, ...triage.statuses.map((s) => s.count));
    const stale = triage.staleCriticalUnassigned;
    const hours = triage.staleAfterHours;
    return (
        <DiscoveryCard title="Triage status" subtitle={periodDays ? `All violations in the last ${periodDays} ${periodDays === 1 ? "day" : "days"}` : "All violations"} status={loading ? LOAD_STATE.LOADING : LOAD_STATE.READY}>
            <>
                {triage.statuses.map((status) => (
                    <div key={status.key} onClick={() => onStatusClick(status)} style={{ cursor: "pointer" }}>
                        <VerticalStack gap="1">
                            <HorizontalStack align="space-between">
                                <Text variant="bodyMd">{status.label}</Text>
                                <Text variant="bodyMd" fontWeight="semibold">{observeFunc.formatNumberWithCommas(status.count)}</Text>
                            </HorizontalStack>
                            <Box background="bg-inset" borderRadius="1" minHeight="6px">
                                <Box background={BAR_COLOR[status.key]} borderRadius="1" minHeight="6px" width={`${Math.round((status.count / max) * 100)}%`} />
                            </Box>
                        </VerticalStack>
                    </div>
                ))}
                {stale > 0 && (
                    <CalloutStrip tone="warning">
                        <Text as="span" fontWeight="semibold">{observeFunc.formatNumberWithCommas(stale)}</Text>
                        {` critical ${stale === 1 ? "violation has" : "violations have"} no owner and ${stale === 1 ? "is" : "are"} older than `}
                        <Text as="span" fontWeight="semibold">{`${hours} ${hours === 1 ? "hour" : "hours"}`}</Text>
                        {". "}
                        <Text as="span" fontWeight="semibold">
                            <Link onClick={onAssign} removeUnderline>Assign now</Link>
                        </Text>
                    </CalloutStrip>
                )}
            </>
        </DiscoveryCard>
    );
}

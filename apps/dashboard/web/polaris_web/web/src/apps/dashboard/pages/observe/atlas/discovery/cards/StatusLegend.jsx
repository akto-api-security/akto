import { HorizontalStack, Text } from "@shopify/polaris";
import { LegendDot } from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import { STATUS_META, STATUS_ORDER } from "../constants";

// Colour key for a chart coloured by tool status; statuses and colors override the defaults.
export default function StatusLegend({ statuses = STATUS_ORDER, colors }) {
    return (
        <HorizontalStack gap="3">
            {statuses.map((status) => (
                <HorizontalStack key={status} gap="1" blockAlign="center" wrap={false}>
                    <LegendDot color={colors ? colors[status] : STATUS_META[status].color} />
                    <Text variant="bodySm" color="subdued">{STATUS_META[status].label}</Text>
                </HorizontalStack>
            ))}
        </HorizontalStack>
    );
}

import { HorizontalStack, Text } from "@shopify/polaris";
import { LegendDot } from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import { CHAIN_LEGEND } from "../constants";

// Same cues the graph draws: node borders by status, and a red line for any path touching an unapproved tool.
export default function ChainLegend() {
    return (
        <HorizontalStack gap="3">
            {CHAIN_LEGEND.map((item) => (
                <HorizontalStack key={item.label} gap="1" blockAlign="center" wrap={false}>
                    <LegendDot color={item.color} />
                    <Text variant="bodySm" color="subdued">{item.label}</Text>
                </HorizontalStack>
            ))}
        </HorizontalStack>
    );
}

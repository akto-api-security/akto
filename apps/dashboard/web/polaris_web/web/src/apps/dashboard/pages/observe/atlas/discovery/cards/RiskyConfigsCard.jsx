import { Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import DiscoveryCard from "../shared/DiscoveryCard";
import { RISKY_CONFIG_META } from "../constants";

export default function RiskyConfigsCard({ rows, status }) {
    return (
        <DiscoveryCard title="Risky configurations" status={status}>
            <VerticalStack gap="2">
                {(rows || []).map((row) => {
                    const meta = RISKY_CONFIG_META[row.id];
                    return (
                        <Box key={row.id} padding="1">
                            <HorizontalStack align="space-between" gap="3" wrap={false}>
                                <VerticalStack gap="0">
                                    <Text as="p" fontWeight="medium">{meta.label}</Text>
                                    <Text as="p" variant="bodySm" color="subdued">{meta.sub}</Text>
                                </VerticalStack>
                                <Text as="span" fontWeight="semibold" color={row.count > 0 ? "critical" : undefined}>{row.count}</Text>
                            </HorizontalStack>
                        </Box>
                    );
                })}
            </VerticalStack>
        </DiscoveryCard>
    );
}

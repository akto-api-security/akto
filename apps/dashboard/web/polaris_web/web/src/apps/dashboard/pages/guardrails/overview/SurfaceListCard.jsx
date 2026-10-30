import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { OverviewCardHeader, OverviewListRow } from "./OverviewCard";

function formatChange(pct) {
    return pct > 0 ? `+${pct}%` : `−${Math.abs(pct)}%`;
}

export default function SurfaceListCard({ surfaces, onSurfaceClick }) {
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader title="Where violations happen" />
            </Box>
            {surfaces.map((surface) => (
                <OverviewListRow key={surface.name} onClick={() => onSurfaceClick(surface)}>
                    <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="2">
                        <VerticalStack gap="0">
                            <Text variant="bodyMd" fontWeight="medium">{surface.name}</Text>
                            <Text variant="bodySm" color="subdued">{surface.examples}</Text>
                        </VerticalStack>
                        <VerticalStack gap="0" inlineAlign="end">
                            <Text variant="bodyMd" fontWeight="semibold">{surface.count}</Text>
                            <Text variant="bodySm" color={surface.changePct > 0 ? "critical" : "success"}>{`${formatChange(surface.changePct)} vs last week`}</Text>
                        </VerticalStack>
                    </HorizontalStack>
                </OverviewListRow>
            ))}
        </Card>
    );
}

import { Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import SeverityPill from "@/apps/dashboard/components/shared/SeverityPill";
import CompactButton from "@/apps/dashboard/components/shared/CompactButton";
import { OverviewCardHeader, OverviewListRow } from "./OverviewCard";

export default function AttentionCard({ rows, onReview, onViewAll }) {
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader
                    title="Needs your attention"
                    subtitle="Grouped by root cause, ranked by severity and reach"
                    action={<Button plain onClick={onViewAll}>View all</Button>}
                />
            </Box>
            {rows.map((row) => (
                <OverviewListRow key={row.title}>
                    <Box paddingBlockStart="1" paddingBlockEnd="1">
                        <HorizontalGrid columns="80px minmax(0, 1fr) auto" gap="3" alignItems="center">
                            <SeverityPill severity={row.severity} />
                            <VerticalStack gap="0">
                                <Text variant="bodyMd" fontWeight="semibold">{row.title}</Text>
                                <Text variant="bodyMd" color="subdued">{row.meta}</Text>
                            </VerticalStack>
                            <HorizontalStack gap="2" wrap={false}>
                                <CompactButton>{row.secondaryAction}</CompactButton>
                                <CompactButton onClick={() => onReview(row)}>{`Review ${row.count}`}</CompactButton>
                            </HorizontalStack>
                        </HorizontalGrid>
                    </Box>
                </OverviewListRow>
            ))}
        </Card>
    );
}

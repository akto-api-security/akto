import { Box, Button, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import SeverityPill from "@/apps/dashboard/components/shared/SeverityPill";
import CompactButton from "@/apps/dashboard/components/shared/CompactButton";
import { ComingSoonOverlay } from "@/apps/dashboard/pages/dashboard/agenticPostureShared";
import { OverviewListRow } from "./OverviewCard";

export default function AttentionCard({ rows, onReview, onViewAll }) {
    return (
        <CardWithHeader
            title="Needs your attention"
            tooltipContent="Grouped by root cause, ranked by severity and reach"
            headerAction={<Button plain onClick={onViewAll}>View all</Button>}
            hasData
        >
            <ComingSoonOverlay panelId="needsAttention">
            <Box>
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
            </Box>
            </ComingSoonOverlay>
        </CardWithHeader>
    );
}

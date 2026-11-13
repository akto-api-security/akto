import { Box, Button, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import SeverityPill from "@/apps/dashboard/components/shared/SeverityPill";
import CompactButton from "@/apps/dashboard/components/shared/CompactButton";
import { THREAT_ACTIVITY_PATH } from "@/apps/dashboard/pages/threat_detection/utils/threatDashboardUtils";
import { OverviewListRow, OverviewLoadError } from "./OverviewCard";
import { formatCount } from "./atlasOverviewUtils";

export default function AttentionCard({ data, onOpenActivity, onOpenPolicy, onViewAll }) {
    const rows = data?.rows || [];
    const openCta = (cta) => (cta.route === THREAT_ACTIVITY_PATH ? onOpenActivity(cta.params || {}) : onOpenPolicy(cta.params?.policy));
    return (
        <CardWithHeader
            title="Needs your attention"
            tooltipContent="Grouped by root cause"
            headerAction={<Button plain onClick={onViewAll}>View all</Button>}
            hasData
        >
            {data === null ? <SpinnerCentered height="200px" />
                : data?.failed ? <OverviewLoadError />
                : rows.length === 0 ? <Text variant="bodyMd" color="subdued">Nothing needs your attention in this period.</Text>
                : (
                    <Box>
                        {rows.map((row) => {
                            const review = (row.ctas || []).find((cta) => cta.primary);
                            const secondary = (row.ctas || []).find((cta) => !cta.primary);
                            return (
                                <OverviewListRow key={row.id}>
                                    <Box paddingBlockStart="1" paddingBlockEnd="1">
                                        <HorizontalGrid columns="80px minmax(0, 1fr) auto" gap="3" alignItems="center">
                                            <SeverityPill severity={row.severity} />
                                            <VerticalStack gap="0">
                                                <Text variant="bodyMd" fontWeight="semibold">{row.title}</Text>
                                                <Text variant="bodyMd" color="subdued">{row.meta}</Text>
                                            </VerticalStack>
                                            <HorizontalStack gap="2" wrap={false}>
                                                {secondary && <CompactButton onClick={() => openCta(secondary)}>{secondary.label}</CompactButton>}
                                                {review && <CompactButton onClick={() => openCta(review)}>{`Review ${formatCount(row.count)}`}</CompactButton>}
                                            </HorizontalStack>
                                        </HorizontalGrid>
                                    </Box>
                                </OverviewListRow>
                            );
                        })}
                    </Box>
                )}
        </CardWithHeader>
    );
}

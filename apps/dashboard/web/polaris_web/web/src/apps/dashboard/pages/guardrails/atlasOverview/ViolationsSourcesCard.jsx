import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { OverviewCardHeader, OverviewListRow } from "./OverviewCard";

const TREND_UP_COLOR = "#8E1F0B";
const TREND_DOWN_COLOR = "#0C5132";

function formatChange(pct) {
    if (pct === 0) return "0%";
    return pct > 0 ? `+${pct}%` : `−${Math.abs(pct)}%`;
}

export default function ViolationsSourcesCard({ rows, periodDays, onRowClick }) {
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader title="Where violations happen" />
            </Box>
            {rows.map((row) => (
                <OverviewListRow key={row.name} onClick={() => onRowClick(row)}>
                    <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="2">
                        <VerticalStack gap="0">
                            <Text variant="bodyMd" fontWeight="medium">{row.name}</Text>
                            <Text variant="bodySm" color="subdued">{row.examples}</Text>
                        </VerticalStack>
                        <VerticalStack gap="0" inlineAlign="end">
                            <Text variant="bodyMd" fontWeight="semibold">{row.count}</Text>
                            {row.changePct !== undefined && row.changePct !== null && (
                                <Text variant="bodySm" color={row.changePct === 0 ? "subdued" : undefined}>
                                    <span style={{ whiteSpace: "nowrap", ...(row.changePct === 0 ? {} : { color: row.changePct > 0 ? TREND_UP_COLOR : TREND_DOWN_COLOR }) }}>
                                        {`${formatChange(row.changePct)} vs previous ${periodDays} ${periodDays === 1 ? "day" : "days"}`}
                                    </span>
                                </Text>
                            )}
                        </VerticalStack>
                    </HorizontalStack>
                </OverviewListRow>
            ))}
        </Card>
    );
}

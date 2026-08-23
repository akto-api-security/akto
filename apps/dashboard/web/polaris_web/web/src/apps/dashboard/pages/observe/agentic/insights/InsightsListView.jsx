import React, { useState, useMemo, useCallback } from "react";
import { Box, VerticalStack, HorizontalStack, Text, Badge, Tabs, Icon, Banner } from "@shopify/polaris";
import { ChevronRightMinor } from "@shopify/polaris-icons";
import { SeverityBadge } from "../AgenticCellRenderers";
import { STATUS_BADGE_STATUS, statusLabel, categoryLabel } from "./insightsHelpers";

const InsightRow = React.memo(function InsightRow({ insight, onSelect }) {
    const handleClick = useCallback(() => onSelect(insight.insightId), [insight.insightId, onSelect]);
    const headline = insight.headline || (insight.metrics && insight.metrics[0]?.formatted) || "";

    return (
        <Box
            className="insight-row"
            padding="4"
            borderBlockEndWidth="1"
            borderColor="border-subdued"
            onClick={handleClick}
        >
            <HorizontalStack align="space-between" blockAlign="center" gap="4" wrap={false}>
                <Box minWidth="0">
                    <VerticalStack gap="1">
                        <HorizontalStack gap="2" blockAlign="center" wrap>
                            <Text variant="bodyMd" fontWeight="semibold">{insight.title}</Text>
                            <Badge>{categoryLabel(insight.category)}</Badge>
                            {insight.severity ? <SeverityBadge severity={insight.severity} /> : null}
                        </HorizontalStack>
                        {headline ? (
                            <Text variant="bodySm" color="subdued">{headline}</Text>
                        ) : null}
                    </VerticalStack>
                </Box>
                <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                    <Badge status={STATUS_BADGE_STATUS[insight.status]}>{statusLabel(insight.status)}</Badge>
                    <Icon source={ChevronRightMinor} color="subdued" />
                </HorizontalStack>
            </HorizontalStack>
        </Box>
    );
});

const TABS = [
    { id: "all", content: "All", match: () => true },
    { id: "actionable", content: "Actionable", match: (i) => i.category === "ACTIONABLE" },
    { id: "read_only", content: "Read-only", match: (i) => i.category === "READ_ONLY" },
];

export default function InsightsListView({ insights, error, onSelect }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const handleTabChange = useCallback((index) => setSelectedTab(index), []);

    const tabs = useMemo(() => TABS.map((tab) => ({
        id: tab.id,
        content: `${tab.content} · ${insights.filter(tab.match).length}`,
    })), [insights]);

    const visibleInsights = useMemo(
        () => insights.filter(TABS[selectedTab].match),
        [insights, selectedTab]
    );

    if (error) {
        return (
            <Box padding="4">
                <Banner status="critical" title="Couldn't load Atlas Insights">
                    Something went wrong while computing these insights. Try again in a moment.
                </Banner>
            </Box>
        );
    }

    return (
        <Box style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
            <Box paddingInlineStart="4" paddingInlineEnd="4">
                <Tabs tabs={tabs} selected={selectedTab} onSelect={handleTabChange} />
            </Box>
            <Box style={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
                {visibleInsights.length === 0 ? (
                    <Box padding="8">
                        <Text variant="bodyMd" color="subdued" alignment="center">No insights in this category.</Text>
                    </Box>
                ) : (
                    visibleInsights.map((insight) => (
                        <InsightRow key={insight.insightId} insight={insight} onSelect={onSelect} />
                    ))
                )}
            </Box>
        </Box>
    );
}

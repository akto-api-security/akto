import React, { useState, useMemo, useCallback } from "react";
import { Box, VerticalStack, HorizontalStack, Text, Tabs, Icon, Banner, Badge } from "@shopify/polaris";
import { ChevronRightMinor } from "@shopify/polaris-icons";
import { SeverityBadge } from "../AgenticCellRenderers";
import "../../../../components/layouts/style.css";

const InsightRow = React.memo(function InsightRow({ insight, onSelect }) {
    // Backend-computed, straight from InsightId.disabled — a static "not wired up yet" toggle,
    // same for every account. NOT set for a confirmed-zero or NO_DATA result (those still render
    // normally with no severity badge — see InsightResult.disabled's own comment).
    const disabled = !!insight.disabled;
    const handleClick = useCallback(() => {
        if (disabled) return;
        onSelect(insight.insightId);
    }, [insight.insightId, onSelect, disabled]);
    const headline = insight.headline || (insight.metrics && insight.metrics[0]?.formatted) || "";
    const severity = String(insight.severity || "").toUpperCase();

    return (
        <Box
            className={`insight-row${disabled ? " insight-row-disabled" : ""}`}
            padding={"3"}
            onClick={handleClick}
        >
            <HorizontalStack gap="4" wrap={false}>
                <div className={`insight-severity-bar insight-severity-bar-${severity || "DEFAULT"}`} />
                <Box minWidth="0" style={{ flex: 1 }}>
                    <VerticalStack gap="2">
                        <HorizontalStack gap="2" blockAlign="center" wrap>
                            <Text variant="bodyMd" fontWeight="semibold" color={disabled ? "subdued" : undefined}>{insight.title}</Text>
                            {disabled ? <Badge>Coming soon</Badge> : (insight.severity ? <SeverityBadge severity={insight.severity} /> : null)}
                        </HorizontalStack>
                        {headline ? (
                            <Text variant="bodySm" color="subdued">{headline}</Text>
                        ) : null}
                    </VerticalStack>
                </Box>
                {!disabled && <Icon source={ChevronRightMinor} color="subdued" />}
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
            <Box padding="3">
                <Tabs tabs={tabs} selected={selectedTab} onSelect={handleTabChange} />
            </Box>
            <Box style={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
                {visibleInsights.length === 0 ? (
                    <Box padding="10">
                        <Text variant="bodyMd" color="subdued" alignment="center">No insights in this category.</Text>
                    </Box>
                ) : (
                    <VerticalStack gap={"3"}>
                        {visibleInsights.map((insight) => (
                            <InsightRow key={insight.insightId} insight={insight} onSelect={onSelect} />
                        ))}
                    </VerticalStack>
                )}
            </Box>
        </Box>
    );
}

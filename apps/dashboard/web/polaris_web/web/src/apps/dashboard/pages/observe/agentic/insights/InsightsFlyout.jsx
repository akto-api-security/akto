import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Box, VerticalStack, HorizontalStack, Text, Badge, Button, Spinner } from "@shopify/polaris";
import { ChevronLeftMinor, RefreshMinor, CancelMajor } from "@shopify/polaris-icons";
import AgenticFlyoutShell from "../AgenticFlyoutShell";
import { SeverityBadge } from "../AgenticCellRenderers";
import insightsApi from "./insightsApi";
import InsightsListView from "./InsightsListView";
import InsightDetailView from "./InsightDetailView";
import { STATUS_BADGE_STATUS, statusLabel, categoryLabel, INSIGHT_GROUP } from "./insightsHelpers";

// Atlas Insights — the "Insights" button's flyout. One AgenticFlyoutShell instance whose body
// switches between the list and a selected insight's detail; the header switches with it.
// `group` picks which InsightId.Group this instance shows — Atlas Discovery and the
// guardrail/violations set never mix in one list (see InsightService.listInsights).
export default function InsightsFlyout({ show, onClose, startTimestamp, endTimestamp, initialInsightId = null, group = INSIGHT_GROUP.ATLAS_DISCOVERY }) {
    const [insights, setInsights] = useState([]);
    const [listLoading, setListLoading] = useState(false);
    const [listError, setListError] = useState(false);
    const [selectedInsightId, setSelectedInsightId] = useState(null);

    const loadList = useCallback(() => {
        setListLoading(true);
        setListError(false);
        insightsApi.fetchInsightsList({ startTimestamp, endTimestamp, group })
            .then((data) => setInsights(data))
            .catch(() => setListError(true))
            .finally(() => setListLoading(false));
    }, [startTimestamp, endTimestamp, group]);

    useEffect(() => {
        if (!show) return;
        setSelectedInsightId(initialInsightId);
        loadList();
    }, [show, initialInsightId, loadList]);

    const handleSelectInsight = useCallback((insightId) => setSelectedInsightId(insightId), []);
    const handleBackToList = useCallback(() => setSelectedInsightId(null), []);

    const selectedInsight = useMemo(
        () => insights.find((i) => i.insightId === selectedInsightId) || null,
        [insights, selectedInsightId]
    );

    const isViolationsGroup = group === INSIGHT_GROUP.GUARDRAIL_VIOLATIONS;
    const flyoutTitle = isViolationsGroup ? "Guardrail Insights" : "Atlas Insights";
    const flyoutSubtitle = isViolationsGroup
        ? `governance signal${insights.length === 1 ? "" : "s"} across your guardrail policies`
        : `governance signal${insights.length === 1 ? "" : "s"} across your agentic AI surface`;

    const header = useMemo(() => (
        <Box padding="5" borderBlockEndWidth="1" borderColor="border-subdued">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="start" wrap={false}>
                    {selectedInsight ? (
                        <Button plain icon={ChevronLeftMinor} onClick={handleBackToList}>Back to Insights</Button>
                    ) : (
                        <VerticalStack gap="1">
                            <Text variant="headingMd" as="h2">{flyoutTitle}</Text>
                            <Text variant="bodySm" color="subdued">
                                {insights.length} {flyoutSubtitle}
                            </Text>
                        </VerticalStack>
                    )}
                    <HorizontalStack gap="1" wrap={false}>
                        {!selectedInsight && (
                            <Button plain icon={RefreshMinor} onClick={loadList} accessibilityLabel="Refresh insights" />
                        )}
                        <Button plain icon={CancelMajor} onClick={onClose} accessibilityLabel="Close" />
                    </HorizontalStack>
                </HorizontalStack>
                {selectedInsight && (
                    <HorizontalStack gap="2" blockAlign="center" wrap>
                        <Text variant="headingMd" as="h2">{selectedInsight.title}</Text>
                        <Badge status={STATUS_BADGE_STATUS[selectedInsight.status]}>{statusLabel(selectedInsight.status)}</Badge>
                        {selectedInsight.severity ? <SeverityBadge severity={selectedInsight.severity} /> : null}
                        <Badge>{categoryLabel(selectedInsight.category)}</Badge>
                    </HorizontalStack>
                )}
            </VerticalStack>
        </Box>
    ), [selectedInsight, insights.length, handleBackToList, loadList, onClose, flyoutTitle, flyoutSubtitle]);

    return (
        <AgenticFlyoutShell show={show} width={800} header={header}>
            {listLoading ? (
                <Box padding="8"><Spinner accessibilityLabel="Loading insights" size="large" /></Box>
            ) : selectedInsight ? (
                <InsightDetailView
                    insightId={selectedInsight.insightId}
                    startTimestamp={startTimestamp}
                    endTimestamp={endTimestamp}
                />
            ) : (
                <InsightsListView insights={insights} error={listError} onSelect={handleSelectInsight} />
            )}
        </AgenticFlyoutShell>
    );
}

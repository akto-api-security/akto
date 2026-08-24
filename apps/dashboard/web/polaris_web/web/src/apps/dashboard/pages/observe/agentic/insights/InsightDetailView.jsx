import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Box, VerticalStack, HorizontalStack, Text, Button, Banner, Spinner } from "@shopify/polaris";
import { RefreshMinor, ExternalMinor } from "@shopify/polaris-icons";
import MarkdownViewer from "@/apps/dashboard/components/shared/MarkdownViewer";
import GridRows from "@/apps/dashboard/components/shared/GridRows";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import insightsApi from "./insightsApi";
import InsightEvidenceTable from "./InsightEvidenceTable";
import { buildCtaHref } from "./insightsHelpers";
import "../../../../components/layouts/style.css";

// Metric stat card — same title/value card shape GridRows' other callers use (see
// TestRunResultFlyout's RowComp). value/label always come straight from InsightResult.metrics;
// `formatted` is the string the backend built and the narrative is validated against, so
// this component never reformats a number itself.
function MetricCardComp({ cardObj }) {
    const { title, value } = cardObj;
    return value ? (
        <Box>
            <VerticalStack gap="2">
                <TitleWithInfo textProps={{ variant: "bodySm", color: "subdued" }} titleText={title} />
                {value}
            </VerticalStack>
        </Box>
    ) : null;
}

export default function InsightDetailView({ insightId, startTimestamp, endTimestamp }) {
    const navigate = useNavigate();
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [regenerating, setRegenerating] = useState(false);

    // Shared unmount guard — InsightDetailView unmounts entirely when the user hits "Back to
    // Insights", and both the initial fetch and a still-in-flight Regenerate call must not
    // setState after that.
    const unmountedRef = useRef(false);
    useEffect(() => () => { unmountedRef.current = true; }, []);

    useEffect(() => {
        setLoading(true);
        setError(false);
        setDetail(null);
        insightsApi.fetchInsightDetail({ insightId, startTimestamp, endTimestamp })
            .then((data) => { if (!unmountedRef.current) setDetail(data); })
            .catch(() => { if (!unmountedRef.current) setError(true); })
            .finally(() => { if (!unmountedRef.current) setLoading(false); });
    }, [insightId, startTimestamp, endTimestamp]);

    const handleRegenerate = useCallback(() => {
        setRegenerating(true);
        insightsApi.refreshInsightNarrative({ insightId, startTimestamp, endTimestamp })
            .then((data) => { if (!unmountedRef.current) setDetail(data); })
            .catch(() => {})
            .finally(() => { if (!unmountedRef.current) setRegenerating(false); });
    }, [insightId, startTimestamp, endTimestamp]);

    const handleCtaClick = useCallback((cta) => {
        if (!cta?.route) return;
        if (cta.kind === "GUARDRAIL_TEMPLATE") {
            navigate(cta.route, { state: { topicGuardrailPrefill: cta.params || {} } });
        } else if (cta.href) {
            navigate(cta.href);
        }
    }, [navigate]);

    const ctas = useMemo(
        () => (detail?.ctas || []).map((cta) => ({ ...cta, href: buildCtaHref(cta) })),
        [detail?.ctas]
    );

    const metricItems = useMemo(
        () => (detail?.metrics || []).map((metric) => ({
            title: metric.label,
            value: <Text variant="headingLg" as="p">{metric.formatted}</Text>,
        })),
        [detail?.metrics]
    );

    if (loading) {
        return <Box padding="8"><Spinner accessibilityLabel="Loading insight" size="large" /></Box>;
    }

    if (error || !detail) {
        return (
            <Box padding="4">
                <Banner status="critical" title="Couldn't load this insight">
                    Something went wrong while computing it. Try again in a moment.
                </Banner>
            </Box>
        );
    }

    const hasNarrative = detail.narrativeStatus === "OK" && !!detail.markdown;

    return (
        <Box overflowY="scroll" padding="5">
            <VerticalStack gap="5">
                {metricItems.length > 0 && (
                    <GridRows columns={metricItems.size} items={metricItems} CardComponent={MetricCardComp} />
                )}

                <div className="chat-message-row">
                    <Box style={{ flex: 1, minWidth: 0 }}>
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="bodyMd" fontWeight="semibold" color="subdued">Akto AI Agent</Text>
                            <Button
                                plain
                                monochrome
                                icon={RefreshMinor}
                                loading={regenerating}
                                onClick={handleRegenerate}
                                accessibilityLabel="Regenerate narrative"
                            >
                                Regenerate
                            </Button>
                        </HorizontalStack>
                        {hasNarrative ? (
                            <MarkdownViewer markdown={detail.markdown} noPadding />
                        ) : (
                            <Box paddingBlockStart="2">
                                <Text variant="bodyMd" color="subdued">
                                    AI summary isn't available right now — the metrics and actions here are unaffected.
                                </Text>
                            </Box>
                        )}
                    </Box>
                </div>

                {detail.dataGaps?.length > 0 && (
                    <Banner status="warning" title="Some data is incomplete">
                        <VerticalStack gap="1">
                            {detail.dataGaps.map((gap, idx) => (
                                <Text key={idx} variant="bodyMd">{gap.impact}</Text>
                            ))}
                        </VerticalStack>
                    </Banner>
                )}

                {detail.evidence?.length > 0 && (
                    <VerticalStack gap="4">
                        {detail.evidence.map((evidence) => (
                            <InsightEvidenceTable key={evidence.id} evidence={evidence} />
                        ))}
                    </VerticalStack>
                )}
            </VerticalStack>

            {ctas.length > 0 && (
                <Box paddingBlockStart="5">
                    <HorizontalStack gap="3">
                        {ctas.map((cta) => (
                            <Button
                                key={cta.id}
                                primary={cta.primary}
                                icon={ExternalMinor}
                                onClick={() => handleCtaClick(cta)}
                            >
                                {cta.label}
                            </Button>
                        ))}
                    </HorizontalStack>
                </Box>
            )}
        </Box>
    );
}

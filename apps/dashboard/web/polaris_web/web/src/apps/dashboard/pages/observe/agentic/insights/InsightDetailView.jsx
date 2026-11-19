import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Box, VerticalStack, HorizontalStack, Text, Button, Banner, Spinner, Tooltip } from "@shopify/polaris";
import { RefreshMinor, ExternalMinor } from "@shopify/polaris-icons";
import MarkdownViewer from "@/apps/dashboard/components/shared/MarkdownViewer";
import GridRows from "@/apps/dashboard/components/shared/GridRows";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import insightsApi from "./insightsApi";
import InsightEvidenceTable from "./InsightEvidenceTable";
import { buildCtaHref, buildInsightChatMetadata } from "./insightsHelpers";
import { buildTopicGuardrailPrefillForTopic } from "../../../guardrails/topicGuardrailUtils";
import LocalStore from "@/apps/main/LocalStorageStore";
import AskAktoSection from "../../../testing/TestRunResultPage/AskAktoSection";
import { sendQuery } from "../../../agentic/services/agenticService";
import "../../../../components/layouts/style.css";

// Static intro line for the Ask Akto card — never fetched/AI-generated. AiAnalysisCard (inside
// AskAktoSection) only shows its "Get AI Overview" button when `summary` is falsy; that flow
// isn't used here, so a truthy static summary keeps the card in its collapsed/expand-toggle mode
// straight away, with zero network calls until the user actually sends a message.
const AI_CHAT_INTRO = "Ask about this insight's metrics, evidence, or what to do next.";

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

export default function InsightDetailView({ insightId, startTimestamp, endTimestamp, onClose }) {
    const navigate = useNavigate();
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [regenerating, setRegenerating] = useState(false);

    // Ask Akto follow-up chat state. `aiConversationIdRef` is a ref, not state, on purpose — it's
    // never rendered, only read/written, so setting it after the first response doesn't cost an
    // extra re-render the way TestRunResultPage's equivalent useState does.
    const [aiMessages, setAiMessages] = useState([]);
    const [aiLoading, setAiLoading] = useState(false);
    const aiConversationIdRef = useRef(null);

    // Shared unmount guard — InsightDetailView unmounts entirely when the user hits "Back to
    // Insights", and both the initial fetch and a still-in-flight Regenerate call must not
    // setState after that.
    const unmountedRef = useRef(false);
    useEffect(() => () => { unmountedRef.current = true; }, []);

    useEffect(() => {
        setLoading(true);
        setError(false);
        setDetail(null);
        // Wipe Ask Akto chat state too — this insight's identity changed, so any in-progress
        // conversation belongs to a different insight and must not bleed into the new one.
        setAiMessages([]);
        setAiLoading(false);
        aiConversationIdRef.current = null;
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
        // Close the flyout before navigating — several CTAs point at the very page the flyout is
        // already open on top of (e.g. Violations insights opened from the Violations page
        // itself), so leaving it open hides the result of the click entirely, looking exactly
        // like the button did nothing.
        onClose?.();
        if (cta.kind === "GUARDRAIL_TEMPLATE") {
            // `blockTopic` is a bare topic name (see OffDomainTokenBurnProvider) — build the
            // actual prefill client-side via the same helper the LLM Observability "Create
            // guardrail" flow uses, rather than the backend duplicating DeniedTopic's shape.
            // Every other provider already sends a fully-formed GuardrailPolicies-shaped prefill.
            const prefill = cta.params?.blockTopic
                ? buildTopicGuardrailPrefillForTopic(cta.params.blockTopic, {})
                : (cta.params || {});
            navigate(cta.route, { state: { topicGuardrailPrefill: prefill } });
        } else if (cta.href) {
            // ViolationsPage.jsx immediately redirects to a legacy path (dropping the whole query
            // string, including any ?policy=/?user= this CTA set) whenever the "new layout" flag
            // is off — a CTA into that page only makes sense with the new layout, which is the
            // only place these deep-link filters are read. Force it on before navigating so the
            // redirect never fires.
            if (cta.route === "/dashboard/guardrails/violations") {
                LocalStore.getState().setGuardrailViolationsNewLayout(true);
            }
            navigate(cta.href);
        }
    }, [navigate, onClose]);

    const handleSendFollowUp = useCallback((query) => {
        const trimmed = query?.trim();
        if (!trimmed || aiLoading) return;

        const userMsg = { _id: "user_" + Date.now(), role: "user", message: trimmed };
        setAiMessages((prev) => [...prev, userMsg]);
        setAiLoading(true);

        sendQuery(trimmed, aiConversationIdRef.current, "INSIGHTS", buildInsightChatMetadata(detail))
            .then((response) => {
                if (unmountedRef.current) return;
                if (response?.conversationId) aiConversationIdRef.current = response.conversationId;
                if (response?.response) {
                    const aiMsg = { _id: "system_" + Date.now(), role: "system", message: response.response, isComplete: true, isFromHistory: false };
                    setAiMessages((prev) => [...prev, aiMsg]);
                }
            })
            .catch(() => {})
            .finally(() => { if (!unmountedRef.current) setAiLoading(false); });
    }, [aiLoading, detail]);

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
    const hasNarrativeSummary = detail.concern || detail.impact || detail.remediation;

    return (
        <Box overflowY="scroll" padding="5">
            <VerticalStack gap="5">
                {metricItems.length > 0 && (
                    <GridRows columns={metricItems.length} items={metricItems} CardComponent={MetricCardComp} />
                )}

                <Box background="bg-surface-secondary" padding="4" borderRadius="2">
                    <VerticalStack gap="4">
                        <VerticalStack gap="1">
                            <HorizontalStack align="space-between" blockAlign="center">
                                <Text variant="bodySm" fontWeight="semibold" color="subdued">Analysis</Text>
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
                                <Text variant="bodyMd" color="subdued">
                                    AI summary isn't available right now — the metrics and actions here are unaffected.
                                </Text>
                            )}
                        </VerticalStack>

                        {hasNarrativeSummary && (
                            <VerticalStack gap="3">
                                {detail.concern && (
                                    <VerticalStack gap="1">
                                        <Text variant="bodySm" fontWeight="semibold" color="subdued">Concern</Text>
                                        <Text variant="bodyMd">{detail.concern}</Text>
                                    </VerticalStack>
                                )}
                                {detail.impact && (
                                    <VerticalStack gap="1">
                                        <Text variant="bodySm" fontWeight="semibold" color="subdued">Impact</Text>
                                        <Text variant="bodyMd">{detail.impact}</Text>
                                    </VerticalStack>
                                )}
                                {detail.remediation && (
                                    <VerticalStack gap="1">
                                        <Text variant="bodySm" fontWeight="semibold" color="subdued">Remediation</Text>
                                        <Text variant="bodyMd">{detail.remediation}</Text>
                                    </VerticalStack>
                                )}
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </Box>

                {detail.evidence?.length > 0 && (
                    <VerticalStack gap="4">
                        {detail.evidence.map((evidence) => (
                            <InsightEvidenceTable key={evidence.id} evidence={evidence} />
                        ))}
                    </VerticalStack>
                )}

                <AskAktoSection
                    aiSummary={AI_CHAT_INTRO}
                    aiSummaryLoading={false}
                    aiMessages={aiMessages}
                    aiLoading={aiLoading}
                    onSendFollowUp={handleSendFollowUp}
                />
            </VerticalStack>

            {(ctas.length > 0 || detail.dataGaps?.length > 0) && (
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
                        {detail.dataGaps?.map((gap, idx) => (
                            <Tooltip key={idx} content={gap.impact}>
                                <Button disabled>Coming soon</Button>
                            </Tooltip>
                        ))}
                    </HorizontalStack>
                </Box>
            )}
        </Box>
    );
}

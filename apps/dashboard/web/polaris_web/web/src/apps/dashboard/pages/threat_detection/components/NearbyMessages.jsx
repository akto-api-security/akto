import React, { useEffect, useState } from "react";
import { Badge, Box, Divider, HorizontalStack, Spinner, Text, VerticalStack } from "@shopify/polaris";

import ChatMessage from "@/apps/dashboard/pages/testing/TestRunResultPage/components/ChatMessage";
import { MESSAGE_TYPES } from "@/apps/dashboard/pages/testing/TestRunResultPage/components/chatConstants";
import { isAgenticSecurityCategory } from "@/apps/main/labelHelper";
import threatDetectionApi from "../api";

// Agentic Security events often have no session/trace id to key off, so we fetch the nearest
// before/after messages on the same host instead. Internal-only until generally available.

const CONTEXT_WINDOW_ACCOUNT_ID = 1703087742;
const TURNS_BEFORE = 3;
const TURNS_AFTER = 3;

function isContextWindowAvailable() {
    return isAgenticSecurityCategory() && window?.ACTIVE_ACCOUNT === CONTEXT_WINDOW_ACCOUNT_ID;
}

// anchorTimestamp is the flagged event's detectedAt, in epoch seconds.
function useContextWindow(host, anchorTimestamp, enabled) {
    const [contextWindow, setContextWindow] = useState(null); // {anchor, before, after}
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        setContextWindow(null);
        setLoading(false);
        if (!enabled || !isContextWindowAvailable() || !host || !anchorTimestamp) return;

        let cancelled = false;
        setLoading(true);
        threatDetectionApi.fetchContextMessages(host, anchorTimestamp)
            .then((resp) => {
                if (cancelled) return;
                const hasContent = resp && (resp.anchor || resp.before?.length > 0 || resp.after?.length > 0);
                setContextWindow(hasContent ? resp : null);
            })
            .catch(() => {})
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => { cancelled = true; };
    }, [enabled, host, anchorTimestamp]);

    return { contextWindow, loading };
}

function ContextTurn({ turn, isAnchor = false }) {
    return (
        <Box
            borderWidth="1"
            borderRadius="2"
            borderColor={isAnchor ? "border-critical" : "border-subdued"}
            background="bg"
        >
            {isAnchor && (
                <Box background="bg-critical-subdued" padding="2" borderRadius="2">
                    <Badge status="critical" size="small">Current Message</Badge>
                </Box>
            )}
            <Box padding="3">
                <VerticalStack gap="3">
                    <ChatMessage
                        type={MESSAGE_TYPES.REQUEST}
                        content={turn?.queryPayload || ""}
                        timestamp={turn?.latestTimestamp ? Math.floor(turn.latestTimestamp / 1000) : null}
                        customLabel="User prompt"
                        isCode={false}
                        toolsMetadata={{}}
                    />
                    {turn?.responsePayload ? (
                        <ChatMessage
                            type={MESSAGE_TYPES.RESPONSE}
                            content={turn.responsePayload}
                            customLabel="AI agent response"
                            isCode={false}
                            toolsMetadata={{}}
                        />
                    ) : null}
                </VerticalStack>
            </Box>
        </Box>
    );
}

// Renders a divider followed by the nearby messages, or `fallback` when there are none to show
// (feature unavailable, disabled, or nothing found). `padding` lets callers that aren't already
// inside a padded container match their surrounding sections.
export default function NearbyMessages({ host, anchorTimestamp, enabled = true, heading, padding = "0", fallback = null }) {
    const { contextWindow, loading } = useContextWindow(host, anchorTimestamp, enabled);

    if (!loading && !contextWindow) return fallback;

    return (
        <>
            <Divider />
            <Box padding={padding}>
                <VerticalStack gap="4">
                    {heading && <Text variant="headingMd" color="subdued">{heading}</Text>}
                    {loading ? (
                        <Box padding="4">
                            <HorizontalStack gap="2" align="center">
                                <Spinner size="small" />
                                <Text variant="bodyMd" color="subdued">Loading nearby messages...</Text>
                            </HorizontalStack>
                        </Box>
                    ) : (
                        <VerticalStack gap="4">
                            {(contextWindow.before || []).slice(-TURNS_BEFORE).map((turn, idx) => (
                                <ContextTurn key={`before-${idx}`} turn={turn} />
                            ))}
                            {contextWindow.anchor && <ContextTurn turn={contextWindow.anchor} isAnchor />}
                            {(contextWindow.after || []).slice(0, TURNS_AFTER).map((turn, idx) => (
                                <ContextTurn key={`after-${idx}`} turn={turn} />
                            ))}
                        </VerticalStack>
                    )}
                </VerticalStack>
            </Box>
        </>
    );
}

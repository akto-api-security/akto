import { useCallback, useEffect, useState } from "react";
import { Badge, Box, Button, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { ArrowLeftMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import api from "./api";
import { enrichRow } from "./utils";
import { truncate } from "./constants";
import CardList from "./CardList";
import SpansPanel from "./SpansPanel";

export default function SessionsView({ currDateRange }) {
    const [sessions, setSessions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selectedSession, setSelectedSession] = useState(null);

    const [messages, setMessages] = useState([]);
    const [messagesLoading, setMessagesLoading] = useState(false);
    const [selectedMessage, setSelectedMessage] = useState(null);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    const loadSessions = useCallback(async () => {
        const { since, until } = getEpochs();
        setLoading(true);
        setSelectedSession(null);
        setSelectedMessage(null);
        try {
            const rows = await api.fetchSessions(since, until);
            setSessions((rows || []).map(enrichRow));
        } finally {
            setLoading(false);
        }
    }, [getEpochs]);

    useEffect(() => { loadSessions(); }, [loadSessions]);

    useEffect(() => {
        if (!selectedSession) { setMessages([]); return; }
        const { since, until } = getEpochs();
        let cancelled = false;
        setMessagesLoading(true);
        setSelectedMessage(null);
        api.fetchMessages(since, until, selectedSession.sessionIdentifier)
            .then(rows => { if (!cancelled) setMessages((rows || []).map(enrichRow)); })
            .finally(() => { if (!cancelled) setMessagesLoading(false); });
        return () => { cancelled = true; };
    }, [selectedSession, getEpochs]);

    const renderSessionCard = (s) => (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between" blockAlign="center">
                <HorizontalStack gap="2" blockAlign="center">
                    {s.serviceId ? <Badge tone="new">{s.serviceId}</Badge> : null}
                    {s.userName
                        ? <Text variant="bodySm" fontWeight="medium">{s.userName}</Text>
                        : null}
                </HorizontalStack>
                <Text variant="bodySm" tone="subdued">
                    {func.prettifyEpoch(Math.floor((s.latestTimestamp || 0) / 1000))}
                </Text>
            </HorizontalStack>
            <Text variant="bodySm">{truncate(s._promptText || "", 80)}</Text>
            <Text variant="bodySm" tone="subdued">
                {s.messageCount + " message" + (s.messageCount !== 1 ? "s" : "")}
            </Text>
        </VerticalStack>
    );

    const renderMessageCard = (m) => (
        <VerticalStack gap="1">
            <HorizontalStack align="space-between" blockAlign="center">
                <Text variant="bodySm" tone="subdued">
                    {func.prettifyEpoch(Math.floor((m.latestTimestamp || 0) / 1000))}
                </Text>
                <HorizontalStack gap="2">
                    <Badge>{m.spanCount + " span" + (m.spanCount !== 1 ? "s" : "")}</Badge>
                    {(m.totalTokens > 0) && <Badge tone="info">{m.totalTokens + " tok"}</Badge>}
                </HorizontalStack>
            </HorizontalStack>
            <Text variant="bodySm">{truncate(m._promptText || "", 90)}</Text>
        </VerticalStack>
    );

    const rightPanel = () => {
        if (!selectedSession) {
            return (
                <Box padding="8">
                    <Text tone="subdued" variant="bodyMd">Select a session to view its messages</Text>
                </Box>
            );
        }

        if (selectedMessage) {
            return (
                <VerticalStack gap="0" style={{ height: "100%", display: "flex", flexDirection: "column" }}>
                    <Box padding="4" borderBlockEndWidth="025" borderColor="border">
                        <VerticalStack gap="2">
                            <Button plain icon={ArrowLeftMinor} onClick={() => setSelectedMessage(null)}>
                                Back to messages
                            </Button>
                            <Text variant="headingSm">{truncate(selectedMessage._promptText || "", 120)}</Text>
                            <HorizontalStack gap="3">
                                <Text variant="bodySm" tone="subdued">
                                    {func.prettifyEpoch(Math.floor((selectedMessage.latestTimestamp || 0) / 1000))}
                                </Text>
                                <Badge>{selectedMessage.spanCount + " span" + (selectedMessage.spanCount !== 1 ? "s" : "")}</Badge>
                                {(selectedMessage.totalTokens > 0) &&
                                    <Badge tone="info">{selectedMessage.totalTokens + " tokens"}</Badge>}
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                    <SpansPanel traceId={selectedMessage.traceId} />
                </VerticalStack>
            );
        }

        return (
            <VerticalStack gap="0" style={{ height: "100%", display: "flex", flexDirection: "column" }}>
                <Box padding="3" borderBlockEndWidth="025" borderColor="border">
                    <VerticalStack gap="1">
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="headingSm">
                                {truncate(selectedSession.sessionIdentifier || "Session", 40)}
                            </Text>
                            {messages.length > 0 &&
                                <Badge tone="new">{String(messages.length)}</Badge>}
                        </HorizontalStack>
                        <HorizontalStack gap="3">
                            {selectedSession.serviceId &&
                                <Badge tone="new">{selectedSession.serviceId}</Badge>}
                            {selectedSession.userName &&
                                <Text variant="bodySm" tone="subdued">{selectedSession.userName}</Text>}
                            {(selectedSession.totalTokens > 0) &&
                                <Badge tone="info">{selectedSession.totalTokens + " tokens"}</Badge>}
                        </HorizontalStack>
                    </VerticalStack>
                </Box>
                <CardList
                    items={messages}
                    loading={messagesLoading}
                    emptyText="No messages in this session."
                    selectedKey={selectedMessage?.traceId}
                    getKey={(m) => m.traceId}
                    onSelect={setSelectedMessage}
                    renderCard={renderMessageCard}
                />
            </VerticalStack>
        );
    };

    return (
        <HorizontalStack wrap={false} blockAlign="stretch" style={{ height: "calc(100vh - 220px)", minHeight: 0 }}>
            <Box
                borderInlineEndWidth="025"
                borderColor="border"
                minWidth="360px"
                maxWidth="360px"
                style={{ display: "flex", flexDirection: "column" }}
            >
                <Box padding="3" borderBlockEndWidth="025" borderColor="border">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingSm">Sessions</Text>
                        {sessions.length > 0 && <Badge tone="new">{String(sessions.length)}</Badge>}
                    </HorizontalStack>
                </Box>
                <CardList
                    items={sessions}
                    loading={loading}
                    emptyText="No sessions for this time range."
                    selectedKey={selectedSession?.sessionIdentifier}
                    getKey={(s) => s.sessionIdentifier}
                    onSelect={setSelectedSession}
                    renderCard={renderSessionCard}
                />
            </Box>
            <Box minWidth="0" width="100%" style={{ display: "flex", flexDirection: "column" }}>
                {rightPanel()}
            </Box>
        </HorizontalStack>
    );
}

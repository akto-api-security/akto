import { useCallback, useEffect, useState } from "react";
import { Badge, Box, Button, DataTable, HorizontalStack, Link, Text, VerticalStack } from "@shopify/polaris";
import { ArrowLeftMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import api from "./api";
import { enrichRow } from "./utils";
import { truncate, MESSAGE_COLUMN_DEFS } from "./constants";
import SpansPanel from "./SpansPanel";
import GridRows from "../../../components/shared/GridRows";
import observeFunc from "../transform";
import LLMFilterBar from "./LLMFilterBar";
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";

function RowComp({ cardObj }) {
    const { title, value } = cardObj;
    return (
        <Box width="140px">
            <VerticalStack gap="1">
                <Text variant="bodySm" fontWeight="semibold">{title}</Text>
                <Text variant="bodySm" color="subdued">{value}</Text>
            </VerticalStack>
        </Box>
    );
}

export default function SessionsView({ currDateRange }) {
    const [sessions, setSessions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [selectedSession, setSelectedSession] = useState(null);

    const [messages, setMessages] = useState([]);
    const [messagesLoading, setMessagesLoading] = useState(false);
    const [selectedMessage, setSelectedMessage] = useState(null);

    const [sessionInput, setSessionInput] = useState("");
    const [enumFilters, setEnumFilters] = useState({ userName: "", deviceId: "", serviceId: "" });

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
            const rows = await api.fetchSessions(since, until, {
                sessionId: sessionInput.trim(),
                userName:  enumFilters.userName,
                deviceId:  enumFilters.deviceId,
                serviceId: enumFilters.serviceId,
            });
            setSessions((rows || []).map(enrichRow));
        } finally {
            setLoading(false);
        }
    }, [getEpochs, sessionInput, enumFilters]);

    useEffect(() => { loadSessions(); }, [loadSessions]);

    useEffect(() => {
        if (!selectedSession) { setMessages([]); return; }
        const { since, until } = getEpochs();
        let cancelled = false;
        setMessagesLoading(true);
        setSelectedMessage(null);
        api.fetchMessages(since, until, { sessionId: selectedSession.sessionIdentifier })
            .then(rows => { if (!cancelled) setMessages((rows || []).map(enrichRow)); })
            .finally(() => { if (!cancelled) setMessagesLoading(false); });
        return () => { cancelled = true; };
    }, [selectedSession, getEpochs]);

    const getRowItemsForSessions = (s) => [{
        title: "Session ID",
        value: s.sessionIdentifier,
    }, {
        title: "Service ID",
        value: s.serviceId || null,
    }, {
        title: "User",
        value: s.userName || null,
    }, {
        title: "Messages",
        value: s.messageCount,
    }, {
        title: "Tokens",
        value: observeFunc.formatNumberWithCommas(s.totalTokens),
    }, {
        title: "Last active",
        value: func.prettifyEpoch(Math.floor((s.latestTimestamp || 0) / 1000)),
    }];


    const rightPanel = () => {
        if (!selectedSession) {
            return (
                <Box padding="8">
                    <Text tone="subdued" variant="bodyMd">Select a session to view its messages</Text>
                </Box>
            );
        }

        if(messagesLoading) {
            return <SpinnerCentered />
        }

        if (selectedMessage) {
            return (
                <VerticalStack gap="4">
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
            <VerticalStack gap="4">
                <GridRows columns={3} items={getRowItemsForSessions(selectedSession)} CardComponent={RowComp} verticalGap="1" horizontalGap="2" />
                <AgGridTable
                    rowData={messages}
                    columnDefs={MESSAGE_COLUMN_DEFS}
                    defaultColDef={{ resizable: true, sortable: false, filter: false }}
                    rowSelection="single"
                    pagination={false}
                    noOuterBorder
                    onRowClicked={p => setSelectedMessage(p.data)}
                />
            </VerticalStack>
        );
    };

    const renderSessionRow = (s) => [
        <Link 
            removeUnderline plain monochrome onClick={() => setSelectedSession(s)}>
            <Text variant="bodyMd" fontWeight="semibold" alignment="start">{s.queryPayload}</Text>
        </Link>, 
        <Text variant="bodyMd" color="subdued" alignment="start">{func.prettifyEpoch(Math.floor((s.latestTimestamp || 0) / 1000)) || ""}</Text>
    ];

    return (
        <div style={{ maxHeight: "calc(100vh - 220px)", minHeight: 0, display: "flex" }}>
            <Box
                minWidth="480px"
                maxWidth="480px"
                padding={"3"}
            >
                <VerticalStack gap={"3"}>
                    <HorizontalStack gap={"3"} wrap={false} align="space-between" blockAlign="start">
                        <HorizontalStack gap={"1"} wrap={false}>
                            <Text variant="headingSm">Sessions</Text>
                            {sessions.length > 0 &&
                                <Badge status="info">{String(sessions.length)}</Badge>}
                        </HorizontalStack>
                        <LLMFilterBar
                            currDateRange={currDateRange}
                            sessionValue={sessionInput}
                            onSessionChange={setSessionInput}
                            filters={enumFilters}
                            onFiltersChange={setEnumFilters}
                            queryPlaceholder="Search by Session ID"
                        />
                    </HorizontalStack>
                    <DataTable 
                        headings={[]}
                        columnContentTypes={[
                            'text',
                            'numeric'
                        ]}
                        rows={sessions.map(renderSessionRow)}
                        hoverable
                    />
                </VerticalStack>
            </Box>
            <Box minWidth="0" width="100%" style={{ display: "flex", flexDirection: "column" }}>
                {rightPanel()}
            </Box>
        </div>
    );
}

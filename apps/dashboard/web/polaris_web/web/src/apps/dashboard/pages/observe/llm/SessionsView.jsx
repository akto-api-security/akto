import { useCallback, useEffect, useState } from "react";
import { Badge, Box, DataTable, HorizontalStack, Link, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import api from "./api";
import { enrichRow } from "./utils";
import { MESSAGE_COLUMN_DEFS } from "./constants";
import SpansPanelModal from "./SpansPanelModal";
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
    const [selectedSession, setSelectedSession] = useState(null);

    const [messages, setMessages] = useState([]);
    const [messagesLoading, setMessagesLoading] = useState(false);

    const [sessionInput, setSessionInput] = useState("");
    const [enumFilters, setEnumFilters] = useState({ userName: "", deviceId: "", serviceId: "" });

    const [spansModalOpen, setSpansModalOpen] = useState(false);
    const [spansModalTraceId, setSpansModalTraceId] = useState(null);

    const getEpochs = useCallback(() => ({
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    }), [currDateRange]);

    const loadSessions = useCallback(async () => {
        const { since, until } = getEpochs();
        setSelectedSession(null);
        try {
            const rows = await api.fetchSessions(since, until, {
                sessionId: sessionInput.trim(),
                userName:  enumFilters.userName,
                deviceId:  enumFilters.deviceId,
                serviceId: enumFilters.serviceId,
            });
            setSessions((rows || []).map(enrichRow));
        } finally {
        }
    }, [getEpochs, sessionInput, enumFilters]);

    useEffect(() => { loadSessions(); }, [loadSessions]);

    useEffect(() => {
        if (!selectedSession) { setMessages([]); return; }
        const { since, until } = getEpochs();
        let cancelled = false;
        setMessagesLoading(true);
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

    const handleRowClicked = (p) => {
        setSpansModalTraceId(p.data.traceId);
        setSpansModalOpen(true);
    };

    const rightPanel = () => {
        if (!selectedSession) {
            return (
                <Box padding="8">
                    <Text tone="subdued" variant="bodyMd">Select a session to view its messages</Text>
                </Box>
            );
        }

        if (messagesLoading) {
            return <SpinnerCentered />;
        }

        return (
            <Box padding="3">
                <VerticalStack gap="4">
                    <GridRows columns={3} items={getRowItemsForSessions(selectedSession)} CardComponent={RowComp} verticalGap="1" horizontalGap="2" />
                    <AgGridTable
                        rowData={messages}
                        columnDefs={MESSAGE_COLUMN_DEFS}
                        defaultColDef={{ resizable: true, sortable: false, filter: false }}
                        rowSelection="single"
                        pagination={true}
                        noOuterBorder
                        onRowClicked={handleRowClicked}
                        sideBar={false}
                    />
                </VerticalStack>
            </Box>
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
        <div style={{ maxHeight: "calc(100vh - 220px)", minHeight: 0, display: "flex", gap: 12 }}>
            <Box
                minWidth="480px"
                maxWidth="480px"
                padding="3"
            >
                <VerticalStack gap="3">
                    <HorizontalStack gap="3" wrap={false} align="space-between" blockAlign="start">
                        <HorizontalStack gap="1" wrap={false}>
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
            <Box style={{ flex: 1 }}>
                {rightPanel()}
            </Box>

            <SpansPanelModal
                open={spansModalOpen}
                onClose={() => setSpansModalOpen(false)}
                traceId={spansModalTraceId}
            />
        </div>
    );
}

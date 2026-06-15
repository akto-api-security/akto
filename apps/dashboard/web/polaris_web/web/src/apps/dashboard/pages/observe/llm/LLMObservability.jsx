import { useReducer, useState } from "react";
import { Card, HorizontalStack, Tabs, Tag, VerticalStack } from "@shopify/polaris";
import { produce } from "immer";

import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";

import SessionsView from "./SessionsView";
import TracesView from "./TracesView";
import MessagesView from "./MessagesView";
import SummaryBand from "./SummaryBand";
import { truncate } from "./constants";

const TAB_SESSIONS = 0;
const TAB_TRACES = 1;
const TAB_MESSAGES = 2;

const TABS = [
    { id: "sessions", content: "Sessions", panelID: "sessions-panel" },
    { id: "traces", content: "Traces", panelID: "traces-panel" },
    { id: "messages", content: "Messages", panelID: "messages-panel" },
];

export default function LLMObservability() {
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[5]
    );
    const [activeTab, setActiveTab] = useState(TAB_SESSIONS);
    const [sessionFilter, setSessionFilter] = useState(null); // scopes Traces tab
    const [traceFilter, setTraceFilter] = useState(null);     // scopes Messages tab

    // Drill-down handlers.
    const openSession = (sessionId) => { setSessionFilter(sessionId); setActiveTab(TAB_TRACES); };
    const openTraceMessages = (tId) => { setTraceFilter(tId); setActiveTab(TAB_MESSAGES); };

    const onSelectTab = (idx) => {
        // Clear scope when switching tabs manually so each tab starts unfiltered.
        if (idx !== TAB_TRACES) setSessionFilter(null);
        if (idx !== TAB_MESSAGES) setTraceFilter(null);
        setActiveTab(idx);
    };

    return (
        <PageWithMultipleCards
            title="LLM Observability"
            isFirstPage
            primaryAction={
                <DateRangeFilter
                    initialDispatch={currDateRange}
                    dispatch={(dateObj) =>
                        dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
                    }
                />
            }
            components={[
                <VerticalStack key="llm-summary" gap="4">
                    <SummaryBand currDateRange={currDateRange} />
                    <Card padding={2}>
                        <VerticalStack gap="2">
                            <Tabs tabs={TABS} selected={activeTab} onSelect={onSelectTab} />

                            {(activeTab === TAB_TRACES && sessionFilter) || (activeTab === TAB_MESSAGES && traceFilter) ? (
                                <HorizontalStack gap="2">
                                    {activeTab === TAB_TRACES && sessionFilter && (
                                        <Tag onRemove={() => setSessionFilter(null)}>{"Session: " + truncate(sessionFilter, 20)}</Tag>
                                    )}
                                    {activeTab === TAB_MESSAGES && traceFilter && (
                                        <Tag onRemove={() => setTraceFilter(null)}>{"Trace: " + truncate(traceFilter, 20)}</Tag>
                                    )}
                                </HorizontalStack>
                            ) : null}

                            {activeTab === TAB_SESSIONS && (
                                <SessionsView key="sessions" currDateRange={currDateRange} onOpenSession={openSession} />
                            )}
                            {activeTab === TAB_TRACES && (
                                <TracesView
                                    key="traces"
                                    currDateRange={currDateRange}
                                    sessionFilter={sessionFilter}
                                    onOpenSession={openSession}
                                    onViewMessages={openTraceMessages}
                                />
                            )}
                            {activeTab === TAB_MESSAGES && (
                                <MessagesView key="messages" currDateRange={currDateRange} traceFilter={traceFilter} />
                            )}
                        </VerticalStack>
                    </Card>
                </VerticalStack>
            ]}
        />
    );
}

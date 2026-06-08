import { useReducer, useState } from "react";
import { Card, Tabs, VerticalStack } from "@shopify/polaris";
import { produce } from "immer";

import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";

import SessionsView from "./SessionsView";
import MessagesView from "./MessagesView";
import PromptsView from "./PromptsView";

const TABS = [
    // { id: "sessions", content: "Sessions", panelID: "sessions-panel" },
    // { id: "messages", content: "Messages", panelID: "messages-panel" },
    { id: "prompts", content: "All messages", panelID: "prompts-panel" },
];

export default function LLMObservability() {
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[5]
    );
    const [activeTab, setActiveTab] = useState(0);

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
                <Card key={"llm-layout"} padding={2}>
                    <VerticalStack gap={"2"}>
                        <Tabs tabs={TABS} selected={activeTab} onSelect={setActiveTab} />
                        
                        {/* {activeTab === 0 && <SessionsView key="sessions" currDateRange={currDateRange} />}
                        {activeTab === 1 && <MessagesView key="messages" currDateRange={currDateRange} />} */}
                        {activeTab === 0 && <PromptsView key="prompts" currDateRange={currDateRange} />}
                    </VerticalStack>
                </Card>
            ]}
        />
    );
}

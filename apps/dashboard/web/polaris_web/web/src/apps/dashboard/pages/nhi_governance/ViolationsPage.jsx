import { useState, useMemo, useReducer } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import DonutChart from "../../components/shared/DonutChart";
import LineChart from "../../components/charts/LineChart";
import InfoCard from "../dashboard/new_components/InfoCard";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import values from "@/util/values";
import { violationsTableData, violationsHeaders, violationsSortOptions } from "./nhiViolationsData";

const definedTableTabs = ["All", "Open", "Fixed"];
const resourceName = { singular: "violation", plural: "violations" };

// ── Chart data ─────────────────────────────────────────────────────────────────
const severityDonutData = {
    Critical: { text: 31,  color: "#DF2909" },
    High:     { text: 48,  color: "#FED3D1" },
    Medium:   { text: 44,  color: "#FFD79D" },
    Low:      { text: 25,  color: "#E4E5E7" },
};

const violationsOverTimeData = [{
    data: [
        [Date.UTC(2026, 2, 28), 182],
        [Date.UTC(2026, 3,  0), 158],
        [Date.UTC(2026, 3,  1), 104],
        [Date.UTC(2026, 3,  2), 78],
        [Date.UTC(2026, 3,  3), 96],
        [Date.UTC(2026, 3,  4), 108],
        [Date.UTC(2026, 3,  5), 98],
    ],
    color: "#EF4444",
    name: "Violations",
}];

function ChartLegend({ items }) {
    return (
        <VerticalStack gap="2">
            {items.map(({ label, color, count }) => (
                <HorizontalStack key={label} gap="2" blockAlign="center">
                    <span style={{ display:"inline-block", width:10, height:10, borderRadius:"50%", background:color, flexShrink:0 }} />
                    <Text variant="bodyMd" color="subdued">{label}</Text>
                    <Text variant="bodyMd" fontWeight="semibold">{count.toLocaleString()}</Text>
                </HorizontalStack>
            ))}
        </VerticalStack>
    );
}

function DonutCard({ title, donutData }) {
    const legendItems = Object.entries(donutData).map(([label, { text, color }]) => ({ label, color, count: text }));
    return (
        <InfoCard title={title} component={
            <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                <DonutChart data={donutData} title="" size={150} pieInnerSize="55%" />
                <ChartLegend items={legendItems} />
            </HorizontalStack>
        } />
    );
}

// ── Page ───────────────────────────────────────────────────────────────────────
const violationsPageTitle = (
    <TitleWithInfo
        titleText="Violations"
        tooltipContent="Policy violations detected across all non-human identities used by your AI agents."
        docsUrl="https://ai-security-docs.akto.io/nhi-governance/violations"
    />
);

export default function ViolationsPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "open";

    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected]       = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const dataByTab = useMemo(() => ({
        all:   violationsTableData,
        open:  violationsTableData.filter((r) => r.status === "Open"),
        fixed: violationsTableData.filter((r) => r.status === "Fixed"),
    }), []);

    const tableCountObj = func.getTabsCount(definedTableTabs, dataByTab);
    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tableCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo
    );

    return (
        <PageWithMultipleCards
            title={violationsPageTitle}
            isFirstPage
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(d) => dispatchCurrDateRange({ type: "update", period: d.period, title: d.title, alias: d.alias })} />}
            components={[
                <div key="charts" style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "16px" }}>
                    <InfoCard
                        title="Violations over time"
                        component={
                            <LineChart
                                data={violationsOverTimeData}
                                type="line"
                                height={220}
                                text={true}
                                showGridLines={true}
                                exportingDisabled={true}
                                defaultChartOptions={{
                                    xAxis: {
                                        type: "datetime",
                                        dateTimeLabelFormats: { day: "%a" },
                                        title: { text: null },
                                        visible: true,
                                        gridLineWidth: 0,
                                    },
                                    yAxis: {
                                        title: { text: "Violations" },
                                        gridLineWidth: 1,
                                        min: 0,
                                    },
                                    legend: { enabled: true },
                                }}
                            />
                        }
                    />
                    <DonutCard title="Violations by severity" donutData={severityDonutData} />
                </div>,

                <GithubSimpleTable
                    key="violations-table"
                    data={dataByTab[selectedTab]}
                    headers={violationsHeaders}
                    resourceName={resourceName}
                    sortOptions={violationsSortOptions}
                    filters={[]}
                    selectable={true}
                    mode={IndexFiltersMode.Default}
                    headings={violationsHeaders}
                    useNewRow={true}
                    condensedHeight={true}
                    tableTabs={tableTabs}
                    onSelect={(i) => setSelected(i)}
                    selected={selected}
                    promotedBulkActions={() => [
                        { content: "Mark as fixed", onAction: () => {} },
                        { content: "Open Jira ticket", onAction: () => {} },
                    ]}
                />,
            ]}
        />
    );
}

import { useMemo } from "react";
import { Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import StackedBarChart from "@/apps/dashboard/components/charts/StackedBarChart";
import { OverviewCardHeader } from "./OverviewCard";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import observeFunc from "../../observe/transform";

const SERIES = [
    { key: "blocked", name: "Blocked", color: "#6200EA" },
    { key: "approval", name: "Needs approval", color: "#B38CFF" },
    { key: "alerted", name: "Alerted or warned", color: "#D2D5D8" },
];

const LABEL_FORMAT = {
    D: { month: "short", day: "numeric" },
    W: { month: "short", day: "numeric" },
    M: { month: "short", year: "numeric" },
};
const NEXT_PERIOD = {
    D: (date) => date.setDate(date.getDate() + 1),
    W: (date) => date.setDate(date.getDate() + 7),
    M: (date) => date.setMonth(date.getMonth() + 1),
};

const GRANULARITY_TITLE = { D: "Daily", W: "Weekly", M: "Monthly" };

function buildBars(decisions) {
    const format = LABEL_FORMAT[decisions?.granularity];
    const timestamps = format ? SERIES.flatMap((s) => (decisions[s.key] || []).map((point) => point[0])) : [];
    if (timestamps.length === 0) return { categories: [], series: [] };

    const label = (ts) => new Date(ts).toLocaleDateString("en-US", format);
    const categories = [];
    const cursor = new Date(Math.min(...timestamps));
    const last = Math.max(...timestamps);
    while (cursor.getTime() <= last) {
        categories.push(label(cursor.getTime()));
        NEXT_PERIOD[decisions.granularity](cursor);
    }
    const series = SERIES.map((s) => {
        const counts = new Map((decisions[s.key] || []).map((point) => [label(point[0]), point[1]]));
        return { name: s.name, color: s.color, data: categories.map((category) => counts.get(category) || 0) };
    });
    return { categories, series };
}

function tooltipOf() {
    const rows = this.points.map((point) => `<span style="color:${point.color}">●</span> ${point.series.name}: <b>${observeFunc.formatNumberWithCommas(point.y)}</b>`);
    return [`<b>${this.points[0].key}</b>`, ...rows].join("<br/>");
}

export default function DecisionsTrendCard({ decisions, loading }) {
    const bars = useMemo(() => buildBars(decisions), [decisions]);
    if (loading) return <Card><VerticalStack gap="3"><OverviewCardHeader title="Guardrail decisions" /><SpinnerCentered height="200px" /></VerticalStack></Card>;

    const title = GRANULARITY_TITLE[decisions?.granularity];
    const days = decisions?.periodDays;
    const subtitle = title ? `${title}${days ? `, last ${days} ${days === 1 ? "day" : "days"}` : ""}` : "";
    const legend = (
        <HorizontalStack gap="3">
            {SERIES.map((s) => (
                <HorizontalStack key={s.key} gap="1_5-experimental" blockAlign="center" wrap={false}>
                    <span style={{ width: "8px", height: "8px", borderRadius: "2px", background: s.color, display: "inline-block" }} />
                    <Text variant="bodySm" color="subdued">{s.name}</Text>
                </HorizontalStack>
            ))}
        </HorizontalStack>
    );
    return (
        <Card>
            <VerticalStack gap="3">
                <OverviewCardHeader title="Guardrail decisions" subtitle={subtitle} action={legend} />
                {bars.categories.length > 0
                    ? <StackedBarChart categories={bars.categories} series={bars.series} height={200} tooltipFormatter={tooltipOf} />
                    : <Text variant="bodyMd" color="subdued" alignment="center">No guardrail decisions in this period.</Text>}
            </VerticalStack>
        </Card>
    );
}

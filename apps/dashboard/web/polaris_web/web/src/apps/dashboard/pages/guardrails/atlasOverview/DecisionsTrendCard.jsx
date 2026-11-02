import { Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import StackedChart from "@/apps/dashboard/components/charts/StackedChart";
import { OverviewCardHeader } from "./OverviewCard";

const SERIES = [
    { key: "blocked", name: "Blocked", color: "#6200EA" },
    { key: "redacted", name: "Redacted", color: "#B38CFF" },
    { key: "alerted", name: "Alerted or warned", color: "#D2D5D8" },
];

const X_AXIS = {
    type: "datetime",
    dateTimeLabelFormats: { day: "%b %e" },
    title: { text: null },
    gridLineWidth: 0,
};

export default function DecisionsTrendCard({ decisions }) {
    const data = [...SERIES].reverse().map((s) => ({
        name: s.name,
        color: s.color,
        data: decisions.map((d) => [d.day, d[s.key]]),
    }));
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
                <OverviewCardHeader title="Guardrail decisions" subtitle={`Daily, last ${decisions.length} days`} action={legend} />
                <StackedChart
                    type="column"
                    height={200}
                    title=""
                    data={data}
                    customXaxis={X_AXIS}
                    noGap
                    exportingDisabled
                    defaultChartOptions={{ legend: { enabled: false } }}
                />
            </VerticalStack>
        </Card>
    );
}

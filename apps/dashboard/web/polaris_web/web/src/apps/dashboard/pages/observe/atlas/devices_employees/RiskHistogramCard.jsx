import { Button, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { InfoMinor } from "@shopify/polaris-icons";
import BarGraph from "../../../../components/charts/BarGraph";
import InsightCard from "./InsightCard";
import { HIGH_RISK_THRESHOLD, MEDIUM_RISK_THRESHOLD, RISK_BUCKET_WIDTH, RISK_COLORS, RISK_EXPLANATION } from "./peopleConstants";

const BAR_WIDTH = 36;
const CHART_OPTIONS = {
    legend: { enabled: false },
    yAxis: [{ title: { text: "" }, visible: false, gridLineWidth: 0, min: 0, maxPadding: 0.2 }],
};

function bucketLabel(index) {
    return `${index * RISK_BUCKET_WIDTH}-${(index + 1) * RISK_BUCKET_WIDTH}`;
}

function bucketColor(index) {
    const lowerBound = index * RISK_BUCKET_WIDTH;
    if (lowerBound >= HIGH_RISK_THRESHOLD) return RISK_COLORS.high;
    return lowerBound >= MEDIUM_RISK_THRESHOLD ? RISK_COLORS.medium : RISK_COLORS.low;
}

function RiskHistogramCard({ overview, status, onSelectBucket, onSelectHighRisk }) {
    const counts = overview?.riskHistogram || [];
    const highRisk = overview?.highRiskEmployees || 0;
    const lastPopulated = counts.reduce((last, count, index) => (count > 0 ? index : last), 0);
    const data = counts.slice(0, lastPopulated + 1).map((count, index) => ({ text: bucketLabel(index), value: count, color: bucketColor(index), id: index }));

    return (
        <InsightCard title="Employee risk scores" subtitle="Highest API risk score (0 to 5) across each employee's AI tools" status={status}>
            <VerticalStack gap="2">
                {overview?.riskAvailable === false && (
                    <Text as="p" variant="bodySm" color="critical">Risk scores are unavailable right now.</Text>
                )}
                <BarGraph
                    data={data}
                    height="280px"
                    barWidth={BAR_WIDTH}
                    showYAxis={false}
                    showDataLabels
                    defaultChartOptions={CHART_OPTIONS}
                    onBarClick={(name, custom) => onSelectBucket(custom.id)}
                />
                <HorizontalStack align="space-between" blockAlign="center">
                    <Button plain onClick={onSelectHighRisk}>{`${highRisk} employee${highRisk === 1 ? "" : "s"} score ${HIGH_RISK_THRESHOLD} or higher`}</Button>
                    <Tooltip content={RISK_EXPLANATION} width="wide">
                        <Icon source={InfoMinor} color="subdued" />
                    </Tooltip>
                </HorizontalStack>
            </VerticalStack>
        </InsightCard>
    );
}

export default RiskHistogramCard;

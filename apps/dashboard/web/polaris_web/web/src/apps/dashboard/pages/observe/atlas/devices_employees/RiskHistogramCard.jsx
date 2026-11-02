import { Button, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { InfoMinor } from "@shopify/polaris-icons";
import BarGraph from "../../../../components/charts/BarGraph";
import InsightCard from "./InsightCard";
import { HIGH_RISK_THRESHOLD, RISK_BUCKET_COUNT, RISK_COLORS, RISK_FORMULA } from "./peopleConstants";

const BUCKET_WIDTH = 100 / RISK_BUCKET_COUNT;
const MEDIUM_RISK_THRESHOLD = 50;
const BAR_WIDTH = 36;

function bucketLabel(index) {
    return `${index * BUCKET_WIDTH}-${index === RISK_BUCKET_COUNT - 1 ? 100 : index * BUCKET_WIDTH + BUCKET_WIDTH - 1}`;
}

function bucketColor(index) {
    const lowerBound = index * BUCKET_WIDTH;
    if (lowerBound >= HIGH_RISK_THRESHOLD) return RISK_COLORS.high;
    return lowerBound >= MEDIUM_RISK_THRESHOLD ? RISK_COLORS.medium : RISK_COLORS.low;
}

function RiskHistogramCard({ overview, status, onSelectBucket, onSelectHighRisk }) {
    const counts = overview?.riskHistogram || [];
    const highRisk = overview?.highRiskEmployees || 0;
    const data = counts.map((count, index) => ({ text: bucketLabel(index), value: count, color: bucketColor(index), id: index }));

    return (
        <InsightCard title="Employee risk scores" subtitle="Based on unapproved tools, personal accounts and violations" status={status}>
            <VerticalStack gap="2">
                {overview?.violationsAvailable === false && (
                    <Text as="p" variant="bodySm" color="critical">Violation data is unavailable, so scores exclude violations.</Text>
                )}
                <BarGraph
                    data={data}
                    height="280px"
                    barWidth={BAR_WIDTH}
                    showYAxis={false}
                    showDataLabels
                    onBarClick={(name, custom) => onSelectBucket(custom.id)}
                />
                <HorizontalStack align="space-between" blockAlign="center">
                    <Button plain onClick={onSelectHighRisk}>{`${highRisk} employee${highRisk === 1 ? "" : "s"} score ${HIGH_RISK_THRESHOLD} or higher`}</Button>
                    <Tooltip content={RISK_FORMULA} width="wide">
                        <Icon source={InfoMinor} color="subdued" />
                    </Tooltip>
                </HorizontalStack>
            </VerticalStack>
        </InsightCard>
    );
}

export default RiskHistogramCard;

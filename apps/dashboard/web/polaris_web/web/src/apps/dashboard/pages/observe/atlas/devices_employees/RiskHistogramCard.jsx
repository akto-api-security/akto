import { Box, Button, Text, VerticalStack } from "@shopify/polaris";
import BarGraph from "../../../../components/charts/BarGraph";
import InfoCard from "../../../dashboard/new_components/InfoCard";
import { HIGH_RISK_THRESHOLD, RISK_BUCKET_WIDTH, RISK_EXPLANATION, SEVERITY_SEGMENTS } from "./peopleConstants";
import transform from "./transform";
import { getRiskLevel } from "../../agentic/agenticPageBuilders";

const BAR_WIDTH = 36;
const CHART_OPTIONS = {
    legend: { enabled: false },
    yAxis: [{ title: { text: "" }, visible: false, gridLineWidth: 0, min: 0, maxPadding: 0.2 }],
};
const TEXT_COLOR = "#202223";
const DIVIDER_COLOR = "#E1E3E5";
const EXTRA_OPTIONS = {
    xAxis: { lineColor: DIVIDER_COLOR, tickColor: DIVIDER_COLOR, labels: { style: { color: TEXT_COLOR } } },
    plotOptions: { series: { dataLabels: { style: { color: TEXT_COLOR, textOutline: "none" } } } },
};

// Each numeric bucket joins the level of its midpoint, so the thresholds stay in getRiskLevel only.
// A score exactly on a boundary (2.0, 3.0, 4.0) sits in the higher bucket, so it can show one level above its badge.
function levelOfBucket(index) {
    return getRiskLevel((index + 0.5) * RISK_BUCKET_WIDTH).toLowerCase();
}

function groupBucketsByLevel(counts) {
    return SEVERITY_SEGMENTS.slice().reverse().map(({ key, name, color }, id) => {
        const indexes = counts.map((_, index) => index).filter((index) => levelOfBucket(index) === key);
        const value = indexes.reduce((sum, index) => sum + counts[index], 0);
        const riskMin = indexes.length ? indexes[0] * RISK_BUCKET_WIDTH : 0;
        const riskMax = indexes.length ? (indexes[indexes.length - 1] + 1) * RISK_BUCKET_WIDTH : 0;
        return { text: name, value, color, id, riskMin, riskMax };
    });
}

function RiskHistogramCard({ overview, status, onSelectBucket, onSelectHighRisk }) {
    const counts = overview?.riskHistogram || [];
    const highRisk = overview?.highRiskEmployees || 0;
    const levels = groupBucketsByLevel(counts);
    const data = levels.map(({ text, value, color, id }) => ({ text, value, color, id }));
    const riskUnavailable = overview?.riskAvailable === false;

    const body = (
        <VerticalStack gap="2">
            {riskUnavailable ? (
                <Text as="p" variant="bodySm" color="critical">Risk scores are unavailable right now.</Text>
            ) : (
                <BarGraph
                    data={data}
                    height="250px"
                    barWidth={BAR_WIDTH}
                    showYAxis={false}
                    showDataLabels
                    defaultChartOptions={CHART_OPTIONS}
                    extraChartOptions={EXTRA_OPTIONS}
                    onBarClick={(name, custom) => onSelectBucket(levels[custom.id])}
                />
            )}
            <Box>
                <Button plain onClick={onSelectHighRisk}>{`${highRisk} employee${highRisk === 1 ? "" : "s"} score ${HIGH_RISK_THRESHOLD} or higher`}</Button>
            </Box>
        </VerticalStack>
    );

    return <InfoCard title="Employee risk scores" subtitle="Highest risk level across each employee's AI tools" titleToolTip={transform.tooltip(RISK_EXPLANATION)} component={transform.cardBody(status, body)} />;
}

export default RiskHistogramCard;

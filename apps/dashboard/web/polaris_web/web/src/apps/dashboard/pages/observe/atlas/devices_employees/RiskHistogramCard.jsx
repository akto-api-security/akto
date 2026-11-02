import { Button, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { InfoMinor } from "@shopify/polaris-icons";
import HighchartsReact from "highcharts-react-official";
import Highcharts from "highcharts";
import InsightCard from "./InsightCard";
import { HIGH_RISK_THRESHOLD, RISK_BUCKET_COUNT, RISK_COLORS, RISK_FORMULA } from "./peopleConstants";

const BUCKET_WIDTH = 100 / RISK_BUCKET_COUNT;

function bucketColor(index) {
    const lowerBound = index * BUCKET_WIDTH;
    if (lowerBound >= HIGH_RISK_THRESHOLD) return RISK_COLORS.high;
    return lowerBound >= 50 ? RISK_COLORS.medium : RISK_COLORS.low;
}

function RiskHistogramCard({ overview, status, onSelectBucket, onSelectHighRisk }) {
    const counts = overview?.riskHistogram || [];
    const highRisk = overview?.highRiskEmployees || 0;

    const options = {
        chart: { type: "column", height: 300, backgroundColor: "transparent", spacing: [24, 0, 0, 0] },
        title: { text: "" },
        credits: { enabled: false },
        exporting: { enabled: false },
        legend: { enabled: false },
        xAxis: {
            categories: counts.map((_, index) => `${index * BUCKET_WIDTH}-${index === RISK_BUCKET_COUNT - 1 ? 100 : index * BUCKET_WIDTH + BUCKET_WIDTH - 1}`),
            title: { text: "" },
            labels: { rotation: 0, style: { fontSize: "11px", color: "#6D7175" } },
            lineColor: "#E1E3E5",
            tickLength: 0,
        },
        yAxis: { title: { text: "" }, visible: false, min: 0 },
        tooltip: {
            headerFormat: "",
            formatter: function () { return `<b>Risk ${this.point.category}</b><br/>${this.point.y} employees`; },
        },
        plotOptions: {
            series: {
                pointPadding: 0.04,
                groupPadding: 0,
                borderWidth: 0,
                borderRadius: 3,
                cursor: "pointer",
                dataLabels: { enabled: true, style: { fontSize: "12px", fontWeight: "600", textOutline: "none", color: "#202223" } },
                states: { hover: { brightness: -0.1 }, inactive: { opacity: 1 } },
                point: { events: { click: function () { onSelectBucket(this.index); } } },
            },
        },
        series: [{ data: counts.map((count, index) => ({ y: count, color: bucketColor(index) })) }],
    };

    return (
        <InsightCard title="Employee risk scores" subtitle="Based on unapproved tools, personal accounts and violations" status={status}>
            <VerticalStack gap="2">
                {overview?.violationsAvailable === false && (
                    <Text as="p" variant="bodySm" color="critical">Violation data is unavailable, so scores exclude violations.</Text>
                )}
                <HighchartsReact highcharts={Highcharts} options={options} />
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

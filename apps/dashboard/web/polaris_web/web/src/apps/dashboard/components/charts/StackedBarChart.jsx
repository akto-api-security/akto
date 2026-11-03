import { useMemo } from "react";
import HighchartsReact from "highcharts-react-official";
import Highcharts from "highcharts";

const TOTAL_COLOR = "#6D7175";
const BAR_RADIUS = 2;

// Labels only at the first, middle and last category, so a long run of weeks stays readable.
const sparseTicks = (count) => [...new Set([0, Math.floor((count - 1) / 2), count - 1])];

// Stacked columns with totals on top; series: [{ name, color, data }] listed bottom to top.
export default function StackedBarChart({ categories, series, height = 220, tooltipFormatter }) {
    const options = useMemo(() => ({
        chart: { type: "column", height, spacing: [18, 0, 0, 0] },
        title: { text: null },
        credits: { enabled: false },
        exporting: { enabled: false },
        legend: { enabled: false },
        xAxis: { categories, tickPositions: sparseTicks(categories.length), tickLength: 0, lineColor: "#E1E3E5" },
        yAxis: {
            title: { text: null },
            labels: { enabled: false },
            gridLineWidth: 0,
            min: 0,
            reversedStacks: false,
            stackLabels: { enabled: true, formatter() { return this.total || ""; }, style: { fontWeight: "normal", color: TOTAL_COLOR, textOutline: "none" } },
        },
        tooltip: { shared: true, formatter: tooltipFormatter },
        plotOptions: {
            column: { stacking: "normal", borderWidth: 0, borderRadius: BAR_RADIUS, groupPadding: 0.08, pointPadding: 0 },
        },
        series: series.map((item) => ({ name: item.name, color: item.color, data: item.data })),
    }), [categories, series, height, tooltipFormatter]);

    return <HighchartsReact highcharts={Highcharts} options={options} />;
}

import ChartRenderer from "@/apps/dashboard/components/shared/ChartRenderer"
import { severityHex } from "./transform"

// A sparkline via the existing chart pipeline — ChartRenderer/LineChart, not hand-rolled SVG.
// There is no dedicated "sparkline" chart type; this is chart:area with axes/gridlines hidden
// via defaultChartOptions (LineChart's own `text` prop, which ChartRenderer hardcodes true, only
// toggles axis visibility — defaultChartOptions is the one escape hatch that overrides it).
//
// No trend data exists yet for any tile (neither RecommendationCatalog nor the InsightTile
// providers set one) — this renders nothing until a `trend` array actually arrives, so shipping
// trend data later is a pure addition with no layout change. Never synthesize a trend from a
// single point.
export default function TrendSpark({ trend, severity }) {
    if (!Array.isArray(trend) || trend.length < 2) return null

    const points = trend.map((v, i) => [i, v])
    const color = severityHex(severity)

    return (
        <ChartRenderer
            chartType="area"
            data={{
                data: points,
                height: 40,
                color,
                showGridLines: false,
                backgroundColor: "transparent",
                defaultChartOptions: {
                    xAxis: { visible: false },
                    yAxis: { visible: false, title: { text: "" } },
                    legend: { enabled: false },
                },
            }}
        />
    )
}

import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AnalyticsMajor } from "@shopify/polaris-icons";
import StackedBarChart from "@/apps/dashboard/components/charts/StackedBarChart";
import DiscoveryCard from "../shared/DiscoveryCard";
import StatusLegend from "./StatusLegend";
import { openInventoryByStatus } from "../shared/openInventory";
import { STATUS_META, SUBTITLES, TREND_STATUSES, WEEK_SECONDS } from "../constants";

const CHART_HEIGHT = 230;

// Week start, e.g. "Aug 11".
const formatWeek = (epochSeconds) => new Date(epochSeconds * 1000).toLocaleDateString("en-US", { month: "short", day: "numeric" });

// "Week of Aug 11: 5 approved, 1 under review, 2 unapproved"
function tooltipOf() {
    const parts = this.points.map((point) => `${point.y} ${point.series.name.toLowerCase()}`);
    return `Week of ${this.points[0].key}: ${parts.join(", ")}`;
}

export default function NewToolsTrendCard({ periods, status }) {
    const navigate = useNavigate();
    // Stable, so the chart is not rebuilt on every render.
    // A column is one week; its tools are the ones first seen in it.
    const openWeek = useCallback((statusLabel, index) => {
        const start = periods?.[index]?.periodStart;
        openInventoryByStatus(navigate, statusLabel, start ? { start, end: start + WEEK_SECONDS } : undefined);
    }, [navigate, periods]);
    const [selected, setSelected] = useState(null);
    const hasData = (periods || []).some((period) => TREND_STATUSES.some((key) => period[key] > 0));
    const categories = useMemo(() => (periods || []).map((period) => formatWeek(period.periodStart)), [periods]);
    // Listed bottom to top: approved at the base, unapproved on top.
    const series = useMemo(() => TREND_STATUSES.filter((key) => !selected || key === selected).map((key) => ({
        name: STATUS_META[key].label,
        color: STATUS_META[key].color,
        data: (periods || []).map((period) => period[key] || 0),
    })), [periods, selected]);
    return (
        <DiscoveryCard
            title="New AI tools discovered"
            subtitle={SUBTITLES.trend}
            legend={<StatusLegend statuses={TREND_STATUSES} selected={selected} onSelect={setSelected} />}
            status={status}
            empty={!hasData}
            emptyIcon={AnalyticsMajor}
            emptyTone="subdued"
            emptyText="No new AI tools were discovered in the last 12 weeks."
        >
            <StackedBarChart categories={categories} series={series} height={CHART_HEIGHT} tooltipFormatter={tooltipOf} onPointClick={openWeek} />
        </DiscoveryCard>
    );
}

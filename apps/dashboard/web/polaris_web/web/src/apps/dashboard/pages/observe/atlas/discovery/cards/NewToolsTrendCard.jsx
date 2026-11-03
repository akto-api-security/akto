import { useMemo } from "react";
import StackedBarChart from "@/apps/dashboard/components/charts/StackedBarChart";
import DiscoveryCard from "../shared/DiscoveryCard";
import StatusLegend from "./StatusLegend";
import { STATUS_META, SUBTITLES, TREND_STATUSES } from "../constants";

const CHART_HEIGHT = 230;

const formatDay = (epochSeconds) => new Date(epochSeconds * 1000).toLocaleDateString("en-US", { month: "short", day: "numeric" });

// "Week of Aug 11: 5 approved, 1 under review, 2 unapproved"
function tooltipOf() {
    const parts = this.points.map((point) => `${point.y} ${point.series.name.toLowerCase()}`);
    return `Week of ${this.points[0].key}: ${parts.join(", ")}`;
}

export default function NewToolsTrendCard({ weeks, status }) {
    const hasData = (weeks || []).some((week) => TREND_STATUSES.some((key) => week[key] > 0));
    const categories = useMemo(() => (weeks || []).map((week) => formatDay(week.weekStart)), [weeks]);
    // Listed bottom to top: approved at the base, unapproved on top.
    const series = useMemo(() => TREND_STATUSES.map((key) => ({
        name: STATUS_META[key].label,
        color: STATUS_META[key].color,
        data: (weeks || []).map((week) => week[key] || 0),
    })), [weeks]);
    return (
        <DiscoveryCard
            title="New AI tools discovered"
            subtitle={SUBTITLES.trend}
            legend={<StatusLegend statuses={TREND_STATUSES} />}
            status={status}
            empty={!hasData}
            emptyText="No new tools were discovered in the last 12 weeks."
        >
            <StackedBarChart categories={categories} series={series} height={CHART_HEIGHT} tooltipFormatter={tooltipOf} />
        </DiscoveryCard>
    );
}

import { useMemo } from "react";
import BubblePlot from "@/apps/dashboard/components/shared/BubblePlot";
import DiscoveryCard from "../shared/DiscoveryCard";
import StatusLegend from "./StatusLegend";
import { BUBBLE_STYLE, RISK_LEVEL_LABELS, STATUS_ORDER, SUBTITLES } from "../constants";
import { bubblePoints, devicesAxisMax, devicesTickLabels } from "../transform";

const PLOT_HEIGHT = 300;
const ZONE = { widthPct: 50, heightPct: 50, label: "High risk, spreading" };
const LEVELS_TOP_DOWN = [...RISK_LEVEL_LABELS].reverse();
const LEGEND_COLORS = Object.fromEntries(STATUS_ORDER.map((status) => [status, BUBBLE_STYLE[status].legend]));

export default function RiskReachCard({ rows, status, onOpen }) {
    const axisMax = useMemo(() => devicesAxisMax(rows || []), [rows]);
    const bubbles = useMemo(() => bubblePoints(rows || [], axisMax, onOpen), [rows, axisMax, onOpen]);
    return (
        <DiscoveryCard
            title="Risk and reach"
            subtitle={SUBTITLES.reach}
            legend={<StatusLegend colors={LEGEND_COLORS} />}
            status={status}
            empty={!rows || rows.length === 0}
            emptyText="No tools to plot yet."
        >
            <BubblePlot
                bubbles={bubbles}
                height={PLOT_HEIGHT}
                bands={RISK_LEVEL_LABELS.length}
                bandLabels={LEVELS_TOP_DOWN}
                tickLabels={devicesTickLabels(axisMax)}
                zone={ZONE}
            />
        </DiscoveryCard>
    );
}

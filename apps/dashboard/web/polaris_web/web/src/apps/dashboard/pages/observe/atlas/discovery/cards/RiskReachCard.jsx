import { useMemo, useState } from "react";
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
    const [selected, setSelected] = useState(null);
    // The axis stays fixed while a status is picked, so the circles do not move.
    const axisMax = useMemo(() => devicesAxisMax(rows || []), [rows]);
    const shown = useMemo(() => (selected ? (rows || []).filter((row) => row.status === selected) : rows || []), [rows, selected]);
    const bubbles = useMemo(() => bubblePoints(shown, axisMax, onOpen), [shown, axisMax, onOpen]);
    return (
        <DiscoveryCard
            title="Risk and reach"
            subtitle={SUBTITLES.reach}
            legend={<StatusLegend colors={LEGEND_COLORS} selected={selected} onSelect={setSelected} />}
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

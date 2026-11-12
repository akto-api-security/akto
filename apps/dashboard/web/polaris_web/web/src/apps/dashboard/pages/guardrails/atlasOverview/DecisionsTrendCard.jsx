import { useMemo } from "react";
import { Card } from "@shopify/polaris";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import ViolationsTrendCard from "../overview/ViolationsTrendCard";

const DECISION_SERIES = [
    { key: "blocked", name: "Blocked", color: "#6200EA" },
    { key: "approval", name: "Needs approval", color: "#B38CFF" },
    { key: "alerted", name: "Alerted or warned", color: "#D2D5D8" },
];

export default function DecisionsTrendCard({ decisions, onOpenActivity }) {
    const points = useMemo(() => (decisions?.days || []).map((d) => ({ ...d, ts: d.ts * 1000 })), [decisions]);
    if (decisions === null) return <Card><SpinnerCentered height="240px" /></Card>;
    return <ViolationsTrendCard title="Guardrail decisions" series={DECISION_SERIES} points={points} onOpenRange={onOpenActivity} />;
}

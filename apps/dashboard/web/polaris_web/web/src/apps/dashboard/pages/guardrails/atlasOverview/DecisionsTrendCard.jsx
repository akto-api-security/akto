import { useMemo } from "react";
import { Card } from "@shopify/polaris";
import CardWithHeader from "@/apps/dashboard/pages/dashboard/new_components/CardWithHeader";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import ViolationsTrendCard from "../overview/ViolationsTrendCard";
import { OverviewLoadError } from "./OverviewCard";

const DECISION_SERIES = [
    { key: "blocked", name: "Blocked", color: "#F24122" },
    { key: "approval", name: "Needs approval", color: "#F2B322" },
    { key: "alerted", name: "Alerted or warned", color: "#3BC3D3" },
];

export default function DecisionsTrendCard({ decisions, onOpenActivity }) {
    const points = useMemo(() => (decisions?.days || []).map((d) => ({ ...d, ts: d.ts * 1000 })), [decisions]);
    if (decisions === null) return <Card><SpinnerCentered height="240px" /></Card>;
    if (decisions?.failed) return <CardWithHeader title="Guardrail decisions" hasData><OverviewLoadError /></CardWithHeader>;
    return <ViolationsTrendCard title="Guardrail decisions" series={DECISION_SERIES} points={points} onOpenRange={(start, end) => onOpenActivity({ startTimestamp: start, endTimestamp: end })} />;
}

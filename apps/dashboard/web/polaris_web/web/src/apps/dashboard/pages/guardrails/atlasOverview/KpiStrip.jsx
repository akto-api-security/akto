import { Card, Text, VerticalStack } from "@shopify/polaris";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import func from "@/util/func";
import { formatDurationMs } from "../../observe/llm/constants";
import { EMPTY_VALUE, formatCount } from "./atlasOverviewUtils";

function formatTriageDuration(seconds) {
    const days = Math.floor(seconds / 86400);
    if (days === 0) return formatDurationMs(seconds * 1000);
    const rest = seconds % 86400;
    return rest >= 60 ? `${days}d ${formatDurationMs(rest * 1000)}` : `${days}d`;
}

function kpiValue(value, sub, critical) {
    return (
        <VerticalStack gap="1">
            <Text as="p" variant="heading2xl" color={critical ? "critical" : undefined}>{value}</Text>
            {sub && <Text as="p" variant="bodySm" color="subdued">{sub}</Text>}
        </VerticalStack>
    );
}

function buildKpis(kpis, onOpenActivity) {
    const k = kpis || {};
    const open = k.openViolations || {};
    const critical = k.criticalUnassigned || {};
    const blocked = k.blocked || {};
    const employees = k.employeesInvolved || {};
    const triage = k.medianTriage || {};
    const days = k.periodDays;
    return [
        {
            label: "Open violations",
            value: formatCount(open.count),
            sub: open.change !== undefined && open.change !== null
                ? `${open.change > 0 ? "+" : ""}${formatCount(open.change)} vs previous ${days} ${days === 1 ? "day" : "days"}`
                : "",
            onClick: onOpenActivity,
        },
        {
            label: "Critical, unassigned",
            value: formatCount(critical.count),
            sub: critical.oldestAgeSeconds !== undefined && critical.oldestAgeSeconds !== null ? `Oldest ${func.prettifyEpochDuration(Math.max(1, critical.oldestAgeSeconds))} ago` : "",
            critical: true,
            onClick: onOpenActivity,
        },
        {
            label: "Blocked",
            value: formatCount(blocked.count),
            sub: blocked.sharePct !== undefined && blocked.sharePct !== null ? `${blocked.sharePct}% of all guardrail decisions` : "",
            onClick: onOpenActivity,
        },
        {
            label: "Employees involved",
            value: formatCount(employees.count),
            sub: employees.devices === undefined || employees.devices === null
                ? ""
                : k.managedDevices >= employees.devices
                    ? `On ${formatCount(employees.devices)} of ${formatCount(k.managedDevices)} ${k.managedDevices === 1 ? "device" : "devices"}`
                    : `On ${formatCount(employees.devices)} ${employees.devices === 1 ? "device" : "devices"}`,
        },
        {
            label: "Median time to triage",
            value: triage.seconds !== undefined && triage.seconds !== null ? formatTriageDuration(triage.seconds) : EMPTY_VALUE,
            sub: triage.targetHours ? `Target is ${formatTriageDuration(triage.targetHours * 3600)}` : "",
        },
    ].map((item) => ({ title: item.label, isComp: true, data: kpiValue(item.value, item.sub, item.critical), onClick: item.onClick }));
}

export default function KpiStrip({ kpis, onOpenActivity }) {
    if (kpis === null) return <Card><SpinnerCentered height="96px" /></Card>;
    return <SummaryCardInfo summaryItems={buildKpis(kpis, onOpenActivity)} />;
}

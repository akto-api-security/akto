import { useReducer } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import { Button, HorizontalGrid, HorizontalStack } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import { openViolationInGuardrailViolations } from "@/apps/dashboard/pages/observe/agentic/agenticObserveApi";
import func from "@/util/func";
import values from "@/util/values";
import KpiStrip from "./KpiStrip";
import AttentionCard from "./AttentionCard";
import TriageStatusCard from "./TriageStatusCard";
import DecisionsTrendCard from "./DecisionsTrendCard";
import PolicyViolationsCard from "./PolicyViolationsCard";
import SurfaceListCard from "./SurfaceListCard";
import TopEmployeesCard from "./TopEmployeesCard";
import TopAssetsCard from "./TopAssetsCard";
import guardrailOverviewMock from "./guardrailOverviewMock";

const POLICIES_PATH = "/dashboard/guardrails/policies";

function formatCount(n) {
    return Number(n).toLocaleString();
}

function buildKpis(data, openActivity) {
    const { kpis, periodDays, managedDevices } = data;
    const change = kpis.openViolations.changePct;
    return [
        {
            label: "Open violations",
            value: formatCount(kpis.openViolations.count),
            sub: `${change > 0 ? "+" : ""}${change}% vs previous ${periodDays} days`,
            onClick: openActivity,
        },
        {
            label: "Critical, unassigned",
            value: formatCount(kpis.criticalUnassigned.count),
            sub: `Oldest ${kpis.criticalUnassigned.oldestAgeHours}h ago`,
            critical: true,
            onClick: openActivity,
        },
        {
            label: "Blocked or redacted",
            value: formatCount(kpis.blockedOrRedacted.count),
            sub: `${kpis.blockedOrRedacted.sharePct}% of all guardrail decisions`,
            onClick: openActivity,
        },
        {
            label: "Employees involved",
            value: formatCount(kpis.employeesInvolved.count),
            sub: `On ${formatCount(kpis.employeesInvolved.devices)} of ${formatCount(managedDevices)} devices`,
        },
        {
            label: "Median time to triage",
            value: `${kpis.medianTriage.hours}h`,
            sub: `Target is ${kpis.medianTriage.targetHours}h`,
        },
    ];
}

function GuardrailsOverview() {
    const navigate = useNavigate();
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2],
    );
    const data = guardrailOverviewMock;
    const openActivity = () => openViolationInGuardrailViolations();
    const openPolicies = () => navigate(POLICIES_PATH);

    const components = [
        <KpiStrip key="kpis" items={buildKpis(data, openActivity)} />,
        <HorizontalGrid key="attention" columns={{ xs: 1, md: ["twoThirds", "oneThird"] }} gap="4">
            <AttentionCard rows={data.attention} onReview={openActivity} onViewAll={openActivity} />
            <TriageStatusCard triage={data.triage} periodDays={data.periodDays} onStatusClick={openActivity} onAssign={openActivity} />
        </HorizontalGrid>,
        <HorizontalGrid key="decisions" columns={{ xs: 1, md: 2 }} gap="4">
            <DecisionsTrendCard decisions={data.decisions} />
            <PolicyViolationsCard policies={data.byPolicy} onPolicyClick={openActivity} onTune={openPolicies} />
        </HorizontalGrid>,
        <HorizontalGrid key="lists" columns={{ xs: 1, md: 3 }} gap="4">
            <SurfaceListCard surfaces={data.bySurface} onSurfaceClick={openActivity} />
            <TopEmployeesCard employees={data.topEmployees} />
            <TopAssetsCard assets={data.topAssets} />
        </HorizontalGrid>,
    ];

    return (
        <PageWithMultipleCards
            title="Guardrail overview"
            subtitle={`Violations across ${formatCount(data.managedDevices)} managed devices and ${formatCount(data.discoveredAssets)} discovered AI agents, apps and MCP servers`}
            isFirstPage
            secondaryActions={
                <HorizontalStack gap="2" blockAlign="center">
                    <DateRangeFilter
                        initialDispatch={currDateRange}
                        dispatch={(dateObj) =>
                            dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
                        }
                    />
                    <Button icon={ExportMinor}>Export report</Button>
                </HorizontalStack>
            }
            primaryAction={<Button primary onClick={openActivity}>Triage open violations</Button>}
            components={components}
        />
    );
}

export default GuardrailsOverview;

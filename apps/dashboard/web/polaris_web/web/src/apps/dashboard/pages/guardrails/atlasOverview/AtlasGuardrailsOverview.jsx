import { useEffect, useReducer, useState } from "react";
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
import guardrailsApi from "../api";

const POLICIES_PATH = "/dashboard/guardrails/policies";
const EMPTY_VALUE = "—";

function formatCount(n) {
    return n === undefined || n === null ? EMPTY_VALUE : Number(n).toLocaleString();
}

function buildKpis(kpis, openActivity) {
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
            sub: open.changePct !== undefined && open.changePct !== null
                ? `${open.changePct > 0 ? "+" : ""}${open.changePct}% vs previous ${days} ${days === 1 ? "day" : "days"}`
                : "",
            onClick: openActivity,
        },
        {
            label: "Critical, unassigned",
            value: formatCount(critical.count),
            sub: critical.oldestAgeHours !== undefined && critical.oldestAgeHours !== null ? `Oldest ${critical.oldestAgeHours}h ago` : "",
            critical: true,
            onClick: openActivity,
        },
        {
            label: "Blocked",
            value: formatCount(blocked.count),
            sub: blocked.sharePct !== undefined && blocked.sharePct !== null ? `${blocked.sharePct}% of all guardrail decisions` : "",
            onClick: openActivity,
        },
        {
            label: "Employees involved",
            value: formatCount(employees.count),
            sub: `On ${formatCount(employees.devices)} of ${formatCount(k.managedDevices)} devices`,
        },
        {
            label: "Median time to triage",
            value: triage.hours !== undefined && triage.hours !== null ? `${triage.hours}h` : EMPTY_VALUE,
            sub: triage.targetHours ? `Target is ${triage.targetHours}h` : "",
        },
    ];
}

function AtlasGuardrailsOverview() {
    const navigate = useNavigate();
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2],
    );
    const [kpis, setKpis] = useState(null);
    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    useEffect(() => {
        let cancelled = false;
        setKpis(null);
        guardrailsApi.fetchAtlasGuardrailsOverviewKpis(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setKpis(resp); })
            .catch(() => { if (!cancelled) setKpis(null); });
        return () => { cancelled = true; };
    }, [startTimestamp, endTimestamp]);

    const data = guardrailOverviewMock;
    const openActivity = () => openViolationInGuardrailViolations();
    const openPolicies = () => navigate(POLICIES_PATH);

    const components = [
        <KpiStrip key="kpis" items={buildKpis(kpis, openActivity)} />,
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
            subtitle={`Violations across ${formatCount(kpis?.managedDevices)} managed devices and ${formatCount(data.discoveredAssets)} discovered AI agents, apps and MCP servers`}
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

export default AtlasGuardrailsOverview;

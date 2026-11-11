import { useEffect, useReducer, useState } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import { Button, HorizontalGrid, HorizontalStack } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import { openViolationInGuardrailViolations } from "@/apps/dashboard/pages/observe/agentic/agenticObserveApi";
import func from "@/util/func";
import KpiStrip from "./KpiStrip";
import AttentionCard from "./AttentionCard";
import TriageStatusCard from "./TriageStatusCard";
import DecisionsTrendCard from "./DecisionsTrendCard";
import PolicyViolationsCard from "./PolicyViolationsCard";
import ViolationsSourcesCard from "./ViolationsSourcesCard";
import TopEmployeesCard from "./TopEmployeesCard";
import TopAssetsCard from "./TopAssetsCard";
import guardrailOverviewMock from "./guardrailOverviewMock";
import guardrailsApi from "../api";
import { formatDurationMs } from "../../observe/llm/constants";
import { getGuardrailRuleGroupHeading } from "../../threat_detection/constants/guardrailRuleDefinitions";
import { formatAssetDisplayName } from "../violations/ViolationsPage";
import { CLIENT_TYPES } from "../../observe/agentic/mcpClientHelper";

const POLICIES_PATH = "/dashboard/guardrails/policies";
const EMPTY_VALUE = "—";
const POLICY_ROWS = 8;
const MISCONFIGURED_LABEL = "Misconfigured settings";
const ASSET_TYPE_LABELS = {
    [CLIENT_TYPES.LLM]: "Browser AI apps",
    [CLIENT_TYPES.AI_AGENT]: "AI agents & CLIs",
    [CLIENT_TYPES.MCP_SERVER]: "MCP servers",
    [CLIENT_TYPES.SKILL]: "Skills",
    [CLIENT_TYPES.PLUGIN]: "Plugins",
    [CLIENT_TYPES.SAAS_AGENT]: "SaaS agents",
    Other: "Others",
};

function formatCount(n) {
    return n === undefined || n === null ? EMPTY_VALUE : Number(n).toLocaleString();
}

function formatTriageDuration(seconds) {
    const days = Math.floor(seconds / 86400);
    if (days === 0) return formatDurationMs(seconds * 1000);
    const rest = seconds % 86400;
    return rest >= 60 ? `${days}d ${formatDurationMs(rest * 1000)}` : `${days}d`;
}

function buildPolicyRows(rules) {
    const groups = {};
    (rules || []).forEach((r) => {
        const name = r.misconfigured ? MISCONFIGURED_LABEL : (getGuardrailRuleGroupHeading(r.rule) || r.rule || "-");
        const group = groups[name] || (groups[name] = { name, count: 0, ignored: 0 });
        group.count += r.count;
        group.ignored += r.ignored;
    });
    return Object.values(groups)
        .sort((a, b) => b.count - a.count)
        .slice(0, POLICY_ROWS)
        .map((g) => ({ name: g.name, count: g.count, ignoredPct: Math.round((g.ignored * 100) / g.count) }));
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
            sub: open.change !== undefined && open.change !== null
                ? `${open.change > 0 ? "+" : ""}${formatCount(open.change)} vs previous ${days} ${days === 1 ? "day" : "days"}`
                : "",
            onClick: openActivity,
        },
        {
            label: "Critical, unassigned",
            value: formatCount(critical.count),
            sub: critical.oldestAgeSeconds !== undefined && critical.oldestAgeSeconds !== null ? `Oldest ${func.prettifyEpochDuration(Math.max(1, critical.oldestAgeSeconds))} ago` : "",
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
    ];
}

function plural(count, one, many) {
    return `${formatCount(count)} ${count === 1 ? one : many}`;
}

function buildViolationsSourcesRows(assetTypes) {
    return (assetTypes || [])
        .map((s) => {
            const examples = s.type === CLIENT_TYPES.MCP_SERVER
                ? (s.servers ? `${plural(s.servers, "server", "servers")} on ${plural(s.devices, "device", "devices")} · ${formatCount(s.unapproved)} unapproved` : "")
                : [...new Set((s.examples || []).map((x) => x.name || formatAssetDisplayName(x.host)).filter(Boolean))].join(", ");
            return {
                name: ASSET_TYPE_LABELS[s.type] || s.type,
                examples,
                count: s.count,
                changePct: s.changePct,
            };
        })
        .sort((a, b) => b.count - a.count);
}

function AtlasGuardrailsOverview() {
    const navigate = useNavigate();
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        func.getLast30DaysRange(),
    );
    const [kpis, setKpis] = useState(null);
    const [triage, setTriage] = useState(null);
    const [decisions, setDecisions] = useState(null);
    const [policies, setPolicies] = useState(null);
    const [violationsSources, setViolationsSources] = useState(null);
    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    useEffect(() => {
        let cancelled = false;
        setKpis(null);
        setTriage(null);
        guardrailsApi.fetchSummary(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setKpis(resp); })
            .catch(() => { if (!cancelled) setKpis(null); });
        guardrailsApi.fetchTriageStatus(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setTriage(resp); })
            .catch(() => { if (!cancelled) setTriage(null); });
        setDecisions(null);
        guardrailsApi.fetchDecisionsTrend(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setDecisions(resp); })
            .catch(() => { if (!cancelled) setDecisions(null); });
        setPolicies(null);
        guardrailsApi.fetchViolationsByPolicy(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setPolicies(resp); })
            .catch(() => { if (!cancelled) setPolicies(null); });
        setViolationsSources(null);
        guardrailsApi.fetchViolationsSources(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setViolationsSources(resp); })
            .catch(() => { if (!cancelled) setViolationsSources(null); });
        return () => { cancelled = true; };
    }, [startTimestamp, endTimestamp]);

    const data = guardrailOverviewMock;
    const openActivity = () => openViolationInGuardrailViolations();
    const openPolicies = () => navigate(POLICIES_PATH);

    const components = [
        <KpiStrip key="kpis" items={buildKpis(kpis, openActivity)} />,
        <HorizontalGrid key="attention" columns={{ xs: 1, md: ["twoThirds", "oneThird"] }} gap="4">
            <AttentionCard rows={data.attention} onReview={openActivity} onViewAll={openActivity} />
            <TriageStatusCard triage={{ statuses: triage?.statuses || [], staleCriticalUnassigned: triage?.staleCriticalUnassigned || 0, staleAfterHours: triage?.staleAfterHours }} periodDays={triage?.periodDays} onStatusClick={openActivity} onAssign={openActivity} />
        </HorizontalGrid>,
        <HorizontalGrid key="decisions" columns={{ xs: 1, md: 2 }} gap="4">
            <DecisionsTrendCard decisions={decisions} />
            <PolicyViolationsCard policies={buildPolicyRows(policies?.rules)} onPolicyClick={openActivity} onTune={openPolicies} />
        </HorizontalGrid>,
        <HorizontalGrid key="lists" columns={{ xs: 1, md: 3 }} gap="4">
            <ViolationsSourcesCard rows={buildViolationsSourcesRows(violationsSources?.assetTypes)} periodDays={violationsSources?.periodDays} onRowClick={openActivity} />
            <TopEmployeesCard employees={data.topEmployees} />
            <TopAssetsCard assets={data.topAssets} />
        </HorizontalGrid>,
    ];

    return (
        <PageWithMultipleCards
            title="Guardrail overview"
            subtitle={`Violations across ${plural(kpis?.managedDevices, "managed device", "managed devices")} and${formatCount(data.discoveredAssets)} discovered AI agents, apps and MCP servers`}
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

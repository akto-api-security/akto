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
const TOP_AGENTIC_ASSETS = 5;
const AGENTIC_ASSET_KINDS = {
    [CLIENT_TYPES.LLM]: "Browser AI app",
    [CLIENT_TYPES.AI_AGENT]: "AI agent CLI",
    [CLIENT_TYPES.SAAS_AGENT]: "SaaS agent",
    [CLIENT_TYPES.MCP_SERVER]: "MCP server",
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

function buildTopAgenticAssetRows(collections) {
    const groups = new Map();
    (collections || []).forEach((c) => {
        const name = formatAssetDisplayName(c.host) || c.host;
        const group = groups.get(name) || { name, type: c.type, count: 0, devices: new Set(), skills: new Set(), unapproved: false, personal: false };
        group.count += c.count;
        if (c.device) group.devices.add(c.device);
        (c.skills || []).forEach((skill) => group.skills.add(skill));
        if (!c.approved) group.unapproved = true;
        if (c.personal) group.personal = true;
        groups.set(name, group);
    });
    return [...groups.values()]
        .sort((a, b) => b.count - a.count)
        .slice(0, TOP_AGENTIC_ASSETS)
        .map((g) => {
            const isMcpServer = g.type === CLIENT_TYPES.MCP_SERVER;
            const detail = isMcpServer
                ? plural(g.devices.size, "device", "devices")
                : (g.skills.size ? plural(g.skills.size, "skill", "skills") : "");
            return {
                name: g.name,
                kind: [AGENTIC_ASSET_KINDS[g.type] || g.type, detail].filter(Boolean).join(" · "),
                tag: isMcpServer ? (g.unapproved ? "Unapproved" : "Approved") : (g.personal ? "Personal" : undefined),
                count: formatCount(g.count),
            };
        });
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
    const [topAgenticAssets, setTopAgenticAssets] = useState(null);
    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    useEffect(() => {
        let cancelled = false;
        setKpis(null);
        setTriage(null);
        guardrailsApi.fetchSummary(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setKpis(resp); })
            .catch(() => { if (!cancelled) setKpis({}); });
        guardrailsApi.fetchTriageStatus(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setTriage(resp); })
            .catch(() => { if (!cancelled) setTriage({}); });
        setDecisions(null);
        guardrailsApi.fetchDecisionsTrend(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setDecisions(resp); })
            .catch(() => { if (!cancelled) setDecisions({}); });
        setPolicies(null);
        guardrailsApi.fetchViolationsByPolicy(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setPolicies(resp); })
            .catch(() => { if (!cancelled) setPolicies({}); });
        setViolationsSources(null);
        guardrailsApi.fetchViolationsSources(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setViolationsSources(resp); })
            .catch(() => { if (!cancelled) setViolationsSources({}); });
        setTopAgenticAssets(null);
        guardrailsApi.fetchTopAgenticAssets(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setTopAgenticAssets(resp); })
            .catch(() => { if (!cancelled) setTopAgenticAssets({}); });
        return () => { cancelled = true; };
    }, [startTimestamp, endTimestamp]);

    const data = guardrailOverviewMock;
    const openActivity = () => openViolationInGuardrailViolations();
    const openPolicies = () => navigate(POLICIES_PATH);

    const components = [
        <KpiStrip key="kpis" items={buildKpis(kpis, openActivity)} loading={kpis === null} />,
        <HorizontalGrid key="attention" columns={{ xs: 1, md: ["twoThirds", "oneThird"] }} gap="4">
            <AttentionCard rows={data.attention} onReview={openActivity} onViewAll={openActivity} />
            <TriageStatusCard triage={{ statuses: triage?.statuses || [], staleCriticalUnassigned: triage?.staleCriticalUnassigned || 0, staleAfterHours: triage?.staleAfterHours }} periodDays={triage?.periodDays} loading={triage === null} onStatusClick={openActivity} onAssign={openActivity} />
        </HorizontalGrid>,
        <HorizontalGrid key="decisions" columns={{ xs: 1, md: 2 }} gap="4">
            <DecisionsTrendCard decisions={decisions} loading={decisions === null} />
            <PolicyViolationsCard policies={buildPolicyRows(policies?.rules)} loading={policies === null} onPolicyClick={openActivity} onTune={openPolicies} />
        </HorizontalGrid>,
        <HorizontalGrid key="lists" columns={{ xs: 1, md: 3 }} gap="4">
            <ViolationsSourcesCard rows={buildViolationsSourcesRows(violationsSources?.assetTypes)} periodDays={violationsSources?.periodDays} loading={violationsSources === null} onRowClick={openActivity} />
            <TopEmployeesCard employees={data.topEmployees} />
            <TopAssetsCard assets={buildTopAgenticAssetRows(topAgenticAssets?.collections)} loading={topAgenticAssets === null} />
        </HorizontalGrid>,
    ];

    return (
        <PageWithMultipleCards
            title="Guardrail overview"
            subtitle={`Violations across ${kpis?.managedDevices ? `${plural(kpis.managedDevices, "managed device", "managed devices")} and ` : ""}${formatCount(data.discoveredAssets)} discovered AI agents, apps and MCP servers`}
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

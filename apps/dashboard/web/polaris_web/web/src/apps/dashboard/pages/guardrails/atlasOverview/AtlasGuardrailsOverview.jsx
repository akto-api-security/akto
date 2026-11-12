import { useEffect, useMemo, useReducer, useState } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import { Avatar, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { ExportMinor } from "@shopify/polaris-icons";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import { openViolationInGuardrailViolations } from "@/apps/dashboard/pages/observe/agentic/agenticObserveApi";
import func from "@/util/func";
import values from "@/util/values";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import AttentionCard from "./AttentionCard";
import TriageStatusCard from "./TriageStatusCard";
import ViolationsTrendCard from "../overview/ViolationsTrendCard";
import PolicyViolationsCard from "./PolicyViolationsCard";
import { initialsOf } from "@/apps/dashboard/components/shared/PeopleList";
import ItemListCard from "@/apps/dashboard/components/shared/ItemListCard";
import AssetTagBadge from "@/apps/dashboard/components/shared/AssetTagBadge";
import DiscoveryCard from "../../observe/atlas/discovery/shared/DiscoveryCard";
import { LOAD_STATE } from "../../observe/atlas/discovery/constants";
import guardrailOverviewMock from "./guardrailOverviewMock";
import guardrailsApi from "../api";
import { formatDurationMs } from "../../observe/llm/constants";
import { getGuardrailRuleGroupHeading } from "../../threat_detection/constants/guardrailRuleDefinitions";
import { formatAssetDisplayName } from "../violations/ViolationsPage";
import { CLIENT_TYPES } from "../../observe/agentic/mcpClientHelper";
import observeFunc from "../../observe/transform";

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
    [CLIENT_TYPES.SKILL]: "Skill",
};
const AGENTIC_ASSET_STATUS_TAGS = {
    APPROVED: "Approved",
    BLOCKED: "Blocked",
    UNDER_REVIEW: "Under review",
    UNAPPROVED: "Unapproved",
};
const TOP_EMPLOYEES = 5;
const TREND_UP_COLOR = "#8E1F0B";
const TREND_DOWN_COLOR = "#0C5132";
const DECISION_SERIES = [
    { key: "blocked", name: "Blocked", color: "#6200EA" },
    { key: "approval", name: "Needs approval", color: "#B38CFF" },
    { key: "alerted", name: "Alerted or warned", color: "#D2D5D8" },
];

function formatCount(n) {
    return n === undefined || n === null ? EMPTY_VALUE : observeFunc.formatNumberWithCommas(n);
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
        .map((g) => ({ name: g.name, count: g.count, ignoredPct: Math.round((g.ignored * 100) / g.count) }));
}

function kpiValue(value, sub, critical) {
    return (
        <VerticalStack gap="1">
            <Text as="p" variant="heading2xl" color={critical ? "critical" : undefined}>{value}</Text>
            {sub && <Text as="p" variant="bodySm" color="subdued">{sub}</Text>}
        </VerticalStack>
    );
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
    ].map((item) => ({ title: item.label, isComp: true, data: kpiValue(item.value, item.sub, item.critical), onClick: item.onClick }));
}

function plural(count, one, many) {
    return `${formatCount(count)} ${count === 1 ? one : many}`;
}

function buildViolationsSourcesRows(assetTypes, periodDays, openActivity) {
    return (assetTypes || [])
        .map((s) => {
            const examples = s.type === CLIENT_TYPES.MCP_SERVER
                ? (s.servers ? `${plural(s.servers, "server", "servers")} on ${plural(s.devices, "device", "devices")} · ${formatCount(s.unapproved)} unapproved` : "")
                : [...new Set((s.examples || []).map((x) => x.name || formatAssetDisplayName(x.host)).filter(Boolean))].join(", ");
            return {
                id: s.type,
                title: ASSET_TYPE_LABELS[s.type] || s.type,
                description: examples || undefined,
                count: s.count,
                onClick: openActivity,
                tag: (
                    <VerticalStack gap="0" inlineAlign="end">
                        <Text variant="bodyMd" fontWeight="semibold">{formatCount(s.count)}</Text>
                        {s.changePct !== undefined && s.changePct !== null && (
                            <Text variant="bodySm" color={s.changePct === 0 ? "subdued" : undefined}>
                                <span style={{ whiteSpace: "nowrap", ...(s.changePct === 0 ? {} : { color: s.changePct > 0 ? TREND_UP_COLOR : TREND_DOWN_COLOR }) }}>
                                    {`${formatChange(s.changePct)} vs previous ${periodDays} ${periodDays === 1 ? "day" : "days"}`}
                                </span>
                            </Text>
                        )}
                    </VerticalStack>
                ),
            };
        })
        .sort((a, b) => b.count - a.count);
}

function buildTopAgenticAssetRows(assets, openActivity) {
    return (assets || []).map((a) => ({
        id: a.key,
        title: a.name,
        onClick: openActivity,
        description: [AGENTIC_ASSET_KINDS[a.type] || a.type, plural(a.people, "person", "people"), plural(a.devices, "device", "devices")].join(" · "),
        tag: (
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                {a.personal && <AssetTagBadge tag="Personal" />}
                <AssetTagBadge tag={AGENTIC_ASSET_STATUS_TAGS[a.status]} />
                <Text variant="bodyMd" fontWeight="semibold">{formatCount(a.count)}</Text>
            </HorizontalStack>
        ),
    }));
}

function buildTopEmployeeRows(employees, openActivity) {
    return (employees || []).map((e) => {
        const tags = (e.tags || []).map((t) => func.toSentenceCase(t));
        const team = tags.length > 0 ? `${tags[0]}${tags.length > 1 ? ` +${tags.length - 1}` : ""}` : "";
        return {
            id: e.name,
            media: <Avatar size="small" initials={initialsOf(e.name)} name={e.name} />,
            title: e.name,
            onClick: openActivity,
            description: [team, plural(e.devices, "device", "devices")].filter(Boolean).join(" · "),
            tag: (
                <VerticalStack gap="0" inlineAlign="end">
                    <Text variant="bodyMd" fontWeight="semibold">{formatCount(e.count)}</Text>
                    {e.critical > 0 && <Text variant="bodySm" color="critical">{`${formatCount(e.critical)} critical`}</Text>}
                </VerticalStack>
            ),
        };
    });
}

function formatChange(pct) {
    if (pct === 0) return "0%";
    return pct > 0 ? `+${pct}%` : `−${Math.abs(pct)}%`;
}

function AtlasGuardrailsOverview() {
    const navigate = useNavigate();
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2],
    );
    const [kpis, setKpis] = useState(null);
    const [triage, setTriage] = useState(null);
    const [decisions, setDecisions] = useState(null);
    const [policies, setPolicies] = useState(null);
    const [violationsSources, setViolationsSources] = useState(null);
    const [topAgenticAssets, setTopAgenticAssets] = useState(null);
    const [topEmployees, setTopEmployees] = useState(null);
    const policyRows = useMemo(() => buildPolicyRows(policies?.rules), [policies]);
    const decisionPoints = useMemo(() => (decisions?.days || []).map((d) => ({ ...d, ts: d.ts * 1000 })), [decisions]);
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
        setTopEmployees(null);
        guardrailsApi.fetchTopEmployees(startTimestamp, endTimestamp)
            .then((resp) => { if (!cancelled) setTopEmployees(resp); })
            .catch(() => { if (!cancelled) setTopEmployees({}); });
        return () => { cancelled = true; };
    }, [startTimestamp, endTimestamp]);

    const data = guardrailOverviewMock;
    const openActivity = () => openViolationInGuardrailViolations();
    const topAssetRows = buildTopAgenticAssetRows(topAgenticAssets?.assets, openActivity);
    const topEmployeeRows = buildTopEmployeeRows(topEmployees?.employees, openActivity);
    const violationsSourcesRows = buildViolationsSourcesRows(violationsSources?.assetTypes, violationsSources?.periodDays, openActivity);
    const openPolicies = () => navigate(POLICIES_PATH);

    const components = [
        kpis === null
            ? <Card key="kpis"><SpinnerCentered height="96px" /></Card>
            : <SummaryCardInfo key="kpis" summaryItems={buildKpis(kpis, openActivity)} />,
        <HorizontalGrid key="attention" columns={{ xs: 1, md: ["twoThirds", "oneThird"] }} gap="4">
            <AttentionCard rows={data.attention} onReview={openActivity} onViewAll={openActivity} />
            <TriageStatusCard triage={{ statuses: triage?.statuses || [], staleCriticalUnassigned: triage?.staleCriticalUnassigned || 0, staleAfterHours: triage?.staleAfterHours }} periodDays={triage?.periodDays} loading={triage === null} onStatusClick={openActivity} onAssign={openActivity} />
        </HorizontalGrid>,
        <HorizontalGrid key="decisions" columns={{ xs: 1, md: 2 }} gap="4">
            {decisions === null
                ? <Card><SpinnerCentered height="240px" /></Card>
                : <ViolationsTrendCard title="Guardrail decisions" series={DECISION_SERIES} points={decisionPoints} onOpenRange={openActivity} />}
            <PolicyViolationsCard policies={policyRows} pageSize={POLICY_ROWS} loading={policies === null} onPolicyClick={openActivity} onTune={openPolicies} />
        </HorizontalGrid>,
        <HorizontalGrid key="lists" columns={{ xs: 1, md: 3 }} gap="4">
            <DiscoveryCard title="Where violations happen" status={violationsSources === null ? LOAD_STATE.LOADING : LOAD_STATE.READY}>
                <ItemListCard items={violationsSourcesRows} emptyText="No violations in this period." />
            </DiscoveryCard>
            <DiscoveryCard title="Employees with most violations" status={topEmployees === null ? LOAD_STATE.LOADING : LOAD_STATE.READY}>
                <ItemListCard items={topEmployeeRows} limit={TOP_EMPLOYEES} emptyText="No employees with violations in this period." />
            </DiscoveryCard>
            <DiscoveryCard title="Top agents, apps and servers involved" status={topAgenticAssets === null ? LOAD_STATE.LOADING : LOAD_STATE.READY}>
                <ItemListCard items={topAssetRows} limit={TOP_AGENTIC_ASSETS} emptyText="No agents, apps or servers involved in this period." />
            </DiscoveryCard>
        </HorizontalGrid>,
    ];

    return (
        <PageWithMultipleCards
            title="Guardrail overview"
            subtitle="Violations across all managed devices and discovered AI agents, apps and MCP servers"
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

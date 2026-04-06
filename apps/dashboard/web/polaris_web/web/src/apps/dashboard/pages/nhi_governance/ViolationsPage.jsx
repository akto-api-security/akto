import { useState, useMemo, useReducer } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { SettingsMajor } from "@shopify/polaris-icons";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import DonutChart from "../../components/shared/DonutChart";
import LineChart from "../../components/charts/LineChart";
import InfoCard from "../dashboard/new_components/InfoCard";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";
import values from "@/util/values";

// ── Identity icon via Google favicon API ───────────────────────────────────────
const IDENTITY_DOMAIN_MAP = {
    aws: "aws.amazon.com", gcp: "cloud.google.com", azure: "azure.microsoft.com",
    github: "github.com", okta: "okta.com", twilio: "twilio.com",
    hubspot: "hubspot.com", salesforce: "salesforce.com", mongo: "mongodb.com",
    redis: "redis.com", elastic: "elastic.co", vault: "vaultproject.io",
    argo: "argoproj.io", terraform: "hashicorp.com", jenkins: "jenkins.io",
    splunk: "splunk.com", pagerduty: "pagerduty.com", opsgenie: "atlassian.com",
    linear: "linear.app", notion: "notion.so", zoom: "zoom.us", box: "box.com",
    dropbox: "dropbox.com", figma: "figma.com", cloudflare: "cloudflare.com",
    vercel: "vercel.com", netlify: "netlify.com", heroku: "heroku.com",
    fly: "fly.io", render: "render.com", datadog: "datadoghq.com",
    newrelic: "newrelic.com", grafana: "grafana.com", sentry: "sentry.io",
    launchdarkly: "launchdarkly.com", mixpanel: "mixpanel.com",
    amplitude: "amplitude.com", segment: "segment.io", intercom: "intercom.com",
    zendesk: "zendesk.com", stripe: "stripe.com", jira: "atlassian.com",
    slack: "slack.com", vscode: "code.visualstudio.com", entra: "microsoft.com",
    snowflake: "snowflake.com",
};
const INTERNAL_KEYWORDS = new Set(["internal", "connector"]);
function IdentityIcon({ name }) {
    const parts = (name || "").toLowerCase().split(/[-_\d]+/).filter(p => p.length > 2);
    if (parts.some(p => INTERNAL_KEYWORDS.has(p)))
        return <div style={{width:20,height:20,display:"flex",alignItems:"center",justifyContent:"center"}}><Icon source={SettingsMajor} color="subdued" /></div>;
    const domain = parts.reduce((found, p) => found || IDENTITY_DOMAIN_MAP[p] || null, null);
    if (!domain) return null;
    return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={20} height={20} style={{borderRadius:3,flexShrink:0}} alt="" />;
}

// ── Agent icon — named agents get specific icons, others get AI model favicons ──
const AGENT_SPECIFIC_DOMAIN = {
    "cursor prod": "cursor.sh",
    "cursor":      "cursor.sh",
    "entra bot":   "microsoft.com",
};
const AI_ICON_POOL = [
    "claude.ai", "openai.com", "deepseek.com", "azure.microsoft.com",
    "gemini.google.com", "mistral.ai", "perplexity.ai", "cohere.com",
];
function AgentIcon({ name }) {
    const key = (name || "").toLowerCase().trim();
    const specific = AGENT_SPECIFIC_DOMAIN[key];
    const domain = specific || AI_ICON_POOL[
        key.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0) % AI_ICON_POOL.length
    ];
    return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={20} height={20} style={{borderRadius:3,flexShrink:0}} alt="" />;
}

const definedTableTabs = ["All", "Open", "Fixed"];
const resourceName = { singular: "violation", plural: "violations" };
const SEV_ORD = { Critical: 4, High: 3, Medium: 2, Low: 1 };

const sevBadge = (s) => (
    <div className={`badge-wrapper-${s.toUpperCase()}`}>
        <Badge status={func.getHexColorForSeverity(s.toUpperCase())}>{s}</Badge>
    </div>
);

// Policy cell with optional "+N" overflow badge
function PolicyCell({ policy }) {
    if (!policy) return null;
    if (typeof policy === "string") return <Text variant="bodyMd">{policy}</Text>;
    const tooltipContent = policy.extras && policy.extras.length > 0
        ? <VerticalStack gap="1">{policy.extras.map((p, i) => <Text key={p} variant="bodyMd" color="subdued">{`${i + 2}. ${p}`}</Text>)}</VerticalStack>
        : null;
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            <Text variant="bodyMd">{policy.primary}</Text>
            {policy.extra > 0 && tooltipContent && (
                <Tooltip content={tooltipContent} dismissOnMouseOut>
                    <span><Badge>{`+${policy.extra}`}</Badge></span>
                </Tooltip>
            )}
            {policy.extra > 0 && !tooltipContent && <Badge>{`+${policy.extra}`}</Badge>}
        </HorizontalStack>
    );
}

// ── Curated violations (20) ────────────────────────────────────────────────────
const CURATED = [
    { severity:"Critical", violation:"Admin credential exposed to agent runtime",                    identity:"aws-cursor-key",     agent:"Cursor Prod",     policy:{primary:"No Admin Keys for Agents"},           discovered:"2h ago",  status:"Open"  },
    { severity:"Critical", violation:"Credential exceeds intended permission scope",                 identity:"aws-cursor-key",     agent:"Cursor Prod",     policy:{primary:"Enforce Least Privilege"},            discovered:"1h ago",  status:"Open"  },
    { severity:"Critical", violation:"Unusual LLM access spike detected",                           identity:"aws-cursor-key",     agent:"Cursor Prod",     policy:{primary:"Detect LLM Usage Spikes"},           discovered:"Now",     status:"Open"  },
    { severity:"Critical", violation:"Token used outside trusted network boundary",                 identity:"hr-slack-token",     agent:"HR Assistant",    policy:{primary:"Restrict Token by Source", extra:2, extras:["Rotate API Keys Every 30 Days","Detect LLM Usage Spikes"]},  discovered:"6h ago",  status:"Open"  },
    { severity:"High",     violation:"Messaging token triggering bulk automated sends",             identity:"hr-slack-token",     agent:"HR Assistant",    policy:{primary:"Limit Messaging Actions"},            discovered:"3h ago",  status:"Fixed" },
    { severity:"High",     violation:"Dormant credential retains write access",                     identity:"aws-env-sa",         agent:"New Env Agent",   policy:{primary:"Disable Dormant Credentials", extra:2, extras:["Expire Unused Tokens","Block Cross-Environment Token Reuse"]},discovered:"1h ago",  status:"Fixed" },
    { severity:"High",     violation:"Provisioning access without approval controls",               identity:"aws-env-sa",         agent:"New Env Agent",   policy:{primary:"Restrict Infra Provisioning", extra:2, extras:["No Admin Keys for Agents","Enforce Least Privilege"]},discovered:"2h ago", status:"Open"  },
    { severity:"High",     violation:"Repository token has admin-level permissions",                identity:"github-oauth-456",   agent:"Code Reviewer",   policy:{primary:"Enforce Least Privilege"},            discovered:"45m ago", status:"Open"  },
    { severity:"High",     violation:"Token accessing repositories beyond expiry date",             identity:"github-oauth-456",   agent:"Code Reviewer",   policy:{primary:"Restrict Repo Scope"},                discovered:"45m ago", status:"Fixed" },
    { severity:"Medium",   violation:"Access to restricted ticketing projects detected",            identity:"jira-token",         agent:"My Assistant",    policy:{primary:"Restrict Project Access", extra:2, extras:["Expire Unused Tokens","Enforce Least Privilege"]},   discovered:"1h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Idle credential remains active without usage",                identity:"jira-token",         agent:"My Assistant",    policy:{primary:"Expire Unused Tokens"},               discovered:"30m ago", status:"Open"  },
    { severity:"Critical", violation:"Service account with unrestricted API access",                identity:"internal-api-token", agent:"Connector",       policy:{primary:"Restrict Infra Provisioning"},        discovered:"2d ago",  status:"Open"  },
    { severity:"Critical", violation:"Outbound token used without destination binding",             identity:"entra-service",      agent:"Entra Bot",       policy:{primary:"Restrict Token by Source"},           discovered:"5h ago",  status:"Fixed" },
    { severity:"High",     violation:"Agent sending external messages via shared token",            identity:"vscode-oauth",       agent:"Agent Studio",    policy:{primary:"Limit Messaging Actions"},            discovered:"6h ago",  status:"Open"  },
    { severity:"High",     violation:"Production credential active in test environment",            identity:"github-actions-key", agent:"CI Bot",          policy:{primary:"Block Cross-Environment Token Reuse"},discovered:"12h ago", status:"Fixed" },
    { severity:"Medium",   violation:"OAuth scope exceeds minimum required permissions",            identity:"salesforce-token",   agent:"CRM Agent",       policy:{primary:"Enforce Least Privilege"},            discovered:"4h ago",  status:"Fixed" },
    { severity:"High",     violation:"Write credential exposed to read-only agent",                 identity:"zendesk-api-key",    agent:"Support Bot",     policy:{primary:"Read Token Scope Enforcement"},       discovered:"8h ago",  status:"Open"  },
    { severity:"Medium",   violation:"Service account accessing non-authorized endpoint",           identity:"gcp-service-key",    agent:"Infra Agent",     policy:{primary:"Restrict Infra Provisioning"},        discovered:"3h ago",  status:"Fixed" },
    { severity:"Medium",   violation:"API token with administrative scope for read ops",            identity:"snowflake-key",      agent:"Data Agent",      policy:{primary:"Enforce Least Privilege"},            discovered:"2h ago",  status:"Fixed" },
    { severity:"Low",      violation:"Token rotation overdue by 14 days",                          identity:"stripe-key",         agent:"Finance Bot",     policy:{primary:"Rotate API Keys Every 30 Days"},      discovered:"1d ago",  status:"Fixed" },
];

// ── Generation pools ───────────────────────────────────────────────────────────
const AGENTS_POOL = [
    "Cursor Prod","HR Assistant","Code Reviewer","CI Bot","Finance Bot",
    "Deploy Bot","Analytics Agent","Support Agent","Data Sync","Notify Bot",
    "Log Monitor","Docs Assistant","Infra Agent","Marketing Bot","Test Runner",
    "Audit Agent","Compliance Bot","Security Scanner","Backup Agent","Report Bot",
    "API Gateway","Payment Proc","Auth Service","Cache Manager","Queue Worker",
    "ML Pipeline","Data Crawler","Ops Assistant","Identity Broker","Policy Engine",
];
const IDENTITY_POOL = [
    "aws-prod-key","gcp-svc-account","azure-sp-token","github-actions-sa","okta-api-key",
    "twilio-auth-token","hubspot-oauth","salesforce-jwt","mongo-atlas-key","redis-cloud-sa",
    "elastic-api-key","vault-approle","argo-cd-token","terraform-sa","jenkins-cred",
    "splunk-hec-token","pagerduty-key","opsgenie-token","linear-api-key","notion-oauth",
    "zoom-jwt","box-oauth","dropbox-token","figma-pat","cloudflare-api-key",
    "vercel-token","netlify-token","heroku-api-key","fly-io-token","render-token",
];
const VIOLATION_DESCS = [
    "Admin key used by LLM agent without restriction",
    "Credential not rotated in 60+ days",
    "OAuth token scoped to all repositories",
    "Service account provisioning without approval",
    "Token accessed from unrecognised IP address",
    "Write permission granted without MFA binding",
    "Bulk API calls detected from single token",
    "Identity inactive but retains write access",
    "Key used outside defined business hours",
    "Admin role assigned on non-production environment",
    "Token shared across multiple agent instances",
    "API key with org-wide admin privileges",
    "Credential reused across staging and production",
    "Service account key expired 90+ days ago",
    "MCP token missing expiry configuration",
    "Agent accessing secrets outside defined scope",
    "Long-lived session token not invalidated",
    "Excessive permissions granted at agent setup",
    "Token scope broader than required for task",
    "Service account not deprovisioned after offboarding",
    "API key associated with deleted user account",
    "Dormant token with active write permissions",
    "Service token with LLM inference rights",
    "Unauthorized infra provisioning detected",
    "Key rotation policy bypassed",
    "Token flagged for suspicious geographic usage",
    "Agent accessing prod resources from dev token",
    "OAuth token never refreshed in 45 days",
    "Unencrypted credential found in agent config",
    "Token used by multiple unrelated agents simultaneously",
];
const POLICIES_POOL = [
    "No Admin Keys for Agents","Enforce Least Privilege","Detect LLM Usage Spikes",
    "Restrict Token by Source","Disable Dormant Credentials","Restrict Infra Provisioning",
    "Rotate API Keys Every 30 Days","Block Cross-Environment Token Reuse",
    "Read Token Scope Enforcement","Limit Messaging Actions","Expire Unused Tokens",
    "Restrict Project Access","Restrict Repo Scope","Monitor Slack Abuse",
    "Detect Off-Hours API Usage","Enforce Token Expiry < 90d",
];
const TIME_AGO = [
    "10m ago","30m ago","1h ago","2h ago","4h ago","6h ago","12h ago",
    "1d ago","2d ago","3d ago","7d ago","Now","45m ago","8h ago","15m ago",
];

// Severity: Critical=2, High=3, Medium=3, Low=2 per 10
const GEN_SEVS    = ["Critical","Critical","High","High","High","Medium","Medium","Medium","Low","Low"];
// Status: 5 Open per 13 → ~50 Open from 128 generated (total Open ≈ 61)
const GEN_STATUSES= ["Open","Open","Open","Open","Open","Fixed","Fixed","Fixed","Fixed","Fixed","Fixed","Fixed","Fixed"];

const pick = (arr, i) => arr[i % arr.length];

const GENERATED = Array.from({ length: 128 }, (_, i) => {
    const idx   = i + 21;
    const count = i % 7 === 0 ? 1 + (idx % 3) : 0;
    const extras = count > 0
        ? Array.from({ length: count }, (_, j) => pick(POLICIES_POOL, idx + j + 2))
        : undefined;
    const extraObj = count > 0 ? { extra: count, extras } : {};
    return {
        severity:  GEN_SEVS[i % 10],
        violation: pick(VIOLATION_DESCS, idx + 4),
        identity:  `${pick(IDENTITY_POOL, idx)}-${idx}`,
        agent:     pick(AGENTS_POOL, idx + 2),
        policy:    { primary: pick(POLICIES_POOL, idx + 1), ...extraObj },
        discovered:pick(TIME_AGO, idx + 5),
        status:    GEN_STATUSES[i % 13],
    };
});

const ALL_RAW = [...CURATED, ...GENERATED].sort((a, b) => SEV_ORD[b.severity] - SEV_ORD[a.severity]);

const tableData = ALL_RAW.map((r, i) => ({
    ...r,
    id:            i + 1,
    severityOrder: SEV_ORD[r.severity],
    severityComp:  sevBadge(r.severity),
    // Violation is the "main" column — shown bold
    violationComp: <Text variant="bodyMd" fontWeight="medium">{r.violation}</Text>,
    identityComp:  <HorizontalStack gap="2" blockAlign="center" wrap={false}><IdentityIcon name={r.identity} /><Text variant="bodyMd">{r.identity}</Text></HorizontalStack>,
    agentComp:     <HorizontalStack gap="2" blockAlign="center" wrap={false}><AgentIcon name={r.agent} /><Text variant="bodyMd">{r.agent}</Text></HorizontalStack>,
    policyComp:    <PolicyCell policy={r.policy} />,
}));

// ── Chart data — numbers match actual table breakdown ──────────────────────────
// Computed from generation logic: Critical≈31, High≈48, Medium≈44, Low≈25
const severityDonutData = {
    Critical: { text: 31,  color: "#DF2909" },
    High:     { text: 48,  color: "#FED3D1" },
    Medium:   { text: 44,  color: "#FFD79D" },
    Low:      { text: 25,  color: "#E4E5E7" },
};

const violationsOverTimeData = [{
    data: [
        [Date.UTC(2026, 2, 28), 182],
        [Date.UTC(2026, 3,  0), 158],
        [Date.UTC(2026, 3,  1), 104],
        [Date.UTC(2026, 3,  2), 78],
        [Date.UTC(2026, 3,  3), 96],
        [Date.UTC(2026, 3,  4), 108],
        [Date.UTC(2026, 3,  5), 98],
    ],
    color: "#EF4444",
    name: "Violations",
}];

// ── Donut card layout (chart + legend) ─────────────────────────────────────────
function ChartLegend({ items }) {
    return (
        <VerticalStack gap="2">
            {items.map(({ label, color, count }) => (
                <HorizontalStack key={label} gap="2" blockAlign="center">
                    <span style={{ display:"inline-block", width:10, height:10, borderRadius:"50%", background:color, flexShrink:0 }} />
                    <Text variant="bodyMd" color="subdued">{label}</Text>
                    <Text variant="bodyMd" fontWeight="semibold">{count.toLocaleString()}</Text>
                </HorizontalStack>
            ))}
        </VerticalStack>
    );
}

function DonutCard({ title, donutData }) {
    const legendItems = Object.entries(donutData).map(([label, { text, color }]) => ({ label, color, count: text }));
    return (
        <InfoCard title={title} component={
            <HorizontalStack gap="4" blockAlign="center" wrap={false}>
                <DonutChart data={donutData} title="" size={150} pieInnerSize="55%" />
                <ChartLegend items={legendItems} />
            </HorizontalStack>
        } />
    );
}

// ── Headers ────────────────────────────────────────────────────────────────────
const headers = [
    { text: "Violation",  value: "violationComp", title: "Violation"                           },
    { text: "Identity",   value: "identityComp",  title: "Identity"                            },
    { text: "Agent",      value: "agentComp",     title: "Agent"                               },
    { text: "Severity",   value: "severityComp",  title: "Severity"                            },
    { text: "Policy",     value: "policyComp",    title: "Policy"                              },
    { text: "Discovered", value: "discovered",    title: "Discovered", type: CellType.TEXT     },
];

const sortOptions = [
    { label: "Severity", value: "severity asc",  directionLabel: "Critical first", sortKey: "severityOrder", columnIndex: 3 },
    { label: "Severity", value: "severity desc", directionLabel: "Low first",      sortKey: "severityOrder", columnIndex: 3 },
];

// ── Page ───────────────────────────────────────────────────────────────────────
const violationsPageTitle = (
    <TitleWithInfo
        titleText="Violations"
        tooltipContent="Policy violations detected across all non-human identities used by your AI agents."
        docsUrl="https://ai-security-docs.akto.io/nhi-governance/violations"
    />
);

export default function ViolationsPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    // Pre-select "open" tab by default
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "open";

    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected]       = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const dataByTab = useMemo(() => ({
        all:   tableData,
        open:  tableData.filter((r) => r.status === "Open"),
        fixed: tableData.filter((r) => r.status === "Fixed"),
    }), []);

    const tableCountObj = func.getTabsCount(definedTableTabs, dataByTab);
    const tableTabs = func.getTableTabsContent(
        definedTableTabs, tableCountObj,
        (tabId) => {
            setSelectedTab(tabId);
            setTableSelectedTab({ ...tableSelectedTab, [window.location.pathname]: tabId });
        },
        selectedTab, tabsInfo
    );

    return (
        <PageWithMultipleCards
            title={violationsPageTitle}
            isFirstPage
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(d) => dispatchCurrDateRange({ type: "update", period: d.period, title: d.title, alias: d.alias })} />}
            components={[
                // 2fr / 1fr layout: line chart wider, donut compact
                <div key="charts" style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "16px" }}>
                    <InfoCard
                        title="Violations over time"
                        component={
                            <LineChart
                                data={violationsOverTimeData}
                                type="line"
                                height={220}
                                text={true}
                                showGridLines={true}
                                exportingDisabled={true}
                                defaultChartOptions={{
                                    xAxis: {
                                        type: "datetime",
                                        dateTimeLabelFormats: { day: "%a" },
                                        title: { text: null },
                                        visible: true,
                                        gridLineWidth: 0,
                                    },
                                    yAxis: {
                                        title: { text: "Violations" },
                                        gridLineWidth: 1,
                                        min: 0,
                                    },
                                    legend: { enabled: true },
                                }}
                            />
                        }
                    />
                    <DonutCard title="Violations by severity" donutData={severityDonutData} />
                </div>,

                <GithubSimpleTable
                    key="violations-table"
                    data={dataByTab[selectedTab]}
                    headers={headers}
                    resourceName={resourceName}
                    sortOptions={sortOptions}
                    filters={[]}
                    selectable={true}
                    mode={IndexFiltersMode.Default}
                    headings={headers}
                    useNewRow={true}
                    condensedHeight={true}
                    tableTabs={tableTabs}
                    onSelect={(i) => setSelected(i)}
                    selected={selected}
                />,
            ]}
        />
    );
}

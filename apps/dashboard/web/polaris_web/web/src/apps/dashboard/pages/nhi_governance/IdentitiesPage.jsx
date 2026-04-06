import { useState, useMemo, useReducer } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Avatar, Badge, HorizontalStack, Icon, Text } from "@shopify/polaris";
import { SettingsMinor } from "@shopify/polaris-icons";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import CollectionIcon from "../../components/shared/CollectionIcon";
import { produce } from "immer";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
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
        return <Icon source={SettingsMinor} color="subdued" />;
    const domain = parts.reduce((found, p) => found || IDENTITY_DOMAIN_MAP[p] || null, null);
    if (!domain) return null;
    return <Avatar size="extraSmall" shape="square" source={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} />;
}

const definedTableTabs = ["All", "Expired"];
const resourceName = { singular: "identity", plural: "identities" };

// ── Violation bubble component ─────────────────────────────────────────────────
function ViolationBubbles({ critical = 0, high = 0, medium = 0 }) {
    if (!critical && !high && !medium)
        return <Text variant="bodyMd" color="subdued">No violations</Text>;
    const dot = (count, bg, fg) =>
        count > 0 ? (
            <span
                key={bg}
                style={{
                    background: bg, color: fg, borderRadius: "50%",
                    width: 22, height: 22, display: "inline-flex",
                    alignItems: "center", justifyContent: "center",
                    fontSize: 11, fontWeight: 600, flexShrink: 0,
                }}
            >{count}</span>
        ) : null;
    return (
        <HorizontalStack gap="1" blockAlign="center">
            {dot(critical, "#DF2909", "white")}
            {dot(high,     "#FED3D1", "#202223")}
            {dot(medium,   "#FFD79D", "#202223")}
        </HorizontalStack>
    );
}

// ── Expiry status renderer ─────────────────────────────────────────────────────
const expiryComp = (s) => {
    if (!s) return null;
    if (s.startsWith("Expired"))
        return <Text variant="bodyMd" color="critical">{s}</Text>;
    if (s === "Rotation due today" || s.startsWith("Rotation Due in"))
        return <Text variant="bodyMd" style={{ color: "#B7791F", fontWeight: 500 }}>{s}</Text>;
    return <Text variant="bodyMd">{s}</Text>;
};

// ── 10 Critical Curated (all others non-critical) ──────────────────────────────
const CRITICAL_CURATED = [
    { identityName:"aws-cursor-key",     agent:"Cursor Prod",     type:"API Key",      access:"Admin",       violCrit:3, violHigh:0, violMed:0, lastUsed:"2h ago",  expiryStatus:"2d left"                },
    { identityName:"hr-slack-token",     agent:"HR Assistant",    type:"Bearer Token", access:"Write",      violCrit:1, violHigh:3, violMed:0, lastUsed:"45m ago", expiryStatus:"Rotation Due in 2 days" },
    { identityName:"aws-env-sa",         agent:"New Env Agent",   type:"Bearer Token", access:"Write",      violCrit:2, violHigh:1, violMed:0, lastUsed:"1d ago",  expiryStatus:"Rotation due today"     },
    { identityName:"github-oauth-456",   agent:"Code Reviewer",   type:"Bearer Token", access:"Read/Write", violCrit:1, violHigh:3, violMed:2, lastUsed:"3h ago",  expiryStatus:"60d left"               },
    { identityName:"jira-token",         agent:"My Assistant",    type:"API Key",      access:"Read",       violCrit:2, violHigh:4, violMed:1, lastUsed:"Never",   expiryStatus:"15d left"               },
    { identityName:"internal-api-token", agent:"Connector",       type:"Bearer Token", access:"Read",       violCrit:1, violHigh:0, violMed:1, lastUsed:"2d ago",  expiryStatus:"60d left"               },
    { identityName:"entra-service",      agent:"Entra Bot",       type:"Bearer Token", access:"Read",       violCrit:2, violHigh:5, violMed:0, lastUsed:"6h ago",  expiryStatus:"7d left"                },
    { identityName:"vscode-oauth",       agent:"Agent Studio",    type:"API Key",      access:"Write",      violCrit:1, violHigh:2, violMed:1, lastUsed:"Never",   expiryStatus:"No expiry"              },
    { identityName:"stripe-key",         agent:"Finance Bot",     type:"API Key",      access:"Admin",      violCrit:3, violHigh:0, violMed:2, lastUsed:"5h ago",  expiryStatus:"Rotation due today"     },
    { identityName:"github-actions-key", agent:"CI Bot",          type:"Bearer Token", access:"Write",      violCrit:1, violHigh:1, violMed:0, lastUsed:"10m ago", expiryStatus:"Expired 1d ago"         },
];

// ── Non-critical curated (high/medium/low violations) ──────────────────────────
const NON_CRITICAL_CURATED = [
    { identityName:"zendesk-api-key",    agent:"Support Bot",     type:"Bearer Token", access:"Write",      violCrit:0, violHigh:1, violMed:0, lastUsed:"3h ago",  expiryStatus:"15d left"               },
    { identityName:"gcp-service-key",    agent:"Infra Agent",     type:"API Key",      access:"Admin",      violCrit:0, violHigh:1, violMed:1, lastUsed:"6h ago",  expiryStatus:"5d left"                },
    { identityName:"notion-token",       agent:"Docs Assistant",  type:"API Key",      access:"Read",       violCrit:0, violHigh:1, violMed:0, lastUsed:"1d ago",  expiryStatus:"1d left"                },
    { identityName:"hubspot-oauth",      agent:"Marketing Bot",   type:"Bearer Token", access:"Write",      violCrit:0, violHigh:1, violMed:0, lastUsed:"10m ago", expiryStatus:"No expiry"              },
    { identityName:"snowflake-key",      agent:"Data Agent",      type:"API Key",      access:"Read/Write", violCrit:0, violHigh:3, violMed:1, lastUsed:"3h ago",  expiryStatus:"No expiry"              },
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
    "datadog-agent-key","newrelic-license","grafana-cloud-key","sentry-auth","launchdarkly-sdk",
    "mixpanel-token","amplitude-key","segment-write-key","intercom-token","zendesk-jwt",
];
const TYPES_POOL    = ["API Key", "Bearer Token", "API Key", "Bearer Token", "API Key"];
const ACCESS_POOL   = ["Admin", "Read", "Write", "Read/Write", "Read", "Write"];
const LAST_USED_P   = ["2m ago","15m ago","1h ago","3h ago","8h ago","1d ago","3d ago","7d ago","Never","30s ago","45m ago","2d ago","6h ago","12h ago"];
const EXPIRY_POOL   = [
    "30d left","15d left","7d left","5d left","60d left","45d left","90d left",
    "Rotation Due in 7 days","Rotation Due in 14 days","No expiry",
    "Expired 3d ago","Expired 7d ago","2d left","1d left",
];

const pick = (arr, i) => arr[i % arr.length];

// Generated: all non-critical (violCrit = 0), have high/medium/low violations
const GENERATED = Array.from({ length: 109 }, (_, i) => {
    const idx     = i + 16;
    const hasViol = i % 5 !== 4; // 80% have some violations

    return {
        identityName: `${pick(IDENTITY_POOL, idx)}-${idx}`,
        agent:        pick(AGENTS_POOL, idx + 2),
        type:         pick(TYPES_POOL, idx + 1),
        access:       pick(ACCESS_POOL, idx + 3),
        violCrit:     0, // All generated are non-critical
        violHigh:     !hasViol ? 0 : (1 + (idx % 4)),
        violMed:      hasViol ? (idx % 3) : 0,
        lastUsed:     pick(LAST_USED_P, idx + 4),
        expiryStatus: pick(EXPIRY_POOL, idx + 5),
    };
});

const ALL_RAW = [...CRITICAL_CURATED, ...NON_CRITICAL_CURATED, ...GENERATED]
    .sort((a, b) => {
        // Primary: critical violations (most first)
        if (b.violCrit !== a.violCrit) return b.violCrit - a.violCrit;
        // Secondary: total violations (most first)
        return (b.violCrit + b.violHigh + b.violMed) - (a.violCrit + a.violHigh + a.violMed);
    });

const tableData = ALL_RAW.map((r, i) => ({
    ...r,
    id:             i + 1,
    totalViolations: r.violCrit + r.violHigh + r.violMed,
    // priorityScore ensures critical violations always rank above high/medium-only ones
    priorityScore:  r.violCrit * 1000 + (r.violCrit + r.violHigh + r.violMed),
    identityComp:  <HorizontalStack gap="2" blockAlign="center" wrap={false}><IdentityIcon name={r.identityName} /><Text variant="bodyMd" fontWeight="medium">{r.identityName}</Text></HorizontalStack>,
    agentComp:     <HorizontalStack gap="2" blockAlign="center" wrap={false}><CollectionIcon assetTagValue={r.agent} displayName={r.agent} /><Text variant="bodyMd">{r.agent}</Text></HorizontalStack>,
    typeComp:      <Badge>{r.type}</Badge>,
    violationsComp: <ViolationBubbles critical={r.violCrit} high={r.violHigh} medium={r.violMed} />,
    expiryComp:    expiryComp(r.expiryStatus),
}));

// ── Computed summary ───────────────────────────────────────────────────────────
const TOTAL_I    = tableData.length;
const EXPIRED_I  = tableData.filter((r) => r.expiryStatus && r.expiryStatus.startsWith("Expired")).length;
const WITH_VIOL  = tableData.filter((r) => r.totalViolations > 0).length;

const summaryItems = [
    { title: "Total Identities",          data: TOTAL_I.toLocaleString()   },
    { title: "Expired Identities",        data: EXPIRED_I.toLocaleString() },
    { title: "Identities with Violations",data: WITH_VIOL.toLocaleString() },
];

// ── Headers ────────────────────────────────────────────────────────────────────
const headers = [
    { text: "Identity",      value: "identityComp",   title: "Identity"                           },
    { text: "Agent",         value: "agentComp",      title: "Agent"                                },
    { text: "Type",          value: "typeComp",       title: "Type"                               },
    { text: "Access",        value: "access",         title: "Access",      type: CellType.TEXT   },
    { text: "Violations",    value: "violationsComp", title: "Violations"                         },
    { text: "Last Used",     value: "lastUsed",       title: "Last Used",   type: CellType.TEXT   },
    { text: "Expiry Status", value: "expiryComp",     title: "Expiry Status"                      },
];

const sortOptions = [
    { label: "Violations", value: "violations asc",  directionLabel: "Most first",   sortKey: "priorityScore", columnIndex: 4 },
    { label: "Violations", value: "violations desc", directionLabel: "Least first",  sortKey: "priorityScore", columnIndex: 4 },
];

// ── Page ───────────────────────────────────────────────────────────────────────
const pageTitle = (
    <TitleWithInfo
        titleText="Identities"
        tooltipContent="Non-human identities (API keys, tokens, service accounts) used by your AI agents."
        docsUrl="https://ai-security-docs.akto.io/nhi-governance/identities"
    />
);

export default function IdentitiesPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab    = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab  = tableSelectedTab[window.location.pathname] || "all";

    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected]       = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[2]
    );

    const dataByTab = useMemo(() => ({
        "all":     tableData,
        "expired": tableData.filter((r) => r.expiryStatus && r.expiryStatus.startsWith("Expired")),
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
            title={pageTitle}
            isFirstPage
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(d) => dispatchCurrDateRange({ type: "update", period: d.period, title: d.title, alias: d.alias })} />}
            components={[
                <SummaryCardInfo key="summary" summaryItems={summaryItems} />,

                <GithubSimpleTable
                    key="identities-table"
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

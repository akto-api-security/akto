import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import DonutChart from "../../components/shared/DonutChart";
import LineChart from "../../components/charts/LineChart";
import InfoCard from "../dashboard/new_components/InfoCard";
import func from "@/util/func";

const resourceName = { singular: "violation", plural: "violations" };
const SEV_ORD = { Critical: 4, High: 3, Medium: 2, Low: 1 };

const sevBadge = (s) => (
    <div className={`badge-wrapper-${s.toUpperCase()}`}>
        <Badge status={func.getHexColorForSeverity(s.toUpperCase())}>{s}</Badge>
    </div>
);

const STATUS_COLOR = { Open: "critical", Investigating: "warning", Resolved: "success" };
const statusBadge = (s) => <Badge status={STATUS_COLOR[s] || ""}>{s}</Badge>;

// ── Curated top-20 ────────────────────────────────────────────────────────────
const CURATED = [
    { severity:"Critical", agent:"Cursor Prod",     violation:"Admin key used by agent",                             identity:"aws-cursor-key",       violationType:"Over-Privileged Access",    status:"Open",          policy:"No Admin Keys for Agents",       firstSeen:"2h ago",   lastSeen:"2h ago"  },
    { severity:"Critical", agent:"HR Assistant",    violation:"Token unused for 30+ days",                           identity:"hr-slack-token",       violationType:"Orphaned Identity",         status:"Open",          policy:"Disable Orphaned Tokens",        firstSeen:"45m ago",  lastSeen:"Never"   },
    { severity:"Critical", agent:"New Env Agent",   violation:"Sudden spike in LLM usage",                           identity:"aws-env-sa",           violationType:"Suspicious Activity",       status:"Investigating",  policy:"Detect LLM Usage Spikes",        firstSeen:"1d ago",   lastSeen:"Now"     },
    { severity:"Critical", agent:"Code Reviewer",   violation:"Key not rotated in 60 days",                          identity:"github-oauth-456",     violationType:"No Rotation",               status:"Investigating",  policy:"Rotate API Keys every 30 days",  firstSeen:"3h ago",   lastSeen:"6h ago"  },
    { severity:"Critical", agent:"My Assistant",    violation:"OAuth token with wide repo access",                   identity:"jira-token",           violationType:"Over-Privileged Access",    status:"Open",          policy:"Enforce Least Privilege",        firstSeen:"Never",    lastSeen:"3h ago"  },
    { severity:"Critical", agent:"Connector",       violation:"Service account provisioning infra without approval", identity:"internal-api-token",   violationType:"Unauthorized Provisioning", status:"Investigating",  policy:"Restrict Infra Provisioning",    firstSeen:"2d ago",   lastSeen:"1d ago"  },
    { severity:"Critical", agent:"Entra Bot",       violation:"Internal API token used from new IP",                 identity:"entra-service",        violationType:"Anomalous Usage",           status:"Open",          policy:"Detect New IP Usage",            firstSeen:"6h ago",   lastSeen:"2d ago"  },
    { severity:"Critical", agent:"Agent Studio",    violation:"Slack token sending bulk messages",                   identity:"vscode-oauth",         violationType:"Abuse / Excessive Actions", status:"Open",          policy:"Monitor Slack Abuse",            firstSeen:"Never",    lastSeen:"45m ago" },
    { severity:"Critical", agent:"CI Bot",          violation:"Identity inactive but still has write access",        identity:"github-actions-key",   violationType:"Dormant Privilege",         status:"Open",          policy:"Expire Unused Tokens (30d)",     firstSeen:"15d ago",  lastSeen:"Never"   },
    { severity:"Critical", agent:"Finance Bot",     violation:"Payment API key used outside business hours",         identity:"stripe-key",           violationType:"Suspicious Activity",       status:"Investigating",  policy:"Detect Off-Hours API Usage",     firstSeen:"10h ago",  lastSeen:"5h ago"  },
    { severity:"High",     agent:"Deploy Bot",      violation:"Admin cluster role on non-prod environment",          identity:"k8s-service-account",  violationType:"Over-Privileged Access",    status:"Open",          policy:"Enforce Least Privilege",        firstSeen:"3h ago",   lastSeen:"1h ago"  },
    { severity:"High",     agent:"Data Sync",       violation:"Snowflake key not rotated in 90+ days",               identity:"snowflake-sa-key",     violationType:"No Rotation",               status:"Open",          policy:"Rotate API Keys every 30 days",  firstSeen:"5d ago",   lastSeen:"3d ago"  },
    { severity:"High",     agent:"Support Agent",   violation:"Write token missing scope restrictions",              identity:"zendesk-oauth",        violationType:"Over-Privileged Access",    status:"Investigating",  policy:"Enforce Least Privilege",        firstSeen:"2d ago",   lastSeen:"4h ago"  },
    { severity:"High",     agent:"Notify Bot",      violation:"PagerDuty token never scoped to specific alerts",     identity:"pagerduty-token",      violationType:"Dormant Privilege",         status:"Open",          policy:"Expire Unused Tokens (30d)",     firstSeen:"7d ago",   lastSeen:"2d ago"  },
    { severity:"High",     agent:"Analytics Agent", violation:"BigQuery service account orphaned for 30+ days",      identity:"bigquery-sa",          violationType:"Orphaned Identity",         status:"Investigating",  policy:"Disable Orphaned Tokens",        firstSeen:"1mo ago",  lastSeen:"30d ago" },
    { severity:"High",     agent:"Log Monitor",     violation:"Datadog key has org-wide read access",                identity:"datadog-api-key",      violationType:"Over-Privileged Access",    status:"Open",          policy:"Restrict MCP Token Scope",       firstSeen:"1d ago",   lastSeen:"2h ago"  },
    { severity:"High",     agent:"Infra Agent",     violation:"Terraform SA credential unused for 45+ days",         identity:"terraform-cloud-sa",   violationType:"Dormant Privilege",         status:"Open",          policy:"Flag Tokens Unused > 14 Days",   firstSeen:"45d ago",  lastSeen:"45d ago" },
    { severity:"Medium",   agent:"Doc Writer",      violation:"OAuth refresh token not revoked after 60 days",        identity:"confluence-oauth",     violationType:"No Rotation",               status:"Investigating",  policy:"Require Rotation Every 60 Days", firstSeen:"2mo ago",  lastSeen:"60d ago" },
    { severity:"Medium",   agent:"Email Agent",     violation:"SendGrid key grants global send permission",           identity:"sendgrid-api-key",     violationType:"Over-Privileged Access",    status:"Investigating",  policy:"Read Token Scope Enforcement",   firstSeen:"3d ago",   lastSeen:"1d ago"  },
    { severity:"Medium",   agent:"Test Runner",     violation:"GitHub test token scoped to all repos",                identity:"github-test-token",    violationType:"No Rotation",               status:"Investigating",  policy:"Restrict MCP Token Scope",       firstSeen:"2w ago",   lastSeen:"1w ago"  },
];

// ── Generation pools ──────────────────────────────────────────────────────────
const AGENTS = [
    "Cursor Prod","HR Assistant","Code Reviewer","CI Bot","Finance Bot",
    "Deploy Bot","Analytics Agent","Support Agent","Data Sync","Notify Bot",
    "Log Monitor","Doc Writer","Infra Agent","Email Agent","Test Runner",
    "Audit Agent","Compliance Bot","Security Scanner","Backup Agent","Report Bot",
    "API Gateway","Payment Proc","Auth Service","Cache Manager","Queue Worker",
    "ML Pipeline","Data Crawler","Ops Assistant","Identity Broker","Policy Engine",
];
const IDENTITIES_POOL = [
    "aws-prod-key","gcp-svc-account","azure-sp-token","github-actions-sa","okta-api-key",
    "twilio-auth-token","hubspot-oauth","salesforce-jwt","mongo-atlas-key","redis-cloud-sa",
    "elastic-api-key","vault-approle","argo-cd-token","terraform-sa","jenkins-cred",
    "splunk-hec-token","pagerduty-key","opsgenie-token","linear-api-key","notion-oauth",
    "zoom-jwt","box-oauth","dropbox-token","figma-pat","cloudflare-api-key",
    "vercel-token","netlify-token","heroku-api-key","fly-io-token","render-token",
];
const VIOLATION_DESCS = [
    "Admin key used by LLM agent",
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
    "Unencrypted credential found in config",
    "Token used by multiple unrelated agents",
];
const VIOLATION_TYPES = [
    "Over-Privileged Access","Over-Privileged Access","Over-Privileged Access",
    "Orphaned Identity","Orphaned Identity",
    "Suspicious Activity","Suspicious Activity",
    "No Rotation","No Rotation",
    "Unauthorized Provisioning",
    "Anomalous Usage",
    "Abuse / Excessive Actions",
    "Dormant Privilege",
];
const POLICIES_POOL = [
    "No Admin Keys for Agents","Rotate API Keys every 30 days","Detect LLM Usage Spikes",
    "Disable Orphaned Tokens","Enforce Least Privilege","Detect New IP Usage",
    "Block Cross-Environment Token Reuse","Revoke Admin OAuth Tokens",
    "Restrict Infra Provisioning","Monitor Slack Abuse","Expire Unused Tokens (30d)",
    "Flag Tokens Unused > 14 Days","Restrict MCP Token Scope",
    "Require Rotation Every 60 Days","Detect Off-Hours API Usage","Enforce Token Expiry < 90d",
    "Read Token Scope Enforcement",
];
const TIME_AGO = [
    "10m ago","30m ago","1h ago","2h ago","4h ago","6h ago","12h ago",
    "1d ago","2d ago","3d ago","5d ago","7d ago","2w ago","1mo ago","Never","Now",
];

// Severity: 1 Critical per 10 → 13 Critical from 128 generated  (total Critical = 10+13 = 23)
const GEN_SEVS = ["Critical","High","High","Medium","Medium","Medium","Low","Low","Low","Low"];
// Status: 5 Open per 13 → 50 Open from 128 generated  (total Open = 11+50 = 61)
const GEN_STATUSES = ["Open","Open","Open","Open","Open","Investigating","Investigating","Investigating","Investigating","Investigating","Investigating","Investigating","Investigating"];

const pick = (arr, i) => arr[i % arr.length];

const GENERATED = Array.from({ length: 128 }, (_, i) => {
    const idx = i + 21;
    return {
        severity:      GEN_SEVS[i % 10],
        agent:         pick(AGENTS, idx + 2),
        violation:     pick(VIOLATION_DESCS, idx + 4),
        identity:      `${pick(IDENTITIES_POOL, idx)}-${idx}`,
        violationType: pick(VIOLATION_TYPES, idx + 3),
        status:        GEN_STATUSES[i % 13],
        policy:        pick(POLICIES_POOL, idx + 1),
        firstSeen:     pick(TIME_AGO, idx + 5),
        lastSeen:      pick(TIME_AGO, idx + 9),
    };
});

const ALL_RAW = [...CURATED, ...GENERATED].sort((a, b) => SEV_ORD[b.severity] - SEV_ORD[a.severity]);

const tableData = ALL_RAW.map((r, i) => ({
    ...r,
    id:            i + 1,
    severityOrder: SEV_ORD[r.severity],
    severityComp:  sevBadge(r.severity),
    identityComp:  <Text variant="bodyMd" fontWeight="medium">{r.identity}</Text>,
    statusComp:    statusBadge(r.status),
}));

// ── Computed summary ──────────────────────────────────────────────────────────
const TOTAL_V    = tableData.length;
const CRITICAL_V = tableData.filter((r) => r.severity === "Critical").length;
const OPEN_V     = tableData.filter((r) => r.status === "Open").length;

const summaryItems = [
    { title: "Total Violations",    data: TOTAL_V.toLocaleString()    },
    { title: "Critical Violations", data: CRITICAL_V.toLocaleString() },
    { title: "Open Violations",     data: OPEN_V.toLocaleString()     },
];

// ── Headers ───────────────────────────────────────────────────────────────────
const headers = [
    { text: "Severity",       value: "severityComp",  title: "Severity"                              },
    { text: "Agent",          value: "agent",          title: "Agent",         type: CellType.TEXT    },
    { text: "Violation",      value: "violation",      title: "Violation",     type: CellType.TEXT    },
    { text: "Identity",       value: "identityComp",   title: "Identity"                              },
    { text: "Violation Type", value: "violationType",  title: "Violation Type",type: CellType.TEXT    },
    { text: "Status",         value: "statusComp",     title: "Status"                                },
    { text: "Policy",         value: "policy",         title: "Policy",        type: CellType.TEXT    },
    { text: "First Seen",     value: "firstSeen",      title: "First Seen",    type: CellType.TEXT    },
    { text: "Last Seen",      value: "lastSeen",       title: "Last Seen",     type: CellType.TEXT    },
];

const sortOptions = [
    { label: "Severity", value: "severity asc",     directionLabel: "Critical first", sortKey: "severityOrder", columnIndex: 0 },
    { label: "Severity", value: "severity desc",    directionLabel: "Low first",      sortKey: "severityOrder", columnIndex: 0 },
];

// ── Chart data ────────────────────────────────────────────────────────────────
const severityDonutData = {
    Critical: { text: 532, color: "#AE2E24" },
    High:     { text: 312, color: "#F5A6B0" },
    Medium:   { text: 186, color: "#F5D88E" },
    Low:      { text: 78,  color: "#D9D9D9" },
};

const violationTypeDonutData = {
    "Over-Privileged Access":    { text: 368, color: "#6366F1" },
    "Orphaned Identity":         { text: 248, color: "#F97316" },
    "Suspicious Activity":       { text: 186, color: "#EF4444" },
    "No Rotation":               { text: 156, color: "#EAB308" },
    "Unauthorized Provisioning": { text: 150, color: "#22C55E" },
};

const violationsOverTimeData = [{
    data: [
        [Date.UTC(2026, 2, 28), 220],
        [Date.UTC(2026, 3,  0), 245],
        [Date.UTC(2026, 3,  1), 262],
        [Date.UTC(2026, 3,  2), 248],
        [Date.UTC(2026, 3,  3), 310],
        [Date.UTC(2026, 3,  4), 338],
        [Date.UTC(2026, 3,  5), 381],
    ],
    color: "#EF4444",
    name: "Violations",
}];

// ── Donut card layout ─────────────────────────────────────────────────────────
function ChartLegend({ items }) {
    return (
        <VerticalStack gap="2">
            {items.map(({ label, color, count }) => (
                <HorizontalStack key={label} gap="2" blockAlign="center">
                    <span style={{ display: "inline-block", width: 10, height: 10, borderRadius: "50%", background: color, flexShrink: 0 }} />
                    <Text variant="bodySm" color="subdued">{label}</Text>
                    <Text variant="bodySm" fontWeight="semibold">{count.toLocaleString()}</Text>
                </HorizontalStack>
            ))}
        </VerticalStack>
    );
}

function DonutCard({ title, donutData }) {
    const legendItems = Object.entries(donutData).map(([label, { text, color }]) => ({ label, color, count: text }));
    return (
        <InfoCard title={title} component={
            <HorizontalStack gap="6" blockAlign="center" wrap={false}>
                <DonutChart data={donutData} title="" size={180} pieInnerSize="60%" />
                <ChartLegend items={legendItems} />
            </HorizontalStack>
        } />
    );
}

// ── Page ──────────────────────────────────────────────────────────────────────
export default function ViolationsPage() {
    return (
        <PageWithMultipleCards
            title="Violations"
            isFirstPage
            components={[
                <SummaryCardInfo key="summary" summaryItems={summaryItems} />,

                <HorizontalGrid key="charts" columns={3} gap="4">
                    <DonutCard title="Violations Severity" donutData={severityDonutData} />
                    <InfoCard
                        title="Violations Over Time"
                        component={
                            <LineChart
                                data={violationsOverTimeData}
                                type="line"
                                height={200}
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
                    <DonutCard title="Violation Type" donutData={violationTypeDonutData} />
                </HorizontalGrid>,

                <GithubSimpleTable
                    key="violations-table"
                    data={tableData}
                    headers={headers}
                    resourceName={resourceName}
                    sortOptions={sortOptions}
                    filters={[]}
                    selectable={false}
                    mode={IndexFiltersMode.Default}
                    headings={headers}
                    useNewRow={true}
                    condensedHeight={true}
                />,
            ]}
        />
    );
}

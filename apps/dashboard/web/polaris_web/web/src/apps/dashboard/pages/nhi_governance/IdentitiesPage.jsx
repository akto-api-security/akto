import { useState, useMemo } from "react";
import { IndexFiltersMode } from "@shopify/polaris";
import { Badge, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import SummaryCardInfo from "../../components/shared/SummaryCardInfo";
import DonutChart from "../../components/shared/DonutChart";
import LineChart from "../../components/charts/LineChart";
import InfoCard from "../dashboard/new_components/InfoCard";
import useTable from "../../components/tables/TableContext";
import PersistStore from "../../../main/PersistStore";
import func from "@/util/func";

const definedTableTabs = ['All', 'Active', 'Orphaned'];
const resourceName = { singular: "identity", plural: "identities" };

const SEV_ORD = { Critical: 4, High: 3, Medium: 2, Low: 1 };

const sevBadge = (s) => (
    <div className={`badge-wrapper-${s.toUpperCase()}`}>
        <Badge status={func.getHexColorForSeverity(s.toUpperCase())}>{s}</Badge>
    </div>
);
const statusBadge = (s) => <Badge status={s === "Active" ? "success" : "warning"}>{s}</Badge>;

// ── Data pools for bulk generation ────────────────────────────────────────────
const OWNERS = [
    "Eve Martinez","John Chen","Adam Taylor","Sarah Johnson","Luke Williams","Noah Kim",
    "Theodore Brooks","Evelyn Ross","Marcus Davis","Priya Patel","Lucas Hernandez",
    "Aria Singh","Felix Wagner","Nora Lindqvist","Ryan O'Brien","Zoe Anderson",
    "Carmen Flores","Sam Nguyen","Emma Wilson","David Lee","Sofia Reyes","Chris Martin",
    "Aisha Khan","Max Müller","Raj Iyer","Diana Park","Omar Hassan","Lena Vogel",
    "Victor Moreau","Yuki Tanaka",
];
const AGENTS = [
    "Cursor Prod","HR Assistant","Code Reviewer","CI Bot","Finance Bot",
    "Deploy Bot","Analytics Agent","Support Agent","Data Sync","Notify Bot",
    "Log Monitor","Doc Writer","Infra Agent","Email Agent","Test Runner",
    "Audit Agent","Compliance Bot","Security Scanner","Backup Agent","Report Bot",
    "API Gateway","Payment Proc","Auth Service","Cache Manager","Queue Worker",
    "ML Pipeline","Data Crawler","Ops Assistant","Identity Broker","Policy Engine",
];
const IDENTITIES = [
    "aws-prod-key","gcp-svc-account","azure-sp-token","github-actions-sa","okta-api-key",
    "twilio-auth-token","hubspot-oauth","salesforce-jwt","mongo-atlas-key","redis-cloud-sa",
    "elastic-api-key","vault-approle","argo-cd-token","terraform-sa","jenkins-cred",
    "splunk-hec-token","pagerduty-key","opsgenie-token","linear-api-key","notion-oauth",
    "zoom-jwt","box-oauth","dropbox-token","figma-pat","cloudflare-api-key",
    "vercel-token","netlify-token","heroku-api-key","fly-io-token","render-token",
    "datadog-agent-key","newrelic-license","grafana-cloud-key","sentry-auth","launchdarkly-sdk",
    "mixpanel-token","amplitude-key","segment-write-key","intercom-token","zendesk-jwt",
    "supabase-anon-key","firebase-admin-sa","auth0-m2m-token","cognito-app-client","ping-identity-key",
    "cyberark-app-id","hashicorp-vault-token","aws-secrets-sa","gcp-kms-key","azure-keyvault-sp",
];
const ACTIONS = [
    "LLM Access","Code Execution","Cloud Provisioning","Data Analysis","CI/CD Automation",
    "Messaging Actions","Ticketing Actions","Incident Alerting","Log Streaming","IaC Provisioning",
    "Identity Management","Payments Access","Cluster Deployment","Data Warehouse Query",
    "Email Delivery","Documentation","Ticket Management","Internal API Access","Code Access",
    "Audit Logging","Compliance Checks","Backup Orchestration","Secret Rotation","Policy Enforcement",
    "User Provisioning","Access Reviews","Threat Detection","Vulnerability Scanning","ML Inference","Report Generation",
];
const TYPES    = ["API Key","OAuth","Service","MCP Token"];
const ACCESSES = ["Read","Write","Admin","Read/Write"];
const ENVS     = ["Prod","Staging","Dev","DR"];
const SEVS     = ["Critical","High","Medium","Low"];
const LAST_USED= ["2m ago","15m ago","1h ago","3h ago","8h ago","1d ago","3d ago","7d ago","Never","30s ago","45m ago","2d ago"];

// ── Curated top-20 rows ───────────────────────────────────────────────────────
const CURATED = [
    { severity:"Critical", owner:"Eve Martinez",    agent:"Cursor Prod",     identityName:"aws-cursor-key",       type:"API Key",   access:"Admin",      actions:"LLM Access",           status:"Active",   environment:"Prod",    openViolations:3, lastUsed:"2h ago"  },
    { severity:"Critical", owner:"John Chen",       agent:"HR Assistant",    identityName:"hr-slack-token",       type:"OAuth",     access:"Write",      actions:"Messaging Actions",    status:"Active",   environment:"Prod",    openViolations:0, lastUsed:"45m ago" },
    { severity:"Critical", owner:"John Chen",       agent:"New Env Agent",   identityName:"aws-env-sa",           type:"Service",   access:"Write",      actions:"Cloud Provisioning",   status:"Orphaned", environment:"Staging", openViolations:3, lastUsed:"1d ago"  },
    { severity:"Critical", owner:"Adam Taylor",     agent:"Code Reviewer",   identityName:"github-oauth-456",     type:"MCP Token", access:"Read/Write", actions:"Code Access",          status:"Orphaned", environment:"Prod",    openViolations:4, lastUsed:"3h ago"  },
    { severity:"Critical", owner:"Sarah Johnson",   agent:"My Assistant",    identityName:"jira-token",           type:"MCP Token", access:"Read",       actions:"Ticketing Actions",    status:"Active",   environment:"Prod",    openViolations:2, lastUsed:"Never"   },
    { severity:"Critical", owner:"Luke Williams",   agent:"Connector",       identityName:"internal-api-token",   type:"Service",   access:"Read",       actions:"Internal API Access",  status:"Orphaned", environment:"Prod",    openViolations:1, lastUsed:"2d ago"  },
    { severity:"Critical", owner:"Adam Taylor",     agent:"Entra Bot",       identityName:"entra-service",        type:"Service",   access:"Read",       actions:"Identity Management",  status:"Active",   environment:"Prod",    openViolations:0, lastUsed:"6h ago"  },
    { severity:"Critical", owner:"Noah Kim",        agent:"Agent Studio",    identityName:"vscode-oauth",         type:"MCP Token", access:"Write",      actions:"Code Execution",       status:"Active",   environment:"Dev",     openViolations:0, lastUsed:"Never"   },
    { severity:"Critical", owner:"Theodore Brooks", agent:"CI Bot",          identityName:"github-actions-key",   type:"API Key",   access:"Write",      actions:"CI/CD Automation",     status:"Active",   environment:"Prod",    openViolations:2, lastUsed:"10m ago" },
    { severity:"Critical", owner:"Evelyn Ross",     agent:"Finance Bot",     identityName:"stripe-key",           type:"API Key",   access:"Admin",      actions:"Payments Access",      status:"Orphaned", environment:"Prod",    openViolations:2, lastUsed:"5h ago"  },
    { severity:"High",     owner:"Marcus Davis",    agent:"Data Sync",       identityName:"snowflake-sa-key",     type:"Service",   access:"Read",       actions:"Data Warehouse Query",  status:"Active",   environment:"Prod",    openViolations:1, lastUsed:"1h ago"  },
    { severity:"High",     owner:"Priya Patel",     agent:"Support Agent",   identityName:"zendesk-oauth",        type:"OAuth",     access:"Write",      actions:"Ticket Management",    status:"Active",   environment:"Prod",    openViolations:0, lastUsed:"30m ago" },
    { severity:"High",     owner:"Lucas Hernandez", agent:"Deploy Bot",      identityName:"k8s-service-account",  type:"Service",   access:"Admin",      actions:"Cluster Deployment",   status:"Active",   environment:"Staging", openViolations:2, lastUsed:"4h ago"  },
    { severity:"High",     owner:"Aria Singh",      agent:"Analytics Agent", identityName:"bigquery-sa",          type:"Service",   access:"Read",       actions:"Data Analysis",        status:"Orphaned", environment:"Prod",    openViolations:0, lastUsed:"3d ago"  },
    { severity:"High",     owner:"Felix Wagner",    agent:"Notify Bot",      identityName:"pagerduty-token",      type:"API Key",   access:"Write",      actions:"Incident Alerting",    status:"Active",   environment:"Prod",    openViolations:1, lastUsed:"12m ago" },
    { severity:"Medium",   owner:"Nora Lindqvist",  agent:"Doc Writer",      identityName:"confluence-oauth",     type:"OAuth",     access:"Write",      actions:"Documentation",        status:"Active",   environment:"Dev",     openViolations:0, lastUsed:"2h ago"  },
    { severity:"Medium",   owner:"Ryan O'Brien",    agent:"Log Monitor",     identityName:"datadog-api-key",      type:"API Key",   access:"Read",       actions:"Log Streaming",        status:"Active",   environment:"Prod",    openViolations:0, lastUsed:"5m ago"  },
    { severity:"Medium",   owner:"Zoe Anderson",    agent:"Infra Agent",     identityName:"terraform-cloud-sa",   type:"Service",   access:"Write",      actions:"IaC Provisioning",     status:"Orphaned", environment:"Staging", openViolations:1, lastUsed:"7d ago"  },
    { severity:"Medium",   owner:"Carmen Flores",   agent:"Email Agent",     identityName:"sendgrid-api-key",     type:"API Key",   access:"Write",      actions:"Email Delivery",       status:"Active",   environment:"Prod",    openViolations:0, lastUsed:"20m ago" },
    { severity:"Low",      owner:"Sam Nguyen",      agent:"Test Runner",     identityName:"github-test-token",    type:"MCP Token", access:"Read",       actions:"Test Execution",       status:"Active",   environment:"Dev",     openViolations:0, lastUsed:"1h ago"  },
];

// ── Generate bulk rows for enterprise feel ────────────────────────────────────
const pick = (arr, i) => arr[i % arr.length];

const GENERATED = Array.from({ length: 110 }, (_, i) => {
    const idx   = i + 21;
    const sev   = pick(SEVS,      [0,0,0,1,1,2,3][i % 7]); // weight toward Critical/High
    const statusArr = [0,0,1][i % 3]; // 2/3 Active
    return {
        severity:     sev,
        owner:        pick(OWNERS,     idx),
        agent:        pick(AGENTS,     idx + 3),
        identityName: `${pick(IDENTITIES, idx)}-${idx}`,
        type:         pick(TYPES,      idx + 1),
        access:       pick(ACCESSES,   idx + 2),
        actions:      pick(ACTIONS,    idx + 5),
        status:       statusArr === 1 ? "Orphaned" : "Active",
        environment:  pick(ENVS,       idx),
        openViolations: (idx * 3) % 6,
        lastUsed:     pick(LAST_USED,  idx),
    };
});

const ALL_RAW = [...CURATED, ...GENERATED].sort((a, b) => SEV_ORD[b.severity] - SEV_ORD[a.severity]);

const tableData = ALL_RAW.map((r, i) => ({
    ...r,
    id:            i + 1,
    severityOrder: SEV_ORD[r.severity],
    severityComp:  sevBadge(r.severity),
    identityComp:  <Text variant="bodyMd" fontWeight="medium">{r.identityName}</Text>,
    typeComp:      <Badge>{r.type}</Badge>,
    statusComp:    statusBadge(r.status),
}));

// ── Headers ───────────────────────────────────────────────────────────────────
const headers = [
    { text: "Severity",    value: "severityComp",   title: "Severity",    sortKey: "severityOrder", sortActive: true },
    { text: "Identity",    value: "identityComp",   title: "Identity"   },
    { text: "Owner",       value: "owner",          title: "Owner",       type: CellType.TEXT },
    { text: "Agent",       value: "agent",          title: "Agent",       type: CellType.TEXT },
    { text: "Type",        value: "typeComp",       title: "Type"       },
    { text: "Access",      value: "access",         title: "Access",      type: CellType.TEXT },
    { text: "Actions",     value: "actions",        title: "Actions",     type: CellType.TEXT },
    { text: "Status",      value: "statusComp",     title: "Status"     },
    { text: "Environment", value: "environment",    title: "Environment", type: CellType.TEXT },
    { text: "Violations",  value: "openViolations", title: "Violations",  type: CellType.TEXT },
    { text: "Last Used",   value: "lastUsed",       title: "Last Used",   type: CellType.TEXT },
];

const sortOptions = [
    { label: "Severity",    value: "severity asc",        directionLabel: "Critical first", sortKey: "severityOrder",  columnIndex: 0 },
    { label: "Severity",    value: "severity desc",       directionLabel: "Low first",      sortKey: "severityOrder",  columnIndex: 0 },
    { label: "Violations",  value: "openViolations desc", directionLabel: "Most",           sortKey: "openViolations", columnIndex: 9 },
    { label: "Violations",  value: "openViolations asc",  directionLabel: "Least",          sortKey: "openViolations", columnIndex: 9 },
];

// ── Summary ───────────────────────────────────────────────────────────────────
const TOTAL      = tableData.length;
const HIGH_RISK  = tableData.filter((r) => r.severity === "Critical" || r.severity === "High").length;
const ORPHANED   = tableData.filter((r) => r.status === "Orphaned").length;

const summaryItems = [
    { title: "Total Identities",     data: TOTAL.toLocaleString() },
    { title: "High Risk Identities", data: HIGH_RISK.toLocaleString() },
    { title: "Orphaned / Unused",    data: ORPHANED.toLocaleString() },
];

// ── Chart data ────────────────────────────────────────────────────────────────
const severityDonutData = {
    Critical: { text: 738, color: "#AE2E24" },
    High:     { text: 184, color: "#F5A6B0" },
    Medium:   { text: 184, color: "#F5D88E" },
    Low:      { text: 124, color: "#D9D9D9" },
};
const typeDonutData = {
    "OAuth Token": { text: 369, color: "#7C3AED" },
    "API Key":     { text: 308, color: "#F97316" },
    "Service":     { text: 246, color: "#EAB308" },
    "MCP Token":   { text: 307, color: "#EF4444" },
};
const usageLineData = [{
    data: [
        [Date.UTC(2026, 2, 30), 180],
        [Date.UTC(2026, 2, 31), 210],
        [Date.UTC(2026, 3,  1), 195],
        [Date.UTC(2026, 3,  2), 280],
        [Date.UTC(2026, 3,  3), 240],
        [Date.UTC(2026, 3,  4), 195],
        [Date.UTC(2026, 3,  5), 320],
    ],
    color: "#7C3AED",
    name: "Requests",
}];

// ── Donut card: chart LEFT, legend RIGHT ──────────────────────────────────────
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
export default function IdentitiesPage() {
    const { tabsInfo } = useTable();
    const tableSelectedTab  = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const initialSelectedTab = tableSelectedTab[window.location.pathname] || "all";

    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected] = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );

    const dataByTab = useMemo(() => ({
        all:      tableData,
        active:   tableData.filter((r) => r.status === "Active"),
        orphaned: tableData.filter((r) => r.status === "Orphaned"),
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
            title="Identities"
            isFirstPage
            components={[
                <SummaryCardInfo key="summary" summaryItems={summaryItems} />,

                <HorizontalGrid key="charts" columns={3} gap="4">
                    <DonutCard title="Identity Severity" donutData={severityDonutData} />
                    <InfoCard
                        title="Identity Usage Timeline"
                        component={
                            <LineChart
                                data={usageLineData}
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
                                        title: { text: "Requests" },
                                        gridLineWidth: 1,
                                        min: 0,
                                    },
                                    legend: { enabled: true },
                                }}
                            />
                        }
                    />
                    <DonutCard title="Identity Type" donutData={typeDonutData} />
                </HorizontalGrid>,

                <GithubSimpleTable
                    key="identities-table"
                    data={dataByTab[selectedTab]}
                    headers={headers}
                    resourceName={resourceName}
                    sortOptions={sortOptions}
                    filters={[]}
                    selectable={false}
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

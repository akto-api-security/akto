import { Badge, Box, Card, HorizontalGrid, Text, VerticalStack } from "@shopify/polaris";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";

const resourceName = { singular: "violation", plural: "violations" };

const SEVERITY_STATUS = { Critical: "critical", High: "warning", Medium: "attention", Low: "" };
const VSTATUS_MAP = { Open: "critical", "In Review": "warning", Resolved: "success", Suppressed: "" };

const sevBadge   = (sev)    => <Badge status={SEVERITY_STATUS[sev] || ""}>{sev}</Badge>;
const vstatusBadge = (s)    => <Badge status={VSTATUS_MAP[s] || ""}>{s}</Badge>;

const RAW_VIOLATIONS = [
    { id: 1,  severity: "Critical", identity: "aws-cursor-key",      policy: "No Admin Keys for LLM Agents",        agent: "Cursor Prod",     owner: "Eve",      identityType: "API Key",   vstatus: "Open",       detectedAt: "Apr 1, 2026",  riskScore: 98 },
    { id: 2,  severity: "Critical", identity: "github-oauth-456",    policy: "Orphaned Credentials Must Be Revoked", agent: "Code Reviewer",   owner: "Adam",     identityType: "MCP Token", vstatus: "Open",       detectedAt: "Mar 29, 2026", riskScore: 95 },
    { id: 3,  severity: "Critical", identity: "stripe-key",          policy: "No Admin Keys for LLM Agents",        agent: "Finance Bot",     owner: "Evelyn",   identityType: "API Key",   vstatus: "Open",       detectedAt: "Mar 31, 2026", riskScore: 97 },
    { id: 4,  severity: "Critical", identity: "aws-env-sa",          policy: "Orphaned Credentials Must Be Revoked", agent: "New Env Agent",  owner: "John",     identityType: "Service",   vstatus: "In Review",  detectedAt: "Mar 28, 2026", riskScore: 92 },
    { id: 5,  severity: "Critical", identity: "aws-cursor-key",      policy: "Excessive Scope: Admin on Non-Admin Agent", agent: "Cursor Prod", owner: "Eve",   identityType: "API Key",   vstatus: "Open",       detectedAt: "Apr 2, 2026",  riskScore: 96 },
    { id: 6,  severity: "High",     identity: "github-actions-key",  policy: "Write Access Without MFA Binding",    agent: "CI Bot",          owner: "Theodore", identityType: "API Key",   vstatus: "Open",       detectedAt: "Apr 1, 2026",  riskScore: 82 },
    { id: 7,  severity: "High",     identity: "jira-token",          policy: "Token Unused > 30 Days",              agent: "My Assistant",    owner: "Sarah",    identityType: "MCP Token", vstatus: "In Review",  detectedAt: "Mar 25, 2026", riskScore: 78 },
    { id: 8,  severity: "High",     identity: "k8s-service-account", policy: "Admin Cluster Role on Non-Prod Agent", agent: "Deploy Bot",     owner: "Lucas",    identityType: "Service",   vstatus: "Open",       detectedAt: "Mar 30, 2026", riskScore: 85 },
    { id: 9,  severity: "High",     identity: "internal-api-token",  policy: "Orphaned Credentials Must Be Revoked", agent: "Connector",      owner: "Luke",     identityType: "Service",   vstatus: "Open",       detectedAt: "Mar 26, 2026", riskScore: 80 },
    { id: 10, severity: "High",     identity: "snowflake-sa-key",    policy: "Production Keys Require Rotation < 90d", agent: "Data Sync",    owner: "Marcus",   identityType: "Service",   vstatus: "In Review",  detectedAt: "Mar 22, 2026", riskScore: 76 },
    { id: 11, severity: "High",     identity: "pagerduty-token",     policy: "Write Token Without Scope Restriction", agent: "Notify Bot",   owner: "Felix",    identityType: "API Key",   vstatus: "Open",       detectedAt: "Apr 2, 2026",  riskScore: 74 },
    { id: 12, severity: "Medium",   identity: "bigquery-sa",         policy: "Orphaned Credentials Must Be Revoked", agent: "Analytics Agent", owner: "Aria",   identityType: "Service",   vstatus: "Resolved",   detectedAt: "Mar 15, 2026", riskScore: 55 },
    { id: 13, severity: "Medium",   identity: "terraform-cloud-sa",  policy: "Orphaned Credentials Must Be Revoked", agent: "Infra Agent",   owner: "Zoe",      identityType: "Service",   vstatus: "Suppressed", detectedAt: "Mar 10, 2026", riskScore: 50 },
    { id: 14, severity: "Medium",   identity: "vscode-oauth",        policy: "Token Never Used Post Issuance",       agent: "Agent Studio",   owner: "Noah",     identityType: "MCP Token", vstatus: "In Review",  detectedAt: "Mar 27, 2026", riskScore: 60 },
    { id: 15, severity: "Medium",   identity: "entra-service",       policy: "Identity Management Keys Require Approval", agent: "Entra Bot", owner: "Adam",   identityType: "Service",   vstatus: "Resolved",   detectedAt: "Mar 18, 2026", riskScore: 58 },
    { id: 16, severity: "Low",      identity: "github-test-token",   policy: "Read Tokens Should Be Scoped to Repo",  agent: "Test Runner",  owner: "Sam",      identityType: "MCP Token", vstatus: "Resolved",   detectedAt: "Mar 5, 2026",  riskScore: 30 },
    { id: 17, severity: "Low",      identity: "confluence-oauth",    policy: "OAuth Token Refresh > 60 Days",        agent: "Doc Writer",     owner: "Nora",     identityType: "OAuth",     vstatus: "Resolved",   detectedAt: "Mar 1, 2026",  riskScore: 25 },
];

const data = RAW_VIOLATIONS.map((r) => ({
    ...r,
    severityComp:    sevBadge(r.severity),
    vstatusComp:     vstatusBadge(r.vstatus),
    identityComp:    <Text variant="bodyMd" fontWeight="medium">{r.identity}</Text>,
}));

const headers = [
    { text: "Severity",        value: "severityComp",  title: "Severity"        },
    { text: "Identity",        value: "identityComp",  title: "Identity"        },
    { text: "Policy Violated", value: "policy",        title: "Policy Violated", type: CellType.TEXT },
    { text: "Agent",           value: "agent",         title: "Agent",           type: CellType.TEXT },
    { text: "Owner",           value: "owner",         title: "Owner",           type: CellType.TEXT },
    { text: "Identity Type",   value: "identityType",  title: "Identity Type",   type: CellType.TEXT },
    { text: "Status",          value: "vstatusComp",   title: "Status"          },
    { text: "Detected",        value: "detectedAt",    title: "Detected",        type: CellType.TEXT },
    { text: "Risk Score",      value: "riskScore",     title: "Risk Score",      type: CellType.TEXT },
];

const sortOptions = [
    { label: "Risk Score", value: "riskScore desc", directionLabel: "Highest", sortKey: "riskScore", columnIndex: 8 },
    { label: "Risk Score", value: "riskScore asc",  directionLabel: "Lowest",  sortKey: "riskScore", columnIndex: 8 },
    { label: "Severity",   value: "severity asc",   directionLabel: "Critical first", sortKey: "severity", columnIndex: 0 },
];

const OPEN     = RAW_VIOLATIONS.filter((v) => v.vstatus === "Open").length;
const IN_REVIEW = RAW_VIOLATIONS.filter((v) => v.vstatus === "In Review").length;
const RESOLVED  = RAW_VIOLATIONS.filter((v) => v.vstatus === "Resolved").length;

function StatCard({ label, value }) {
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="1">
                    <Text variant="bodyMd" color="subdued">{label}</Text>
                    <Text variant="heading2xl" as="h2" fontWeight="bold">{value}</Text>
                </VerticalStack>
            </Box>
        </Card>
    );
}

export default function ViolationsPage() {
    return (
        <PageWithMultipleCards
            title="Violations"
            isFirstPage
            components={[
                <HorizontalGrid key="stats" columns={3} gap="4">
                    <StatCard label="Open Violations" value={OPEN} />
                    <StatCard label="In Review"        value={IN_REVIEW} />
                    <StatCard label="Resolved (30d)"   value={RESOLVED} />
                </HorizontalGrid>,
                <GithubSimpleTable
                    key="violations-table"
                    data={data}
                    headers={headers}
                    resourceName={resourceName}
                    sortOptions={sortOptions}
                    filterOptions={[]}
                />,
            ]}
        />
    );
}

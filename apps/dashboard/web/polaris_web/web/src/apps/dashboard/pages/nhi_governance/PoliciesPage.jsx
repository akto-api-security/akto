import { Badge, Box, Card, HorizontalGrid, Text, VerticalStack } from "@shopify/polaris";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";

const resourceName = { singular: "policy", plural: "policies" };

const SEV_STATUS   = { Critical: "critical", High: "warning", Medium: "attention", Low: "" };
const PSTATUS_MAP  = { Active: "success", Draft: "attention", Disabled: "" };
const ENFORCE_MAP  = { Block: "critical", Alert: "warning", "Log Only": "info" };

const sevBadge      = (s) => <Badge status={SEV_STATUS[s] || ""}>{s}</Badge>;
const pstatusBadge  = (s) => <Badge status={PSTATUS_MAP[s] || ""}>{s}</Badge>;
const enforceBadge  = (s) => <Badge status={ENFORCE_MAP[s] || ""}>{s}</Badge>;

const RAW_POLICIES = [
    { id: 1,  name: "No Admin Keys for LLM Agents",               category: "Excessive Privilege", severity: "Critical", scope: "All Agents",         enforcement: "Block",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 1, 2026"  },
    { id: 2,  name: "Orphaned Credentials Must Be Revoked",        category: "Lifecycle",           severity: "Critical", scope: "All Identities",     enforcement: "Block",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 1, 2026"  },
    { id: 3,  name: "Excessive Scope: Admin on Non-Admin Agent",   category: "Excessive Privilege", severity: "Critical", scope: "API Key, Service",   enforcement: "Block",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 10, 2026" },
    { id: 4,  name: "Write Access Without MFA Binding",            category: "Authentication",      severity: "High",     scope: "OAuth, MCP Token",   enforcement: "Alert",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 5, 2026"  },
    { id: 5,  name: "Token Unused > 30 Days",                      category: "Lifecycle",           severity: "High",     scope: "All Identities",     enforcement: "Alert",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 1, 2026"  },
    { id: 6,  name: "Admin Cluster Role on Non-Prod Agent",        category: "Environment Risk",    severity: "High",     scope: "Service",            enforcement: "Block",    pstatus: "Active",   createdBy: "Adam",          lastUpdated: "Mar 15, 2026" },
    { id: 7,  name: "Production Keys Require Rotation < 90d",      category: "Lifecycle",           severity: "High",     scope: "API Key, Service",   enforcement: "Alert",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 1, 2026"  },
    { id: 8,  name: "Write Token Without Scope Restriction",       category: "Excessive Privilege", severity: "High",     scope: "API Key",            enforcement: "Alert",    pstatus: "Active",   createdBy: "Eve",           lastUpdated: "Mar 20, 2026" },
    { id: 9,  name: "Identity Management Keys Require Approval",   category: "Access Control",      severity: "Medium",   scope: "Service",            enforcement: "Alert",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 1, 2026"  },
    { id: 10, name: "Token Never Used Post Issuance",              category: "Lifecycle",           severity: "Medium",   scope: "All Identities",     enforcement: "Alert",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Feb 20, 2026" },
    { id: 11, name: "OAuth Token Refresh > 60 Days",               category: "Lifecycle",           severity: "Low",      scope: "OAuth",              enforcement: "Log Only", pstatus: "Active",   createdBy: "System",        lastUpdated: "Feb 15, 2026" },
    { id: 12, name: "Read Tokens Should Be Scoped to Repo",        category: "Least Privilege",     severity: "Low",      scope: "MCP Token",          enforcement: "Log Only", pstatus: "Active",   createdBy: "System",        lastUpdated: "Feb 15, 2026" },
    { id: 13, name: "No Cross-Environment Token Reuse",            category: "Environment Risk",    severity: "Critical", scope: "All Identities",     enforcement: "Block",    pstatus: "Active",   createdBy: "Sarah",         lastUpdated: "Mar 25, 2026" },
    { id: 14, name: "MCP Tokens Must Have Expiry < 7 Days",        category: "Lifecycle",           severity: "High",     scope: "MCP Token",          enforcement: "Alert",    pstatus: "Draft",    createdBy: "Noah",          lastUpdated: "Apr 1, 2026"  },
    { id: 15, name: "Service Accounts Cannot Have LLM Access",     category: "Excessive Privilege", severity: "Critical", scope: "Service",            enforcement: "Block",    pstatus: "Draft",    createdBy: "Theodore",      lastUpdated: "Apr 2, 2026"  },
    { id: 16, name: "API Keys Older Than 180 Days Auto-Flag",      category: "Lifecycle",           severity: "Medium",   scope: "API Key",            enforcement: "Alert",    pstatus: "Active",   createdBy: "Marcus",        lastUpdated: "Mar 18, 2026" },
    { id: 17, name: "Agent Cannot Have Both Read and Write Admin",  category: "Least Privilege",    severity: "High",     scope: "All Identities",     enforcement: "Alert",    pstatus: "Active",   createdBy: "System",        lastUpdated: "Mar 8, 2026"  },
    { id: 18, name: "Staging Identities Must Not Access Prod",     category: "Environment Risk",    severity: "Critical", scope: "All Identities",     enforcement: "Block",    pstatus: "Disabled", createdBy: "Lucas",         lastUpdated: "Feb 28, 2026" },
];

const data = RAW_POLICIES.map((r) => ({
    ...r,
    severityComp:   sevBadge(r.severity),
    pstatusComp:    pstatusBadge(r.pstatus),
    enforcementComp: enforceBadge(r.enforcement),
    nameComp: <Text variant="bodyMd" fontWeight="medium">{r.name}</Text>,
}));

const headers = [
    { text: "Policy Name",   value: "nameComp",        title: "Policy Name"   },
    { text: "Category",      value: "category",         title: "Category",      type: CellType.TEXT },
    { text: "Severity",      value: "severityComp",    title: "Severity"       },
    { text: "Scope",         value: "scope",            title: "Scope",         type: CellType.TEXT },
    { text: "Enforcement",   value: "enforcementComp", title: "Enforcement"    },
    { text: "Status",        value: "pstatusComp",     title: "Status"         },
    { text: "Created By",    value: "createdBy",        title: "Created By",    type: CellType.TEXT },
    { text: "Last Updated",  value: "lastUpdated",      title: "Last Updated",  type: CellType.TEXT },
];

const sortOptions = [
    { label: "Severity",     value: "severity asc",      directionLabel: "Critical first", sortKey: "severity",     columnIndex: 2 },
    { label: "Last Updated", value: "lastUpdated desc",   directionLabel: "Newest",         sortKey: "lastUpdated",  columnIndex: 7 },
    { label: "Last Updated", value: "lastUpdated asc",    directionLabel: "Oldest",         sortKey: "lastUpdated",  columnIndex: 7 },
];

const ACTIVE   = RAW_POLICIES.filter((p) => p.pstatus === "Active").length;
const BLOCKING = RAW_POLICIES.filter((p) => p.enforcement === "Block").length;
const DRAFTS   = RAW_POLICIES.filter((p) => p.pstatus === "Draft").length;

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

export default function PoliciesPage() {
    return (
        <PageWithMultipleCards
            title="Policies"
            isFirstPage
            components={[
                <HorizontalGrid key="stats" columns={3} gap="4">
                    <StatCard label="Active Policies"  value={ACTIVE} />
                    <StatCard label="Blocking Rules"   value={BLOCKING} />
                    <StatCard label="Drafts"           value={DRAFTS} />
                </HorizontalGrid>,
                <GithubSimpleTable
                    key="policies-table"
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
